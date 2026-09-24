package br.com.lojabaterias.data

import androidx.room.withTransaction
import br.com.lojabaterias.domain.DateRange
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.ReportItem
import br.com.lojabaterias.domain.ReportSale
import br.com.lojabaterias.domain.SaleCalculator
import kotlinx.coroutines.flow.Flow

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
) {
    val amperageOrNull: Int? get() = if (returned > 0 && amperage > 0) amperage else null

    companion object {
        /** Sucata não informada. */
        val NONE = ScrapInput(0, 0, 0, 0)
    }
}

class StoreRepository(private val db: AppDatabase) {

    private val products = db.productDao()
    private val sales = db.saleDao()
    private val movements = db.movementDao()
    private val scraps = db.scrapDao()

    // ---------------------------------------------------------------- Produtos

    fun observeProducts(): Flow<List<Product>> = products.observeAll()
    fun observeProduct(id: Long): Flow<Product?> = products.observeById(id)
    suspend fun getProduct(id: Long): Product? = products.getById(id)

    /** Cria ou atualiza um produto. O estoque só é definido na criação. */
    suspend fun saveProduct(product: Product): Long = db.withTransaction {
        val model = product.model.trim()
        if (model.isEmpty()) throw BusinessException("Informe o modelo")
        if (product.stock < 0) throw BusinessException("Estoque não pode ser negativo")
        val existing = products.findByModel(model)
        if (existing != null && existing.id != product.id) {
            throw BusinessException("Já existe uma bateria com o modelo $model")
        }
        if (product.id == 0L) {
            val id = products.insert(product.copy(model = model))
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
            val current = products.getById(product.id) ?: throw BusinessException("Produto não encontrado")
            // O estoque é alterado apenas por entrada/ajuste/vendas.
            products.update(product.copy(model = model, stock = current.stock, createdAt = current.createdAt))
            product.id
        }
    }

    suspend fun deleteProduct(id: Long): Unit = db.withTransaction {
        movements.deleteForProduct(id)
        products.delete(id)
    }

