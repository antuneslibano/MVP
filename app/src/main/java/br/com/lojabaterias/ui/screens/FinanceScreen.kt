package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.FinancePeriod
import br.com.lojabaterias.data.FinanceReport
import br.com.lojabaterias.data.FinanceSummary
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.BarGroup
import br.com.lojabaterias.ui.components.ChartColors
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.HBarList
import br.com.lojabaterias.ui.components.HBarRow
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.LegendDot
import br.com.lojabaterias.ui.components.PairedBarChart
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.StackedShareBar
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.FinanceViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen() {
    val vm = appViewModel { FinanceViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val f = state.report
    val c = f.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financeiro") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FinancePeriod.entries.forEachIndexed { index, p ->
                        SegmentedButton(
                            selected = state.selection.period == p,
                            onClick = { vm.setPeriod(p) },
                            shape = SegmentedButtonDefaults.itemShape(index, FinancePeriod.entries.size),
                            icon = {},
                        ) { FitText(p.label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val canMove = state.selection.period != FinancePeriod.ALL
                    IconButton(onClick = vm::previous, enabled = canMove) {
                        if (canMove) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Período anterior")
                    }
                    Text(f.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::next, enabled = canMove && state.selection.offset < 0) {
                        if (canMove) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo período")
                    }
                }
            }

            item { HeroCard(f) }
            item { WhereMoneyWentCard(c) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Vendas", c.salesCount.toString(), Modifier.weight(1f))
                    Metric("Baterias", c.units.toString(), Modifier.weight(1f))
                    Metric("Ticket médio", Money.format(c.averageTicket), Modifier.weight(1.4f))
                }
            }

            item { SectionTitle(evolutionTitle(f.period)) }
            item { EvolutionCard(f) }

            item { SectionTitle("A conta completa") }
            item { StatementCard(c) }

            item { SectionTitle("Baterias extras (ganhadas)") }
            item { ExtrasCard(f) }

            item { SectionTitle("Como os clientes pagaram") }
            item {
                ChartCard {
                    if (c.byPayment.isEmpty()) Empty("Sem vendas no período.")
                    HBarList(
                        rows = c.byPayment.map {
                            HBarRow(
                                label = it.method.label,
                                value = it.revenue,
                                valueText = Money.format(it.revenue),
                                detail = "${it.salesCount} ${if (it.salesCount == 1) "venda" else "vendas"}" +
                                    if (c.revenue > 0) " • ${it.revenue * 100 / c.revenue}% do que entrou" else "",
                            )
                        },
                        color = ChartColors.slot(0),
                    )
                }
            }

            item { SectionTitle("Despesas por categoria") }
            item {
                ChartCard {
                    if (c.expensesByCategory.isEmpty()) Empty("Nenhuma despesa paga no período.")
                    HBarList(
                        rows = c.expensesByCategory.map { (cat, total) ->
                            HBarRow(
                                label = cat,
                                value = total,
                                valueText = Money.format(total),
                                detail = if (c.expenses > 0) "${total * 100 / c.expenses}% das despesas" else null,
                            )
                        },
                        color = ChartColors.slot(1),
                    )
                }
            }

            item { SectionTitle("Modelos que mais deram lucro") }
            item {
                ChartCard {
                    if (c.topModels.isEmpty()) Empty("Sem vendas no período.")
                    HBarList(
                        rows = c.topModels.map {
                            HBarRow(
                                label = it.model,
                                value = it.profit,
                                valueText = Money.format(it.profit),
                                detail = "${it.quantity} vendida${if (it.quantity == 1) "" else "s"} • faturou ${Money.format(it.revenue)}",
                            )
                        },
                        color = ChartColors.slot(2),
                    )
                }
            }

            item { SectionTitle("O que a loja tem agora") }
            item {
                ChartCard {
                    Text(
                        "Não depende do período escolhido: é a foto de hoje.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InfoRow("Baterias no estoque (pelo custo)", Money.format(f.stockAtCost))
                    InfoRow("Baterias no estoque (se vender no PIX)", Money.format(f.stockAtPix))
                    InfoRow("Sucatas no estoque (tabela)", Money.format(f.scrapStockValue))
                    InfoRow("A receber da carga (não pago)", Money.format(f.toReceive))
                }
            }

            item { SectionTitle("Entenda os termos") }
            item { GlossaryCard() }
        }
    }
}

private fun evolutionTitle(p: FinancePeriod) = when (p) {
    FinancePeriod.MONTH -> "Dia a dia do mês"
    FinancePeriod.YEAR -> "Mês a mês"
    FinancePeriod.ALL -> "Últimos 12 meses"
}

