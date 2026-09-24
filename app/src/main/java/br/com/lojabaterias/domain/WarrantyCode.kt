package br.com.lojabaterias.domain

/** Código de garantia exibido para cada venda (ex.: "G-7K2Q9XA"), derivado do ID único da venda. */
object WarrantyCode {
    fun of(saleId: Long): String = "G-" + java.lang.Long.toString(saleId, 36).uppercase().takeLast(7)

    /** Aceita "G-7K2Q9XA", "g7k2q9xa" ou "7K2Q9XA". */
    fun normalize(text: String): String = text.trim().uppercase().removePrefix("G").removePrefix("-").trim()

    fun matches(saleId: Long, query: String): Boolean {
        val q = normalize(query)
        return q.isNotEmpty() && of(saleId).removePrefix("G-").contains(q)
    }
}
