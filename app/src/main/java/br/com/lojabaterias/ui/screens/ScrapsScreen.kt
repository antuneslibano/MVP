package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.ScrapMovement
import br.com.lojabaterias.data.ScrapMovementType
import br.com.lojabaterias.data.ScrapPeriodSummary
import br.com.lojabaterias.data.ScrapStockRow
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.domain.Scrap
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.HeroCard
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.ListRow
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.TextInput
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.viewmodel.ScrapsState
import br.com.lojabaterias.ui.viewmodel.ScrapsViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

private enum class ScrapDialog { NONE, ENTRY, PURCHASE, SELL, ADJUST }

@Composable
fun ScrapsScreen() {
    val vm = appViewModel { ScrapsViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var dialog by remember { mutableStateOf(ScrapDialog.NONE) }
    var dialogAmperage by remember { mutableStateOf<Int?>(null) }
    var toDelete by remember { mutableStateOf<ScrapMovement?>(null) }

    MainPage { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = Gaps.screen, end = Gaps.screen, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { HeroCard(label = "Sucatas em estoque", value = Scrap.units(state.totalQuantity)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqualRow {
                        ActionButton("Entrada", Modifier.weight(1f)) { dialogAmperage = null; dialog = ScrapDialog.ENTRY }
                        ActionButton("Compra", Modifier.weight(1f)) { dialogAmperage = null; dialog = ScrapDialog.PURCHASE }
                    }
                    EqualRow {
                        ActionButton("Venda", Modifier.weight(1f), enabled = state.totalQuantity > 0) {
                            dialogAmperage = null; dialog = ScrapDialog.SELL
                        }
                        ActionButton("Ajuste", Modifier.weight(1f)) { dialogAmperage = null; dialog = ScrapDialog.ADJUST }
                    }
                }
            }

            item { SectionHeader("Por amperagem") }
            if (!state.loading && state.stock.isEmpty()) {
                item { EmptyState("Nenhuma sucata em estoque.") }
            }
            items(state.stock, key = { it.amperage }) { row ->
                ListRow(
                    title = "${row.amperage}Ah",
                    onClick = { dialogAmperage = row.amperage; dialog = ScrapDialog.SELL },
                ) {
                    Text(
                        "${row.quantity} un.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (row.quantity < 0) dangerColor() else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            item { SectionHeader(state.monthLabel.replaceFirstChar { it.uppercase() }) }
            item { PanelCard { ScrapSummaryRows(state.month) } }

            if (state.movements.isNotEmpty()) item { SectionHeader("Histórico") }
            items(state.movements, key = { it.id }) { m ->
                ScrapMovementRow(m, onDelete = if (ScrapMovementType.isDeletable(m.type)) ({ toDelete = m }) else null)
            }
        }
    }

    val amperages = state.stock.map { it.amperage }
    when (dialog) {
        ScrapDialog.ENTRY -> ScrapEntryDialog(
            title = "Entrada de sucatas",
            confirmLabel = "Registrar",
            withAmount = false,
            options = amperages,
            onConfirm = { a, q, _, note -> vm.addScrap(a, q, note) { dialog = ScrapDialog.NONE } },
            onDismiss = { dialog = ScrapDialog.NONE },
        )
        ScrapDialog.PURCHASE -> ScrapEntryDialog(
            title = "Compra de sucatas",
            confirmLabel = "Registrar",
            withAmount = true,
            options = amperages,
            onConfirm = { a, q, amount, note -> vm.buyScrap(a, q, amount, note) { dialog = ScrapDialog.NONE } },
            onDismiss = { dialog = ScrapDialog.NONE },
        )
        ScrapDialog.SELL -> ScrapSellDialog(
            stock = state.stock,
            initialAmperage = dialogAmperage ?: state.stock.firstOrNull()?.amperage,
            onConfirm = { a, q, amount, note -> vm.sellScrap(a, q, amount, note) { dialog = ScrapDialog.NONE } },
            onDismiss = { dialog = ScrapDialog.NONE },
        )
        ScrapDialog.ADJUST -> ScrapAdjustDialog(
            state = state,
            onConfirm = { a, q, note -> vm.adjustScrap(a, q, note) { dialog = ScrapDialog.NONE } },
            onDismiss = { dialog = ScrapDialog.NONE },
        )
        ScrapDialog.NONE -> Unit
    }

    toDelete?.let { m ->
        ConfirmDialog(
            title = "Excluir registro?",
            text = "${ScrapMovementType.label(m.type)} · ${Math.abs(m.quantity)} × ${m.amperage}Ah · ${Periods.formatDateTime(m.dateTime)}",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { vm.deleteMovement(m.id) { toDelete = null } },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun ActionButton(text: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(text) }
}

@Composable
private fun ScrapSummaryRows(s: ScrapPeriodSummary) {
    InfoRow("Recebidas nas vendas", s.returnedInSales.toString())
    InfoRow("Clientes sem sucata", s.missingInSales.toString())
    if (s.charged > 0) InfoRow("Casco cobrado", Money.format(s.charged))
    if (s.manualInQuantity != 0) InfoRow("Entradas", s.manualInQuantity.toString())
    if (s.purchasedQuantity != 0) InfoRow("Compradas", "${s.purchasedQuantity} · ${Money.format(s.purchasedAmount)}")
    if (s.soldQuantity != 0) InfoRow("Vendidas", "${s.soldQuantity} · ${Money.format(s.soldAmount)}")
    if (s.adjustmentNet != 0) InfoRow("Ajustes", (if (s.adjustmentNet > 0) "+" else "") + s.adjustmentNet)
    if (s.voucherPaidAmount > 0) InfoRow("Vales pagos", Money.format(s.voucherPaidAmount))
    InfoRow("Resultado", Money.format(s.netAmount), bold = true, valueColor = moneyResultColor(s.netAmount))
}

@Composable
private fun ScrapMovementRow(m: ScrapMovement, onDelete: (() -> Unit)?) {
    ListRow(
        title = "${m.amperage}Ah · ${ScrapMovementType.label(m.type)}",
        subtitle = listOfNotNull(
            Periods.formatDateTime(m.dateTime),
            m.amount.takeIf { it > 0 }?.let { Money.format(it) },
            m.note,
        ).joinToString(" · "),
    ) {
        Text(
            (if (m.quantity > 0) "+" else "") + m.quantity,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (m.quantity >= 0) profitColor() else dangerColor(),
            modifier = Modifier.padding(end = if (onDelete == null) 8.dp else 0.dp),
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AmperageChips(options: List<Int>, selected: String, onSelect: (Int) -> Unit, label: (Int) -> String = { "${it}Ah" }) {
    if (options.isEmpty()) return
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { a ->
            FilterChip(selected = selected == a.toString(), onClick = { onSelect(a) }, label = { Text(label(a)) })
        }
    }
}

@Composable
private fun ScrapEntryDialog(
    title: String,
    confirmLabel: String,
    withAmount: Boolean,
    options: List<Int>,
    onConfirm: (Int, Int, Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amperage by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var amount by remember { mutableLongStateOf(0L) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                EqualRow {
                    IntField(value = amperage, onValueChange = { amperage = it.take(3) }, label = "Ah", modifier = Modifier.weight(1f))
                    IntField(value = qty, onValueChange = { qty = it }, label = "Quantidade", modifier = Modifier.weight(1f))
                }
                AmperageChips(options.sorted(), amperage, { amperage = it.toString() })
                if (withAmount) MoneyField(value = amount, onValueChange = { amount = it }, label = "Valor pago")
                TextInput(note, { note = it }, "Observação (opcional)")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(amperage.toIntOrNull() ?: 0, qty.toIntOrNull() ?: 0, amount, note)
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ScrapSellDialog(
    stock: List<ScrapStockRow>,
    initialAmperage: Int?,
    onConfirm: (Int, Int, Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amperage by remember { mutableStateOf(initialAmperage?.toString() ?: "") }
    var qty by remember { mutableStateOf("1") }
    var amount by remember { mutableLongStateOf(0L) }
    var note by remember { mutableStateOf("") }
    val row = stock.firstOrNull { it.amperage.toString() == amperage }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vender sucatas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                AmperageChips(stock.map { it.amperage }, amperage, { amperage = it.toString() }) { a ->
                    "${a}Ah (${stock.first { it.amperage == a }.quantity})"
                }
                IntField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = "Quantidade",
                    supportingText = row?.let { "Disponível: ${it.quantity}" },
                )
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Valor recebido")
                TextInput(note, { note = it }, "Observação (opcional)")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(amperage.toIntOrNull() ?: 0, qty.toIntOrNull() ?: 0, amount, note)
            }) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ScrapAdjustDialog(state: ScrapsState, onConfirm: (Int, Int, String) -> Unit, onDismiss: () -> Unit) {
    var amperage by remember { mutableStateOf(state.stock.firstOrNull()?.amperage?.toString() ?: "") }
    val current = state.stock.firstOrNull { it.amperage.toString() == amperage }?.quantity ?: 0
    var qty by remember(amperage) { mutableStateOf(current.toString()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar sucatas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                EqualRow {
                    IntField(value = amperage, onValueChange = { amperage = it.take(3) }, label = "Ah", modifier = Modifier.weight(1f))
                    IntField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = "Contadas",
                        modifier = Modifier.weight(1f),
                    )
                }
                AmperageChips(state.stock.map { it.amperage }.sorted(), amperage, { amperage = it.toString() })
                Text(
                    "No sistema: $current",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextInput(note, { note = it }, "Observação (opcional)")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toIntOrNull()
                if (q != null) onConfirm(amperage.toIntOrNull() ?: 0, q, note)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
