package br.com.lojabaterias.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.AppContainer
import br.com.lojabaterias.data.ReportDocument
import br.com.lojabaterias.data.ReportPdfWriter
import br.com.lojabaterias.data.SaleWithItems
import br.com.lojabaterias.data.ScrapPeriodSummary
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ReportSelection(val type: PeriodType = PeriodType.DAY, val offset: Int = 0)

data class ReportsState(
    val selection: ReportSelection = ReportSelection(),
    val today: LocalDate = LocalDate.now(),
    val label: String = "",
    val report: Report = Report.EMPTY,
    /** Vendas válidas do período (usadas no PDF). */
    val sales: List<SaleWithItems> = emptyList(),
    val scrap: ScrapPeriodSummary = ScrapPeriodSummary(),
    val loading: Boolean = true,
) {
    val pdfFileName: String get() = Periods.reportFileName(selection.type, today, selection.offset)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(private val container: AppContainer) : MessageViewModel() {

    private val repo = container.repository
    private val selection = MutableStateFlow(ReportSelection())

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    val state: StateFlow<ReportsState> = combine(selection, currentDateFlow()) { sel, today -> sel to today }
        .flatMapLatest { (sel, today) ->
            val range = Periods.range(sel.type, today, sel.offset)
            combine(repo.observeActiveSales(range), repo.observeScrapSold(range)) { sales, sold -> sales to sold }
                .map { (sales, sold) ->
                    ReportsState(
                        selection = sel,
                        today = today,
                        label = Periods.label(sel.type, today, sel.offset),
                        report = ReportCalculator.build(sales.map { StoreRepository.toReportSale(it) }),
                        sales = sales,
                        scrap = ScrapPeriodSummary.from(sales, sold),
                        loading = false,
                    )
                }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState())

    fun setType(type: PeriodType) = selection.update { ReportSelection(type, 0) }
    fun previous() = selection.update { it.copy(offset = it.offset - 1) }
    fun next() = selection.update { if (it.offset < 0) it.copy(offset = it.offset + 1) else it }

    /** Gera o PDF do período atualmente exibido e grava no arquivo escolhido pelo usuário. */
    fun exportPdf(uri: Uri) {
        if (_exporting.value) return
        val s = state.value
        if (s.loading) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                val doc = ReportDocument(
                    type = s.selection.type,
                    periodLabel = Periods.formalLabel(s.selection.type, s.today, s.selection.offset),
                    report = s.report,
                    sales = s.sales,
                    scrap = s.scrap,
                )
                withContext(Dispatchers.IO) {
                    val out = container.app.contentResolver.openOutputStream(uri, "wt")
                        ?: error("Não foi possível criar o arquivo")
                    out.use { ReportPdfWriter.write(doc, it) }
                }
                message("PDF salvo com sucesso")
            } catch (e: Exception) {
                message(errorMessage(e))
            } finally {
                _exporting.value = false
            }
        }
    }
}
