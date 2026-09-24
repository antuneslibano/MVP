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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.domain.Scrap
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.viewmodel.ScrapStockRow
import br.com.lojabaterias.ui.viewmodel.ScrapsState
import br.com.lojabaterias.ui.viewmodel.ScrapsViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

private enum class ScrapDialog { NONE, ENTRY, SELL, ADJUST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrapsScreen(onPriceTable: () -> Unit) {
    val vm = appViewModel { ScrapsViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var dialog by remember { mutableStateOf(ScrapDialog.NONE) }
    var dialogAmperage by remember { mutableStateOf<Int?>(null) }

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { dialogAmperage = null; dialog = ScrapDialog.ENTRY },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("+ Entrada") }
                    FilledTonalButton(
                        onClick = { dialogAmperage = null; dialog = ScrapDialog.SELL },
                        enabled = state.totalQuantity > 0,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Vender") }
                    FilledTonalButton(
                        onClick = { dialogAmperage = null; dialog = ScrapDialog.ADJUST },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Ajustar") }
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
                        InfoRow("Recebidas nas vendas", state.month.returnedInSales.toString())
                        InfoRow("Clientes sem sucata", state.month.missingInSales.toString())
                        InfoRow("Cobrado por sucata faltante", Money.format(state.month.charged))
                        InfoRow("Sucatas vendidas", state.month.soldQuantity.toString())
                        InfoRow("Recebido na venda de sucatas", Money.format(state.month.soldAmount), bold = true)
                    }
                }
            }

            item { SectionTitle("Histórico") }
            if (!state.loading && state.movements.isEmpty()) {
                item { EmptyState("Sem movimentações de sucata.") }
            }
            items(state.movements, key = { it.id }) { ScrapMovementRow(it) }
        }
    }

    when (dialog) {
        ScrapDialog.ENTRY -> ScrapEntryDialog(
            amperages = state.prices.keys.sorted(),
            onConfirm = { a, q, note -> vm.addScrap(a, q, note) { dialog = ScrapDialog.NONE } },
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
}

@Composable
private fun SummaryCard(state: ScrapsState) {
    AppCard(containerColor = MaterialTheme.colorScheme.primary) {
        val on = MaterialTheme.colorScheme.onPrimary
        Row(Modifier.padding(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Em estoque", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                Text(
                    Scrap.units(state.totalQuantity),
                    style = MaterialTheme.typography.headlineSmall,
                    color = on,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("Valor estimado", style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.8f))
                Text(
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
private fun ScrapMovementRow(m: ScrapMovement) {
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
private fun ScrapEntryDialog(amperages: List<Int>, onConfirm: (Int, Int, String) -> Unit, onDismiss: () -> Unit) {
    var amperage by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entrada de sucatas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(value = amperage, onValueChange = { amperage = it.take(3) }, label = "Amperagem (Ah)")
                AmperageChips(amperages, amperage, { amperage = it.toString() })
                IntField(value = qty, onValueChange = { qty = it }, label = "Quantidade")
                NoteInput(note) { note = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(amperage.toIntOrNull() ?: 0, qty.toIntOrNull() ?: 0, note) }) {
                Text("Registrar entrada")
            }
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
