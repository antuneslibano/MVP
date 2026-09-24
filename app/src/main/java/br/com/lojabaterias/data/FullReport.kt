package br.com.lojabaterias.data

import br.com.lojabaterias.domain.Report
import br.com.lojabaterias.domain.ReportCalculator

/** Posição de estoque de um modelo. */
data class StockRow(
    val model: String,
    val amperage: Int,
    val stock: Int,
    val minStock: Int,
    val cost: Long,
    val pricePix: Long,
) {
    val valueAtCost: Long get() = cost * stock
    val valueAtPix: Long get() = pricePix * stock
    val isOut: Boolean get() = stock <= 0
    val isLow: Boolean get() = stock in 1..minStock
}

/** Estoque de sucatas de uma amperagem, com valor de tabela. */
data class ScrapStockRow(
    val amperage: Int,
    val quantity: Int,
    /** Valor de tabela por unidade (null se a amperagem não está na tabela). */
    val unitValue: Long?,
) {
    val totalValue: Long get() = (unitValue ?: 0) * quantity
}

/** Movimentação do estoque de baterias no período. */
data class StockPeriodSummary(
    val entriesQuantity: Int = 0,
    /** Custo das entradas (quantidade × custo unitário informado). */
    val entriesCost: Long = 0,
    val initialQuantity: Int = 0,
    val adjustmentsIn: Int = 0,
    val adjustmentsOut: Int = 0,
    val soldUnits: Int = 0,
)

/** Relatório completo de um período: vendas, estoque e sucatas. */
data class FullReport(
    val sales: Report = Report.EMPTY,
    /** Vendas válidas do período, mais recentes primeiro. */
    val activeSales: List<SaleWithItems> = emptyList(),
    val canceledSales: List<SaleWithItems> = emptyList(),
    val grossTotal: Long = 0,
    val discountTotal: Long = 0,
    // Estoque (posição atual)
    val stockRows: List<StockRow> = emptyList(),
    val stockPeriod: StockPeriodSummary = StockPeriodSummary(),
    val stockMovements: List<MovementWithModel> = emptyList(),
    // Sucatas
    val scrap: ScrapPeriodSummary = ScrapPeriodSummary(),
    val scrapStock: List<ScrapStockRow> = emptyList(),
    val scrapMovements: List<ScrapMovement> = emptyList(),
) {
    val canceledAmount: Long get() = canceledSales.sumOf { it.sale.finalAmount }
    val stockUnits: Int get() = stockRows.sumOf { it.stock.coerceAtLeast(0) }
    val stockValueAtCost: Long get() = stockRows.sumOf { it.valueAtCost.coerceAtLeast(0) }
    val stockValueAtPix: Long get() = stockRows.sumOf { it.valueAtPix.coerceAtLeast(0) }
    val lowStock: List<StockRow> get() = stockRows.filter { it.isLow }
    val outOfStock: List<StockRow> get() = stockRows.filter { it.isOut }
    val scrapStockQuantity: Int get() = scrapStock.sumOf { it.quantity }
    val scrapStockValue: Long get() = scrapStock.sumOf { it.totalValue }

    companion object {
        fun build(
            salesInPeriod: List<SaleWithItems>,
            stockMovements: List<MovementWithModel>,
            scrapMovements: List<ScrapMovement>,
            products: List<Product>,
            scrapStock: List<ScrapStock>,
            scrapPrices: Map<Int, Long>,
        ): FullReport {
            val active = salesInPeriod.filter { !it.sale.isCanceled }
            val canceled = salesInPeriod.filter { it.sale.isCanceled }
            val report = ReportCalculator.build(active.map { StoreRepository.toReportSale(it) })
            val moves = stockMovements.map { it.movement }
            fun of(type: String) = moves.filter { it.type == type }
            val entries = of(MovementType.ENTRY)
            val adjustments = of(MovementType.ADJUSTMENT)
            return FullReport(
                sales = report,
                activeSales = active,
                canceledSales = canceled,
                grossTotal = active.sumOf { it.sale.grossAmount },
                discountTotal = active.sumOf { it.sale.discount },
                stockRows = products.map { StockRow(it.model, it.amperage, it.stock, it.minStock, it.cost, it.pricePix) },
                stockPeriod = StockPeriodSummary(
                    entriesQuantity = entries.sumOf { it.quantity },
                    entriesCost = entries.sumOf { it.quantity * (it.unitCost ?: 0) },
                    initialQuantity = of(MovementType.INITIAL).sumOf { it.quantity },
                    adjustmentsIn = adjustments.filter { it.quantity > 0 }.sumOf { it.quantity },
                    adjustmentsOut = -adjustments.filter { it.quantity < 0 }.sumOf { it.quantity },
                    soldUnits = report.unitsSold,
                ),
                stockMovements = stockMovements,
                scrap = ScrapPeriodSummary.from(active, scrapMovements),
                scrapStock = scrapStock.filter { it.quantity != 0 }
                    .map { ScrapStockRow(it.amperage, it.quantity, scrapPrices[it.amperage]) },
                scrapMovements = scrapMovements,
            )
        }
    }
}
