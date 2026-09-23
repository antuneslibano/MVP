package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.domain.Report
import br.com.lojabaterias.domain.ReportCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ReportSelection(val type: PeriodType = PeriodType.DAY, val offset: Int = 0)

data class ReportsState(
    val selection: ReportSelection = ReportSelection(),
    val label: String = "",
    val report: Report = Report.EMPTY,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(repo: StoreRepository) : ViewModel() {

    private val selection = MutableStateFlow(ReportSelection())

    val state: StateFlow<ReportsState> = combine(selection, currentDateFlow()) { sel, today -> sel to today }
        .flatMapLatest { (sel, today) ->
            repo.observeActiveSales(Periods.range(sel.type, today, sel.offset))
                .map { sales ->
                    ReportsState(
                        selection = sel,
                        label = Periods.label(sel.type, today, sel.offset),
                        report = ReportCalculator.build(sales.map { StoreRepository.toReportSale(it) }),
                        loading = false,
                    )
                }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState())

    fun setType(type: PeriodType) = selection.update { ReportSelection(type, 0) }
    fun previous() = selection.update { it.copy(offset = it.offset - 1) }
    fun next() = selection.update { if (it.offset < 0) it.copy(offset = it.offset + 1) else it }
}
