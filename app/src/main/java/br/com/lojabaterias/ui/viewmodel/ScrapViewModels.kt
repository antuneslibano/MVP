package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.ScrapMovement
import br.com.lojabaterias.data.ScrapPeriodSummary
import br.com.lojabaterias.data.ScrapStockRow
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.domain.Periods
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScrapsState(
    val stock: List<ScrapStockRow> = emptyList(),
    val totalQuantity: Int = 0,
    val month: ScrapPeriodSummary = ScrapPeriodSummary(),
    val monthLabel: String = "",
    val movements: List<ScrapMovement> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ScrapsViewModel(private val repo: StoreRepository) : MessageViewModel() {

    private val monthSummary = currentDateFlow().flatMapLatest { today ->
        val range = Periods.range(PeriodType.MONTH, today)
        combine(repo.observeActiveSales(range), repo.observeScrapMovementsInRange(range)) { sales, moves ->
            ScrapPeriodSummary.from(sales, moves) to Periods.formalLabel(PeriodType.MONTH, today, 0)
        }
    }

    val state: StateFlow<ScrapsState> = combine(
        repo.observeScrapStock(),
        monthSummary,
        repo.observeScrapMovements(100),
    ) { stock, (month, monthLabel), movements ->
        val rows = stock.filter { it.quantity != 0 }.map { ScrapStockRow(it.amperage, it.quantity, null) }
        ScrapsState(
            stock = rows,
            totalQuantity = rows.sumOf { it.quantity },
            month = month,
            monthLabel = monthLabel,
            movements = movements.filter { it.type != br.com.lojabaterias.data.ScrapMovementType.VOUCHER_ISSUED },
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

    fun buyScrap(amperage: Int, quantity: Int, amount: Long, note: String, onSuccess: () -> Unit) =
        execute("Compra de sucatas registrada", onSuccess) { repo.buyScrap(amperage, quantity, amount, note) }

    fun deleteMovement(id: Long, onSuccess: () -> Unit) =
        execute("Registro excluído", onSuccess) { repo.deleteScrapMovement(id) }

    fun sellScrap(amperage: Int, quantity: Int, amount: Long, note: String, onSuccess: () -> Unit) =
        execute("Venda de sucatas registrada", onSuccess) { repo.sellScrap(amperage, quantity, amount, note) }

    fun adjustScrap(amperage: Int, newQuantity: Int, note: String, onSuccess: () -> Unit) =
        execute("Estoque de sucatas ${amperage}Ah ajustado para $newQuantity", onSuccess) {
            repo.adjustScrap(amperage, newQuantity, note)
        }
}
