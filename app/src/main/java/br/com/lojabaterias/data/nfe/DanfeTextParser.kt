package br.com.lojabaterias.data.nfe

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Lê o texto extraído do PDF da nota (DANFE). Usa as partes que todo DANFE tem:
 * chave de acesso (44 dígitos), "RECEBEMOS DE ...", "DATA DA EMISSÃO", a tabela de produtos,
 * "VALOR TOTAL DA NOTA" e as duplicatas. O que não for encontrado fica vazio para conferir na tela.
 */
object DanfeTextParser {

    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private const val MONEY = """\d{1,3}(?:\.\d{3})*,\d{2}"""

    /** Maiúsculas e sem acento, com o mesmo tamanho do texto original (os índices continuam valendo). */
    private fun fold(text: String): String = buildString(text.length) {
        text.forEach { c ->
            val base = java.text.Normalizer.normalize(c.toString(), java.text.Normalizer.Form.NFD).firstOrNull() ?: c
            append(base.uppercaseChar())
        }
    }

    fun parse(raw: String): NfeData {
        val text = raw.replace(' ', ' ').replace("\r", "")
        val up = fold(text)
        val lines = text.lines()
        val upLines = lines.map { fold(it) }

        val key = accessKey(text)
        val number = key?.substring(25, 34)?.trimStart('0') ?: numberFromLabel(up)
        val supplier = Regex("""RECEBEMOS DE\s+(.{3,120}?)\s+OS PRODUTOS""", RegexOption.DOT_MATCHES_ALL)
            .find(up)?.groups?.get(1)?.let { text.substring(it.range).replace(Regex("\\s+"), " ").trim() }.orEmpty()
        val issueDate = Regex("""DATA\s+D[AE]\s+EMISSAO""").find(up)?.let { m ->
            Regex("""(\d{2}/\d{2}/\d{4})""").find(up, m.range.last)?.takeIf { it.range.first - m.range.last < 400 }
                ?.let { runCatching { LocalDate.parse(it.value, DATE) }.getOrNull() }
        }

        val totals = totals(upLines)
        var items = items(lines, upLines)
        val total = totals["VALOR TOTAL DA NOTA"]
        val gross = items.sumOf { it.grossTotal }
        // Desconto só no total da nota: usa o do quadro de impostos; sem ele, a diferença entre produtos e total
        val discountTotal = totals["DESCONTO"]?.takeIf { it > 0 }
            ?: if (total != null && total < gross && !totals.containsKey("VALOR DO FRETE")) gross - total else null
        if (items.isNotEmpty() && items.all { it.discount == 0L } && discountTotal != null && discountTotal > 0) {
            items = spreadDiscount(items, discountTotal)
        }

        return NfeData(
            source = "PDF",
            number = number.orEmpty(),
            supplier = supplier,
            issueDate = issueDate,
            items = items,
            total = total,
            bills = bills(text, up),
        )
    }

    /** Chave de acesso: 44 dígitos (às vezes em grupos de 4), conferida pelo dígito verificador. */
    fun accessKey(text: String): String? {
        Regex("""\d[\d .]{43,}""").findAll(text).forEach { m ->
            val digits = m.value.filter { it.isDigit() }
            for (offset in 0..digits.length - 44) {
                val candidate = digits.substring(offset, offset + 44)
                // Modelo 55 (NF-e) nas posições 21-22 e dígito verificador correto
                if (candidate.substring(20, 22) == "55" && checkDigitOk(candidate)) return candidate
            }
        }
        return null
    }

    private fun checkDigitOk(key: String): Boolean {
        var weight = 2
        var sum = 0
        for (i in 42 downTo 0) {
            sum += (key[i] - '0') * weight
            weight = if (weight == 9) 2 else weight + 1
        }
        val rest = sum % 11
        val dv = if (rest < 2) 0 else 11 - rest
        return dv == key[43] - '0'
    }

    private fun numberFromLabel(up: String): String? =
        Regex("""\bN\s*[º°O]\s*\.?\s*:?\s*(\d[\d.]{0,14})""").find(up)?.groups?.get(1)?.value
            ?.filter { it.isDigit() }?.trimStart('0')?.ifEmpty { null }

