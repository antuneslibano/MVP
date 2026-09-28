package br.com.lojabaterias.data

import androidx.room.withTransaction
import br.com.lojabaterias.data.sync.IdGenerator
import br.com.lojabaterias.domain.CardFees
import br.com.lojabaterias.domain.CostLayer
import br.com.lojabaterias.domain.CostLayers
import br.com.lojabaterias.domain.DateRange
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.ReportItem
import br.com.lojabaterias.domain.ReportSale
import br.com.lojabaterias.domain.SaleCalculator
import br.com.lojabaterias.domain.SplitPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Erro de regra de negócio com mensagem pronta para o usuário. */
class BusinessException(message: String) : Exception(message)

/**
 * Sucatas informadas na venda.
 * [returned] deixadas pelo cliente (na amperagem [amperage]); [missing] não deixadas, cobradas por [charge].
 */
data class ScrapInput(
    val returned: Int,
    val missing: Int,
    val amperage: Int,
    val charge: Long,
    /** O cliente levou vale: devolvemos o valor cobrado quando ele trouxer o casco. */
    val voucher: Boolean = false,
) {
    val hasVoucher: Boolean get() = voucher && missing > 0 && charge > 0

    val amperageOrNull: Int? get() = if (returned > 0 && amperage > 0) amperage else null

    companion object {
        /** Sucata não informada. */
        val NONE = ScrapInput(0, 0, 0, 0)
    }
}

/** Boleto no formulário da nota (id nulo = boleto novo). */
data class BillDraft(val id: Long? = null, val dueDate: Long, val amount: Long, val paidAt: Long? = null)

/** Nomes das tabelas sincronizadas (iguais no aparelho e na nuvem). */
object SyncTables {
    const val PRODUCTS = "products"
    const val SALES = "sales"
    const val SALE_ITEMS = "sale_items"
    const val STOCK_MOVEMENTS = "stock_movements"
    const val SCRAP_PRICES = "scrap_prices"
    const val SCRAP_MOVEMENTS = "scrap_movements"
    const val CHARGES = "charge_services"
    const val WARRANTIES = "warranty_claims"
    const val EXPENSES = "expenses"
    const val SALE_PAYMENTS = "sale_payments"
    const val INVOICES = "invoices"
    const val INVOICE_BILLS = "invoice_bills"
}

/**
 * @param onChange chamado após cada alteração local (dispara a sincronização).
 */
