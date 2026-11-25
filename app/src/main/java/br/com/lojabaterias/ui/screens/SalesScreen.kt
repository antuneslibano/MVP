package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.domain.Labels
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.DatePickerModal
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.Hint
import br.com.lojabaterias.ui.components.PageHeader
import br.com.lojabaterias.ui.components.SaleRow
import br.com.lojabaterias.ui.components.SearchField
import br.com.lojabaterias.ui.components.Segmented
import br.com.lojabaterias.ui.components.StatTile
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.SalesTab
import br.com.lojabaterias.ui.viewmodel.SalesViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(onNewSale: () -> Unit, onOpenSale: (Long) -> Unit) {
    val vm = appViewModel { SalesViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val filter = state.filter
    var showDatePicker by remember { mutableStateOf(false) }

    MainPage(
        header = {
            PageHeader(
                title = "Vendas",
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Escolher um dia")
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
            contentPadding = PaddingValues(start = Gaps.screen, end = Gaps.screen, bottom = FabSpace),
            verticalArrangement = Arrangement.spacedBy(Gaps.item),
        ) {
            item {
                if (filter.date != null) {
                    FilterChip(
                        selected = true,
                        onClick = { vm.setDate(null) },
                        label = { Text(filter.date.format(Periods.DATE)) },
                        trailingIcon = { Icon(Icons.Filled.Clear, contentDescription = "Limpar data") },
                    )
                } else {
                    Segmented(
                        options = SalesTab.entries.map { it.label },
                        selected = filter.tab.ordinal,
                        onSelect = { vm.setTab(SalesTab.entries[it]) },
                    )
                }
            }
            item {
                SearchField(value = filter.model, onValueChange = vm::setModel, placeholder = "Buscar modelo")
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = filter.payment == method,
                            onClick = { vm.setPayment(if (filter.payment == method) null else method) },
                            label = { Text(method.label) },
                        )
                    }
                }
            }
            item {
                EqualRow {
                    StatTile(
                        "Faturamento",
                        Money.format(state.revenue),
                        Modifier.weight(1f),
                        caption = "${Labels.sales(state.count)} · ${Labels.batteries(state.units)}",
                    )
                    StatTile("Lucro", Money.format(state.profit), Modifier.weight(1f), valueColor = moneyResultColor(state.profit))
                }
            }
            if (state.modelCounts.isNotEmpty()) {
                item {
                    Column {
                        Hint("Por modelo", Modifier.padding(bottom = 6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.modelCounts.forEach { (model, qty) ->
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                                    Text(
                                        "$qty× $model",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (!state.loading && state.sales.isEmpty()) {
                item { EmptyState("Nenhuma venda encontrada.") }
            }
            items(state.sales, key = { it.sale.id }) { sale ->
                SaleRow(sale, onClick = { onOpenSale(sale.sale.id) })
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initial = filter.date ?: LocalDate.now(),
            onPick = { vm.setDate(it) },
            onDismiss = { showDatePicker = false },
        )
    }
}
