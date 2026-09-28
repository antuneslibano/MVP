package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.Expense
import br.com.lojabaterias.data.ExpenseKind
import br.com.lojabaterias.data.PeriodSummary
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.domain.Periods
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExpensesState(
    val monthOffset: Int = 0,
    val monthLabel: String = "",
    val sales: PeriodSummary = PeriodSummary(),
    /** Despesas pagas no mês, mais recentes primeiro. */
    val payments: List<Expense> = emptyList(),
    val loading: Boolean = true,
) {
    val paidTotal: Long get() = payments.sumOf { it.amount }
    val netProfit: Long get() = sales.profit - paidTotal
    val byCategory: List<Pair<String, Long>>
        get() = payments.groupBy { it.category }.map { (c, l) -> c to l.sumOf { it.amount } }.sortedByDescending { it.second }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(private val repo: StoreRepository) : MessageViewModel() {
    private val offset = MutableStateFlow(0)

    init {
        // Contas fixas não são mais usadas: apaga as que existirem (e os pagamentos delas).
        viewModelScope.launch { runCatching { repo.removeFixedBills() } }
    }

    val state: StateFlow<ExpensesState> = combine(offset, currentDateFlow()) { off, today -> off to today }
        .flatMapLatest { (off, today) ->
            val range = Periods.range(PeriodType.MONTH, today, off)
            combine(repo.observeExpenses(), repo.observeSummary(range)) { all, sales ->
                val payments = all.filter { it.kind == ExpenseKind.PAYMENT && it.date in range }
                ExpensesState(
                    monthOffset = off,
                    monthLabel = Periods.label(PeriodType.MONTH, today, off),
                    sales = sales,
                    payments = payments,
                    loading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesState())

    fun previousMonth() = offset.update { it - 1 }
    fun nextMonth() = offset.update { if (it < 0) it + 1 else it }

    private fun act(success: String, after: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                message(success)
                after()
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }

    fun addExpense(category: String, description: String, amount: Long, date: LocalDate, onDone: () -> Unit) =
        act("Despesa registrada", onDone) { repo.addExpense(category, description, amount, Periods.toMillis(date) + NOON) }

    fun delete(e: Expense) = act("Despesa excluída") { repo.deleteExpense(e.id) }

    private companion object {
        /** Meio-dia: evita que a despesa caia no dia anterior por fuso/horário de verão. */
        const val NOON = 12 * 60 * 60 * 1000L
    }
}
