package br.com.lojabaterias.data.nfe

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

/** Lê o XML da NF-e (o arquivo que o fornecedor manda junto com o PDF). Os dados vêm exatos. */
object NfeXmlParser {

    fun looksLikeNfe(text: String): Boolean = text.contains("<infNFe") || text.contains(":infNFe")

    fun parse(xml: String): NfeData {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Segurança: não processa entidades externas
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val root = doc.documentElement

        fun Element.child(name: String): Element? {
            val list = getElementsByTagNameNS("*", name)
            return if (list.length > 0) list.item(0) as Element else null
        }
        fun Element.text(name: String): String = child(name)?.textContent?.trim().orEmpty()

        val ide = root.child("ide")
        val emit = root.child("emit")
        val number = ide?.text("nNF").orEmpty().trimStart('0')
        val date = ide?.let { (it.text("dhEmi").ifEmpty { it.text("dEmi") }).take(10) }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val supplier = emit?.let { it.text("xFant").ifEmpty { it.text("xNome") } }.orEmpty()

        val dets = root.getElementsByTagNameNS("*", "det")
        val items = (0 until dets.length).mapNotNull { i ->
            val prod = (dets.item(i) as Element).child("prod") ?: return@mapNotNull null
            NfeItem(
                description = prod.text("xProd"),
                code = prod.text("cProd"),
                quantity = NfeText.quantity(prod.text("qCom")) ?: 0,
                grossTotal = NfeText.money(prod.text("vProd")) ?: 0,
                discount = NfeText.money(prod.text("vDesc")) ?: 0,
            )
        }

        val total = root.child("ICMSTot")?.text("vNF")?.let { NfeText.money(it) }

        val dups = root.getElementsByTagNameNS("*", "dup")
        val bills = (0 until dups.length).mapNotNull { i ->
            val d = dups.item(i) as Element
            val due = runCatching { LocalDate.parse(d.text("dVenc")) }.getOrNull() ?: return@mapNotNull null
            val amount = NfeText.money(d.text("vDup")) ?: return@mapNotNull null
            NfeBill(d.text("nDup"), due, amount)
        }

        return NfeData(
            source = "XML",
            number = number,
            supplier = supplier,
            issueDate = date,
            items = items,
            total = total,
            bills = bills,
        )
    }
}
