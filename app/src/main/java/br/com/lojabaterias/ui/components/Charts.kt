package br.com.lojabaterias.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Cores dos gráficos (paleta testada para daltonismo, nesta ordem).
 * Os números e textos continuam nas cores de texto; a cor só identifica a série.
 */
object ChartColors {
    private val light = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100))
    private val dark = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500))

    @Composable
    fun slot(index: Int): Color = (if (isSystemInDarkTheme()) dark else light)[index]
}

/** "R$ 1,2 mil", "R$ 850" — valores curtos para eixos. */
fun compactMoney(cents: Long): String {
    val reais = cents / 100.0
    val a = abs(reais)
    val sign = if (reais < 0) "-" else ""
    return when {
        a >= 1_000_000 -> sign + "R$ " + "%.1f".format(a / 1_000_000).replace('.', ',').removeSuffix(",0") + " mi"
        a >= 1_000 -> sign + "R$ " + "%.1f".format(a / 1_000).replace('.', ',').removeSuffix(",0") + " mil"
        else -> sign + "R$ " + a.toLong()
    }
}

/** Quadradinho colorido + texto (legenda). */
@Composable
fun LegendDot(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Barra única dividida em partes (ex.: para onde foi cada real que entrou). */
@Composable
fun StackedShareBar(parts: List<Pair<Long, Color>>, modifier: Modifier = Modifier, description: String = "") {
    val gapColor = MaterialTheme.colorScheme.surface
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = description }
    ) {
        val visible = parts.filter { it.first > 0 }
        val total = visible.sumOf { it.first }.toFloat()
        if (total <= 0f) {
            drawRect(track)
            return@Canvas
        }
        val gap = 2.dp.toPx()
        val usable = size.width - gap * (visible.size - 1)
        var x = 0f
        visible.forEach { (value, color) ->
            val w = usable * value / total
            drawRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w
            if (x < size.width) drawRect(gapColor, topLeft = Offset(x, 0f), size = Size(gap, size.height))
            x += gap
        }
    }
}

/** Linha de uma lista de barras horizontais. */
data class HBarRow(val label: String, val value: Long, val valueText: String, val detail: String? = null)

/** Lista de barras horizontais (uma série só): rótulo, valor e barra proporcional. */
@Composable
fun HBarList(rows: List<HBarRow>, color: Color, modifier: Modifier = Modifier) {
    val max = rows.maxOfOrNull { abs(it.value) }?.takeIf { it > 0 } ?: 1L
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(row.valueText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                if (row.detail != null) {
                    Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val fraction = (abs(row.value).toFloat() / max).coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

/** Um grupo de barras do gráfico de evolução. */
data class BarGroup(val label: String, val a: Long, val b: Long)

/**
 * Gráfico de barras em pares (ex.: faturamento × lucro), com valores negativos abaixo da linha do zero.
 * Tocar num grupo seleciona (o valor aparece no texto acima do gráfico).
 */
@Composable
fun PairedBarChart(
    groups: List<BarGroup>,
    colorA: Color,
    colorB: Color,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String = "",
) {
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zeroLine = MaterialTheme.colorScheme.outline
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val n = groups.size.coerceAtLeast(1)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = description }
            .pointerInput(n) {
                detectTapGestures { pos ->
                    val axisW = 44.dp.toPx()
                    if (pos.x >= axisW) onSelect(((pos.x - axisW) / ((size.width - axisW) / n)).toInt().coerceIn(0, n - 1))
                }
            }
    ) {
        val axisW = 44.dp.toPx()
        val labelH = 20.dp.toPx()
        val top = 8.dp.toPx()
        val plotH = size.height - labelH - top
        val plotW = size.width - axisW
        val maxV = groups.maxOfOrNull { maxOf(it.a, it.b) }?.coerceAtLeast(0) ?: 0
        val minV = groups.minOfOrNull { minOf(it.a, it.b) }?.coerceAtMost(0) ?: 0
        val span = (maxV - minV).takeIf { it > 0 } ?: 1L
        fun y(v: Long) = top + plotH * (maxV - v).toFloat() / span
        val zeroY = y(0)

        // Linhas de referência (topo, meio e zero), discretas
        listOf(maxV, maxV / 2).distinct().filter { it > 0 }.forEach { v ->
            drawLine(grid, Offset(axisW, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx())
            drawAxisLabel(measurer, compactMoney(v), axisStyle, y(v))
        }
        if (minV < 0) drawAxisLabel(measurer, compactMoney(minV), axisStyle, y(minV))
        drawLine(zeroLine, Offset(axisW, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())

        val slot = plotW / n
        val gap = 2.dp.toPx()
        val barW = ((slot * 0.8f - gap) / 2).coerceIn(1.dp.toPx(), 16.dp.toPx())
        val every = ceil(n / 8f).toInt().coerceAtLeast(1)
        groups.forEachIndexed { i, g ->
            val x0 = axisW + slot * i
            if (i == selected) drawRect(highlight, Offset(x0, top), Size(slot, plotH))
            val cx = x0 + slot / 2
            drawBar(cx - gap / 2 - barW, barW, zeroY, y(g.a), colorA)
            drawBar(cx + gap / 2, barW, zeroY, y(g.b), colorB)
            if (i % every == 0 || i == selected) {
                val layout = measurer.measure(g.label, axisStyle)
                val lx = (cx - layout.size.width / 2).coerceIn(axisW, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(lx, size.height - labelH + 4.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.drawAxisLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    y: Float,
) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(0f, (y - layout.size.height / 2).coerceAtLeast(0f)))
}

/** Barra presa na linha do zero, com a ponta (do lado do valor) arredondada. */
private fun DrawScope.drawBar(x: Float, w: Float, zeroY: Float, valueY: Float, color: Color) {
    if (abs(valueY - zeroY) < 0.5f) return
    val r = CornerRadius(minOf(4.dp.toPx(), w / 2))
    val up = valueY < zeroY
    val rect = if (up) Rect(x, valueY, x + w, zeroY) else Rect(x, zeroY, x + w, valueY)
    val rr = if (up) {
        RoundRect(rect, topLeft = r, topRight = r, bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero)
    } else {
        RoundRect(rect, topLeft = CornerRadius.Zero, topRight = CornerRadius.Zero, bottomRight = r, bottomLeft = r)
    }
    drawPath(Path().apply { addRoundRect(rr) }, color)
}
