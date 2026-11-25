package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.PeriodSummary
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.HeroCard
import br.com.lojabaterias.ui.components.HeroStat
import br.com.lojabaterias.ui.components.ListRow
import br.com.lojabaterias.ui.components.PageHeader
import br.com.lojabaterias.ui.components.SaleRow
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.StatTile
import br.com.lojabaterias.ui.components.StatusPill
import br.com.lojabaterias.ui.components.SyncIndicator
import br.com.lojabaterias.ui.components.UpdateBanner
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.warningColor
import br.com.lojabaterias.ui.viewmodel.HomeViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("pt", "BR"))

@Composable
fun HomeScreen(
    onNewSale: () -> Unit,
    onOpenSale: (Long) -> Unit,
    onSeeAllSales: () -> Unit,
    onSettings: () -> Unit,
    onInvoices: () -> Unit,
    onStock: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    MainPage(
        header = {
            PageHeader(
                title = "Art das Baterias",
                subtitle = {
                    Text(
                        state.date.format(DAY_TITLE).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SyncIndicator(Modifier.padding(top = 2.dp))
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes e backup")
                    }
                },
            )
        },
        fabLabel = "Nova venda",
        onFab = onNewSale,
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = Gaps.screen, end = Gaps.screen, top = 4.dp, bottom = FabSpace),
            verticalArrangement = Arrangement.spacedBy(Gaps.item),
        ) {
            item { UpdateBanner() }
            item { TodayCard(state.today) }
            item {
                EqualRow {
                    PeriodTile("Esta semana", state.week, Modifier.weight(1f))
                    PeriodTile("Este mês", state.month, Modifier.weight(1f))
                }
            }

            val debt = state.debt
            if (debt.overdueCount > 0) {
                item {
                    ListRow(
                        title = "${debt.overdueCount} boleto${plural(debt.overdueCount)} vencido${plural(debt.overdueCount)}",
                        subtitle = Money.format(debt.overdue),
                        onClick = onInvoices,
                    ) { StatusPill("vencido", dangerColor()) }
                }
            }
            if (debt.dueSoonCount > 0) {
                item {
                    ListRow(
                        title = "${debt.dueSoonCount} boleto${plural(debt.dueSoonCount)} nos próximos 7 dias",
                        subtitle = Money.format(debt.dueSoon),
                        onClick = onInvoices,
                    ) { StatusPill("a vencer", warningColor()) }
                }
            }

            item { SectionHeader("Vendas recentes", action = "Ver todas", onAction = onSeeAllSales) }
            if (state.recent.isEmpty()) {
                item { EmptyState("Nenhuma venda ainda.") }
            } else {
                items(state.recent, key = { it.sale.id }) { sale ->
                    SaleRow(sale, onClick = { onOpenSale(sale.sale.id) })
                }
            }
        }
    }
}

private fun plural(n: Int) = if (n == 1) "" else "s"

@Composable
private fun TodayCard(summary: PeriodSummary) {
    HeroCard(label = "Vendido hoje", value = Money.format(summary.revenue)) {
        Spacer(Modifier.height(12.dp))
        Row {
            HeroStat("Lucro", Money.format(summary.profit), Modifier.weight(1.4f))
            HeroStat("Vendas", summary.count.toString(), Modifier.weight(0.8f))
            HeroStat("Baterias", summary.units.toString(), Modifier.weight(0.8f))
        }
    }
}

@Composable
private fun PeriodTile(title: String, summary: PeriodSummary, modifier: Modifier = Modifier) {
    Column(modifier) {
        StatTile(
            label = title,
            value = Money.format(summary.revenue),
            caption = "lucro ${Money.format(summary.profit)} · ${summary.units} bat.",
        )
    }
}
