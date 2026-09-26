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

/** Conta fixa no mês escolhido: paga ou a pagar. */
data class BillStatus(val bill: Expense, val payment: Expense?, val overdue: Boolean) {
    val paid: Boolean get() = payment != null
}

data class ExpensesState(
    val monthOffset: Int = 0,
    val monthLabel: String = "",
    /** Mês no formato AAAAMM (usado para marcar a conta fixa paga). */
    val monthKey: Int = 0,
    val sales: PeriodSummary = PeriodSummary(),
    val bills: List<BillStatus> = emptyList(),
    /** Despesas pagas no mês (avulsas e contas fixas), mais recentes primeiro. */
    val payments: List<Expense> = emptyList(),
    val loading: Boolean = true,
) {
    val paidTotal: Long get() = payments.sumOf { it.amount }
    val pendingBills: List<BillStatus> get() = bills.filter { !it.paid }
    val pendingTotal: Long get() = pendingBills.sumOf { it.bill.amount }
    val netProfit: Long get() = sales.profit - paidTotal
    val byCategory: List<Pair<String, Long>>
        get() = payments.groupBy { it.category }.map { (c, l) -> c to l.sumOf { it.amount } }.sortedByDescending { it.second }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(private val repo: StoreRepository) : MessageViewModel() {
    private val offset = MutableStateFlow(0)

    val state: StateFlow<ExpensesState> = combine(offset, currentDateFlow()) { off, today -> off to today }
        .flatMapLatest { (off, today) ->
            val range = Periods.range(PeriodType.MONTH, today, off)
            val first = Periods.startOf(PeriodType.MONTH, today).plusMonths(off.toLong())
            val key = first.year * 100 + first.monthValue
            combine(repo.observeExpenses(), repo.observeSummary(range)) { all, sales ->
                val payments = all.filter { it.kind == ExpenseKind.PAYMENT && it.date in range }
                val paidBills = all.filter { it.kind == ExpenseKind.PAYMENT && it.billMonth == key && it.billId != null }
                    .associateBy { it.billId }
                val bills = all.filter { it.isBill && (it.active || paidBills.containsKey(it.id)) }
                    .map { b ->
                        val payment = paidBills[b.id]
                        val overdue = payment == null && off == 0 && today.dayOfMonth > (b.dueDay ?: 31)
                        BillStatus(b, payment, overdue)
                    }
                    .sortedWith(compareBy<BillStatus> { it.paid }.thenBy { it.bill.dueDay ?: 31 })
                ExpensesState(
                    monthOffset = off,
                    monthLabel = Periods.label(PeriodType.MONTH, today, off),
                    monthKey = key,
                    sales = sales,
                    bills = bills,
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

    fun payBill(s: BillStatus, amount: Long, date: LocalDate, onDone: () -> Unit) =
        act("Conta paga: ${s.bill.description}", onDone) {
            repo.payBill(s.bill.id, state.value.monthKey, amount, Periods.toMillis(date) + NOON)
        }

    fun saveBill(id: Long?, description: String, category: String, amount: Long, dueDay: Int, onDone: () -> Unit) =
        act(if (id == null) "Conta fixa cadastrada" else "Conta fixa alterada", onDone) {
            repo.saveBill(id, description, category, amount, dueDay)
        }

    fun removeBill(id: Long, onDone: () -> Unit) =
        act("Conta fixa removida dos próximos meses", onDone) { repo.deactivateBill(id) }

    fun delete(e: Expense) = act("Despesa excluída") { repo.deleteExpense(e.id) }

    private companion object {
        /** Meio-dia: evita que a despesa caia no dia anterior por fuso/horário de verão. */
        const val NOON = 12 * 60 * 60 * 1000L
    }
}
