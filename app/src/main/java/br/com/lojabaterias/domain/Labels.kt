package br.com.lojabaterias.domain

/** Textos com singular/plural corretos. */
object Labels {
    fun sales(n: Int): String = if (n == 1) "1 venda" else "$n vendas"
    fun batteries(n: Int): String = if (n == 1) "1 bateria" else "$n baterias"

    /** Ex.: "4 vendas • 5 baterias" */
    fun salesAndBatteries(sales: Int, batteries: Int): String = "${sales(sales)} • ${batteries(batteries)}"
}
