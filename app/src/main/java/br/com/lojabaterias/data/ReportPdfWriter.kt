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
                summary(f.sales)
                salesExtras(f)
                ranking("Modelos mais vendidos", f.sales.topByQuantity)
                ranking("Modelos com maior faturamento", f.sales.topByRevenue)
                ranking("Modelos com maior lucro", f.sales.topByProfit)
                payments(f.sales)
                salesList(f.activeSales)
                canceledList(f.canceledSales)
                chapter("2. Estoque de baterias")
                stockPeriod(f)
                stockSnapshot(f)
                stockAlerts(f)
                stockMovements(f.stockMovements)
                chapter("3. Sucatas")
                scraps(f.scrap)
                scrapStock(f)
                scrapMovements(f.scrapMovements)
                chapter("4. Baterias na carga")
                chargesSection(f)
                chapter("5. Garantias")
                warrantySection(f)
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

        fun salesExtras(f: FullReport) {
            sectionTitle("Detalhes das vendas")
            keyValueTable(
                listOf(
                    "Valor bruto (preço × quantidade)" to Money.format(f.grossTotal),
                    "Descontos concedidos" to Money.format(f.discountTotal),
                    "Cobrado por sucata faltante" to Money.format(f.scrap.charged),
                    "Faturamento (valor final)" to Money.format(f.sales.revenue),
                    "Vendas canceladas no período" to "${f.canceledSales.size} • ${Money.format(f.canceledAmount)}",
                ),
            )
        }

        fun canceledList(sales: List<SaleWithItems>) {
            if (sales.isEmpty()) return
            sectionTitle("Vendas canceladas (${sales.size}) — não entram nos totais")
            table(
                listOf(Col(1.7f), Col(2.2f), Col(0.6f, true), Col(1.2f), Col(1.4f, true)),
                listOf("Data/hora", "Modelo", "Qtd.", "Pagamento", "Valor"),
                sales.map { s ->
                    listOf(
                        Periods.formatDateTime(s.sale.dateTime),
                        s.modelsLabel,
                        s.quantity.toString(),
                        s.sale.payment.label,
                        Money.format(s.sale.finalAmount),
                    )
                },
            )
        }

        fun stockPeriod(f: FullReport) {
            sectionTitle("Movimentação no período")
            val p = f.stockPeriod
            keyValueTable(
                listOf(
                    "Baterias vendidas" to p.soldUnits.toString(),
                    "Entradas de mercadoria" to "${p.entriesQuantity} un. • ${Money.format(p.entriesCost)}",
                    "Estoque inicial cadastrado" to "${p.initialQuantity} un.",
                    "Ajustes para mais" to "+${p.adjustmentsIn}",
                    "Ajustes para menos" to "-${p.adjustmentsOut}",
                ),
            )
        }

        fun stockSnapshot(f: FullReport) {
            sectionTitle("Posição atual do estoque (no momento da geração)")
            if (f.stockRows.isEmpty()) return emptyLine("Nenhuma bateria cadastrada.")
            val cols = listOf(Col(2f), Col(0.7f, true), Col(0.7f, true), Col(1.2f, true), Col(1.4f, true), Col(1.4f, true))
            val rows = f.stockRows.map { r ->
                listOf(
                    r.model,
                    if (r.amperage > 0) "${r.amperage}Ah" else "—",
                    r.stock.toString(),
                    Money.format(r.cost),
                    Money.format(r.valueAtCost),
                    Money.format(r.valueAtPix),
                )
            } + listOf(
                listOf("TOTAL", "", f.stockUnits.toString(), "", Money.format(f.stockValueAtCost), Money.format(f.stockValueAtPix)),
            )
            table(
                cols,
                listOf("Modelo", "Amp.", "Qtd.", "Custo un.", "Valor (custo)", "Valor (PIX)"),
                rows,
                rowColors = { i ->
                    val r = f.stockRows.getOrNull(i)
                    when {
                        r == null -> null
                        r.isOut -> listOf(null, null, RED, null, null, null)
                        r.isLow -> listOf(null, null, ORANGE, null, null, null)
                        else -> null
                    }
                },
            )
        }

        fun stockAlerts(f: FullReport) {
            if (f.outOfStock.isEmpty() && f.lowStock.isEmpty()) return
            sectionTitle("Alertas de estoque")
            keyValueTable(
                f.outOfStock.map { it.model to "ZERADO" } + f.lowStock.map { it.model to "baixo (${it.stock})" },
                colors = (f.outOfStock.indices.associateWith { RED } +
                    f.lowStock.indices.associate { (it + f.outOfStock.size) to ORANGE }),
            )
        }

        fun stockMovements(list: List<MovementWithModel>) {
            sectionTitle("Movimentações de estoque no período (${list.size})")
            if (list.isEmpty()) return emptyLine("Sem movimentações no período.")
            table(
                listOf(Col(1.7f), Col(1.7f), Col(1.8f), Col(0.8f, true), Col(0.8f, true), Col(2f)),
                listOf("Data/hora", "Modelo", "Tipo", "Qtd.", "Saldo", "Obs."),
                list.map { m ->
                    listOf(
                        Periods.formatDateTime(m.movement.dateTime),
                        m.model ?: "(excluído)",
                        MovementType.label(m.movement.type),
                        (if (m.movement.quantity > 0) "+" else "") + m.movement.quantity,
                        m.movement.stockAfter.toString(),
                        m.movement.note ?: (m.movement.saleId?.let { "Venda #$it" } ?: ""),
                    )
                },
            )
        }

        fun scrapStock(f: FullReport) {
            sectionTitle("Estoque atual de sucatas (no momento da geração)")
            if (f.scrapStock.isEmpty()) return emptyLine("Nenhuma sucata em estoque.")
            table(
                listOf(Col(1.5f), Col(1f, true), Col(1.5f, true), Col(1.5f, true)),
                listOf("Amperagem", "Qtd.", "Valor un. (tabela)", "Valor total"),
                f.scrapStock.map {
                    listOf("${it.amperage}Ah", it.quantity.toString(), it.unitValue?.let(Money::format) ?: "—", Money.format(it.totalValue))
                } + listOf(listOf("TOTAL", f.scrapStockQuantity.toString(), "", Money.format(f.scrapStockValue))),
            )
        }

        fun scrapMovements(list: List<ScrapMovement>) {
            sectionTitle("Movimentações de sucatas no período (${list.size})")
            if (list.isEmpty()) return emptyLine("Sem movimentações de sucata no período.")
            table(
                listOf(Col(1.7f), Col(0.9f), Col(1.9f), Col(0.7f, true), Col(1.3f, true), Col(1.6f)),
                listOf("Data/hora", "Amp.", "Tipo", "Qtd.", "Valor", "Obs."),
                list.map { m ->
                    listOf(
                        Periods.formatDateTime(m.dateTime),
                        "${m.amperage}Ah",
                        ScrapMovementType.label(m.type),
                        (if (m.quantity > 0) "+" else "") + m.quantity,
                        if (m.amount > 0) Money.format(m.amount) else "",
                        m.note ?: (m.saleId?.let { "Venda #$it" } ?: ""),
                    )
                },
            )
        }

        fun chargesSection(f: FullReport) {
            val c = f.charges
            sectionTitle("Resumo")
            keyValueTable(
                listOf(
                    "Recebidas no período" to c.received.toString(),
                    "Valor cobrado" to Money.format(c.charged),
                    "Pago" to Money.format(c.paid),
                    "Não pago" to Money.format(c.unpaid),
                    "Na loja agora" to c.openNow.toString(),
                    "Baterias da loja emprestadas agora" to c.loansOutNow.toString(),
                    "Total a receber (todas)" to Money.format(c.unpaidTotalNow),
                ),
            )
            sectionTitle("Recebidas no período (${f.chargesInPeriod.size})")
            if (f.chargesInPeriod.isEmpty()) return emptyLine("Nenhuma bateria recebida para carga no período.")
            table(
                listOf(Col(1.6f), Col(2f), Col(1.5f), Col(1.2f, true), Col(1f), Col(1.3f)),
                listOf("Recebida", "Cliente", "Telefone", "Valor", "Pago", "Situação"),
                f.chargesInPeriod.map { ch ->
                    listOf(
                        Periods.formatDateTime(ch.receivedAt),
                        ch.customerName + (ch.loanModel?.let { " (empr. $it)" } ?: ""),
                        ch.phone,
                        Money.format(ch.price),
                        if (ch.paid) "Sim" else "Não",
                        ChargeStatus.label(ch.status),
                    )
                },
                rowColors = { i -> listOf(null, null, null, null, if (f.chargesInPeriod[i].paid) GREEN else RED, null) },
            )
        }

        fun warrantySection(f: FullReport) {
            val g = f.warranty
            sectionTitle("Resumo")
            keyValueTable(
                listOf(
                    "Atendimentos no período" to g.attended.toString(),
                    "Trocas (bateria ruim)" to g.exchanged.toString(),
                    "Testadas sem defeito" to g.noDefect.toString(),
                    "Diferenças recebidas" to Money.format(g.differenceTotal),
                    "Custo das baterias novas entregues" to Money.format(g.replacementCost),
                    "Repostas pela fábrica no período" to g.replacedByFactory.toString(),
                    "Garantias negadas no período" to g.denied.toString(),
                    "Usadas vendidas no período" to Money.format(g.usedSoldValue),
                    "Aguardando recolha (agora)" to g.awaitingPickupNow.toString(),
                    "Na fábrica (agora)" to g.atFactoryNow.toString(),
                    "Usadas na loja (agora)" to g.usedInShopNow.toString(),
                ),
            )
            sectionTitle("Atendimentos no período (${f.warrantiesInPeriod.size})")
            if (f.warrantiesInPeriod.isEmpty()) return emptyLine("Nenhum atendimento de garantia no período.")
            table(
                listOf(Col(1.5f), Col(1.3f), Col(1.6f), Col(1.6f), Col(1.1f, true), Col(1.9f)),
                listOf("Data", "Garantia", "Trouxe", "Entregue", "Diferença", "Situação"),
                f.warrantiesInPeriod.map { w ->
                    listOf(
                        Periods.formatDate(w.createdAt),
                        w.saleId?.let { br.com.lojabaterias.domain.WarrantyCode.of(it) } ?: "sem venda",
                        w.returnedModel,
                        w.replacementModel ?: "—",
                        if (w.differenceAmount > 0) Money.format(w.differenceAmount) else "",
                        if (w.status == WarrantyStatus.DENIED) "Negada: " + UsedDestination.label(w.usedDestination)
                        else WarrantyStatus.label(w.status),
                    )
                },
            )
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

        private fun emptyLine(text: String = "Sem vendas no período.") {
            ensure(20f)
            canvas.drawText(text, MARGIN + 4f, y + 12f, small)
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
            val cols = listOf(Col(2f), Col(1f, true), Col(1f, true), Col(1.6f, true), Col(1f, true))
            table(
                cols,
                listOf("Forma de pagamento", "Vendas", "Baterias", "Faturamento", "% do total"),
                r.byPayment.map { p ->
                    val pct = if (r.revenue > 0) "${p.revenue * 100 / r.revenue}%" else "-"
                    listOf(p.method.label, p.salesCount.toString(), p.units.toString(), Money.format(p.revenue), pct)
                },
            )
        }

        fun scraps(s: ScrapPeriodSummary) {
            sectionTitle("Resumo de sucatas no período")
            keyValueTable(
                listOf(
                    "Recebidas nas vendas" to s.returnedInSales.toString(),
                    "Clientes sem sucata (faltantes)" to s.missingInSales.toString(),
                    "Cobrado por sucata faltante (no faturamento)" to Money.format(s.charged),
                    "Entradas manuais" to s.manualInQuantity.toString(),
                    "Compradas" to "${s.purchasedQuantity} • ${Money.format(s.purchasedAmount)}",
                    "Vendidas" to "${s.soldQuantity} • ${Money.format(s.soldAmount)}",
                    "Ajustes (saldo)" to ((if (s.adjustmentNet > 0) "+" else "") + s.adjustmentNet),
                    "Resultado das sucatas (vendido − comprado)" to Money.format(s.netAmount),
                ),
                colors = mapOf(7 to if (s.netAmount < 0) RED else GREEN),
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
