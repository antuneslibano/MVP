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

class StoreRepository(private val db: AppDatabase) {

    private val products = db.productDao()
    private val sales = db.saleDao()
    private val movements = db.movementDao()

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
     * grava venda + item (com custo histórico), dá baixa no estoque e registra a movimentação.
     */
    suspend fun registerSale(
        productId: Long,
        quantity: Int,
        method: PaymentMethod,
        unitPrice: Long,
        discount: Long,
        dateTime: Long,
    ): Long = db.withTransaction {
        val product = products.getById(productId) ?: throw BusinessException("Produto não encontrado")
        SaleCalculator.validate(unitPrice, quantity, discount, product.stock)?.let { throw BusinessException(it) }
        val totals = SaleCalculator.compute(unitPrice, quantity, discount, product.cost)
        val saleId = sales.insertSale(
            Sale(
                dateTime = dateTime,
                paymentMethod = method.name,
                grossAmount = totals.grossAmount,
                discount = totals.discount,
                finalAmount = totals.finalAmount,
                totalCost = totals.totalCost,
                grossProfit = totals.grossProfit,
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
        saleId
    }

    /**
     * Edita uma venda. O custo unitário histórico é preservado.
     * A diferença de quantidade é refletida no estoque.
     */
    suspend fun updateSale(
        saleId: Long,
        quantity: Int,
        method: PaymentMethod,
        unitPrice: Long,
        discount: Long,
        dateTime: Long,
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
        val totals = SaleCalculator.compute(unitPrice, quantity, discount, item.unitCost)

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
            )
        )
        if (product != null && delta != 0) {
            val newStock = product.stock - delta
            products.updateStock(product.id, newStock)
            movements.insert(
                StockMovement(
                    productId = product.id,
                    dateTime = System.currentTimeMillis(),
                    type = MovementType.SALE_EDIT,
                    quantity = -delta,
                    stockAfter = newStock,
                    saleId = saleId,
                )
            )
        }
    }

    /** Cancela a venda: devolve o estoque e a retira dos relatórios (status CANCELED). */
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
        sales.updateSale(current.sale.copy(status = SaleStatus.CANCELED, canceledAt = now))
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
