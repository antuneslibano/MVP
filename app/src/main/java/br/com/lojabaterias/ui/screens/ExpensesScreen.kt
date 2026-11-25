package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import br.com.lojabaterias.data.Expense
import br.com.lojabaterias.data.ExpenseCategory
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.DateButton
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.HeroCard
import br.com.lojabaterias.ui.components.HeroStat
import br.com.lojabaterias.ui.components.InfoRow
import br.com.lojabaterias.ui.components.ListRow
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.PeriodSwitcher
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.TextInput
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.viewmodel.ExpensesViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel
import java.time.LocalDate

/** Despesas da loja pagas no mês, por categoria. */
@Composable
fun ExpensesScreen() {
    val vm = appViewModel { ExpensesViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var newExpense by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Expense?>(null) }

    MainPage(fabLabel = "Nova despesa", onFab = { newExpense = true }) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = Gaps.screen, end = Gaps.screen, top = 4.dp, bottom = FabSpace),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PeriodSwitcher(
                    label = state.monthLabel,
                    onPrevious = vm::previousMonth,
                    onNext = vm::nextMonth,
                    canNext = state.monthOffset < 0,
                )
            }
            item {
                HeroCard(label = "Despesas pagas", value = Money.format(state.paidTotal)) {
                    Row(Modifier.padding(top = 12.dp)) {
                        HeroStat("Lucro das vendas", Money.format(state.sales.profit), Modifier.weight(1f))
                        HeroStat("Sobrou", Money.format(state.netProfit), Modifier.weight(1f))
                    }
                }
            }

            if (state.byCategory.size > 1) {
                item { SectionHeader("Por categoria") }
                item {
                    PanelCard {
                        state.byCategory.forEach { (cat, total) -> InfoRow(cat, Money.format(total)) }
                    }
                }
            }

            item { SectionHeader("Lançamentos") }
            if (!state.loading && state.payments.isEmpty()) item { EmptyState("Nenhuma despesa neste mês.") }
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
            text = "${e.description} · ${Money.format(e.amount)} · ${Periods.formatDate(e.date)}",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { vm.delete(e); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun PaymentRow(e: Expense, onDelete: () -> Unit) {
    ListRow(title = e.description, subtitle = "${Periods.formatDate(e.date)} · ${e.category}") {
        Text(Money.format(e.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.ALL.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MoneyField(value = amount, onValueChange = { amount = it }, label = "Valor")
                TextInput(description, { description = it }, "Descrição (opcional)", maxLength = 60)
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