class StoreRepository(
    private val db: AppDatabase,
    private val onChange: () -> Unit = {},
    private val feeRates: () -> CardFees = { CardFees.DEFAULT },
) {

    /** Taxas das maquininhas em vigor (configuráveis). */
    val cardFees: CardFees get() = feeRates()

    private val products = db.productDao()
    private val sales = db.saleDao()
    private val movements = db.movementDao()
    private val scraps = db.scrapDao()
    private val sync = db.syncDao()
    private val charges = db.chargeDao()
    private val warranties = db.warrantyDao()
    private val expenses = db.expenseDao()
    private val invoices = db.invoiceDao()

    /** Executa uma alteração em transação e avisa a sincronização. */
    private suspend fun <T> write(block: suspend () -> T): T {
        val result = db.withTransaction { block() }
        onChange()
        return result
    }

    /** Registra exclusões para serem enviadas à nuvem. */
    private suspend fun tomb(table: String, ids: List<Long>) {
        if (ids.isNotEmpty()) sync.insertTombstones(ids.map { Tombstone(tableName = table, recordId = it) })
    }

    private fun now() = System.currentTimeMillis()

    /** Lotes (custo por lote) das baterias de [product] em estoque, sem as extras (custo zero, saem primeiro). */
    private suspend fun layersOf(product: Product): List<CostLayer> =
        costLayersFrom(product, movements.forProduct(product.id), availableExtras(product.id).size)

    /** Lotes do estoque de uma bateria, do mais antigo (sai primeiro) para o mais novo. */
    suspend fun costLayers(productId: Long): List<CostLayer> {
        val p = products.getById(productId) ?: return emptyList()
        return layersOf(p)
    }

    /** Valor a preço de custo (pelos lotes) do estoque de cada bateria. */
    fun observeStockValues(): Flow<Map<Long, Long>> = combine(
        products.observeAll(),
        movements.observeAllRaw(),
        warranties.observeAll(),
    ) { list, moves, claims ->
        val byProduct = moves.groupBy { it.productId }
        val extras = claims.filter { it.isExtra && it.saleId == null }.groupingBy { it.returnedProductId }.eachCount()
        list.associate { p -> p.id to costLayersFrom(p, byProduct[p.id].orEmpty(), extras[p.id] ?: 0).sumOf { it.total } }
    }

    /** Registra uma movimentação de estoque e atualiza o estoque do produto. Retorna o ID da movimentação. */
    private suspend fun moveStock(
        productId: Long,
        quantity: Int,
        type: String,
        note: String?,
        at: Long = now(),
        unitCost: Long? = null,
    ): Long {
        val product = products.getById(productId) ?: throw BusinessException("Bateria não encontrada no estoque")
        val newStock = product.stock + quantity
        products.updateStock(product.id, newStock)
        val m = StockMovement(
            productId = product.id,
            dateTime = at,
            type = type,
            quantity = quantity,
            stockAfter = newStock,
            note = note,
            unitCost = unitCost,
        )
        movements.insert(m)
        return m.id
    }

    /** Desfaz uma movimentação de estoque (usada ao excluir registros de carga/garantia). */
    private suspend fun undoStockMovement(id: Long?) {
        if (id == null) return
        val m = movements.getById(id) ?: return
        products.getById(m.productId)?.let { products.updateStock(it.id, it.stock - m.quantity) }
        tomb(SyncTables.STOCK_MOVEMENTS, listOf(id))
        movements.deleteById(id)
    }

    // ---------------------------------------------------------------- Produtos

    fun observeProducts(): Flow<List<Product>> = products.observeAll()
    fun observeProduct(id: Long): Flow<Product?> = products.observeById(id)
    suspend fun getProduct(id: Long): Product? = products.getById(id)

    /** Cria ou atualiza um produto. O estoque só é definido na criação. */
    suspend fun saveProduct(product: Product): Long = write {
        val model = product.model.trim()
        if (model.isEmpty()) throw BusinessException("Informe o modelo")
        if (product.stock < 0) throw BusinessException("Estoque não pode ser negativo")
        val existing = products.findByModel(model)
        if (existing != null && existing.id != product.id) {
            throw BusinessException("Já existe uma bateria com o modelo $model")
        }
        val current = if (product.id == 0L) null else products.getById(product.id)
        if (current == null) {
            val newId = if (product.id == 0L) IdGenerator.next() else product.id
            val id = products.insert(product.copy(id = newId, model = model, updatedAt = now(), dirty = true))
            if (product.stock > 0) {
                movements.insert(
                    StockMovement(
                        productId = id,
                        dateTime = System.currentTimeMillis(),
                        type = MovementType.INITIAL,
                        quantity = product.stock,
                        stockAfter = product.stock,
                        unitCost = product.cost,
                    )
                )
            }
            id
        } else {
            // O estoque é alterado apenas por entrada/ajuste/vendas.
            products.update(
                product.copy(
                    model = model,
                    stock = current.stock,
                    createdAt = current.createdAt,
                    updatedAt = now(),
                    dirty = true,
                )
            )
            product.id
        }
    }

    suspend fun deleteProduct(id: Long): Unit = write {
        tomb(SyncTables.STOCK_MOVEMENTS, sync.stockMovementIdsForProduct(id))
        tomb(SyncTables.PRODUCTS, listOf(id))
        movements.deleteForProduct(id)
        products.delete(id)
    }

    /** Entrada de mercadoria. Se [newUnitCost] for informado, atualiza o custo atual do produto. */
    suspend fun addStock(productId: Long, quantity: Int, newUnitCost: Long?, note: String?): Unit = write {
        if (quantity <= 0) throw BusinessException("Informe a quantidade de entrada")
        val product = products.getById(productId) ?: throw BusinessException("Produto não encontrado")
        val newStock = product.stock + quantity
        val updated = if (newUnitCost != null && newUnitCost > 0) {
            product.copy(stock = newStock, cost = newUnitCost, updatedAt = now(), dirty = true)
        } else {
            product.copy(stock = newStock)
        }
        products.update(updated)
        movements.insert(
            StockMovement(
                productId = productId,
                dateTime = System.currentTimeMillis(),
                type = MovementType.ENTRY,
                quantity = quantity,
                stockAfter = newStock,
                note = note?.trim()?.ifEmpty { null },
                unitCost = updated.cost,
            )
        )
    }

    /** Ajuste de estoque: define a quantidade real contada. */
    suspend fun adjustStock(productId: Long, newStock: Int, note: String?): Unit = write {
        if (newStock < 0) throw BusinessException("Estoque não pode ser negativo")
        val product = products.getById(productId) ?: throw BusinessException("Produto não encontrado")
        val delta = newStock - product.stock
        if (delta == 0) return@write
        products.updateStock(productId, newStock)
        movements.insert(
            StockMovement(
                productId = productId,
                dateTime = System.currentTimeMillis(),
                type = MovementType.ADJUSTMENT,
                quantity = delta,
                stockAfter = newStock,
                note = note?.trim()?.ifEmpty { null },
            )
        )
    }

    /**
     * Exclui uma movimentação de estoque (estoque inicial, entrada ou ajuste) e desfaz o efeito dela no estoque.
     * Movimentações de vendas só saem excluindo a própria venda.
     */
    suspend fun deleteStockMovement(id: Long): Unit = write {
        val m = movements.getById(id) ?: throw BusinessException("Movimentação não encontrada")
        if (!MovementType.isDeletable(m.type)) {
            throw BusinessException("Movimentação de venda: para removê-la, exclua ou edite a venda")
        }
        val product = products.getById(m.productId)
        if (product != null) {
            val newStock = product.stock - m.quantity
            if (newStock < 0) {
                throw BusinessException("Não é possível excluir: o estoque de ${product.model} ficaria negativo ($newStock)")
            }
            products.updateStock(product.id, newStock)
        }
        tomb(SyncTables.STOCK_MOVEMENTS, listOf(id))
        movements.deleteById(id)
    }

    fun observeStockMovementsInRange(range: DateRange): Flow<List<MovementWithModel>> =
        movements.observeInRange(range.start, range.end)

    fun observeMovements(productId: Long): Flow<List<MovementWithModel>> = movements.observeForProduct(productId)
    fun observeRecentMovements(limit: Int = 300): Flow<List<MovementWithModel>> = movements.observeRecent(limit)

    // ------------------------------------------------------------------ Vendas

    fun observeRecentSales(limit: Int): Flow<List<SaleWithItems>> = sales.observeRecent(limit)
    fun observeSales(range: DateRange): Flow<List<SaleWithItems>> = sales.observeInRange(range.start, range.end)
    fun observeSale(id: Long): Flow<SaleWithItems?> = sales.observeWithItems(id)
    suspend fun getSale(id: Long): SaleWithItems? = sales.getWithItems(id)
    fun observeSummary(range: DateRange): Flow<PeriodSummary> = sales.observeSummary(range.start, range.end)
    fun observeActiveSales(range: DateRange): Flow<List<SaleWithItems>> =
        sales.observeActiveInRange(range.start, range.end)

    /**
     * Registra a venda em uma única transação:
     * grava venda + item (com custo histórico), dá baixa no estoque, registra a movimentação
     * e, se o cliente deixou sucata, dá entrada no estoque de sucatas.
     */
    suspend fun registerSale(
        productId: Long,
        quantity: Int,
        method: PaymentMethod,
        unitPrice: Long,
        discount: Long,
        dateTime: Long,
        scrap: ScrapInput = ScrapInput.NONE,
        payments: List<Pair<PaymentMethod, Long>> = emptyList(),
    ): Long = write {
        val product = products.getById(productId) ?: throw BusinessException("Produto não encontrado")
        val split = payments.size > 1
        SaleCalculator.validate(unitPrice, quantity, if (split) 0 else discount, product.stock)?.let { throw BusinessException(it) }
        if (split) SplitPayment.validate(payments)?.let { throw BusinessException(it) }
        validateScrap(quantity, scrap)
        rebuildOrphanExtras()
        val extras = availableExtras(product.id).take(quantity)
        // Custo pelos lotes: as baterias mais antigas saem primeiro
        val unitCost = CostLayers.unitCostOf(layersOf(product), quantity - extras.size, product.cost)
        val totals = totalsFor(unitPrice, quantity, discount, unitCost, scrap.charge, method, payments, extras.size)
        val saleId = sales.insertSale(
            Sale(
                dateTime = dateTime,
                paymentMethod = mainMethod(method, payments).name,
                grossAmount = totals.grossAmount,
                discount = totals.discount,
                finalAmount = totals.finalAmount,
                totalCost = totals.totalCost,
                grossProfit = totals.grossProfit,
                scrapReturned = scrap.returned,
                scrapAmperage = scrap.amperageOrNull,
                scrapMissing = scrap.missing,
                scrapCharge = scrap.charge,
                cardFee = totals.cardFee,
            )
        )
        sales.insertItem(
            SaleItem(
                saleId = saleId,
                productId = product.id,
                modelSnapshot = product.model,
                quantity = quantity,
                unitPrice = unitPrice,
                unitCost = unitCost,
                subtotal = totals.grossAmount,
            )
        )
        useExtras(extras, saleId)
        setPayments(saleId, payments)
        val newStock = product.stock - quantity
        products.updateStock(product.id, newStock)
        movements.insert(
            StockMovement(
                productId = product.id,
                dateTime = dateTime,
                type = MovementType.SALE,
                quantity = -quantity,
                stockAfter = newStock,
                saleId = saleId,
            )
        )
        if (scrap.returned > 0) {
            scraps.insertMovement(
                ScrapMovement(
                    dateTime = dateTime,
                    type = ScrapMovementType.SALE_IN,
                    amperage = scrap.amperage,
                    quantity = scrap.returned,
                    saleId = saleId,
                )
            )
        }
        setVoucher(saleId, scrap, dateTime)
        saleId
    }

    /** O cliente levou vale nesta venda? */
    suspend fun hasVoucher(saleId: Long): Boolean =
        scraps.getAllMovements().any { it.type == ScrapMovementType.VOUCHER_ISSUED && it.saleId == saleId }

    /** Grava (ou retira) a marca de vale da venda, conforme a sucata informada. */
    private suspend fun setVoucher(saleId: Long, scrap: ScrapInput, at: Long) {
        val existing = scraps.getAllMovements().filter { it.type == ScrapMovementType.VOUCHER_ISSUED && it.saleId == saleId }
        if (scrap.hasVoucher) {
            if (existing.isEmpty()) {
                scraps.insertMovement(
                    ScrapMovement(dateTime = at, type = ScrapMovementType.VOUCHER_ISSUED, amperage = 0, quantity = 0,
                        amount = scrap.charge, saleId = saleId)
                )
            }
        } else if (existing.isNotEmpty()) {
            tomb(SyncTables.SCRAP_MOVEMENTS, existing.map { it.id })
            existing.forEach { scraps.deleteMovement(it.id) }
        }
    }

    /**
     * Edita uma venda. O custo unitário histórico é preservado.
     * A diferença de quantidade é refletida no estoque, e a diferença de sucatas no estoque de sucatas.
     */
    suspend fun updateSale(
        saleId: Long,
        quantity: Int,
        method: PaymentMethod,
        unitPrice: Long,
        discount: Long,
        dateTime: Long,
        scrap: ScrapInput = ScrapInput.NONE,
        payments: List<Pair<PaymentMethod, Long>> = emptyList(),
    ): Unit = write {
        val current = sales.getWithItems(saleId) ?: throw BusinessException("Venda não encontrada")
        if (current.sale.isCanceled) throw BusinessException("Venda cancelada não pode ser editada")
        val item = current.items.singleOrNull() ?: throw BusinessException("Venda inválida")
        val delta = quantity - item.quantity
        val product = products.getById(item.productId)
        val available = item.quantity + (product?.stock ?: 0)
        if (product == null && delta != 0) {
            throw BusinessException("O produto desta venda foi excluído; a quantidade não pode ser alterada")
        }
        val split = payments.size > 1
        SaleCalculator.validate(unitPrice, quantity, if (split) 0 else discount, available)?.let { throw BusinessException(it) }
        if (split) SplitPayment.validate(payments)?.let { throw BusinessException(it) }
        validateScrap(quantity, scrap)
        rebuildOrphanExtras()
        releaseExtras(saleId)
        val extras = availableExtras(item.productId).take(quantity)
        val totals = totalsFor(unitPrice, quantity, discount, item.unitCost, scrap.charge, method, payments, extras.size)
        useExtras(extras, saleId)
        setPayments(saleId, payments)
        val now = System.currentTimeMillis()

        sales.updateItem(
            item.copy(quantity = quantity, unitPrice = unitPrice, subtotal = totals.grossAmount, updatedAt = now, dirty = true)
        )
        sales.updateSale(
            current.sale.copy(
                dateTime = dateTime,
                paymentMethod = mainMethod(method, payments).name,
                grossAmount = totals.grossAmount,
                discount = totals.discount,
                finalAmount = totals.finalAmount,
                totalCost = totals.totalCost,
                grossProfit = totals.grossProfit,
                scrapReturned = scrap.returned,
                scrapAmperage = scrap.amperageOrNull,
                scrapMissing = scrap.missing,
                scrapCharge = scrap.charge,
                cardFee = totals.cardFee,
                updatedAt = now,
                dirty = true,
            )
        )
        if (product != null && delta != 0) {
            val newStock = product.stock - delta
            products.updateStock(product.id, newStock)
            movements.insert(
                StockMovement(
                    productId = product.id,
                    dateTime = now,
                    type = MovementType.SALE_EDIT,
                    quantity = -delta,
                    stockAfter = newStock,
                    saleId = saleId,
                )
            )
        }
        // Sucatas: desfaz a entrada anterior e registra a nova, se algo mudou.
        val old = current.sale
        val oldAmperage = old.scrapAmperage ?: 0
        if (old.scrapReturned != scrap.returned || (scrap.returned > 0 && oldAmperage != scrap.amperage)) {
            if (old.scrapReturned > 0 && oldAmperage > 0) {
                removeScrap(oldAmperage, old.scrapReturned, ScrapMovementType.SALE_EDIT, saleId, now)
            }
            if (scrap.returned > 0) {
                scraps.insertMovement(
                    ScrapMovement(
                        dateTime = now,
                        type = ScrapMovementType.SALE_EDIT,
                        amperage = scrap.amperage,
                        quantity = scrap.returned,
                        saleId = saleId,
                    )
                )
            }
        }
        setVoucher(saleId, scrap, dateTime)
    }

    /**
     * Cancela a venda: devolve o estoque, retira a venda dos relatórios (status CANCELED)
     * e retira do estoque de sucatas as sucatas que vieram com ela (devolvidas ao cliente).
     */
    suspend fun cancelSale(saleId: Long): Unit = write {
        val current = sales.getWithItems(saleId) ?: throw BusinessException("Venda não encontrada")
        if (current.sale.isCanceled) return@write
        val now = System.currentTimeMillis()
        for (item in current.items) {
            val product = products.getById(item.productId) ?: continue
            val newStock = product.stock + item.quantity
            products.updateStock(product.id, newStock)
            movements.insert(
                StockMovement(
                    productId = product.id,
                    dateTime = now,
                    type = MovementType.SALE_CANCEL,
                    quantity = item.quantity,
                    stockAfter = newStock,
                    saleId = saleId,
                )
            )
        }
        val s = current.sale
        if (s.scrapReturned > 0 && (s.scrapAmperage ?: 0) > 0) {
            removeScrap(s.scrapAmperage ?: 0, s.scrapReturned, ScrapMovementType.SALE_CANCEL, saleId, now)
        }
        releaseExtras(saleId)
        sales.updateSale(s.copy(status = SaleStatus.CANCELED, canceledAt = now, updatedAt = now, dirty = true))
    }

    /**
     * Exclui a venda definitivamente, como se nunca tivesse existido:
     * devolve o estoque (se ainda estava ativa) e apaga as movimentações de estoque e de sucata ligadas a ela.
     */
    suspend fun deleteSale(saleId: Long): Unit = write {
        val current = sales.getWithItems(saleId) ?: throw BusinessException("Venda não encontrada")
        if (!current.sale.isCanceled) {
            for (item in current.items) {
                val product = products.getById(item.productId) ?: continue
                products.updateStock(product.id, product.stock + item.quantity)
            }
        }
        releaseExtras(saleId)
        tomb(SyncTables.STOCK_MOVEMENTS, sync.stockMovementIdsForSale(saleId))
        tomb(SyncTables.SCRAP_MOVEMENTS, sync.scrapMovementIdsForSale(saleId))
        tomb(SyncTables.SALE_ITEMS, sync.saleItemIds(saleId))
        tomb(SyncTables.SALE_PAYMENTS, sync.salePaymentIds(saleId))
        sales.deletePaymentsFor(saleId)
        tomb(SyncTables.SALES, listOf(saleId))
        movements.deleteForSale(saleId)
        scraps.deleteForSale(saleId)
        sales.deleteItemsForSale(saleId)
        sales.deleteSale(saleId)
    }

    /** Totais da venda: normal (uma forma de pagamento) ou dividida (soma das partes). */
    private fun totalsFor(
        unitPrice: Long, quantity: Int, discount: Long, unitCost: Long, scrapCharge: Long,
        method: PaymentMethod, payments: List<Pair<PaymentMethod, Long>>, freeUnits: Int,
    ) = if (payments.size > 1) {
        SplitPayment.compute(unitPrice, quantity, unitCost, scrapCharge, payments, cardFees::rateFor, freeUnits)
    } else {
        SaleCalculator.compute(unitPrice, quantity, discount, unitCost, scrapCharge, cardFees.rateFor(method), freeUnits)
    }

    /** No pagamento dividido, a venda fica marcada com a forma de maior valor. */
    private fun mainMethod(method: PaymentMethod, payments: List<Pair<PaymentMethod, Long>>): PaymentMethod =
        if (payments.size > 1) payments.maxBy { it.second }.first else method

    /** Troca as partes do pagamento dividido da venda (nenhuma = pagamento único). */
    private suspend fun setPayments(saleId: Long, payments: List<Pair<PaymentMethod, Long>>) {
        tomb(SyncTables.SALE_PAYMENTS, sync.salePaymentIds(saleId))
        sales.deletePaymentsFor(saleId)
        if (payments.size > 1) {
            sales.insertPayments(payments.map { (m, amount) -> SalePayment(saleId = saleId, method = m.name, amount = amount) })
        }
    }

    private fun validateScrap(quantity: Int, scrap: ScrapInput) {
        SaleCalculator.validateScrap(quantity, scrap.returned, scrap.missing, scrap.amperageOrNull, scrap.charge)
            ?.let { throw BusinessException(it) }
    }

    /** Retira sucatas do estoque sem deixá-lo negativo (se já foram vendidas, retira o que houver). */
    private suspend fun removeScrap(amperage: Int, quantity: Int, type: String, saleId: Long?, now: Long) {
        val available = scraps.stockOf(amperage)
        val toRemove = minOf(quantity, available)
        if (toRemove > 0) {
            scraps.insertMovement(
                ScrapMovement(dateTime = now, type = type, amperage = amperage, quantity = -toRemove, saleId = saleId)
            )
        }
    }

    // ---------------------------------------------------------- Baterias na carga

    fun observeCharges(): Flow<List<ChargeService>> = charges.observeAll()
    fun observeCharge(id: Long): Flow<ChargeService?> = charges.observeById(id)
    fun observeChargesInRange(range: DateRange): Flow<List<ChargeService>> = charges.observeInRange(range.start, range.end)
    suspend fun getCharge(id: Long): ChargeService? = charges.getById(id)

    /**
     * Recebe uma bateria para carga.
     * [loaned] = a loja emprestou uma bateria usada ao cliente (não mexe no estoque; qual foi fica na observação).
     */
    suspend fun createCharge(
        customerName: String,
        phone: String,
        batteryDescription: String,
        receivedAt: Long,
        price: Long,
        paid: Boolean,
        paymentMethod: PaymentMethod?,
        loaned: Boolean,
        note: String?,
    ): Long = write {
        val name = customerName.trim()
        if (name.isEmpty()) throw BusinessException("Informe o nome do cliente")
        if (price < 0) throw BusinessException("Valor inválido")
        val id = IdGenerator.next()
        charges.insert(
            ChargeService(
                id = id,
                customerName = name,
                phone = phone.trim(),
                batteryDescription = batteryDescription.trim(),
                receivedAt = receivedAt,
                price = price,
                paid = paid,
                paidAt = if (paid) now() else null,
                paymentMethod = if (paid) paymentMethod?.name else null,
                loanModel = if (loaned) ChargeService.LOAN_USED else null,
                note = note?.trim()?.ifEmpty { null },
            )
        )
        id
    }

    /** Edita os dados da carga. Empréstimos antigos com controle de estoque não mudam aqui. */
    suspend fun updateCharge(
        id: Long,
        customerName: String,
        phone: String,
        batteryDescription: String,
        receivedAt: Long,
        price: Long,
        paid: Boolean,
        paymentMethod: PaymentMethod?,
        note: String?,
        loaned: Boolean? = null,
    ): Unit = write {
        val c = charges.getById(id) ?: throw BusinessException("Registro não encontrado")
        val loanModel = when {
            c.loanProductId != null || loaned == null -> c.loanModel
            loaned -> c.loanModel ?: ChargeService.LOAN_USED
            else -> null
        }
        val name = customerName.trim()
        if (name.isEmpty()) throw BusinessException("Informe o nome do cliente")
        if (price < 0) throw BusinessException("Valor inválido")
        charges.update(
            c.copy(
                customerName = name,
                phone = phone.trim(),
                batteryDescription = batteryDescription.trim(),
                receivedAt = receivedAt,
                price = price,
                paid = paid,
                paidAt = if (paid) (c.paidAt ?: now()) else null,
                paymentMethod = if (paid) (paymentMethod?.name ?: c.paymentMethod) else null,
                loanModel = loanModel,
                note = note?.trim()?.ifEmpty { null },
                updatedAt = now(),
                dirty = true,
            )
        )
    }

    suspend fun markChargePaid(id: Long, method: PaymentMethod): Unit = write {
        val c = charges.getById(id) ?: throw BusinessException("Registro não encontrado")
        charges.update(c.copy(paid = true, paidAt = now(), paymentMethod = method.name, updatedAt = now(), dirty = true))
    }

    suspend fun markChargeReady(id: Long): Unit = write {
        val c = charges.getById(id) ?: throw BusinessException("Registro não encontrado")
        if (c.status == ChargeStatus.DELIVERED) return@write
        charges.update(c.copy(status = ChargeStatus.READY, updatedAt = now(), dirty = true))
    }

    /**
     * Entrega a bateria ao cliente. Se houve empréstimo, a bateria da loja volta ao estoque.
     * [paidNow] registra o pagamento no momento da entrega.
     */
    suspend fun deliverCharge(id: Long, paidNow: PaymentMethod?): Unit = write {
        val c = charges.getById(id) ?: throw BusinessException("Registro não encontrado")
        if (c.status == ChargeStatus.DELIVERED) return@write
        val at = now()
        var returnId: Long? = c.loanReturnMovementId
        if (c.loanProductId != null && returnId == null && products.getById(c.loanProductId) != null) {
            returnId = moveStock(c.loanProductId, +1, MovementType.LOAN_RETURN, "Devolvida por ${c.customerName} (carga)", at)
        }
        charges.update(
            c.copy(
                status = ChargeStatus.DELIVERED,
                deliveredAt = at,
                loanReturnMovementId = returnId,
                paid = c.paid || paidNow != null,
                paidAt = if (c.paid) c.paidAt else if (paidNow != null) at else null,
                paymentMethod = if (c.paid) c.paymentMethod else paidNow?.name,
                updatedAt = at,
                dirty = true,
            )
        )
    }

    /** Exclui o registro de carga, desfazendo o empréstimo no estoque. */
    suspend fun deleteCharge(id: Long): Unit = write {
        val c = charges.getById(id) ?: return@write
        undoStockMovement(c.loanReturnMovementId)
        undoStockMovement(c.loanMovementId)
        tomb(SyncTables.CHARGES, listOf(id))
        charges.delete(id)
    }

    // ------------------------------------------------------- Garantias e extras

    fun observeWarranties(): Flow<List<WarrantyClaim>> = warranties.observeAll()
    fun observeWarrantiesForSale(saleId: Long): Flow<List<WarrantyClaim>> = warranties.observeForSale(saleId)

    /** Baterias trocadas em garantia: só a contagem por modelo, sem mexer no estoque. */
    suspend fun registerExchange(productId: Long, quantity: Int, at: Long = now()): Unit = write {
        if (quantity <= 0) throw BusinessException("Informe a quantidade")
        val p = products.getById(productId) ?: throw BusinessException("Bateria não encontrada")
        repeat(quantity) {
            warranties.insert(
                WarrantyClaim(
                    createdAt = at, returnedProductId = p.id, returnedModel = p.model,
                    defective = true, status = WarrantyStatus.EXCHANGE,
                )
            )
        }
    }

    /** Baterias extras ganhadas: entram no estoque com custo zero (lucro de 100% na venda). */
    suspend fun registerExtra(productId: Long, quantity: Int, at: Long = now()): Unit = write {
        if (quantity <= 0) throw BusinessException("Informe a quantidade")
        val p = products.getById(productId) ?: throw BusinessException("Bateria não encontrada")
        repeat(quantity) {
            val moveId = moveStock(p.id, +1, MovementType.EXTRA_IN, "Bateria extra (ganhada)", at, 0)
            warranties.insert(
                WarrantyClaim(
                    // Mesmo ID da entrada no estoque: a recuperação (repairExtras) gera o mesmo registro em qualquer celular.
                    id = moveId,
                    createdAt = at, returnedProductId = p.id, returnedModel = p.model,
                    defective = false, status = WarrantyStatus.EXTRA, inMovementId = moveId,
                )
            )
        }
    }

    /** Exclui uma troca ou uma extra (a extra sai do estoque, se ainda não foi vendida). */
    suspend fun deleteWarranty(id: Long) = deleteWarranties(listOf(id))

    /** Exclui várias de uma vez (tudo ou nada). */
    suspend fun deleteWarranties(ids: List<Long>): Unit = write {
        for (id in ids) {
            val w = warranties.getById(id) ?: continue
            if (w.isExtra) {
                if (w.saleId != null) {
                    throw BusinessException("Uma dessas extras já foi vendida. Para excluí-la, cancele ou exclua a venda antes.")
                }
                val p = w.returnedProductId?.let { products.getById(it) }
                if (p != null && p.stock <= 0) {
                    throw BusinessException("Não é possível excluir: o estoque de ${p.model} ficaria negativo")
                }
                undoStockMovement(w.inMovementId)
            }
            tomb(SyncTables.WARRANTIES, listOf(id))
            warranties.delete(id)
        }
    }

    /** Quantas extras (custo zero) de um produto podem entrar numa venda; na edição, conta também as da própria venda. */
    suspend fun freeExtraCount(productId: Long, saleId: Long? = null): Int =
        warranties.getAll().count { it.isExtra && it.returnedProductId == productId && (it.saleId == null || it.saleId == saleId) }

    /**
     * Recria as extras cujo registro sumiu mas cuja entrada no estoque ("Extra (ganhada)") continua.
     * As que já saíram numa venda com custo zero voltam ligadas a essa venda (não contam duas vezes).
     */
    suspend fun repairExtras() {
        if (orphanExtraMovements().isEmpty()) return
        write { rebuildOrphanExtras() }
    }

    private suspend fun orphanExtraMovements(): List<StockMovement> {
        val known = warranties.getAll().mapNotNull { it.inMovementId }.toSet()
        return movements.getAll().filter { it.type == MovementType.EXTRA_IN && it.quantity > 0 && it.id !in known }
    }

    private suspend fun rebuildOrphanExtras() {
        val orphans = orphanExtraMovements()
        if (orphans.isEmpty()) return
        val claims = warranties.getAll()
        val active = sales.getActiveWithItems()
        for ((productId, moves) in orphans.groupBy { it.productId }) {
            val product = products.getById(productId)
            val pending = moves.sortedBy { it.dateTime }.toMutableList()
            // Vendas deste produto que saíram com unidades de custo zero ainda sem extra ligada
            val freeBySale = active.mapNotNull { s ->
                val item = s.items.singleOrNull()?.takeIf { it.productId == productId } ?: return@mapNotNull null
                if (item.unitCost <= 0) return@mapNotNull null
                val free = ((item.unitCost * item.quantity - s.sale.totalCost) / item.unitCost).toInt()
                    .coerceIn(0, item.quantity) - claims.count { it.isExtra && it.saleId == s.sale.id }
                if (free > 0) s to free else null
            }.sortedBy { it.first.sale.dateTime }
            val assigned = HashMap<Long, Long?>()
            for ((s, free) in freeBySale) {
                repeat(free) {
                    val m = pending.firstOrNull { it.dateTime <= s.sale.dateTime } ?: pending.firstOrNull() ?: return@repeat
                    pending.remove(m)
                    assigned[m.id] = s.sale.id
                }
            }
            for (m in moves) {
                sync.upsertWarranty(
                    WarrantyClaim(
                        id = m.id,
                        createdAt = m.dateTime,
                        returnedProductId = productId,
                        returnedModel = product?.model ?: "(excluída)",
                        defective = false,
                        status = WarrantyStatus.EXTRA,
                        inMovementId = m.id,
                        saleId = assigned[m.id],
                    )
                )
            }
        }
    }

    private suspend fun availableExtras(productId: Long): List<WarrantyClaim> =
        warranties.getAll().filter { it.isExtra && it.returnedProductId == productId && it.saleId == null }.sortedBy { it.createdAt }

    private suspend fun useExtras(extras: List<WarrantyClaim>, saleId: Long) {
        extras.forEach { warranties.update(it.copy(saleId = saleId, updatedAt = now(), dirty = true)) }
    }

    /** Devolve ao estoque de extras as que foram usadas na venda (cancelada, editada ou excluída). */
    private suspend fun releaseExtras(saleId: Long) {
        warranties.getAll().filter { it.isExtra && it.saleId == saleId }
            .forEach { warranties.update(it.copy(saleId = null, updatedAt = now(), dirty = true)) }
    }

    // ---------------------------------------------------------------- Despesas

    fun observeExpenses(): Flow<List<Expense>> = expenses.observeAll()
    fun observeExpensePayments(range: DateRange): Flow<List<Expense>> = expenses.observePaymentsInRange(range.start, range.end)

    /** Despesa avulsa (já paga). */
    suspend fun addExpense(category: String, description: String, amount: Long, date: Long, note: String? = null): Unit = write {
        if (amount <= 0) throw BusinessException("Informe o valor da despesa")
        expenses.insert(
            Expense(
                kind = ExpenseKind.PAYMENT, category = category, description = description.trim().ifEmpty { category },
                amount = amount, date = date, note = note?.trim()?.ifEmpty { null },
            )
        )
    }

    /**
     * Contas fixas deixaram de existir: apaga as contas fixas e os pagamentos delas
     * (aqui e na nuvem). O valor pago volta para o lucro. Não faz nada se não houver nenhuma.
     */
    suspend fun removeFixedBills() {
        val ids = expenses.getAll().filter { it.kind == ExpenseKind.BILL || (it.kind == ExpenseKind.PAYMENT && it.billId != null) }.map { it.id }
        if (ids.isEmpty()) return
        write {
            tomb(SyncTables.EXPENSES, ids)
            ids.forEach { expenses.delete(it) }
        }
    }

    /** Retirada de um sócio (divisão do lucro: não é despesa, só sai do caixa). */
    suspend fun addWithdrawal(partner: String, amount: Long, date: Long, note: String? = null): Unit = write {
        val name = partner.trim()
        if (name.isEmpty()) throw BusinessException("Informe quem retirou")
        if (amount <= 0) throw BusinessException("Informe o valor da retirada")
        expenses.insert(
            Expense(
                kind = ExpenseKind.WITHDRAWAL, category = name, description = "Retirada de $name",
                amount = amount, date = date, note = note?.trim()?.ifEmpty { null },
            )
        )
    }

    /** Define o saldo inicial do caixa (gaveta + banco) no começo do dia [date]. Substitui o anterior. */
    suspend fun setOpeningBalance(amount: Long, date: Long): Unit = write {
        if (amount < 0) throw BusinessException("Valor inválido")
        val current = expenses.getAll().filter { it.kind == ExpenseKind.OPENING }
        val keep = current.maxByOrNull { it.updatedAt }
        current.filter { it.id != keep?.id }.forEach {
            tomb(SyncTables.EXPENSES, listOf(it.id))
            expenses.delete(it.id)
        }
        if (keep == null) {
            expenses.insert(
                Expense(kind = ExpenseKind.OPENING, category = "Caixa", description = "Saldo inicial do caixa", amount = amount, date = date)
            )
        } else {
            expenses.update(keep.copy(amount = amount, date = date, updatedAt = now(), dirty = true))
        }
    }

    /** Exclui uma despesa paga. */
    suspend fun deleteExpense(id: Long): Unit = write {
        tomb(SyncTables.EXPENSES, listOf(id))
        expenses.delete(id)
    }

    // ----------------------------------------------------------- Notas fiscais

    fun observeInvoices(): Flow<List<InvoiceWithBills>> = invoices.observeAll()
    fun observeInvoice(id: Long): Flow<InvoiceWithBills?> = invoices.observeById(id)
    fun observeInvoiceBills(): Flow<List<InvoiceBill>> = invoices.observeBills()

    /**
     * Cria ou altera uma nota fiscal com seus boletos. Nota nova fica "aguardando baterias"; com [alreadyReceived]
     * (nota antiga, baterias que chegaram antes) fica recebida sem mexer no estoque.
     * Na edição, a situação da chegada é mantida (muda-se pela tela da nota).
     */
    suspend fun saveInvoice(
        id: Long?,
        number: String,
        supplier: String,
        issueDate: Long,
        items: List<InvoiceItem>,
        total: Long,
        bills: List<BillDraft>,
        note: String?,
        alreadyReceived: Boolean = false,
    ): Long = write {
        val num = number.trim()
        if (num.isEmpty()) throw BusinessException("Informe o número da nota")
        var list = items.filter { it.quantity > 0 }
        if (list.isEmpty()) throw BusinessException("Adicione as baterias da nota")
        if (list.any { it.unitCost < 0 }) throw BusinessException("Valor de bateria inválido")
        if (total <= 0) throw BusinessException("Informe o valor da nota")
        if (bills.isEmpty()) throw BusinessException("Adicione pelo menos um boleto")
        if (bills.any { it.amount <= 0 }) throw BusinessException("Todo boleto precisa ter valor")
        val billsTotal = bills.sumOf { it.amount }
        if (billsTotal != total) {
            throw BusinessException(
                "A soma dos boletos (${Money.format(billsTotal)}) está diferente do total da nota (${Money.format(total)})"
            )
        }
        val name = supplier.trim()
        if (invoices.getAll().any { it.id != id && it.number.equals(num, true) && it.supplier.equals(name, true) }) {
            throw BusinessException("A nota nº $num${if (name.isNotEmpty()) " de $name" else ""} já foi lançada")
        }
        val current = id?.let { invoices.getById(it) }
        if (current != null) {
            // Nota que já entrou no estoque: as baterias só mudam depois de desfazer a chegada.
            val old = current.items
            if (old.any { it.movementId != null }) {
                val same = old.size == list.size &&
                    old.zip(list).all { (a, b) -> a.productId == b.productId && a.quantity == b.quantity }
                if (!same) {
                    throw BusinessException(
                        "Essas baterias já entraram no estoque. Para mudar as baterias da nota, toque antes em \"Ainda não chegaram\"."
                    )
                }
                list = list.zip(old).map { (n, o) -> n.copy(received = o.received, movementId = o.movementId) }
            }
        }
        val received = current?.isReceived ?: alreadyReceived
        val invoice = Invoice(
            id = current?.id ?: IdGenerator.next(),
            number = num,
            supplier = name,
            issueDate = issueDate,
            itemsJson = InvoiceItems.encode(list),
            total = total,
            status = if (received) InvoiceStatus.RECEIVED else InvoiceStatus.WAITING,
            receivedAt = if (received) current?.receivedAt ?: now() else null,
            receivedNote = if (received) current?.receivedNote else null,
            note = note?.trim()?.ifEmpty { null },
            updatedAt = now(),
            dirty = true,
        )
        invoices.upsert(invoice)
        val keep = bills.mapNotNull { it.id }.toSet()
        val removed = invoices.billsFor(invoice.id).map { it.id }.filter { it !in keep }
        tomb(SyncTables.INVOICE_BILLS, removed)
        removed.forEach { invoices.deleteBill(it) }
        bills.forEach { b ->
            invoices.upsertBill(
                InvoiceBill(
                    id = b.id ?: IdGenerator.next(), invoiceId = invoice.id, dueDate = b.dueDate, amount = b.amount,
                    paidAt = b.paidAt, updatedAt = now(), dirty = true,
                )
            )
        }
        invoice.id
    }

    /**
     * As baterias da nota chegaram. [receivedQty] diz quantas chegaram de cada item (na ordem da nota;
     * vazio = todas). Com [addToStock], as que chegaram entram no estoque como um lote com o custo
     * da nota (valor unitário − desconto); as vendas usam primeiro os lotes mais antigos.
     */
    suspend fun markInvoiceReceived(
        id: Long,
        note: String?,
        receivedQty: List<Int> = emptyList(),
        addToStock: Boolean = true,
        at: Long = now(),
    ): Unit = write {
        val i = invoices.getById(id) ?: throw BusinessException("Nota não encontrada")
        if (i.isReceived) throw BusinessException("Essa nota já foi marcada como recebida")
        val label = "Nota ${i.number}" + if (i.supplier.isNotBlank()) " (${i.supplier})" else ""
        val items = i.items.mapIndexed { k, item ->
            val qty = receivedQty.getOrNull(k)?.coerceAtLeast(0) ?: item.quantity
            val product = item.productId?.let { products.getById(it) }
            val moveId = if (addToStock && qty > 0 && product != null) {
                // Entra como um lote novo, com o custo desta nota (já com o desconto)
                moveStock(product.id, qty, MovementType.ENTRY, label, at, item.unitCost)
            } else {
                null
            }
            item.copy(received = qty, movementId = moveId)
        }
        val missing = i.items.zip(items).filter { (a, b) -> (b.received ?: a.quantity) < a.quantity }
            .joinToString { (a, b) -> "${a.quantity - (b.received ?: 0)} ${a.model}" }
        val autoNote = if (missing.isNotEmpty()) "Faltou: $missing" else null
        val fullNote = listOfNotNull(autoNote, note?.trim()?.ifEmpty { null }).joinToString(" • ").ifEmpty { null }
        invoices.upsert(
            i.copy(
                itemsJson = InvoiceItems.encode(items),
                status = InvoiceStatus.RECEIVED, receivedAt = at, receivedNote = fullNote,
                updatedAt = now(), dirty = true,
            )
        )
    }

    /** Desfaz a entrada no estoque feita na chegada da nota (se ainda der). */
    private suspend fun undoInvoiceStock(i: Invoice): List<InvoiceItem> {
        val items = i.items
        for (item in items) {
            val moveId = item.movementId ?: continue
            val m = movements.getById(moveId) ?: continue
            val p = products.getById(m.productId) ?: continue
            if (p.stock - m.quantity < 0) {
                throw BusinessException(
                    "Não dá para desfazer: parte das ${p.model} dessa nota já foi vendida (o estoque ficaria negativo). " +
                        "Se precisar, corrija o estoque pelo ajuste."
                )
            }
        }
        items.forEach { undoStockMovement(it.movementId) }
        return items.map { it.copy(received = null, movementId = null) }
    }

    /** Volta a nota para "aguardando baterias" e tira do estoque o que entrou na chegada. */
    suspend fun markInvoiceWaiting(id: Long): Unit = write {
        val i = invoices.getById(id) ?: throw BusinessException("Nota não encontrada")
        val items = undoInvoiceStock(i)
        invoices.upsert(
            i.copy(
                itemsJson = InvoiceItems.encode(items),
                status = InvoiceStatus.WAITING, receivedAt = null, receivedNote = null, updatedAt = now(), dirty = true,
            )
        )
    }

    /** Marca o boleto como pago (ou desfaz, com [paid] = false). */
    suspend fun setInvoiceBillPaid(billId: Long, paid: Boolean, at: Long = now()): Unit = write {
        val b = invoices.getBill(billId) ?: throw BusinessException("Boleto não encontrado")
        invoices.upsertBill(b.copy(paidAt = if (paid) at else null, updatedAt = now(), dirty = true))
    }

    /** Exclui a nota e os boletos dela (e tira do estoque o que entrou pela nota). */
    suspend fun deleteInvoice(id: Long): Unit = write {
        invoices.getById(id)?.let { undoInvoiceStock(it) }
        val bills = invoices.billsFor(id).map { it.id }
        tomb(SyncTables.INVOICE_BILLS, bills)
        bills.forEach { invoices.deleteBill(it) }
        tomb(SyncTables.INVOICES, listOf(id))
        invoices.delete(id)
    }

    // ----------------------------------------------------------------- Sucatas

    fun observeScrapPrices(): Flow<List<ScrapPrice>> = scraps.observePrices()
    fun observeScrapStock(): Flow<List<ScrapStock>> = scraps.observeStock()
    fun observeScrapMovements(limit: Int = 200): Flow<List<ScrapMovement>> = scraps.observeRecent(limit)
    fun observeScrapSold(range: DateRange): Flow<ScrapSoldSummary> = scraps.observeSold(range.start, range.end)
    fun observeScrapMovementsInRange(range: DateRange): Flow<List<ScrapMovement>> =
        scraps.observeInRange(range.start, range.end)

    /** Exclui uma movimentação de sucata (entrada, compra, venda ou ajuste). O estoque é recalculado. */
    suspend fun deleteScrapMovement(id: Long): Unit = write {
        val m = scraps.getMovement(id) ?: throw BusinessException("Movimentação não encontrada")
        if (!ScrapMovementType.isDeletable(m.type)) {
            throw BusinessException("Sucata de venda: para removê-la, exclua ou edite a venda")
        }
        val after = scraps.stockOf(m.amperage) - m.quantity
        if (after < 0) {
            throw BusinessException("Não é possível excluir: o estoque de sucatas ${m.amperage}Ah ficaria negativo ($after)")
        }
        tomb(SyncTables.SCRAP_MOVEMENTS, listOf(id))
        scraps.deleteMovement(id)
    }

    /**
     * Paga o vale de casco: o cliente trouxe [quantity] casco(s) da venda [saleId].
     * Os cascos entram no estoque de sucatas e o valor devolvido fica registrado nesta data.
     */
    suspend fun payVoucher(saleId: Long, quantity: Int, amperage: Int, at: Long = now()): Unit = write {
        if (quantity <= 0) throw BusinessException("Informe quantos cascos o cliente trouxe")
        if (amperage <= 0) throw BusinessException("Informe a amperagem do casco")
        val sale = sales.getWithItems(saleId) ?: throw BusinessException("Venda não encontrada")
        val ofSale = scraps.getAllMovements().filter { it.saleId == saleId }
        val paid = ofSale.filter { it.type == ScrapMovementType.VOUCHER_PAID }
        val voucher = Vouchers.open(listOf(sale), ofSale).firstOrNull()
            ?: throw BusinessException(if (paid.isEmpty()) "Esta venda não tem vale" else "Este vale já foi pago")
        if (quantity > voucher.remaining) throw BusinessException("Este vale é de ${voucher.remaining} casco(s)")
        val amount = if (quantity == voucher.remaining) sale.sale.scrapCharge - paid.sumOf { it.amount }
        else voucher.unitValue * quantity
        scraps.insertMovement(
            ScrapMovement(
                dateTime = at,
                type = ScrapMovementType.VOUCHER_PAID,
                amperage = amperage,
                quantity = quantity,
                amount = amount,
                saleId = saleId,
            )
        )
    }

    /** Compra de sucatas (pagando por elas). */
    suspend fun buyScrap(amperage: Int, quantity: Int, amountPaid: Long, note: String?): Unit = write {
        if (amperage <= 0) throw BusinessException("Informe a amperagem")
        if (quantity <= 0) throw BusinessException("Informe a quantidade")
        if (amountPaid < 0) throw BusinessException("Valor inválido")
        scraps.insertMovement(
            ScrapMovement(
                dateTime = System.currentTimeMillis(),
                type = ScrapMovementType.PURCHASE,
                amperage = amperage,
                quantity = quantity,
                amount = amountPaid,
                note = note?.trim()?.ifEmpty { null },
            )
        )
    }

    /** Cadastra ou altera o valor de uma amperagem na tabela de sucatas. */
    suspend fun saveScrapPrice(id: Long, amperage: Int, value: Long): Unit = write {
        if (amperage <= 0) throw BusinessException("Informe a amperagem")
        if (value < 0) throw BusinessException("Valor inválido")
        val existing = scraps.findPrice(amperage)
        if (existing != null && existing.id != id) throw BusinessException("A amperagem ${amperage}Ah já está na tabela")
        if (id == 0L) scraps.insertPrice(ScrapPrice(id = IdGenerator.next(), amperage = amperage, value = value))
        else scraps.updatePrice(ScrapPrice(id = id, amperage = amperage, value = value))
    }

    suspend fun deleteScrapPrice(id: Long): Unit = write {
        tomb(SyncTables.SCRAP_PRICES, listOf(id))
        scraps.deletePrice(id)
    }

    /** Entrada manual de sucatas (ex.: recebidas fora de uma venda). */
    suspend fun addScrap(amperage: Int, quantity: Int, note: String?): Unit = write {
        if (amperage <= 0) throw BusinessException("Informe a amperagem")
        if (quantity <= 0) throw BusinessException("Informe a quantidade")
        scraps.insertMovement(
            ScrapMovement(
                dateTime = System.currentTimeMillis(),
                type = ScrapMovementType.MANUAL_IN,
                amperage = amperage,
                quantity = quantity,
                note = note?.trim()?.ifEmpty { null },
            )
        )
    }

    /** Venda de sucatas (ex.: para o reciclador), com o valor recebido. */
    suspend fun sellScrap(amperage: Int, quantity: Int, amountReceived: Long, note: String?): Unit = write {
        if (amperage <= 0) throw BusinessException("Escolha a amperagem")
        if (quantity <= 0) throw BusinessException("Informe a quantidade")
        if (amountReceived < 0) throw BusinessException("Valor inválido")
        val available = scraps.stockOf(amperage)
        if (quantity > available) throw BusinessException("Estoque insuficiente de sucatas ${amperage}Ah (disponível: $available)")
        scraps.insertMovement(
            ScrapMovement(
                dateTime = System.currentTimeMillis(),
                type = ScrapMovementType.SOLD,
                amperage = amperage,
                quantity = -quantity,
                amount = amountReceived,
                note = note?.trim()?.ifEmpty { null },
            )
        )
    }

    /** Ajuste: define a quantidade real de sucatas de uma amperagem. */
    suspend fun adjustScrap(amperage: Int, newQuantity: Int, note: String?): Unit = write {
        if (amperage <= 0) throw BusinessException("Informe a amperagem")
        if (newQuantity < 0) throw BusinessException("Quantidade inválida")
        val delta = newQuantity - scraps.stockOf(amperage)
        if (delta == 0) return@write
        scraps.insertMovement(
            ScrapMovement(
                dateTime = System.currentTimeMillis(),
                type = ScrapMovementType.ADJUSTMENT,
                amperage = amperage,
                quantity = delta,
                note = note?.trim()?.ifEmpty { null },
            )
        )
    }

    companion object {
        /** Movimentações que trazem baterias com custo (cada uma vira um lote). */
        private val COST_ENTRY_TYPES = setOf(MovementType.INITIAL, MovementType.ENTRY)

        /** Lotes das [product].stock − [extrasInStock] baterias em estoque, a partir das entradas com custo. */
        fun costLayersFrom(product: Product, moves: List<StockMovement>, extrasInStock: Int): List<CostLayer> {
            val entries = moves
                .filter { it.productId == product.id && it.type in COST_ENTRY_TYPES && it.quantity > 0 && it.unitCost != null }
                .sortedWith(compareBy<StockMovement>({ it.dateTime }, { it.id }))
                .map { it.quantity to it.unitCost!! }
            return CostLayers.build(entries, product.stock - extrasInStock, product.cost)
        }

        fun toReportSale(s: SaleWithItems): ReportSale = ReportSale(
            paymentMethod = s.sale.payment,
            grossAmount = s.sale.grossAmount,
            discount = s.sale.discount,
            finalAmount = s.sale.finalAmount,
            totalCost = s.sale.totalCost,
            cardFee = s.sale.cardFee,
            payments = if (s.isSplit) s.paymentParts else emptyList(),
            items = s.items.map {
                // Venda de um item: usa o custo gravado na venda (já com as extras de custo zero).
                ReportItem(
                    it.modelSnapshot, it.quantity, it.subtotal,
                    if (s.items.size == 1) s.sale.totalCost else it.unitCost * it.quantity,
                )
            },
        )
    }
}
