package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.ScrapMovement
import br.com.lojabaterias.data.ScrapMovementType
import br.com.lojabaterias.data.ScrapPeriodSummary
import br.com.lojabaterias.data.ScrapStockRow
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.domain.Scrap
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.viewmodel.ScrapsState
import br.com.lojabaterias.ui.viewmodel.ScrapsViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

private enum class ScrapDialog { NONE, ENTRY, PURCHASE, SELL, ADJUST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrapsScreen(onPriceTable: () -> Unit) {
    val vm = appViewModel { ScrapsViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var dialog by remember { mutableStateOf(ScrapDialog.NONE) }
    var dialogAmperage by remember { mutableStateOf<Int?>(null) }
    var toDelete by remember { mutableStateOf<ScrapMovement?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sucatas") },
                actions = { TextButton(onClick = onPriceTable) { Text("Tabela de valores") } },
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
            item { SummaryCard(state) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Entrada", Modifier.weight(1f)) { dialogAmperage = null; dialog = ScrapDialog.ENTRY }
                        ActionButton("Compra", Modifier.weight(1f)) { dialogAmperage = null; dialog = ScrapDialog.PURCHASE }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Vender", Modifier.weight(1f), enabled = state.totalQuantity > 0) {
                            dialogAmperage = null; dialog = ScrapDialog.SELL
                        }
                        ActionButton("Ajustar", Modifier.weight(1f)) { dialogAmperage = null; dialog = ScrapDialog.ADJUST }
                    }
                }
            }

            item { SectionTitle("Estoque por amperagem") }
            if (!state.loading && state.stock.isEmpty()) {
                item {
                    EmptyState(
                        "Nenhuma sucata em estoque.\nAs sucatas deixadas pelos clientes nas vendas entram aqui automaticamente."
                    )
                }
            }
            items(state.stock, key = { it.amperage }) { row ->
                StockRow(row) { dialogAmperage = row.amperage; dialog = ScrapDialog.SELL }
            }

            item { SectionTitle("Resumo de ${state.monthLabel}") }
            item {
                AppCard {
                    Column(Modifier.padding(16.dp)) {
                        ScrapSummaryRows(state.month)
                    }
                }
            }

            item { SectionTitle("Histórico") }
            if (!state.loading && state.movements.isEmpty()) {
                item { EmptyState("Sem movimentações de sucata.") }
            }
            items(state.movements, key = { it.id }) { m ->
                ScrapMovementRow(m, onDelete = if (ScrapMovementType.isDeletable(m.type)) ({ toDelete = m }) else null)
            }
        }
    }

    when (dialog) {
        ScrapDialog.ENTRY -> ScrapEntryDialog(
            title = "Entrada de sucatas",
            description = "Sucatas recebidas sem custo e fora de uma venda (as das vendas entram sozinhas).",
            confirmLabel = "Registrar entrada",
            withAmount = false,
            prices = state.prices,
            onConfirm = { a, q, _, note -> vm.addScrap(a, q, note) { dialog = ScrapDialog.NONE } },
            onDismiss = { dialog = ScrapDialog.NONE },
        )
        ScrapDialog.PURCHASE -> ScrapEntryDialog(
            title = "Compra de sucatas",
            description = "Sucatas que a loja comprou. Informe o valor pago.",
            confirmLabel = "Registrar compra",
            withAmount = true,
            prices = state.prices,
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
            text = "${ScrapMovementType.label(m.type)} de ${Math.abs(m.quantity)} sucata(s) ${m.amperage}Ah em " +
                "${Periods.formatDateTime(m.dateTime)}. O estoque de sucatas será recalculado.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { vm.deleteMovement(m.id) { toDelete = null } },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun ActionButton(text: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp)) {
        Text(text)
    }
}

/** Linhas do resumo de sucatas (usado na aba Sucatas e nos Relatórios). */
@Composable
fun ScrapSummaryRows(s: ScrapPeriodSummary) {
    InfoRow("Recebidas nas vendas", s.returnedInSales.toString())
    InfoRow("Clientes sem sucata", s.missingInSales.toString())
    InfoRow("Cobrado por sucata faltante", Money.format(s.charged))
    InfoRow("Entradas manuais", s.manualInQuantity.toString())
    InfoRow("Compradas", "${s.purchasedQuantity} • ${Money.format(s.purchasedAmount)}")
    InfoRow("Vendidas", "${s.soldQuantity} • ${Money.format(s.soldAmount)}")
    if (s.adjustmentNet != 0) InfoRow("Ajustes", (if (s.adjustmentNet > 0) "+" else "") + s.adjustmentNet)
    if (s.voucherPaidQuantity > 0) {
        InfoRow("Vales pagos (casco devolvido)", "${s.voucherPaidQuantity} • ${Money.format(s.voucherPaidAmount)}")
    }
    InfoRow(
        "Resultado (vendido − comprado − vales)",
        Money.format(s.netAmount),
        bold = true,
        valueColor = if (s.netAmount < 0) dangerColor() else profitColor(),
    )
}

