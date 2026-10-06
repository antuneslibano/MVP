package br.com.lojabaterias.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import br.com.lojabaterias.domain.Labels
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.domain.Periods
import java.io.OutputStream

/** Dados necessários para gerar o PDF de um relatório. */
data class ReportDocument(
    val type: PeriodType,
    val periodLabel: String,
    val full: FullReport,
    val generatedAt: Long = System.currentTimeMillis(),
)

/**
 * Gera o relatório em PDF (A4) usando o [PdfDocument] nativo do Android,
 * sem bibliotecas externas.
 */
object ReportPdfWriter {

    private const val PAGE_WIDTH = 595 // A4 em pontos (1/72")
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val FOOTER_SPACE = 36f

    private val PRIMARY = Color.rgb(0x1D, 0x4E, 0xD8)
    private val TEXT = Color.rgb(0x15, 0x18, 0x1E)
    private val MUTED = Color.rgb(0x58, 0x5E, 0x6B)
    private val HEADER_BG = Color.rgb(0xE8, 0xEE, 0xFF)
    private val ZEBRA_BG = Color.rgb(0xF5, 0xF6, 0xFA)
    private val LINE = Color.rgb(0xD5, 0xD9, 0xE2)
    private val GREEN = Color.rgb(0x15, 0x80, 0x3D)
    private val RED = Color.rgb(0xB9, 0x1C, 0x1C)
    private val ORANGE = Color.rgb(0xB4, 0x53, 0x09)

    fun typeTitle(type: PeriodType): String = when (type) {
        PeriodType.DAY -> "Relatório diário"
        PeriodType.WEEK -> "Relatório semanal"
        PeriodType.MONTH -> "Relatório mensal"
    }

