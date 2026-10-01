package br.com.lojabaterias.domain

/** Textos com singular/plural corretos. */
object Labels {
    fun sales(n: Int): String = if (n == 1) "1 venda" else "$n vendas"
    fun batteries(n: Int): String = if (n == 1) "1 bateria" else "$n baterias"

    /** Ex.: "4 vendas • 5 baterias" */
    fun salesAndBatteries(sales: Int, batteries: Int): String = "${sales(sales)} • ${batteries(batteries)}"

    private val WEEKDAYS = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
    private val DAY_MONTH = java.time.format.DateTimeFormatter.ofPattern("dd/MM")

    /** Ex.: "Seg, 21/09" */
    fun dayTitle(date: java.time.LocalDate): String = "${WEEKDAYS[date.dayOfWeek.value - 1]}, ${date.format(DAY_MONTH)}"

    /** 10.5 → "10,5"; 10.0 → "10" */
    fun oneDecimal(v: Double): String = String.format(java.util.Locale.US, "%.1f", v).replace('.', ',').removeSuffix(",0")
}
