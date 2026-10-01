package br.com.lojabaterias.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.DaySales
import br.com.lojabaterias.data.FullReport
import br.com.lojabaterias.data.ModelCount
import br.com.lojabaterias.domain.Labels
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.ReportsViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

/** Relatório simples: vendas do período, baterias por dia e um resumo curto do resto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen() {
    val vm = appViewModel { ReportsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
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
                    Text(state.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = vm::next, enabled = state.selection.offset < 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo período")
                    }
                }
            }

            item { SalesCard(f) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Baterias", f.sales.unitsSold.toString(), null, Modifier.weight(1f))
                    Metric("Média por dia", Labels.oneDecimal(f.averagePerSalesDay), daysLabel(f.daily.size), Modifier.weight(1.2f))
                    Metric("Vendas", f.sales.salesCount.toString(), null, Modifier.weight(1f))
                }
            }

            item { SectionTitle("Baterias vendidas por dia") }
            if (f.daily.isEmpty()) {
                item { Text("Sem vendas no período.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(f.daily, key = { it.date.toString() }) { DayCard(it) }

            if (state.selection.type != PeriodType.DAY && f.daily.size > 1) {
                item { SectionTitle("Total por modelo no período") }
                item {
                    AppCard { Column(Modifier.padding(16.dp)) { ModelTable(f.modelsTotal) } }
                }
            }

            item { SectionTitle("Resumo geral") }
            item { SummaryCard(f) }
            item {
                Text(
                    "O PDF traz este mesmo resumo, pronto para imprimir ou enviar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SalesCard(f: FullReport) {
    val r = f.sales
    AppCard(containerColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(20.dp)) {
            val on = MaterialTheme.colorScheme.onPrimary
            Text("Faturamento", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
            FitText(Money.format(r.revenue), style = MaterialTheme.typography.headlineMedium, color = on, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 12.dp)) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Lucro das vendas", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                    FitText(Money.format(r.profit), style = MaterialTheme.typography.titleMedium, color = on)
                }
                Column(Modifier.weight(1f)) {
                    Text("Lucro líquido", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                    FitText(Money.format(f.netProfit), style = MaterialTheme.typography.titleMedium, color = on)
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, detail: String?, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            FitText(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FitText(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (detail != null) FitText(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun daysLabel(n: Int) = if (n == 1) "em 1 dia com venda" else "em $n dias com venda"

@Composable
private fun DayCard(d: DaySales) {
    AppCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Labels.dayTitle(d.date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${d.units} bateria${if (d.units == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(Money.format(d.revenue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.padding(top = 8.dp)) { ModelTable(d.models) }
        }
    }
}

/** Tabela "Modelo | Quantidade", com linhas alternadas. */
@Composable
private fun ModelTable(rows: List<ModelCount>) {
    val stripe = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text("Modelo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text("Qtd", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    rows.forEachIndexed { i, m ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .let { if (i % 2 == 0) it.background(stripe) else it }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(m.model, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(m.count.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryCard(f: FullReport) {
    AppCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Group("Resultado")
            InfoRow("Despesas pagas", Money.format(f.expensesTotal))
            InfoRow("Lucro líquido", Money.format(f.netProfit), valueColor = moneyResultColor(f.netProfit))

            Group("Estoque de baterias")
            InfoRow("Em estoque (agora)", "${f.stockUnits} • ${Money.format(f.stockValueAtCost)}")
            InfoRow("Entradas no período", "${f.stockPeriod.entriesQuantity} un.")
            if (f.lowStock.isNotEmpty() || f.outOfStock.isNotEmpty()) {
                InfoRow("Baixo / zerado", "${f.lowStock.size} / ${f.outOfStock.size} modelos", valueColor = dangerColor())
            }

            Group("Sucatas")
            InfoRow("Em estoque (agora)", f.scrapStockQuantity.toString())
            InfoRow("Vendidas no período", "${f.scrap.soldQuantity} • ${Money.format(f.scrap.soldAmount)}")

            Group("Carga, garantias, extras e Vitor")
            InfoRow("Baterias na carga recebidas", "${f.charges.received} • a receber ${Money.format(f.charges.unpaidTotalNow)}")
            InfoRow("Trocas em garantia", f.warranty.exchangedTotal.toString())
            InfoRow("Extras ganhas / vendidas", "${f.warranty.extrasTotal} / ${f.extrasSold.sold} • lucro ${Money.format(f.extrasSold.profit)}")
            InfoRow("Do Vitor registradas", "${f.warranty.vitorTotal} • pago ${Money.format(f.warranty.vitorPaid)}")

            Group("Notas e boletos")
            InfoRow("Boletos pagos no período", "${f.invoices.paid.size} • ${Money.format(f.invoices.paidTotal)}")
            InfoRow("Devemos aos fornecedores (hoje)", Money.format(f.invoices.debt.open))

            Group("Caixa")
            InfoRow("Entrou / saiu", "${Money.format(f.cash.cashIn)} / ${Money.format(f.cash.cashOut)}")
            InfoRow("Retiradas dos sócios", Money.format(f.withdrawalsTotal))
            val left = f.cash.cashBeforeWithdrawals - f.withdrawalsTotal
            InfoRow("Ficou na loja", Money.format(left), bold = true, valueColor = moneyResultColor(left))
        }
    }
}

@Composable
private fun Group(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}
