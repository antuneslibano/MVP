package br.com.lojabaterias.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import br.com.lojabaterias.domain.ModelStats
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.domain.Report
import java.io.OutputStream

/** Dados necessários para gerar o PDF de um relatório. */
data class ReportDocument(
    val type: PeriodType,
    val periodLabel: String,
    val report: Report,
    /** Vendas válidas (não canceladas) do período, mais recentes primeiro. */
    val sales: List<SaleWithItems>,
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

    fun typeTitle(type: PeriodType): String = when (type) {
        PeriodType.DAY -> "Relatório diário"
        PeriodType.WEEK -> "Relatório semanal"
        PeriodType.MONTH -> "Relatório mensal"
    }

    fun write(doc: ReportDocument, output: OutputStream) {
        val pdf = PdfDocument()
        try {
            Builder(pdf, "${typeTitle(doc.type)} • ${doc.periodLabel}").apply {
                header(doc)
                summary(doc.report)
                ranking("Modelos mais vendidos", doc.report.topByQuantity)
                ranking("Modelos com maior faturamento", doc.report.topByRevenue)
                ranking("Modelos com maior lucro", doc.report.topByProfit)
                payments(doc.report)
                salesList(doc.sales)
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
            canvas.drawText("Loja de Baterias • $footerTitle", MARGIN, footerY, small)
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
            canvas.drawText("Loja de Baterias", MARGIN, y + 18f, title)
            y += 38f
            canvas.drawText("${typeTitle(doc.type)} — ${doc.periodLabel}", MARGIN, y, subtitle)
            y += 16f
            canvas.drawText("Gerado em ${Periods.formatDateTime(doc.generatedAt)}", MARGIN, y, small)
            y += 10f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, stroke)
            y += 18f
        }

        private fun sectionTitle(text: String) {
            ensure(60f)
            y += 8f
            canvas.drawText(text, MARGIN, y, section)
            y += 10f
        }

        fun summary(r: Report) {
            sectionTitle("Resumo")
            val cells = listOf(
                Triple("Faturamento", Money.format(r.revenue), TEXT),
                Triple("Custo", Money.format(r.cost), TEXT),
                Triple("Lucro bruto", Money.format(r.profit), if (r.profit < 0) RED else GREEN),
                Triple("Vendas", r.salesCount.toString(), TEXT),
                Triple("Baterias vendidas", r.unitsSold.toString(), TEXT),
                Triple("Ticket médio", Money.format(r.averageTicket), TEXT),
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
            y += 6f
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

        private fun emptyLine() {
            ensure(20f)
            canvas.drawText("Sem vendas no período.", MARGIN + 4f, y + 12f, small)
            y += 22f
        }

        fun ranking(titleText: String, list: List<ModelStats>) {
            sectionTitle(titleText)
            if (list.isEmpty()) return emptyLine()
            val cols = listOf(Col(0.5f), Col(2.5f), Col(1f, true), Col(1.6f, true), Col(1.6f, true))
            table(
                cols,
                listOf("#", "Modelo", "Qtd.", "Faturamento", "Lucro"),
                list.mapIndexed { i, m ->
                    listOf("${i + 1}º", m.model, m.quantity.toString(), Money.format(m.revenue), Money.format(m.profit))
                },
                rowColors = { i -> listOf(null, null, null, null, if (list[i].profit < 0) RED else GREEN) },
            )
        }

        fun payments(r: Report) {
            sectionTitle("Vendas por forma de pagamento")
            if (r.byPayment.isEmpty()) return emptyLine()
            val cols = listOf(Col(2f), Col(1f, true), Col(1.6f, true), Col(1f, true))
            table(
                cols,
                listOf("Forma de pagamento", "Vendas", "Faturamento", "% do total"),
                r.byPayment.map { p ->
                    val pct = if (r.revenue > 0) "${p.revenue * 100 / r.revenue}%" else "-"
                    listOf(p.method.label, p.salesCount.toString(), Money.format(p.revenue), pct)
                },
            )
        }

        fun salesList(sales: List<SaleWithItems>) {
            sectionTitle("Vendas do período (${sales.size})")
            if (sales.isEmpty()) return emptyLine()
            val cols = listOf(Col(1.7f), Col(1.9f), Col(0.6f, true), Col(1.1f), Col(1.4f, true), Col(1.4f, true), Col(1.4f, true))
            table(
                cols,
                listOf("Data/hora", "Modelo", "Qtd.", "Pagamento", "Valor", "Custo", "Lucro"),
                sales.map { s ->
                    listOf(
                        Periods.formatDateTime(s.sale.dateTime),
                        s.modelsLabel,
                        s.quantity.toString(),
                        s.sale.payment.label,
                        Money.format(s.sale.finalAmount),
                        Money.format(s.sale.totalCost),
                        Money.format(s.sale.grossProfit),
                    )
                },
                rowColors = { i -> listOf(null, null, null, null, null, null, if (sales[i].sale.grossProfit < 0) RED else GREEN) },
            )
            ensure(16f)
            canvas.drawText("Vendas canceladas não entram neste relatório.", MARGIN, y + 8f, small)
            y += 16f
        }
    }
}
