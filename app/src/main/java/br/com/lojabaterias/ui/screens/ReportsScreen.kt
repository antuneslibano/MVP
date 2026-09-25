package br.com.lojabaterias.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.domain.ModelStats
import br.com.lojabaterias.domain.Labels
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.theme.warningColor
import br.com.lojabaterias.ui.viewmodel.ReportsViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen() {
    val vm = appViewModel { ReportsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val r = state.report
    val f = state.full
    ToastEffect(vm.messages)

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.exportPdf(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatórios") },
                actions = {
                    FilledTonalButton(
                        onClick = { pdfLauncher.launch(state.pdfFileName) },
                        enabled = !state.loading && !exporting,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(if (exporting) "Gerando..." else "Baixar PDF") }
                },
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
                    PeriodType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = state.selection.type == type,
                            onClick = { vm.setType(type) },
                            shape = SegmentedButtonDefaults.itemShape(index, PeriodType.entries.size),
                            icon = {},
                        ) { FitText(type.label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::previous) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Período anterior")
                    }
                    Text(
                        state.label,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::next, enabled = state.selection.offset < 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo período")
                    }
                }
            }
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.primary) {
                    Column(Modifier.padding(20.dp)) {
                        val on = MaterialTheme.colorScheme.onPrimary
                        Text("Faturamento", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                        FitText(
                            Money.format(r.revenue),
                            style = MaterialTheme.typography.headlineMedium,
                            color = on,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(Modifier.padding(top = 12.dp)) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Custo", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                                FitText(Money.format(r.cost), style = MaterialTheme.typography.titleMedium, color = on)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Lucro bruto", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                                FitText(Money.format(r.profit), style = MaterialTheme.typography.titleMedium, color = on)
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Vendas", r.salesCount.toString(), Modifier.weight(1f))
                    MetricCard("Baterias", r.unitsSold.toString(), Modifier.weight(1f))
                    MetricCard("Ticket médio", Money.format(r.averageTicket), Modifier.weight(1.4f))
                }
            }

            item {
                AppCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        InfoRow("Valor bruto", Money.format(f.grossTotal))
                        InfoRow("Descontos concedidos", Money.format(f.discountTotal))
                        InfoRow("Taxas das maquininhas", Money.format(r.fees))
                        InfoRow("Cobrado por sucata faltante", Money.format(f.scrap.charged))
                        InfoRow("Vendas canceladas", "${f.canceledSales.size} • ${Money.format(f.canceledAmount)}")
                    }
                }
            }

            item { SectionTitle("Modelos mais vendidos") }
            item {
                RankingCard(r.topByQuantity) { "${it.quantity} un." }
            }
            item { SectionTitle("Maior faturamento") }
            item {
                RankingCard(r.topByRevenue) { Money.format(it.revenue) }
            }
            item { SectionTitle("Maior lucro") }
            item {
                RankingCard(r.topByProfit, valueColor = { moneyResultColor(it.profit) }) { Money.format(it.profit) }
            }
            item { SectionTitle("Vendas por forma de pagamento") }
            item {
                AppCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (r.byPayment.isEmpty()) {
                            EmptyLine()
                        }
                        r.byPayment.forEachIndexed { index, p ->
                            if (index > 0) HorizontalDivider()
                            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.method.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        Labels.salesAndBatteries(p.salesCount, p.units) +
                                            if (r.revenue > 0) " • ${p.revenue * 100 / r.revenue}%" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(Money.format(p.revenue), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Estoque de baterias") }
            item {
                AppCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val p = f.stockPeriod
                        Text("No período", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        InfoRow("Baterias vendidas", p.soldUnits.toString())
                        InfoRow("Entradas", "${p.entriesQuantity} un. • ${Money.format(p.entriesCost)}")
                        if (p.initialQuantity > 0) InfoRow("Estoque inicial cadastrado", "${p.initialQuantity} un.")
                        if (p.adjustmentsIn > 0 || p.adjustmentsOut > 0) {
                            InfoRow("Ajustes", "+${p.adjustmentsIn} / -${p.adjustmentsOut}")
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text("Posição atual", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        InfoRow("Baterias em estoque", "${f.stockUnits} (${f.stockRows.size} modelos)")
                        InfoRow("Valor a preço de custo", Money.format(f.stockValueAtCost))
                        InfoRow("Valor a preço PIX", Money.format(f.stockValueAtPix))
                        if (f.lowStock.isNotEmpty()) {
                            InfoRow("Estoque baixo", f.lowStock.joinToString { it.model }, valueColor = warningColor())
                        }
                        if (f.outOfStock.isNotEmpty()) {
                            InfoRow("Zerados", f.outOfStock.joinToString { it.model }, valueColor = dangerColor())
                        }
                    }
                }
            }

            item { SectionTitle("Sucatas") }
            item {
                AppCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("No período", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        ScrapSummaryRows(f.scrap)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text("Posição atual", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        InfoRow("Sucatas em estoque", f.scrapStockQuantity.toString())
                        InfoRow("Valor estimado", Money.format(f.scrapStockValue))
                    }
                }
            }
            item { SectionTitle("Baterias na carga") }
            item {
                AppCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val c = f.charges
                        Text("No período", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        InfoRow("Recebidas", c.received.toString())
                        InfoRow("Valor cobrado", Money.format(c.charged))
                        InfoRow("Pago", Money.format(c.paid))
                        InfoRow("Não pago", Money.format(c.unpaid), valueColor = if (c.unpaid > 0) warningColor() else Color.Unspecified)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text("Situação atual", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        InfoRow("Na loja", c.openNow.toString())
                        InfoRow("Baterias emprestadas", c.loansOutNow.toString())
                        InfoRow("Total a receber", Money.format(c.unpaidTotalNow))
                    }
                }
            }
            item { SectionTitle("Garantias e extras") }
            item {
                AppCard {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val g = f.warranty
                        Text(
                            "Trocadas em garantia (${g.exchangedTotal})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (g.exchanged.isEmpty()) Text("Nenhuma no período.", style = MaterialTheme.typography.bodyMedium)
                        g.exchanged.forEach { InfoRow(it.model, it.count.toString()) }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(
                            "Extras ganhadas (${g.extrasTotal})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (g.extras.isEmpty()) Text("Nenhuma no período.", style = MaterialTheme.typography.bodyMedium)
                        g.extras.forEach { InfoRow(it.model, it.count.toString()) }
                    }
                }
            }
            item {
                Text(
                    "O PDF traz tudo isso e mais: lista de vendas e cancelamentos, estoque modelo a modelo " +
                        "e todas as movimentações de estoque e de sucatas do período.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            FitText(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FitText(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RankingCard(
    list: List<ModelStats>,
    valueColor: @Composable (ModelStats) -> Color = { Color.Unspecified },
    value: (ModelStats) -> String,
) {
    AppCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (list.isEmpty()) EmptyLine()
            list.forEachIndexed { index, m ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}º",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(m.model, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        value(m),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = valueColor(m),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLine() {
    Text(
        "Sem vendas no período.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 10.dp),
    )
}
