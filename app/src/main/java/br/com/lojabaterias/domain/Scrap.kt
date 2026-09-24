package br.com.lojabaterias.domain

/** Regras e utilitários de sucatas (baterias usadas deixadas pelos clientes). */
object Scrap {

    private val NUMBER = Regex("\\d{2,3}")

    /**
     * Tenta deduzir a amperagem a partir do nome do modelo.
     * Ex.: "M60GD" → 60, "BEP60D" → 60, "BF45D" → 45, "M100HE" → 100.
     */
    fun guessAmperage(model: String): Int? =
        NUMBER.findAll(model).map { it.value.toInt() }.firstOrNull { it in 20..250 }

    fun format(amperage: Int?): String = if (amperage == null || amperage <= 0) "—" else "${amperage}Ah"

    fun units(n: Int): String = if (n == 1) "1 sucata" else "$n sucatas"

    /** Valor de tabela para uma amperagem (0 se não cadastrada). */
    fun priceFor(amperage: Int?, table: Map<Int, Long>): Long =
        if (amperage == null) 0 else table[amperage] ?: 0
}
