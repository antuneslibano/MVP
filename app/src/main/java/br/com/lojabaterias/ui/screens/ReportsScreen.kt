package br.com.lojabaterias.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.DaySales
import br.com.lojabaterias.data.FullReport
import br.com.lojabaterias.data.ModelCount
import br.com.lojabaterias.domain.Labels
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.HeroCard
import br.com.lojabaterias.ui.components.HeroStat
import br.com.lojabaterias.ui.components.Hint
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.PeriodSwitcher
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.Segmented
import br.com.lojabaterias.ui.components.StatTile
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.ReportsViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

/** Relatório do período: vendas, baterias por dia e um resumo curto do resto. */
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

    MainPage { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = Gaps.screen, end = Gaps.screen, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Segmented(
                    options = PeriodType.entries.map { it.label },
                    selected = state.selection.type.ordinal,
                    onSelect = { vm.setType(PeriodType.entries[it]) },
                )
            }
            item {
                PeriodSwitcher(
                    label = state.label,
                    onPrevious = vm::previous,
                    onNext = vm::next,
                    canNext = state.selection.offset < 0,
                )
            }

            item { SalesCard(f) }
            item {
                EqualRow {
                    StatTile("Vendas", f.sales.salesCount.toString(), Modifier.weight(1f))
                    StatTile("Baterias", f.sales.unitsSold.toString(), Modifier.weight(1f))
                    StatTile("Por dia", Labels.oneDecimal(f.averagePerSalesDay), Modifier.weight(1f))
                }
            }
            item {
                FilledTonalButton(
                    onClick = { pdfLauncher.launch(state.pdfFileName) },
                    enabled = !state.loading && !exporting,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(if (exporting) "Gerando..." else "Baixar PDF", modifier = Modifier.padding(start = 8.dp))
                }
            }

            item { SectionHeader("Por dia") }
            if (f.daily.isEmpty()) {
                item { Hint("Sem vendas no período.") }
            }
            items(f.daily, key = { it.date.toString() }) { DayCard(it) }

            if (state.selection.type != PeriodType.DAY && f.daily.size > 1) {
                item { SectionHeader("Por modelo") }
                item { PanelCard { ModelTable(f.modelsTotal) } }
            }

            item { SectionHeader("Resumo geral") }
            item { SummaryCard(f) }
        }
    }
}

@Composable
private fun SalesCard(f: FullReport) {
    val r = f.sales
    HeroCard(label = "Faturamento", value = Money.format(r.revenue)) {
        Row(Modifier.padding(top = 12.dp)) {
            HeroStat("Lucro das vendas", Money.format(r.profit), Modifier.weight(1f))
            HeroStat("Lucro líquido", Money.format(f.netProfit), Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayCard(d: DaySales) {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Labels.dayTitle(d.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(Labels.batteries(d.units), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(Money.format(d.revenue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ModelTable(d.models)
    }
}

@Composable
private fun ModelTable(rows: List<ModelCount>) {
    rows.forEach { m -> InfoRow(m.model, m.count.toString()) }
}

@Composable
private fun SummaryCard(f: FullReport) {
    PanelCard {
        Group("Resultado", first = true)
        InfoRow("Despesas pagas", Money.format(f.expensesTotal))
        InfoRow("Lucro líquido", Money.format(f.netProfit), valueColor = moneyResultColor(f.netProfit))

        Group("Estoque")
        InfoRow("Baterias agora", "${f.stockUnits} · ${Money.format(f.stockValueAtCost)}")
        InfoRow("Entradas no período", f.stockPeriod.entriesQuantity.toString())
        if (f.lowStock.isNotEmpty() || f.outOfStock.isNotEmpty()) {
            InfoRow("Baixo / zerado", "${f.lowStock.size} / ${f.outOfStock.size}", valueColor = dangerColor())
        }
        InfoRow("Sucatas agora", f.scrapStockQuantity.toString())
        InfoRow("Sucatas vendidas", "${f.scrap.soldQuantity} · ${Money.format(f.scrap.soldAmount)}")

        Group("Extras e garantias")
        InfoRow("Trocas", f.warranty.exchangedTotal.toString())
        InfoRow("Extras ganhas / vendidas", "${f.warranty.extrasTotal} / ${f.extrasSold.sold}")
        InfoRow("Lucro das extras", Money.format(f.extrasSold.profit))

        Group("Notas e boletos")
        InfoRow("Boletos pagos", "${f.invoices.paid.size} · ${Money.format(f.invoices.paidTotal)}")
        if (f.invoices.bonusUnits > 0) InfoRow("Bonificações", "${f.invoices.bonusUnits} bat. · ${Money.format(f.invoices.bonusValue)}")
        InfoRow("Devemos hoje", Money.format(f.invoices.debt.open))

        Group("Caixa")
        InfoRow("Entrou", Money.format(f.cash.cashIn))
        InfoRow("Saiu", Money.format(f.cash.cashOut))
        InfoRow("Retiradas", Money.format(f.withdrawalsTotal))
        val left = f.cash.cashBeforeWithdrawals - f.withdrawalsTotal
        InfoRow("Ficou na loja", Money.format(left), bold = true, valueColor = moneyResultColor(left))
    }
}

@Composable
private fun Group(title: String, first: Boolean = false) {
    if (!first) HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = if (first) 0.dp else 10.dp, bottom = 2.dp),
    )
}
