package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.HeroCard
import br.com.lojabaterias.ui.components.HeroStat
import br.com.lojabaterias.ui.components.Hint
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.ListRow
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.PeriodSwitcher
import br.com.lojabaterias.ui.components.ProductPickerDialog
import br.com.lojabaterias.ui.components.QuantityStepper
import br.com.lojabaterias.ui.components.Segmented
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.viewmodel.WarrantiesViewModel
import br.com.lojabaterias.ui.viewmodel.WarrantyGroup
import br.com.lojabaterias.ui.viewmodel.WarrantyTab
import br.com.lojabaterias.ui.viewmodel.appViewModel

/** Extras (baterias ganhadas) e Garantias (baterias trocadas), por modelo e mês. */
@Composable
fun WarrantiesScreen() {
    val vm = appViewModel { WarrantiesViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    val tab = state.tab
    var picking by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Product?>(null) }
    var toDelete by remember { mutableStateOf<WarrantyGroup?>(null) }

    MainPage(
        fabLabel = when (tab) {
            WarrantyTab.EXTRAS -> "Nova extra"
            WarrantyTab.EXCHANGES -> "Nova troca"
        },
        onFab = { picking = true },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = Gaps.screen, end = Gaps.screen, top = 12.dp, bottom = FabSpace),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Segmented(
                    options = WarrantyTab.entries.map { it.label },
                    selected = tab.ordinal,
                    onSelect = { vm.setTab(WarrantyTab.entries[it]) },
                )
            }
            item {
                PeriodSwitcher(
                    label = state.monthLabel,
                    onPrevious = vm::previousMonth,
                    onNext = vm::nextMonth,
                    canNext = state.monthOffset < 0,
                )
            }
            item {
                HeroCard(
                    label = when (tab) {
                        WarrantyTab.EXTRAS -> "Extras ganhadas no mês"
                        WarrantyTab.EXCHANGES -> "Trocas no mês"
                    },
                    value = state.total.toString(),
                ) {
                    if (tab == WarrantyTab.EXTRAS) {
                        Row(Modifier.padding(top = 8.dp)) { HeroStat("No estoque (todas)", state.extrasInStock.toString()) }
                    }
                }
            }
            if (state.byModel.isNotEmpty()) {
                item {
                    PanelCard { state.byModel.forEach { InfoRow(it.model, "${it.count}", bold = true) } }
                }
            }
            if (!state.loading && state.list.isEmpty()) {
                item { EmptyState("Nada registrado neste mês.") }
            }
            items(state.groups, key = { it.key }) { g -> WarrantyRow(g, onDelete = { toDelete = g }) }
        }
    }

    if (picking) {
        ProductPickerDialog(
            title = when (tab) {
                WarrantyTab.EXTRAS -> "Qual bateria a loja ganhou?"
                WarrantyTab.EXCHANGES -> "Qual bateria foi trocada?"
            },
            products = products,
            onPick = { chosen = it; picking = false },
            onDismiss = { picking = false },
        )
    }
    chosen?.let { p ->
        QuantityDialog(
            product = p,
            onConfirm = { q -> vm.register(p, q); chosen = null },
            onDismiss = { chosen = null },
        )
    }
    toDelete?.let { g ->
        ConfirmDialog(
            title = when (g.tab) {
                WarrantyTab.EXTRAS -> "Excluir extra?"
                WarrantyTab.EXCHANGES -> "Excluir troca?"
            },
            text = "${g.count}× ${g.model} de ${Periods.formatDate(g.createdAt)}." +
                if (g.tab == WarrantyTab.EXTRAS) " Sai do estoque." else "",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { vm.delete(g); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun WarrantyRow(g: WarrantyGroup, onDelete: () -> Unit) {
    val detail = when (g.tab) {
        WarrantyTab.EXTRAS -> {
            val inStock = g.count - g.soldCount
            listOfNotNull(
                g.soldCount.takeIf { it > 0 }?.let { "$it vendida(s)" },
                inStock.takeIf { it > 0 }?.let { "$it no estoque" },
            ).joinToString(" · ")
        }
        WarrantyTab.EXCHANGES -> null
    }
    ListRow(
        title = "${g.count}× ${g.model}",
        subtitle = listOfNotNull(Periods.formatDate(g.createdAt), detail?.takeIf { it.isNotBlank() }).joinToString(" · "),
    ) {
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuantityDialog(product: Product, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var quantity by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.model) },
        text = { QuantityStepper(value = quantity, onValueChange = { quantity = it }, min = 1, max = 50) },
        confirmButton = { TextButton(onClick = { onConfirm(quantity) }) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
