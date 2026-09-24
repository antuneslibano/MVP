package br.com.lojabaterias.domain

/**
 * Taxas das maquininhas, em pontos-base (1% = 100).
 * Padrão: crédito 7%, débito 2%. PIX e dinheiro não têm taxa.
 */
data class CardFees(val creditBps: Int = DEFAULT_CREDIT_BPS, val debitBps: Int = DEFAULT_DEBIT_BPS) {

    fun rateFor(method: PaymentMethod): Int = when (method) {
        PaymentMethod.CREDITO -> creditBps
        PaymentMethod.DEBITO -> debitBps
        PaymentMethod.PIX, PaymentMethod.DINHEIRO -> 0
    }

    /** Taxa sobre o valor recebido no cartão, arredondada ao centavo. */
    fun feeFor(method: PaymentMethod, amount: Long): Long = feeOf(amount, rateFor(method))

    companion object {
        const val DEFAULT_CREDIT_BPS = 700
        const val DEFAULT_DEBIT_BPS = 200
        val DEFAULT = CardFees()

        fun feeOf(amount: Long, bps: Int): Long = if (amount <= 0 || bps <= 0) 0 else (amount * bps + 5_000) / 10_000

        /** 700 -> "7%", 250 -> "2,5%" */
        fun formatPercent(bps: Int): String {
            val whole = bps / 100
            val frac = bps % 100
            return if (frac == 0) "$whole%" else "$whole," + frac.toString().padStart(2, '0').trimEnd('0') + "%"
        }

        /** "7" -> 700, "2,5" -> 250. Retorna null se inválido. */
        fun parsePercent(text: String): Int? {
            val t = text.trim().replace(',', '.').removeSuffix("%").trim()
            val v = t.toBigDecimalOrNull() ?: return null
            if (v < java.math.BigDecimal.ZERO || v > java.math.BigDecimal(50)) return null
            return v.multiply(java.math.BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).toInt()
        }
    }
}
