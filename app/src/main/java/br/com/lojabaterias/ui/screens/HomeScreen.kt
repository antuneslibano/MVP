package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.PeriodSummary
import br.com.lojabaterias.domain.Labels
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.SaleRow
import br.com.lojabaterias.ui.components.SyncIndicator
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.HomeViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewSale: () -> Unit,
    onOpenSale: (Long) -> Unit,
    onSeeAllSales: () -> Unit,
    onBackup: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Art das Baterias")
                        Text(
                            state.date.format(Periods.DATE),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SyncIndicator()
                    }
                },
                actions = {
                    IconButton(onClick = onBackup) {
                        Icon(Icons.Filled.Settings, contentDescription = "Backup e sincronização")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewSale,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova Venda", style = MaterialTheme.typography.titleMedium) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { TodayCard(state.today) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PeriodCard("Esta semana", state.week, Modifier.weight(1f))
                    PeriodCard("Este mês", state.month, Modifier.weight(1f))
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Vendas recentes",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSeeAllSales) { Text("Ver todas") }
                }
            }
            if (state.recent.isEmpty()) {
                item { EmptyState("Nenhuma venda registrada ainda.\nToque em \"Nova Venda\" para começar.") }
            } else {
                items(state.recent, key = { it.sale.id }) { sale ->
                    SaleRow(sale, onClick = { onOpenSale(sale.sale.id) })
                }
            }
        }
    }
}

@Composable
private fun TodayCard(summary: PeriodSummary) {
    AppCard(containerColor = MaterialTheme.colorScheme.primary) {
        Column(Modifier.padding(20.dp)) {
            val onPrimary = MaterialTheme.colorScheme.onPrimary
            Text("Hoje", style = MaterialTheme.typography.titleMedium, color = onPrimary.copy(alpha = 0.85f))
            Spacer(Modifier.height(4.dp))
            Text("Faturamento", style = MaterialTheme.typography.bodyMedium, color = onPrimary.copy(alpha = 0.8f))
            Text(
                Money.format(summary.revenue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = onPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1.3f)) {
                    Text("Lucro", style = MaterialTheme.typography.bodyMedium, color = onPrimary.copy(alpha = 0.8f))
                    Text(
                        Money.format(summary.profit),
                        style = MaterialTheme.typography.titleLarge,
                        color = onPrimary,
                    )
                }
                Column(Modifier.weight(0.7f)) {
                    Text("Vendas", style = MaterialTheme.typography.bodyMedium, color = onPrimary.copy(alpha = 0.8f))
                    Text(summary.count.toString(), style = MaterialTheme.typography.titleLarge, color = onPrimary)
                }
                Column(Modifier.weight(0.8f)) {
                    Text("Baterias", style = MaterialTheme.typography.bodyMedium, color = onPrimary.copy(alpha = 0.8f))
                    Text(summary.units.toString(), style = MaterialTheme.typography.titleLarge, color = onPrimary)
                }
            }
        }
    }
}

@Composable
private fun PeriodCard(title: String, summary: PeriodSummary, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("Faturamento", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(summary.revenue), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Lucro", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Money.format(summary.profit),
                style = MaterialTheme.typography.titleMedium,
                color = if (summary.profit == 0L) Color.Unspecified else moneyResultColor(summary.profit),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                Labels.sales(summary.count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                Labels.batteries(summary.units),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
