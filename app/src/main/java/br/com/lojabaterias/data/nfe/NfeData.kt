package br.com.lojabaterias.data.nfe

import br.com.lojabaterias.data.Product
import java.text.Normalizer
import java.time.LocalDate

/** Uma linha de produto lida da nota. Valores em centavos. */
data class NfeItem(
    val description: String,
    val code: String = "",
    val quantity: Int,
    /** Subtotal da linha sem desconto. */
    val grossTotal: Long,
    /** Desconto da linha inteira. */
    val discount: Long = 0,
)

/** Um boleto (duplicata) lido da nota. */
data class NfeBill(val number: String, val dueDate: LocalDate, val amount: Long)

/** Tudo o que foi lido de uma nota fiscal (XML ou PDF). Campos não encontrados ficam vazios/nulos. */
data class NfeData(
    val source: String,
    val number: String = "",
    val supplier: String = "",
    val issueDate: LocalDate? = null,
    val items: List<NfeItem> = emptyList(),
    /** Valor total da nota (o que vai ser pago). */
    val total: Long? = null,
    val bills: List<NfeBill> = emptyList(),
) {
    val itemsNet: Long get() = items.sumOf { it.grossTotal - it.discount }
    /** Frete, impostos e outros: o que o total tem além das baterias. */
    val extras: Long get() = ((total ?: itemsNet) - itemsNet).coerceAtLeast(0)
}

object NfeText {
    /** "2.826,64" ou "2826.64" → 282664 centavos. */
    fun money(text: String): Long? {
        val t = text.trim().replace("R$", "").replace(" ", "")
        if (t.isEmpty()) return null
        val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        return normalized.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()
    }

    /** "4,0000" ou "4.0000" → 4 */
    fun quantity(text: String): Int? {
        val t = text.trim()
        val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        return normalized.toBigDecimalOrNull()?.setScale(0, java.math.RoundingMode.HALF_UP)?.toInt()
    }

    /** Maiúsculas, sem acentos e sem espaços/traços/pontos: "He-60 DD" → "HE60DD". */
    fun compact(text: String): String =
        Normalizer.normalize(text.uppercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace(Regex("[^A-Z0-9]"), "")

    /** Texto com palavras separadas por espaço, para buscar modelos como palavra inteira. */
    fun words(text: String): String =
        " " + Normalizer.normalize(text.uppercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^A-Z0-9]+"), " ").trim() + " "
}

object NfeMatcher {
    /**
     * Acha a bateria do estoque citada na descrição da nota (ex.: "BATERIA HELIAR HE60DD 60AH" → HE60DD).
     * Procura o modelo como palavra inteira; se houver mais de um, fica com o mais longo. Sem achar, devolve null.
     */
    fun match(description: String, products: List<Product>): Product? {
        val words = NfeText.words(description)
        val compactDesc = NfeText.compact(description)
        val byWord = products.filter { p ->
            val model = NfeText.words(p.model).trim()
            model.isNotEmpty() && words.contains(" $model ")
        }
        if (byWord.isNotEmpty()) return byWord.maxBy { NfeText.compact(it.model).length }
        // Sem espaços (ex.: "HE60DD" escrito "HE 60DD"): só aceita modelos com pelo menos 5 caracteres
        return products.filter { p -> NfeText.compact(p.model).let { it.length >= 5 && compactDesc.contains(it) } }
            .maxByOrNull { NfeText.compact(it.model).length }
    }

    private val KNOWN_SUPPLIERS = listOf(
        listOf("HELIAR") to "Heliar do Rio",
        listOf("OESTE RIO", "MOURA") to "Oeste Rio Distribuidora Moura",
        listOf("PCR", "BATERAX") to "PCR Baterias Baterax",
        listOf("BARRA NOTA", "NOTA 10") to "Barra Nota 10",
    )

    /** Troca a razão social pelo nome que vocês usam (ex.: "... MOURA LTDA" → "Oeste Rio Distribuidora Moura"). */
    fun supplierName(raw: String): String {
        val w = NfeText.words(raw)
        KNOWN_SUPPLIERS.forEach { (keys, name) -> if (keys.any { w.contains(" $it ") }) return name }
        return raw.trim().lowercase().split(Regex("\\s+")).joinToString(" ") { part ->
            if (part.length <= 3 && part in setOf("de", "da", "do", "das", "dos", "e")) part else part.replaceFirstChar { it.uppercase() }
        }
    }
}
