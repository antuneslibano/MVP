package br.com.lojabaterias.data

/** Resumo de sucatas de um período. */
data class ScrapPeriodSummary(
    /** Sucatas deixadas pelos clientes nas vendas. */
    val returnedInSales: Int = 0,
    /** Sucatas que os clientes não deixaram. */
    val missingInSales: Int = 0,
    /** Valor cobrado pelas sucatas não deixadas (já incluído no faturamento). */
    val charged: Long = 0,
    /** Entradas manuais (sem custo). */
    val manualInQuantity: Int = 0,
    /** Sucatas compradas e valor pago. */
    val purchasedQuantity: Int = 0,
    val purchasedAmount: Long = 0,
    /** Sucatas vendidas (ex.: ao reciclador) e valor recebido. */
    val soldQuantity: Int = 0,
    val soldAmount: Long = 0,
    /** Saldo dos ajustes de contagem (+/−). */
    val adjustmentNet: Int = 0,
) {
    /** Resultado financeiro das sucatas no período: vendido − comprado. */
    val netAmount: Long get() = soldAmount - purchasedAmount

    companion object {
        fun from(sales: List<SaleWithItems>, movements: List<ScrapMovement>): ScrapPeriodSummary {
            val valid = sales.filter { !it.sale.isCanceled }
            fun of(type: String) = movements.filter { it.type == type }
            return ScrapPeriodSummary(
                returnedInSales = valid.sumOf { it.sale.scrapReturned },
                missingInSales = valid.sumOf { it.sale.scrapMissing },
                charged = valid.sumOf { it.sale.scrapCharge },
                manualInQuantity = of(ScrapMovementType.MANUAL_IN).sumOf { it.quantity },
                purchasedQuantity = of(ScrapMovementType.PURCHASE).sumOf { it.quantity },
                purchasedAmount = of(ScrapMovementType.PURCHASE).sumOf { it.amount },
                soldQuantity = -of(ScrapMovementType.SOLD).sumOf { it.quantity },
                soldAmount = of(ScrapMovementType.SOLD).sumOf { it.amount },
                adjustmentNet = of(ScrapMovementType.ADJUSTMENT).sumOf { it.quantity },
            )
        }
    }
}
