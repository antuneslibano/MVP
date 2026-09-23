package br.com.lojabaterias.domain

/**
 * Valores monetários são sempre tratados em centavos (Long) para evitar
 * erros de arredondamento de ponto flutuante.
 */
object Money {

    /** 123456 -> "R$ 1.234,56" */
    fun format(cents: Long): String {
        val negative = cents < 0
        return (if (negative) "-R$ " else "R$ ") + formatNumber(kotlin.math.abs(cents))
    }

    /** 123456 -> "1.234,56" (sem símbolo). */
    fun formatNumber(cents: Long): String {
        val abs = kotlin.math.abs(cents)
        val reais = abs / 100
        val centavos = abs % 100
        val reaisStr = reais.toString()
        val grouped = StringBuilder()
        for ((count, i) in (reaisStr.length - 1 downTo 0).withIndex()) {
            if (count > 0 && count % 3 == 0) grouped.append('.')
            grouped.append(reaisStr[i])
        }
        val sign = if (cents < 0) "-" else ""
        return sign + grouped.reverse().toString() + "," + centavos.toString().padStart(2, '0')
    }

    /**
     * Converte o texto digitado em um campo com máscara de centavos
     * (ex.: "50000" ou "500,00") em centavos. Apenas dígitos são considerados.
     */
    fun centsFromDigits(text: String): Long {
        val digits = text.filter { it.isDigit() }.trimStart('0').take(MAX_DIGITS)
        return if (digits.isEmpty()) 0L else digits.toLong()
    }

    private const val MAX_DIGITS = 11 // até R$ 999.999.999,99
}