@Composable
private fun HeroCard(f: FinanceReport) {
    val c = f.current
    AppCard(containerColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(20.dp)) {
            val on = MaterialTheme.colorScheme.onPrimary
            val soft = on.copy(alpha = 0.8f)
            Text(if (c.netProfit >= 0) "Sobrou (lucro líquido)" else "Faltou (prejuízo)", style = MaterialTheme.typography.bodyMedium, color = soft)
            FitText(Money.format(c.netProfit), style = MaterialTheme.typography.headlineMedium, color = on, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 12.dp)) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Entrou", style = MaterialTheme.typography.bodyMedium, color = soft)
                    FitText(Money.format(c.revenue), style = MaterialTheme.typography.titleMedium, color = on)
                }
                Column(Modifier.weight(1f)) {
                    Text("Saiu", style = MaterialTheme.typography.bodyMedium, color = soft)
                    FitText(Money.format(c.totalOut), style = MaterialTheme.typography.titleMedium, color = on)
                }
            }
            c.marginPercent?.let {
                Text(
                    "De cada R$ 100 vendidos, sobraram R$ $it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = on,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            f.previous?.let { prev -> comparison(c, prev, f.previousLabel)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = soft, modifier = Modifier.padding(top = 4.dp))
            } }
        }
    }
}

/** "Lucro 12% maior que em agosto" (ou menor). */
private fun comparison(c: FinanceSummary, prev: FinanceSummary, prevLabel: String): String? {
    if (prev.revenue == 0L && prev.expenses == 0L) return null
    val base = kotlin.math.abs(prev.netProfit)
    if (base == 0L) return null
    val pct = (c.netProfit - prev.netProfit) * 100 / base
    return when {
        pct > 0 -> "▲ Lucro $pct% maior que em $prevLabel (${Money.format(prev.netProfit)})"
        pct < 0 -> "▼ Lucro ${-pct}% menor que em $prevLabel (${Money.format(prev.netProfit)})"
        else -> "Lucro igual ao de $prevLabel"
    }
}

