package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.PeriodSummary
import br.com.lojabaterias.data.SaleWithItems
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.domain.Periods
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeState(
    val today: PeriodSummary = PeriodSummary(),
    val week: PeriodSummary = PeriodSummary(),
    val month: PeriodSummary = PeriodSummary(),
    val recent: List<SaleWithItems> = emptyList(),
    val date: LocalDate = LocalDate.now(),
    /** Baterias de clientes na carga (não entregues). */
    val chargesOpen: Int = 0,
)

/** Emite a data atual e muda automaticamente na virada do dia. */
fun currentDateFlow(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(30_000)
    }
}.distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(repo: StoreRepository) : ViewModel() {

    val state: StateFlow<HomeState> = currentDateFlow().flatMapLatest { date ->
        combine(
            repo.observeSummary(Periods.range(PeriodType.DAY, date)),
            repo.observeSummary(Periods.range(PeriodType.WEEK, date)),
            repo.observeSummary(Periods.range(PeriodType.MONTH, date)),
            repo.observeRecentSales(10),
        ) { d, w, m, recent -> HomeState(d, w, m, recent, date) }
    }.combine(repo.observeCharges()) { home, charges -> home.copy(chargesOpen = charges.count { it.isOpen }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}
