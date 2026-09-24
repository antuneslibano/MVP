package br.com.lojabaterias.data

/** Resumo de sucatas de um período. */
data class ScrapPeriodSummary(
    /** Sucatas deixadas pelos clientes nas vendas. */
    val returnedInSales: Int = 0,
    /** Sucatas que os clientes não deixaram. */
    val missingInSales: Int = 0,
    /** Valor cobrado pelas sucatas não deixadas (já incluído no faturamento). */
    val charged: Long = 0,
    /** Sucatas vendidas (ex.: ao reciclador). */
    val soldQuantity: Int = 0,
    /** Valor recebido pela venda de sucatas. */
    val soldAmount: Long = 0,
) {
    companion object {
        fun from(sales: List<SaleWithItems>, sold: ScrapSoldSummary): ScrapPeriodSummary {
            val valid = sales.filter { !it.sale.isCanceled }
            return ScrapPeriodSummary(
                returnedInSales = valid.sumOf { it.sale.scrapReturned },
                missingInSales = valid.sumOf { it.sale.scrapMissing },
                charged = valid.sumOf { it.sale.scrapCharge },
                soldQuantity = sold.quantity,
                soldAmount = sold.amount,
            )
        }
    }
}