    /** Entrada de mercadoria. Se [newUnitCost] for informado, atualiza o custo atual do produto. */
    suspend fun addStock(productId: Long, quantity: Int, newUnitCost: Long?, note: String?): Unit = db.withTransaction {
        if (quantity <= 0) throw BusinessException("Informe a quantidade de entrada")
        val product = products.getById(productId) ?: throw BusinessException("Produto não encontrado")
        val newStock = product.stock + quantity
        val updated = if (newUnitCost != null && newUnitCost > 0) {
            product.copy(stock = newStock, cost = newUnitCost)
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
    suspend fun adjustStock(productId: Long, newStock: Int, note: String?): Unit = db.withTransaction {
        if (newStock < 0) throw BusinessException("Estoque não pode ser negativo")
        val product = products.getById(productId) ?: throw BusinessException("Produto não encontrado")
        val delta = newStock - product.stock
        if (delta == 0) return@withTransaction
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
    ): Long = db.withTransaction {
        val product = products.getById(productId) ?: throw BusinessException("Produto não encontrado")
        SaleCalculator.validate(unitPrice, quantity, discount, product.stock)?.let { throw BusinessException(it) }
        validateScrap(quantity, scrap)
        val totals = SaleCalculator.compute(unitPrice, quantity, discount, product.cost, scrap.charge)
        val saleId = sales.insertSale(
            Sale(
                dateTime = dateTime,
                paymentMethod = method.name,
                grossAmount = totals.grossAmount,
                discount = totals.discount,
                finalAmount = totals.finalAmount,
                totalCost = totals.totalCost,
                grossProfit = totals.grossProfit,
                scrapReturned = scrap.returned,
                scrapAmperage = scrap.amperageOrNull,
                scrapMissing = scrap.missing,
                scrapCharge = scrap.charge,
            )
        )
        sales.insertItem(
            SaleItem(
                saleId = saleId,
                productId = product.id,
                modelSnapshot = product.model,
                quantity = quantity,
                unitPrice = unitPrice,
                unitCost = product.cost,
                subtotal = totals.grossAmount,
            )
        )
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
        saleId
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
    ): Unit = db.withTransaction {
        val current = sales.getWithItems(saleId) ?: throw BusinessException("Venda não encontrada")
        if (current.sale.isCanceled) throw BusinessException("Venda cancelada não pode ser editada")
        val item = current.items.singleOrNull() ?: throw BusinessException("Venda inválida")
        val delta = quantity - item.quantity
        val product = products.getById(item.productId)
        val available = item.quantity + (product?.stock ?: 0)
        if (product == null && delta != 0) {
            throw BusinessException("O produto desta venda foi excluído; a quantidade não pode ser alterada")
        }
        SaleCalculator.validate(unitPrice, quantity, discount, available)?.let { throw BusinessException(it) }
        validateScrap(quantity, scrap)
        val totals = SaleCalculator.compute(unitPrice, quantity, discount, item.unitCost, scrap.charge)
        val now = System.currentTimeMillis()

        sales.updateItem(item.copy(quantity = quantity, unitPrice = unitPrice, subtotal = totals.grossAmount))
        sales.updateSale(
            current.sale.copy(
                dateTime = dateTime,
                paymentMethod = method.name,
                grossAmount = totals.grossAmount,
                discount = totals.discount,
                finalAmount = totals.finalAmount,
                totalCost = totals.totalCost,
                grossProfit = totals.grossProfit,
                scrapReturned = scrap.returned,
                scrapAmperage = scrap.amperageOrNull,
                scrapMissing = scrap.missing,
                scrapCharge = scrap.charge,
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
    }

    /**
     * Cancela a venda: devolve o estoque, retira a venda dos relatórios (status CANCELED)
     * e retira do estoque de sucatas as sucatas que vieram com ela (devolvidas ao cliente).
     */
    suspend fun cancelSale(saleId: Long): Unit = db.withTransaction {
        val current = sales.getWithItems(saleId) ?: throw BusinessException("Venda não encontrada")
        if (current.sale.isCanceled) return@withTransaction
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
        sales.updateSale(s.copy(status = SaleStatus.CANCELED, canceledAt = now))
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

    // ----------------------------------------------------------------- Sucatas

    fun observeScrapPrices(): Flow<List<ScrapPrice>> = scraps.observePrices()
    fun observeScrapStock(): Flow<List<ScrapStock>> = scraps.observeStock()
    fun observeScrapMovements(limit: Int = 200): Flow<List<ScrapMovement>> = scraps.observeRecent(limit)
    fun observeScrapSold(range: DateRange): Flow<ScrapSoldSummary> = scraps.observeSold(range.start, range.end)

    /** Cadastra ou altera o valor de uma amperagem na tabela de sucatas. */
    suspend fun saveScrapPrice(id: Long, amperage: Int, value: Long): Unit = db.withTransaction {
        if (amperage <= 0) throw BusinessException("Informe a amperagem")
        if (value < 0) throw BusinessException("Valor inválido")
        val existing = scraps.findPrice(amperage)
        if (existing != null && existing.id != id) throw BusinessException("A amperagem ${amperage}Ah já está na tabela")
        if (id == 0L) scraps.insertPrice(ScrapPrice(amperage = amperage, value = value))
        else scraps.updatePrice(ScrapPrice(id = id, amperage = amperage, value = value))
    }

    suspend fun deleteScrapPrice(id: Long) = scraps.deletePrice(id)

    /** Entrada manual de sucatas (ex.: recebidas fora de uma venda). */
    suspend fun addScrap(amperage: Int, quantity: Int, note: String?): Unit = db.withTransaction {
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
    suspend fun sellScrap(amperage: Int, quantity: Int, amountReceived: Long, note: String?): Unit = db.withTransaction {
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
    suspend fun adjustScrap(amperage: Int, newQuantity: Int, note: String?): Unit = db.withTransaction {
        if (amperage <= 0) throw BusinessException("Informe a amperagem")
        if (newQuantity < 0) throw BusinessException("Quantidade inválida")
        val delta = newQuantity - scraps.stockOf(amperage)
        if (delta == 0) return@withTransaction
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
        fun toReportSale(s: SaleWithItems): ReportSale = ReportSale(
            paymentMethod = s.sale.payment,
            grossAmount = s.sale.grossAmount,
            discount = s.sale.discount,
            finalAmount = s.sale.finalAmount,
            totalCost = s.sale.totalCost,
            items = s.items.map {
                ReportItem(it.modelSnapshot, it.quantity, it.subtotal, it.unitCost * it.quantity)
            },
        )
    }
}
