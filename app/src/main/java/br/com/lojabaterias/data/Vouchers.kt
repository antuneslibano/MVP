package br.com.lojabaterias.data

/**
 * Vale de casco: venda em que o cliente não deixou a sucata e pagou por ela.
 * Fica em aberto até o cliente trazer os cascos ([ScrapMovementType.VOUCHER_PAID]).
 */
data class Voucher(
    val sale: SaleWithItems,
    /** Cascos que ainda faltam trazer. */
    val remaining: Int,
    /** Valor de cada casco (cobrado na venda). */
    val unitValue: Long,
) {
    val value: Long get() = unitValue * remaining
}

object Vouchers {
    /** Vales em aberto, mais antigos primeiro. */
    fun open(sales: List<SaleWithItems>, scrapMovements: List<ScrapMovement>): List<Voucher> {
        val paid = scrapMovements.filter { it.type == ScrapMovementType.VOUCHER_PAID && it.saleId != null }
            .groupBy { it.saleId }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }
        return sales
            .filter { !it.sale.isCanceled && it.sale.scrapMissing > 0 && it.sale.scrapCharge > 0 }
            .mapNotNull { s ->
                val remaining = s.sale.scrapMissing - (paid[s.sale.id] ?: 0)
                if (remaining <= 0) null else Voucher(s, remaining, s.sale.scrapCharge / s.sale.scrapMissing)
            }
            .sortedBy { it.sale.sale.dateTime }
    }
}
