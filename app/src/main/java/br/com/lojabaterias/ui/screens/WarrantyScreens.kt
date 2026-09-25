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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.WarrantyClaim
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.ProductPickerDialog
import br.com.lojabaterias.ui.components.QuantityStepper
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.viewmodel.WarrantiesViewModel
import br.com.lojabaterias.ui.viewmodel.WarrantyTab
import br.com.lojabaterias.ui.viewmodel.appViewModel

/** Garantias (baterias trocadas) e Extras (baterias ganhadas), contadas por modelo e mês. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarrantiesScreen() {
    val vm = appViewModel { WarrantiesViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    val extras = state.tab == WarrantyTab.EXTRAS
    var picking by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Product?>(null) }
    var toDelete by remember { mutableStateOf<WarrantyClaim?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Garantias e extras") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picking = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (extras) "Registrar extra" else "Registrar troca") },
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
                    WarrantyTab.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = state.tab == t,
                            onClick = { vm.setTab(t) },
                            shape = SegmentedButtonDefaults.itemShape(i, WarrantyTab.entries.size),
                            icon = {},
                        ) { FitText(t.label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            item {
                Text(
                    if (extras) {
                        "Baterias que a loja ganhou (o cliente comprou uma nova e deixou a da garantia). " +
                            "Entram no estoque com custo zero: na venda, o lucro delas é de 100%."
                    } else {
                        "Baterias trocadas em garantia. Só conta o modelo e a quantidade; o estoque não muda."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::previousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior")
                    }
                    Text(
                        state.monthLabel,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = vm::nextMonth, enabled = state.monthOffset < 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo mês")
                    }
                }
            }
            item {
                AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.padding(16.dp)) {
                        FitText(
                            "${state.total} " + if (extras) "extra(s) no mês" else "trocada(s) no mês",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (state.byModel.isEmpty()) {
                            Text(
                                "Nenhum registro neste mês.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        state.byModel.forEach { InfoRow(it.model, "${it.count}", bold = true) }
                        if (extras) {
                            Text(
                                "Extras ainda no estoque (todos os meses): ${state.extrasInStock}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            if (state.list.isNotEmpty()) item { SectionTitle("Registros do mês") }
            if (!state.loading && state.list.isEmpty()) {
                item {
                    EmptyState(
                        if (extras) "Toque em \"Registrar extra\" para anotar uma bateria ganhada."
                        else "Toque em \"Registrar troca\" para anotar uma troca."
                    )
                }
            }
            items(state.list, key = { it.id }) { w -> WarrantyRow(w, onDelete = { toDelete = w }) }
        }
    }

    if (picking) {
        ProductPickerDialog(
            title = if (extras) "Qual bateria a loja ganhou?" else "Qual bateria foi trocada?",
            products = products,
            onPick = { chosen = it; picking = false },
            onDismiss = { picking = false },
        )
    }
    chosen?.let { p ->
        QuantityDialog(
            product = p,
            extra = extras,
            onConfirm = { q -> vm.register(p, q); chosen = null },
            onDismiss = { chosen = null },
        )
    }
    toDelete?.let { w ->
        ConfirmDialog(
            title = if (w.isExtra) "Excluir extra?" else "Excluir troca?",
            text = "1× ${w.returnedModel} de ${Periods.formatDate(w.createdAt)}." +
                if (w.isExtra) " A bateria sai do estoque." else "",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { vm.delete(w); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun WarrantyRow(w: WarrantyClaim, onDelete: () -> Unit) {
    AppCard {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(w.returnedModel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    Periods.formatDateTime(w.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (w.isExtra) {
                    Text(
                        if (w.saleId != null) "Vendida (lucro 100%)" else "No estoque",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (w.saleId != null) profitColor() else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun QuantityDialog(product: Product, extra: Boolean, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var quantity by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.model) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (extra) "Quantas a loja ganhou?" else "Quantas foram trocadas?")
                QuantityStepper(value = quantity, onValueChange = { quantity = it }, min = 1, max = 50)
                Text(
                    if (extra) "Entra no estoque com custo zero." else "O estoque não muda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(quantity) }) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