@Composable
private fun WhereMoneyWentCard(c: FinanceSummary) {
    ChartCard {
        Text("Para onde foi o dinheiro que entrou", style = MaterialTheme.typography.titleSmall)
        if (c.revenue <= 0) {
            Empty("Sem vendas no período.")
            return@ChartCard
        }
        val parts = listOf(
            Triple("Sobrou (lucro)", c.netProfit.coerceAtLeast(0), ChartColors.slot(0)),
            Triple("Custo das baterias e cascos", c.batteryCost + c.cascoCost, ChartColors.slot(1)),
            Triple("Despesas", c.expenses, ChartColors.slot(2)),
            Triple("Taxas das maquininhas", c.fees, ChartColors.slot(3)),
        )
        val total = parts.sumOf { it.second }.coerceAtLeast(1)
        StackedShareBar(
            parts = parts.map { it.second to it.third },
            modifier = Modifier.padding(vertical = 12.dp),
            description = parts.joinToString { "${it.first}: ${Money.format(it.second)}" },
        )
        parts.forEach { (label, value, color) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendDot(color, label, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "R$ ${value * 100 / total} de cada R$ 100",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (c.netProfit < 0) {
            Text(
                "As saídas passaram do que entrou: prejuízo de ${Money.format(-c.netProfit)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = dangerColor(),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun EvolutionCard(f: FinanceReport) {
    val buckets = f.buckets
    val profitLabel = if (f.period == FinancePeriod.MONTH) "Lucro das vendas" else "Lucro líquido"
    val defaultIndex = buckets.indexOfLast { it.revenue != 0L || it.profit != 0L }.takeIf { it >= 0 }
    var selected by remember(f.period, f.label) { mutableStateOf(defaultIndex) }
    val colorA = ChartColors.slot(0)
    val colorB = ChartColors.slot(2)
    ChartCard {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(colorA, "Faturamento")
            LegendDot(colorB, profitLabel)
        }
        val sel = selected?.let { buckets.getOrNull(it) }
        Column(Modifier.padding(top = 8.dp)) {
            if (sel != null) {
                Text(sel.fullLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Faturou ${Money.format(sel.revenue)} • ${profitLabel.lowercase()} ${Money.format(sel.profit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Nenhum movimento no período.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PairedBarChart(
            groups = buckets.map { BarGroup(it.label, it.revenue, it.profit) },
            colorA = colorA,
            colorB = colorB,
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier.padding(top = 12.dp),
            description = "Gráfico de faturamento e lucro. " + buckets.filter { it.revenue != 0L }
                .joinToString { "${it.fullLabel}: ${Money.format(it.revenue)}" },
        )
        Text(
            "Toque numa barra para ver os valores.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        val best = buckets.maxByOrNull { it.revenue }?.takeIf { it.revenue > 0 }
        if (best != null) {
            Text(
                "Melhor: ${best.fullLabel.replaceFirstChar { it.lowercase() }}, com ${Money.format(best.revenue)} de faturamento.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StatementCard(c: FinanceSummary) {
    ChartCard {
        Text("Vendas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        InfoRow("Entrou com as vendas", Money.format(c.revenue))
        InfoRow("(−) Custo das baterias", minus(c.batteryCost))
        if (c.cascoCost > 0) InfoRow("(−) Casco cobrado (para repor o casco)", minus(c.cascoCost))
        InfoRow("(−) Taxas das maquininhas", minus(c.fees))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        InfoRow("= Lucro das vendas", Money.format(c.grossProfit), valueColor = moneyResultColor(c.grossProfit))
        InfoRow("(−) Despesas pagas", minus(c.expenses))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        InfoRow("= Lucro líquido", Money.format(c.netProfit), bold = true, valueColor = moneyResultColor(c.netProfit))

        Text(
            "Outras entradas e saídas",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
        InfoRow("(+) Carga de baterias (recebido)", Money.format(c.chargesPaid))
        InfoRow("(+) Sucatas vendidas", Money.format(c.scrapSold))
        InfoRow("(−) Sucatas compradas", minus(c.scrapPurchased))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        InfoRow("= Resultado geral", Money.format(c.generalResult), bold = true, valueColor = moneyResultColor(c.generalResult))

        Text(
            buildString {
                append("Descontos dados nas vendas: ${Money.format(c.discounts)} (já estão fora do que entrou).")
                if (c.vouchersPaid > 0) {
                    append(" Vales de casco devolvidos: ${Money.format(c.vouchersPaid)} (não entram na conta: o valor já foi contado como custo na venda).")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun minus(value: Long) = if (value == 0L) Money.format(0) else "-" + Money.format(value)

@Composable
private fun ExtrasCard(f: FinanceReport) {
    val all = f.extrasAllTime
    val p = f.extrasPeriod
    val showPeriod = f.period != FinancePeriod.ALL
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(20.dp)) {
            val on = MaterialTheme.colorScheme.onSecondaryContainer
            Text("Desde o começo, as extras deram de lucro", style = MaterialTheme.typography.bodyMedium, color = on)
            FitText(Money.format(all.profit), style = MaterialTheme.typography.headlineMedium, color = on, fontWeight = FontWeight.Bold)
            Text(
                "${all.sold} extra${if (all.sold == 1) "" else "s"} vendida${if (all.sold == 1) "" else "s"}, " +
                    "somando ${Money.format(all.saleValue)} em vendas.",
                style = MaterialTheme.typography.bodyMedium,
                color = on,
            )
        }
    }
    ChartCard {
        if (showPeriod) {
            Text("Neste período (${f.label.lowercase()})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            InfoRow("Extras ganhadas", p.received.toString())
            InfoRow("Extras vendidas", p.sold.toString())
            InfoRow("Valor de venda", Money.format(p.saleValue))
            InfoRow("Lucro", Money.format(p.profit), valueColor = moneyResultColor(p.profit))
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
        }
        Text("Desde o começo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        InfoRow("Extras ganhadas", all.received.toString())
        InfoRow("Extras vendidas", all.sold.toString())
        InfoRow("Valor de venda", Money.format(all.saleValue))
        InfoRow("Lucro", Money.format(all.profit), bold = true, valueColor = moneyResultColor(all.profit))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        InfoRow("Ainda no estoque", f.extrasInStock.toString())
        InfoRow("Valem (se vender no PIX)", Money.format(f.extrasInStockValue))
        if (all.byModel.isNotEmpty()) {
            Text(
                "Lucro das extras por modelo (desde o começo)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            HBarList(
                rows = all.byModel.map {
                    HBarRow(it.model, it.profit, Money.format(it.profit), "${it.sold} vendida${if (it.sold == 1) "" else "s"} • vendeu ${Money.format(it.saleValue)}")
                },
                color = ChartColors.slot(0),
            )
        }
        Text(
            "Extra tem custo zero, então o lucro é o valor da venda (sem o casco) menos a taxa da maquininha.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun GlossaryCard() {
    ChartCard {
        listOf(
            "Entrou (faturamento)" to "Tudo o que os clientes pagaram nas vendas, já com os descontos.",
            "Custo das baterias" to "Quanto a loja pagou pelas baterias que vendeu.",
            "Casco cobrado" to "Entra no faturamento, mas também como custo, porque serve para repor o casco. Não vira lucro.",
            "Taxas das maquininhas" to "O que a maquininha desconta nas vendas no débito e no crédito.",
            "Lucro das vendas" to "Entrou − custo − casco − taxas.",
            "Despesas" to "Contas fixas e despesas avulsas pagas no período.",
            "Lucro líquido" to "O que sobrou de verdade: lucro das vendas − despesas.",
            "Resultado geral" to "Lucro líquido + carga recebida + sucatas vendidas − sucatas compradas.",
            "Ticket médio" to "Quanto, em média, cada venda rendeu.",
        ).forEachIndexed { i, (term, meaning) ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(term, style = MaterialTheme.typography.titleSmall)
            Text(meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(content: @Composable () -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            FitText(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FitText(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