    /**
     * Quadro "CÁLCULO DO IMPOSTO": uma linha com os nomes e a seguinte com os valores, na mesma ordem.
     * Devolve, por exemplo, DESCONTO → 5653 e VALOR TOTAL DA NOTA → 277011.
     */
    private fun totals(upLines: List<String>): Map<String, Long> {
        val labels = listOf(
            "VALOR TOTAL DA NOTA", "VALOR TOTAL DOS PRODUTOS", "VALOR TOTAL DO IPI", "VALOR DO IPI",
            "VALOR DO FRETE", "VALOR DO SEGURO", "OUTRAS DESPESAS", "DESCONTO", "BASE DE CALCULO", "VALOR DO ICMS",
            "V. TOTAL TRIB", "VALOR APROX",
        )
        val result = mutableMapOf<String, Long>()
        upLines.forEachIndexed { i, line ->
            if (!line.contains("VALOR TOTAL DA NOTA") && !line.contains("DESCONTO")) return@forEachIndexed
            // Nomes nesta linha, na ordem em que aparecem
            val found = mutableListOf<Pair<Int, String>>()
            var rest = line
            labels.forEach { label ->
                var idx = rest.indexOf(label)
                while (idx >= 0) {
                    found += idx to label
                    rest = rest.substring(0, idx) + " ".repeat(label.length) + rest.substring(idx + label.length)
                    idx = rest.indexOf(label)
                }
            }
            val names = found.sortedBy { it.first }.map { it.second }
            val values = upLines.drop(i + 1).take(2).map { l -> Regex(MONEY).findAll(l).map { it.value }.toList() }
                .firstOrNull { it.isNotEmpty() } ?: return@forEachIndexed
            if (names.size == values.size) {
                names.zip(values).forEach { (n, v) -> NfeText.money(v)?.let { result.putIfAbsent(n, it) } }
            } else if (names.lastOrNull() == "VALOR TOTAL DA NOTA") {
                NfeText.money(values.last())?.let { result.putIfAbsent("VALOR TOTAL DA NOTA", it) }
            }
        }
        // Valor logo ao lado do nome (alguns DANFEs põem tudo na mesma linha)
        if ("VALOR TOTAL DA NOTA" !in result) {
            upLines.forEach { l ->
                Regex("""VALOR TOTAL DA NOTA\s*:?\s*(?:R\$\s*)?($MONEY)""").find(l)?.let {
                    result.putIfAbsent("VALOR TOTAL DA NOTA", NfeText.money(it.groupValues[1]) ?: 0)
                }
            }
        }
        return result
    }

    private val ITEM_LINE = Regex(
        """^(.*?)\s+(\d{4}\.?\d{2}\.?\d{2})\s+(\d{3,4})\s+([1-7]\d{3})\s+([A-Za-z]{1,6})\s+(\d[\d.]*,\d{1,4}|\d+)\s+(\d[\d.]*,\d{2,10})\s+($MONEY)\s*(.*)$"""
    )

    /** Tabela "DADOS DOS PRODUTOS": código + descrição, NCM, CST, CFOP, unidade, quantidade, valor unitário, total... */
    private fun items(lines: List<String>, upLines: List<String>): List<NfeItem> {
        val start = upLines.indexOfFirst { it.contains("DADOS DO") && it.contains("PRODUTO") }.takeIf { it >= 0 } ?: 0
        val end = upLines.indexOfFirst { it.contains("DADOS ADICIONAIS") || it.contains("CALCULO DO ISSQN") }
            .takeIf { it > start } ?: upLines.size
        val header = upLines.subList(start, end).firstOrNull { it.contains("NCM") }.orEmpty()
        val afterTotal = header.substringAfter("TOTAL", "")
        val hasDiscountColumn = Regex("""DESC""").containsMatchIn(afterTotal.substringBefore("ICMS"))

        val result = mutableListOf<NfeItem>()
        for (i in start until end) {
            val line = lines[i].replace(Regex("\\s+"), " ").trim()
            val m = ITEM_LINE.find(line)
            if (m == null) {
                // Descrição que quebrou para a linha de baixo
                val last = result.lastOrNull()
                if (last != null && line.isNotEmpty() && !Regex(MONEY).containsMatchIn(line) && !fold(line).contains("DADOS")) {
                    result[result.lastIndex] = last.copy(description = "${last.description} $line".trim())
                }
                continue
            }
            val prefix = m.groupValues[1].trim()
            val parts = prefix.split(" ", limit = 2)
            val (code, description) = if (parts.size == 2 && parts[0].any { it.isDigit() }) parts[0] to parts[1] else "" to prefix
            val rest = m.groupValues[9]
            val discount = if (hasDiscountColumn) Regex("^($MONEY)").find(rest.trim())?.let { NfeText.money(it.value) } ?: 0 else 0
            result += NfeItem(
                description = description,
                code = code,
                quantity = NfeText.quantity(m.groupValues[6]) ?: 0,
                grossTotal = NfeText.money(m.groupValues[8]) ?: 0,
                discount = discount,
            )
        }
        return result
    }

    /** Desconto só no total: divide entre as linhas pelo valor de cada uma (a última fica com o resto). */
    private fun spreadDiscount(items: List<NfeItem>, discount: Long): List<NfeItem> {
        val gross = items.sumOf { it.grossTotal }.takeIf { it > 0 } ?: return items
        var given = 0L
        return items.mapIndexed { i, it ->
            val part = if (i == items.lastIndex) discount - given else discount * it.grossTotal / gross
            given += part
            it.copy(discount = part)
        }
    }

    /** Quadro "FATURA / DUPLICATAS": vencimento e valor de cada boleto. */
    private fun bills(text: String, up: String): List<NfeBill> {
        val start = Regex("""FATURA|DUPLICATA""").find(up)?.range?.first ?: return emptyList()
        val end = Regex("""CALCULO DO IMPOSTO""").find(up, start)?.range?.first ?: return emptyList()
        val block = text.substring(start, end)
        return Regex("""(?:(\d{1,3}(?:/\d{1,3})?|\d{3,})\s+)?(\d{2}/\d{2}/\d{4})\s+(?:R\$\s*)?($MONEY)""").findAll(block).mapNotNull { m ->
            val due = runCatching { LocalDate.parse(m.groupValues[2], DATE) }.getOrNull() ?: return@mapNotNull null
            val amount = NfeText.money(m.groupValues[3]) ?: return@mapNotNull null
            NfeBill(m.groupValues[1], due, amount)
        }.toList()
    }
}
