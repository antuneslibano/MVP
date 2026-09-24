package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.ScrapMovement
import br.com.lojabaterias.data.ScrapPeriodSummary
import br.com.lojabaterias.data.ScrapPrice
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.domain.Periods
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScrapStockRow(
    val amperage: Int,
    val quantity: Int,
    /** Valor de tabela por unidade (null se a amperagem não está na tabela). */
    val unitValue: Long?,
) {
    val totalValue: Long get() = (unitValue ?: 0) * quantity
}

data class ScrapsState(
    val stock: List<ScrapStockRow> = emptyList(),
    val totalQuantity: Int = 0,
    val totalValue: Long = 0,
    val prices: Map<Int, Long> = emptyMap(),
    val month: ScrapPeriodSummary = ScrapPeriodSummary(),
    val monthLabel: String = "",
    val movements: List<ScrapMovement> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ScrapsViewModel(private val repo: StoreRepository) : MessageViewModel() {

    private val monthSummary = currentDateFlow().flatMapLatest { today ->
        val range = Periods.range(PeriodType.MONTH, today)
        combine(repo.observeActiveSales(range), repo.observeScrapSold(range)) { sales, sold ->
            ScrapPeriodSummary.from(sales, sold) to Periods.formalLabel(PeriodType.MONTH, today, 0)
        }
    }

    val state: StateFlow<ScrapsState> = combine(
        repo.observeScrapStock(),
        repo.observeScrapPrices().map { list -> list.associate { it.amperage to it.value } },
        monthSummary,
        repo.observeScrapMovements(100),
    ) { stock, prices, (month, monthLabel), movements ->
        val rows = stock.filter { it.quantity != 0 }.map { ScrapStockRow(it.amperage, it.quantity, prices[it.amperage]) }
        ScrapsState(
            stock = rows,
            totalQuantity = rows.sumOf { it.quantity },
            totalValue = rows.sumOf { it.totalValue },
            prices = prices,
            month = month,
            monthLabel = monthLabel,
            movements = movements,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScrapsState())

    private fun execute(success: String, onSuccess: () -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                message(success)
                onSuccess()
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }

    fun addScrap(amperage: Int, quantity: Int, note: String, onSuccess: () -> Unit) =
        execute("Entrada de ${quantity} sucata(s) ${amperage}Ah registrada", onSuccess) {
            repo.addScrap(amperage, quantity, note)
        }

    fun sellScrap(amperage: Int, quantity: Int, amount: Long, note: String, onSuccess: () -> Unit) =
        execute("Venda de sucatas registrada", onSuccess) { repo.sellScrap(amperage, quantity, amount, note) }

    fun adjustScrap(amperage: Int, newQuantity: Int, note: String, onSuccess: () -> Unit) =
        execute("Estoque de sucatas ${amperage}Ah ajustado para $newQuantity", onSuccess) {
            repo.adjustScrap(amperage, newQuantity, note)
        }
}

class ScrapPricesViewModel(private val repo: StoreRepository) : MessageViewModel() {

    val prices: StateFlow<List<ScrapPrice>?> = repo.observeScrapPrices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(id: Long, amperage: Int, value: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.saveScrapPrice(id, amperage, value)
                message("Valor da sucata ${amperage}Ah salvo")
                onSuccess()
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }

    fun delete(id: Long, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteScrapPrice(id)
                message("Amperagem removida da tabela")
                onSuccess()
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }
}
