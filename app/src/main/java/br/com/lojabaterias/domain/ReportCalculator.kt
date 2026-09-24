package br.com.lojabaterias.domain

/** Dados mínimos de uma venda válida (não cancelada) para relatórios. */
data class ReportSale(
    val paymentMethod: PaymentMethod,
    val grossAmount: Long,
    val discount: Long,
    val finalAmount: Long,
    val totalCost: Long,
    val items: List<ReportItem>,
    /** Taxa da maquininha da venda. */
    val cardFee: Long = 0,
)

data class ReportItem(
    val model: String,
    val quantity: Int,
    val subtotal: Long,
    val totalCost: Long,
)

data class ModelStats(
    val model: String,
    val quantity: Int,
    val revenue: Long,
    val profit: Long,
)

data class PaymentStats(
    val method: PaymentMethod,
    val salesCount: Int,
    val units: Int,
    val revenue: Long,
)

data class Report(
    val revenue: Long,
    val cost: Long,
    val profit: Long,
    /** Taxas das maquininhas (já descontadas do lucro). */
    val fees: Long = 0,
    val salesCount: Int,
    val unitsSold: Int,
    val averageTicket: Long,
    val topByQuantity: List<ModelStats>,
    val topByRevenue: List<ModelStats>,
    val topByProfit: List<ModelStats>,
    val byPayment: List<PaymentStats>,
) {
    companion object {
        val EMPTY = Report(
            revenue = 0, cost = 0, profit = 0, fees = 0, salesCount = 0, unitsSold = 0, averageTicket = 0,
            topByQuantity = emptyList(), topByRevenue = emptyList(), topByProfit = emptyList(), byPayment = emptyList(),
        )
    }
}

object ReportCalculator {

    /**
     * Faturamento = soma das vendas válidas (valor final)
     * Custo = soma do custo histórico dos itens vendidos
     * Taxas = soma das taxas das maquininhas (crédito/débito)
     * Lucro bruto = faturamento − custo − taxas
     * Ticket médio = faturamento / quantidade de vendas
     */
    fun build(sales: List<ReportSale>, topLimit: Int = 5): Report {
        if (sales.isEmpty()) return Report.EMPTY

        val revenue = sales.sumOf { it.finalAmount }
        val cost = sales.sumOf { it.totalCost }
        val fees = sales.sumOf { it.cardFee }
        val count = sales.size
        val units = sales.sumOf { s -> s.items.sumOf { it.quantity } }

        val perModel = LinkedHashMap<String, MutableModel>()
        for (sale in sales) {
            val allocated = allocateDiscount(sale)
            sale.items.forEachIndexed { index, item ->
                val m = perModel.getOrPut(item.model) { MutableModel(item.model) }
                m.quantity += item.quantity
                m.revenue += allocated[index]
                m.cost += item.totalCost
                // taxa proporcional ao valor do item
                m.cost += if (sale.finalAmount > 0) sale.cardFee * allocated[index] / sale.finalAmount else 0
            }
        }
        val models = perModel.values.map { ModelStats(it.model, it.quantity, it.revenue, it.revenue - it.cost) }

        val byPayment = sales.groupBy { it.paymentMethod }
            .map { (method, list) -> PaymentStats(
                    method = method,
                    salesCount = list.size,
                    units = list.sumOf { s -> s.items.sumOf { it.quantity } },
                    revenue = list.sumOf { it.finalAmount },
                ) }
            .sortedByDescending { it.revenue }

        return Report(
            revenue = revenue,
            cost = cost,
            profit = revenue - cost - fees,
            fees = fees,
            salesCount = count,
            unitsSold = units,
            averageTicket = revenue / count,
            topByQuantity = models.sortedWith(compareByDescending<ModelStats> { it.quantity }.thenByDescending { it.revenue }).take(topLimit),
            topByRevenue = models.sortedByDescending { it.revenue }.take(topLimit),
            topByProfit = models.sortedByDescending { it.profit }.take(topLimit),
            byPayment = byPayment,
        )
    }

    /**
     * Distribui o valor final da venda entre os itens, proporcionalmente ao subtotal.
     * O último item recebe o resto para que a soma bata exatamente com o valor final.
     */
    internal fun allocateDiscount(sale: ReportSale): List<Long> {
        if (sale.items.isEmpty()) return emptyList()
        if (sale.items.size == 1) return listOf(sale.finalAmount)
        val totalSubtotal = sale.items.sumOf { it.subtotal }
        var assigned = 0L
        return sale.items.mapIndexed { index, item ->
            if (index == sale.items.lastIndex) {
                sale.finalAmount - assigned
            } else {
                val share = if (totalSubtotal == 0L) 0L else sale.finalAmount * item.subtotal / totalSubtotal
                assigned += share
                share
            }
        }
    }

    private class MutableModel(val model: String) {
        var quantity = 0
        var revenue = 0L
        var cost = 0L
    }
}