    fun write(doc: ReportDocument, output: OutputStream) {
        val pdf = PdfDocument()
        try {
            Builder(pdf, "${typeTitle(doc.type)} • ${doc.periodLabel}").apply {
                val f = doc.full
                header(doc)
                chapter("1. Vendas")
                salesSummary(f)
                chapter("2. Baterias vendidas por dia")
                daily(f)
                if (doc.type != PeriodType.DAY && f.daily.size > 1) modelsTotal(f)
                chapter("3. Resumo geral")
                generalSummary(f)
                finish()
            }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    private class Col(val weight: Float, val alignRight: Boolean = false)

    private class Builder(private val pdf: PdfDocument, private val footerTitle: String) {
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = 0f

        private val title = paint(18f, bold = true, color = PRIMARY)
        private val subtitle = paint(13f, bold = true)
        private val small = paint(8.5f, color = MUTED)
        private val section = paint(11.5f, bold = true, color = PRIMARY)
        private val body = paint(9.5f)
        private val bodyBold = paint(9.5f, bold = true)
        private val big = paint(15f, bold = true)
        private val fill = Paint().apply { style = Paint.Style.FILL }
        private val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; color = LINE }

        init {
            newPage()
        }

        private fun paint(size: Float, bold: Boolean = false, color: Int = TEXT) = Paint().apply {
            isAntiAlias = true
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        private fun newPage() {
            if (pageNumber > 0) closePage()
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        private fun closePage() {
            val footerY = PAGE_HEIGHT - MARGIN / 2
            canvas.drawLine(MARGIN, footerY - 12f, PAGE_WIDTH - MARGIN, footerY - 12f, stroke)
            canvas.drawText("Art das Baterias • $footerTitle", MARGIN, footerY, small)
            val pageLabel = "Página $pageNumber"
            canvas.drawText(pageLabel, PAGE_WIDTH - MARGIN - small.measureText(pageLabel), footerY, small)
            pdf.finishPage(page)
        }

        fun finish() = closePage()

        /** Garante espaço vertical; abre nova página se necessário. Retorna true se abriu. */
        private fun ensure(height: Float): Boolean {
            if (y + height > PAGE_HEIGHT - MARGIN - FOOTER_SPACE) {
                newPage()
                return true
            }
            return false
        }

        private fun fit(text: String, p: Paint, width: Float): String {
            if (p.measureText(text) <= width) return text
            var end = text.length
            while (end > 0 && p.measureText(text.substring(0, end) + "…") > width) end--
            return text.substring(0, end) + "…"
        }

        fun header(doc: ReportDocument) {
            canvas.drawText("Art das Baterias", MARGIN, y + 18f, title)
            y += 38f
            canvas.drawText("${typeTitle(doc.type)} — ${doc.periodLabel}", MARGIN, y, subtitle)
            y += 16f
            canvas.drawText("Gerado em ${Periods.formatDateTime(doc.generatedAt)}", MARGIN, y, small)
            y += 10f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, stroke)
            y += 18f
        }

        private val chapterPaint = paint(14f, bold = true, color = PRIMARY)

        /** Título de capítulo com linha. */
        fun chapter(text: String) {
            ensure(80f)
            y += 14f
            canvas.drawText(text, MARGIN, y, chapterPaint)
            y += 6f
            val line = Paint(stroke).apply { color = PRIMARY; strokeWidth = 1.2f }
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, line)
            y += 8f
        }

        private fun keyValueTable(rows: List<Pair<String, String>>, colors: Map<Int, Int> = emptyMap()) {
            table(
                listOf(Col(3f), Col(1.6f, true)),
                listOf("Indicador", "Valor"),
                rows.map { listOf(it.first, it.second) },
                rowColors = { i -> colors[i]?.let { listOf(null, it) } },
            )
        }

        private fun sectionTitle(text: String) {
            ensure(60f)
            y += 8f
            canvas.drawText(text, MARGIN, y, section)
            y += 10f
        }

        private fun tableRow(cols: List<Col>, values: List<String>, p: Paint, bg: Int?, colors: List<Int?>? = null) {
            val rowH = 18f
            val totalWeight = cols.sumOf { it.weight.toDouble() }.toFloat()
            if (bg != null) {
                fill.color = bg
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowH, fill)
            }
            var x = MARGIN
            cols.forEachIndexed { i, col ->
                val w = CONTENT_WIDTH * col.weight / totalWeight
                val text = fit(values[i], p, w - 8f)
                val originalColor = p.color
                colors?.getOrNull(i)?.let { p.color = it }
                val tx = if (col.alignRight) x + w - 4f - p.measureText(text) else x + 4f
                canvas.drawText(text, tx, y + 12.5f, p)
                p.color = originalColor
                x += w
            }
            y += rowH
        }

        private fun table(cols: List<Col>, headers: List<String>, rows: List<List<String>>, rowColors: (Int) -> List<Int?>? = { null }) {
            ensure(40f)
            tableRow(cols, headers, bodyBold, HEADER_BG)
            rows.forEachIndexed { index, row ->
                if (ensure(18f)) tableRow(cols, headers, bodyBold, HEADER_BG)
                tableRow(cols, row, body, if (index % 2 == 1) ZEBRA_BG else null, rowColors(index))
            }
            y += 8f
        }

        private fun emptyLine(text: String = "Sem vendas no período.") {
            ensure(20f)
            canvas.drawText(text, MARGIN + 4f, y + 12f, small)
            y += 22f
        }

        /** Números principais das vendas, em quadros. */
        fun salesSummary(f: FullReport) {
            val r = f.sales
            sectionTitle("Vendas do período")
            val cells = listOf(
                Triple("Faturamento", Money.format(r.revenue), TEXT),
                Triple("Lucro das vendas", Money.format(r.profit), if (r.profit < 0) RED else GREEN),
                Triple("Lucro líquido", Money.format(f.netProfit), if (f.netProfit < 0) RED else GREEN),
                Triple("Baterias vendidas", r.unitsSold.toString(), TEXT),
                Triple("Média por dia", Labels.oneDecimal(f.averagePerSalesDay), TEXT),
                Triple("Vendas", r.salesCount.toString(), TEXT),
            )
            val cellW = CONTENT_WIDTH / 3
            val cellH = 44f
            cells.chunked(3).forEach { row ->
                ensure(cellH + 6f)
                row.forEachIndexed { i, (label, value, color) ->
                    val x = MARGIN + i * cellW
                    fill.color = ZEBRA_BG
                    canvas.drawRect(x + 2f, y, x + cellW - 2f, y + cellH, fill)
                    canvas.drawText(label, x + 10f, y + 15f, small)
                    big.color = color
                    canvas.drawText(fit(value, big, cellW - 20f), x + 10f, y + 34f, big)
                }
                y += cellH + 4f
            }
            big.color = TEXT
            ensure(16f)
            val days = f.daily.size
            canvas.drawText(
                "Média calculada sobre ${if (days == 1) "1 dia" else "$days dias"} com venda. Vendas canceladas não entram.",
                MARGIN, y + 10f, small,
            )
            y += 22f
        }

        /** Uma seção por dia: quantas baterias e quais modelos (modelos iguais somados). */
        fun daily(f: FullReport) {
            if (f.daily.isEmpty()) {
                emptyLine()
                return
            }
            f.daily.forEach { d ->
                sectionTitle("${Labels.dayTitle(d.date)}  •  ${Labels.batteries(d.units)}  •  ${Money.format(d.revenue)}")
                table(
                    listOf(Col(3f), Col(1f, true)),
                    listOf("Modelo", "Quantidade"),
                    d.models.map { listOf(it.model, it.count.toString()) },
                )
            }
        }

        fun modelsTotal(f: FullReport) {
            sectionTitle("Total por modelo no período")
            table(
                listOf(Col(3f), Col(1f, true)),
                listOf("Modelo", "Quantidade"),
                f.modelsTotal.map { listOf(it.model, it.count.toString()) } + listOf(listOf("TOTAL", f.sales.unitsSold.toString())),
            )
        }

        /** Resumo curto de todo o resto (sem listas de movimentações). */
        fun generalSummary(f: FullReport) {
            val left = f.cash.cashBeforeWithdrawals - f.withdrawalsTotal
            keyValueTable(
                listOf(
                    "Despesas pagas" to Money.format(f.expensesTotal),
                    "Lucro líquido" to Money.format(f.netProfit),
                    "Baterias em estoque (agora)" to "${f.stockUnits} • ${Money.format(f.stockValueAtCost)}",
                    "Entradas no estoque no período" to "${f.stockPeriod.entriesQuantity} un.",
                    "Estoque baixo / zerado (agora)" to "${f.lowStock.size} / ${f.outOfStock.size} modelos",
                    "Sucatas em estoque (agora)" to f.scrapStockQuantity.toString(),
                    "Sucatas vendidas no período" to "${f.scrap.soldQuantity} • ${Money.format(f.scrap.soldAmount)}",
                    "Baterias na carga recebidas" to "${f.charges.received} • a receber ${Money.format(f.charges.unpaidTotalNow)}",
                    "Trocas em garantia" to f.warranty.exchangedTotal.toString(),
                    "Extras ganhas / vendidas" to "${f.warranty.extrasTotal} / ${f.extrasSold.sold} • lucro ${Money.format(f.extrasSold.profit)}",
                    "Do Vitor registradas" to "${f.warranty.vitorTotal} • pago ${Money.format(f.warranty.vitorPaid)}",
                    "Boletos pagos no período" to "${f.invoices.paid.size} • ${Money.format(f.invoices.paidTotal)}",
                    "Bonificações recebidas" to "${f.invoices.bonusUnits} baterias • nota ${Money.format(f.invoices.bonusValue)}",
                    "Devemos aos fornecedores (hoje)" to Money.format(f.invoices.debt.open),
                    "Caixa: entrou / saiu" to "${Money.format(f.cash.cashIn)} / ${Money.format(f.cash.cashOut)}",
                    "Retiradas dos sócios" to Money.format(f.withdrawalsTotal),
                    "Ficou na loja" to Money.format(left),
                ),
                colors = mapOf(1 to if (f.netProfit < 0) RED else GREEN, 16 to if (left < 0) RED else GREEN),
            )
        }
    }
}
