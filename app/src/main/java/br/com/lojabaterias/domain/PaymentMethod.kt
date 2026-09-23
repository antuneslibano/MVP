package br.com.lojabaterias.domain

enum class PaymentMethod(val label: String) {
    PIX("PIX"),
    DEBITO("Débito"),
    CREDITO("Crédito"),
    DINHEIRO("Dinheiro");

    companion object {
        fun fromName(name: String): PaymentMethod =
            entries.firstOrNull { it.name == name } ?: PIX
    }
}

/** Preços de tabela de um produto, em centavos. */
data class PriceTable(
    val pix: Long,
    val debit: Long,
    val credit: Long,
) {
    /** Dinheiro usa o preço PIX. */
    fun priceFor(method: PaymentMethod): Long = when (method) {
        PaymentMethod.PIX, PaymentMethod.DINHEIRO -> pix
        PaymentMethod.DEBITO -> debit
        PaymentMethod.CREDITO -> credit
    }
}
