package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.DatePickerModal
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.SaleRow
import br.com.lojabaterias.ui.components.SearchField
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.viewmodel.SalesTab
import br.com.lojabaterias.ui.viewmodel.SalesViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SalesScreen(onNewSale: () -> Unit, onOpenSale: (Long) -> Unit) {
    val vm = appViewModel { SalesViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val filter = state.filter
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vendas") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewSale,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova Venda") },
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SalesTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = filter.date == null && filter.tab == tab,
                            onClick = { vm.setTab(tab) },
                            shape = SegmentedButtonDefaults.itemShape(index, SalesTab.entries.size),
                        ) { Text(tab.label, maxLines = 1) }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filter.date != null,
                        onClick = { if (filter.date != null) vm.setDate(null) else showDatePicker = true },
                        label = { Text(filter.date?.format(Periods.DATE) ?: "Data") },
                        leadingIcon = { Icon(Icons.Filled.DateRange, null, Modifier.padding(0.dp)) },
                        trailingIcon = if (filter.date != null) {
                            { Icon(Icons.Filled.Clear, contentDescription = "Limpar data", modifier = Modifier.padding(0.dp)) }
                        } else {
                            null
                        },
                    )
                    FilterChip(
                        selected = filter.payment == null,
                        onClick = { vm.setPayment(null) },
                        label = { Text("Todas formas") },
                    )
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = filter.payment == method,
                            onClick = { vm.setPayment(if (filter.payment == method) null else method) },
                            label = { Text(method.label) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
            item {
                SearchField(
                    value = filter.model,
                    onValueChange = vm::setModel,
                    placeholder = "Modelo da bateria...",
                )
            }
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(16.dp)) {
                        SummaryCell("Vendas", state.count.toString(), Modifier.weight(0.75f))
                        SummaryCell("Baterias", state.units.toString(), Modifier.weight(0.8f))
                        SummaryCell("Faturamento", Money.format(state.revenue), Modifier.weight(1.25f))
                        SummaryCell(
                            "Lucro",
                            Money.format(state.profit),
                            Modifier.weight(1.2f),
                            color = moneyResultColor(state.profit),
                        )
                    }
                }
            }
            if (state.modelCounts.isNotEmpty()) {
                item {
                    AppCard {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "Baterias vendidas por modelo",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                state.modelCounts.forEach { (model, qty) ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Text(
                                            "$qty× $model",
                                            style = MaterialTheme.typography.titleSmall,
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
            }
            if (!state.loading && state.sales.isEmpty()) {
                item { EmptyState("Nenhuma venda encontrada para este filtro.") }
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

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}
