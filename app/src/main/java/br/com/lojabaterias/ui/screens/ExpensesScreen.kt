package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.Expense
import br.com.lojabaterias.data.ExpenseCategory
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.DateButton
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.SectionTitle
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.moneyResultColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.viewmodel.ExpensesViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel
import java.time.LocalDate

/** Despesas da loja pagas no mês, por categoria. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen() {
    val vm = appViewModel { ExpensesViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var newExpense by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Despesas") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newExpense = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nova despesa") },
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
            item { MonthResultCard(state.sales.revenue, state.sales.profit, state.paidTotal, state.netProfit) }

            if (state.byCategory.isNotEmpty()) {
                item { SectionTitle("Para onde foi o dinheiro") }
                item {
                    AppCard {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            state.byCategory.forEach { (cat, total) -> InfoRow(cat, Money.format(total)) }
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            InfoRow("Total que saiu", Money.format(state.paidTotal), bold = true)
                        }
                    }
                }
            }

            item { SectionTitle("Despesas pagas no mês") }
            if (!state.loading && state.payments.isEmpty()) item { EmptyState("Nenhuma despesa paga neste mês.") }
            items(state.payments, key = { it.id }) { e -> PaymentRow(e) { toDelete = e } }
        }
    }

    if (newExpense) {
        ExpenseDialog(
            onConfirm = { cat, desc, amount, date -> vm.addExpense(cat, desc, amount, date) { newExpense = false } },
            onDismiss = { newExpense = false },
        )
    }
    toDelete?.let { e ->
        ConfirmDialog(
            title = "Excluir despesa?",
            text = "${e.description} • ${Money.format(e.amount)} em ${Periods.formatDate(e.date)}.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { vm.delete(e); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun MonthResultCard(revenue: Long, profit: Long, expenses: Long, net: Long) {
    AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(16.dp)) {
            val on = MaterialTheme.colorScheme.onPrimaryContainer
            Text("Resultado do mês", style = MaterialTheme.typography.labelLarge, color = on)
            InfoRow("Entrou (faturamento)", Money.format(revenue))
            InfoRow("Lucro das vendas", Money.format(profit), valueColor = moneyResultColor(profit))
            InfoRow("Saiu (despesas)", "-" + Money.format(expenses), valueColor = if (expenses > 0) dangerColor() else on)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("Sobrou (lucro líquido)", style = MaterialTheme.typography.bodyMedium, color = on)
            FitText(
                Money.format(net),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = moneyResultColor(net),
            )
        }
    }
}

@Composable
private fun PaymentRow(e: Expense, onDelete: () -> Unit) {
    AppCard {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp, horizontal = 0.dp)) {
                Text(e.description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${Periods.formatDate(e.date)} • ${e.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(Money.format(e.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ExpenseCategory.ALL.forEach { c ->
            FilterChip(selected = selected == c, onClick = { onSelect(c) }, label = { Text(c) })
        }
    }
}

@Composable
private fun ExpenseDialog(onConfirm: (String, String, Long, LocalDate) -> Unit, onDismiss: () -> Unit) {
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableLongStateOf(0L) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova despesa") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Categoria", style = MaterialTheme.typography.labelLarge)
                CategoryChips(category) { category = it }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(60) },
                    label = { Text("Descrição (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Valor pago")
                DateButton("Data", date) { date = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(category, description, amount, date) },
                enabled = category.isNotEmpty() && amount > 0,
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