@Composable
private fun SummaryCard(state: ScrapsState) {
    AppCard(containerColor = MaterialTheme.colorScheme.primary) {
        val on = MaterialTheme.colorScheme.onPrimary
        Row(Modifier.padding(20.dp)) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Em estoque", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                FitText(
                    Scrap.units(state.totalQuantity),
                    style = MaterialTheme.typography.headlineSmall,
                    color = on,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Valor estimado", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                FitText(
                    Money.format(state.totalValue),
                    style = MaterialTheme.typography.headlineSmall,
                    color = on,
                )
            }
        }
    }
}

@Composable
private fun StockRow(row: ScrapStockRow, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${row.amperage}Ah", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    row.unitValue?.let { "${Money.format(it)} cada • total ${Money.format(row.totalValue)}" }
                        ?: "Sem valor na tabela",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${row.quantity} un.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (row.quantity < 0) dangerColor() else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ScrapMovementRow(m: ScrapMovement, onDelete: (() -> Unit)?) {
    AppCard {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${m.amperage}Ah • ${ScrapMovementType.label(m.type)}", style = MaterialTheme.typography.titleSmall)
                Text(
                    Periods.formatDateTime(m.dateTime) + (m.saleId?.let { " • Venda #$it" } ?: "") +
                        (if (m.amount > 0) " • ${Money.format(m.amount)}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                m.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                (if (m.quantity > 0) "+" else "") + m.quantity,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (m.quantity >= 0) profitColor() else dangerColor(),
            )
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
private fun NoteInput(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(120)) },
        label = { Text("Observação (opcional)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ScrapEntryDialog(
    title: String,
    description: String,
    confirmLabel: String,
    withAmount: Boolean,
    prices: Map<Int, Long>,
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
                Text(description, style = MaterialTheme.typography.bodyMedium)
                IntField(value = amperage, onValueChange = { amperage = it.take(3) }, label = "Amperagem (Ah)")
                AmperageChips(prices.keys.sorted(), amperage, { amperage = it.toString() })
                IntField(value = qty, onValueChange = { qty = it }, label = "Quantidade")
                if (withAmount) {
                    val ref = amperage.toIntOrNull()?.let { prices[it] }
                    MoneyField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = "Valor total pago",
                        supportingText = ref?.let { "Tabela: ${Money.format(it)} por sucata" },
                    )
                }
                NoteInput(note) { note = it }
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
    var amountEdited by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val row = stock.firstOrNull { it.amperage.toString() == amperage }
    val suggested = (row?.unitValue ?: 0) * (qty.toIntOrNull() ?: 0)
    val shownAmount = if (amountEdited) amount else suggested

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vender sucatas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Registre as sucatas vendidas (ex.: para o reciclador) e o valor recebido.")
                AmperageChips(stock.map { it.amperage }, amperage, { amperage = it.toString() }) { a ->
                    "${a}Ah (${stock.first { it.amperage == a }.quantity})"
                }
                IntField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = "Quantidade",
                    supportingText = row?.let { "Disponível: ${it.quantity}" } ?: "Escolha a amperagem",
                )
                MoneyField(
                    value = shownAmount,
                    onValueChange = { amount = it; amountEdited = true },
                    label = "Valor recebido",
                )
                NoteInput(note) { note = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(amperage.toIntOrNull() ?: 0, qty.toIntOrNull() ?: 0, shownAmount, note)
            }) { Text("Registrar venda") }
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
    val options = (state.stock.map { it.amperage } + state.prices.keys).distinct().sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar estoque de sucatas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Informe a quantidade real contada de uma amperagem.")
                IntField(value = amperage, onValueChange = { amperage = it.take(3) }, label = "Amperagem (Ah)")
                AmperageChips(options, amperage, { amperage = it.toString() })
                IntField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = "Quantidade real",
                    supportingText = "No sistema: $current",
                )
                NoteInput(note) { note = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toIntOrNull()
                if (q != null) onConfirm(amperage.toIntOrNull() ?: 0, q, note)
            }) { Text("Salvar ajuste") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
