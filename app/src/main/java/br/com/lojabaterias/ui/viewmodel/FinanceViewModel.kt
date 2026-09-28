package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.AppContainer
import br.com.lojabaterias.data.FinancePeriod
import br.com.lojabaterias.data.FinanceReport
import br.com.lojabaterias.domain.DateRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import br.com.lojabaterias.domain.Periods
import java.time.LocalDate

data class FinanceSelection(val period: FinancePeriod = FinancePeriod.MONTH, val offset: Int = 0)

data class FinanceState(
    val selection: FinanceSelection = FinanceSelection(),
    val report: FinanceReport = FinanceReport(),
    val loading: Boolean = true,
)

class FinanceViewModel(container: AppContainer) : MessageViewModel() {

    private val repo = container.repository
    private val selection = MutableStateFlow(FinanceSelection())
    private val everything = DateRange(0, Long.MAX_VALUE)

    private val money = combine(
        repo.observeSales(everything),
        repo.observeExpenses(),
        repo.observeScrapMovementsInRange(everything),
        repo.observeCharges(),
        repo.observeWarranties(),
    ) { sales, expenses, scrap, charges, warranties -> Money(sales, expenses, scrap, charges, warranties) }

    private val stock = combine(
        repo.observeProducts(),
        repo.observeScrapStock(),
        repo.observeScrapPrices().map { list -> list.associate { it.amperage to it.value } },
        repo.observeInvoiceBills(),
    ) { products, scrapStock, prices, bills -> Stock(products, scrapStock, prices, bills) }

    val state: StateFlow<FinanceState> = combine(selection, currentDateFlow(), money, stock) { sel, today, m, (products, scrapStock, prices, bills) ->
        FinanceState(
            selection = sel,
            report = FinanceReport.build(
                period = sel.period,
                offset = sel.offset,
                today = today,
                allSales = m.sales,
                expenses = m.expenses,
                scrapMovements = m.scrap,
                charges = m.charges,
                warranties = m.warranties,
                products = products,
                scrapStock = scrapStock,
                scrapPrices = prices,
                invoiceBills = bills,
            ),
            loading = false,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceState())

    private fun act(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                message(success)
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }

    fun addWithdrawal(partner: String, amount: Long, date: LocalDate) =
        act("Retirada registrada") { repo.addWithdrawal(partner, amount, Periods.toMillis(date) + 12 * 3_600_000L) }

    fun deleteWithdrawal(id: Long) = act("Retirada excluída") { repo.deleteExpense(id) }

    fun setOpeningBalance(amount: Long, date: LocalDate) =
        act("Saldo inicial salvo") { repo.setOpeningBalance(amount, Periods.toMillis(date)) }

    fun setPeriod(period: FinancePeriod) = selection.update { FinanceSelection(period, 0) }
    fun previous() = selection.update { it.copy(offset = it.offset - 1) }
    fun next() = selection.update { if (it.offset < 0) it.copy(offset = it.offset + 1) else it }

    private data class Stock(
        val products: List<br.com.lojabaterias.data.Product>,
        val scrapStock: List<br.com.lojabaterias.data.ScrapStock>,
        val prices: Map<Int, Long>,
        val bills: List<br.com.lojabaterias.data.InvoiceBill>,
    )

    private data class Money(
        val sales: List<br.com.lojabaterias.data.SaleWithItems>,
        val expenses: List<br.com.lojabaterias.data.Expense>,
        val scrap: List<br.com.lojabaterias.data.ScrapMovement>,
        val charges: List<br.com.lojabaterias.data.ChargeService>,
        val warranties: List<br.com.lojabaterias.data.WarrantyClaim>,
    )
}
