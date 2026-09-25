package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.ScrapMovement
import br.com.lojabaterias.data.ScrapMovementType
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.data.Voucher
import br.com.lojabaterias.data.Vouchers
import br.com.lojabaterias.domain.DateRange
import br.com.lojabaterias.domain.Scrap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VouchersState(
    val open: List<Voucher> = emptyList(),
    /** Vales pagos, mais recentes primeiro. */
    val paid: List<ScrapMovement> = emptyList(),
    /** Amperagem sugerida para o casco de cada venda. */
    val amperageBySale: Map<Long, Int> = emptyMap(),
    val loading: Boolean = true,
) {
    val openValue: Long get() = open.sumOf { it.value }
    val openCascos: Int get() = open.sumOf { it.remaining }
}

class VouchersViewModel(private val repo: StoreRepository) : MessageViewModel() {
    private val all = DateRange(0, Long.MAX_VALUE)

    val state: StateFlow<VouchersState> = combine(
        repo.observeSales(all),
        repo.observeScrapMovementsInRange(all),
        repo.observeProducts(),
    ) { sales, moves, products ->
        val open = Vouchers.open(sales, moves)
        VouchersState(
            open = open,
            paid = moves.filter { it.type == ScrapMovementType.VOUCHER_PAID }.sortedByDescending { it.dateTime }.take(50),
            amperageBySale = open.associate { v ->
                val item = v.sale.items.firstOrNull()
                val amp = products.firstOrNull { it.id == item?.productId }?.amperage?.takeIf { it > 0 }
                    ?: item?.let { Scrap.guessAmperage(it.modelSnapshot) } ?: 0
                v.sale.sale.id to amp
            },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VouchersState())

    fun pay(v: Voucher, quantity: Int, amperage: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.payVoucher(v.sale.sale.id, quantity, amperage)
                message("Vale pago: casco(s) no estoque de sucatas")
                onDone()
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }

    fun undo(m: ScrapMovement) {
        viewModelScope.launch {
            try {
                repo.deleteScrapMovement(m.id)
                message("Pagamento do vale desfeito")
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }
}
