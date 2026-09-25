package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.SaleWithItems
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.domain.DateRange
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.domain.Periods
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

enum class SalesTab(val label: String) {
    TODAY("Hoje"),
    WEEK("Semana"),
    MONTH("Mês"),
    ALL("Todas"),
}

data class SalesFilter(
    val tab: SalesTab = SalesTab.TODAY,
    /** Quando definida, sobrepõe a aba e mostra somente esse dia. */
    val date: LocalDate? = null,
    val model: String = "",
    val payment: PaymentMethod? = null,
)

data class SalesState(
    val filter: SalesFilter = SalesFilter(),
    val sales: List<SaleWithItems> = emptyList(),
    val count: Int = 0,
    val units: Int = 0,
    /** Baterias vendidas por modelo no filtro atual (ex.: 3× M60GD, 2× Z60D). */
    val modelCounts: List<Pair<String, Int>> = emptyList(),
    val revenue: Long = 0,
    val profit: Long = 0,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModel(repo: StoreRepository) : ViewModel() {

    private val filter = MutableStateFlow(SalesFilter())

    private val rangeSales = combine(filter.map { it.tab to it.date }.distinctUntilChanged(), currentDateFlow()) { (tab, date), today ->
        when {
            date != null -> Periods.dayRange(date)
            tab == SalesTab.TODAY -> Periods.range(PeriodType.DAY, today)
            tab == SalesTab.WEEK -> Periods.range(PeriodType.WEEK, today)
            tab == SalesTab.MONTH -> Periods.range(PeriodType.MONTH, today)
            else -> DateRange(0, Long.MAX_VALUE)
        }
    }.flatMapLatest { repo.observeSales(it) }

    val state: StateFlow<SalesState> = combine(filter, rangeSales) { f, list ->
        val query = f.model.trim()
        val filtered = list.filter { s ->
            (f.payment == null || s.sale.payment == f.payment) &&
                (query.isEmpty() || s.items.any { it.modelSnapshot.contains(query, ignoreCase = true) })
        }
        val valid = filtered.filter { !it.sale.isCanceled }
        SalesState(
            filter = f,
            sales = filtered,
            count = valid.size,
            units = valid.sumOf { it.quantity },
            modelCounts = valid.flatMap { it.items }
                .groupBy { it.modelSnapshot }
                .map { (model, items) -> model to items.sumOf { it.quantity } }
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }),
            revenue = valid.sumOf { it.sale.finalAmount },
            profit = valid.sumOf { it.sale.grossProfit },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SalesState())

    fun setTab(tab: SalesTab) = filter.update { it.copy(tab = tab, date = null) }
    fun setDate(date: LocalDate?) = filter.update { it.copy(date = date) }
    fun setModel(model: String) = filter.update { it.copy(model = model) }
    fun setPayment(method: PaymentMethod?) = filter.update { it.copy(payment = method) }
}
