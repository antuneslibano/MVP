package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.ModelCount
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.data.WarrantyClaim
import br.com.lojabaterias.data.WarrantyPeriodSummary
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.domain.Periods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WarrantyTab(val label: String) {
    EXCHANGES("Garantias"),
    EXTRAS("Extras"),
}

data class WarrantiesState(
    val tab: WarrantyTab = WarrantyTab.EXCHANGES,
    /** 0 = mês atual, -1 = mês passado... */
    val monthOffset: Int = 0,
    val monthLabel: String = "",
    /** Quantidade por modelo no mês (da aba escolhida). */
    val byModel: List<ModelCount> = emptyList(),
    /** Registros do mês (da aba escolhida), mais recentes primeiro. */
    val list: List<WarrantyClaim> = emptyList(),
    /** Extras ainda no estoque (não vendidas), considerando todos os meses. */
    val extrasInStock: Int = 0,
    val loading: Boolean = true,
) {
    val total: Int get() = list.size
}

class WarrantiesViewModel(private val repo: StoreRepository) : MessageViewModel() {
    private val tab = MutableStateFlow(WarrantyTab.EXCHANGES)
    private val offset = MutableStateFlow(0)

    val products: StateFlow<List<Product>> = repo.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<WarrantiesState> =
        combine(repo.observeWarranties(), tab, offset, currentDateFlow()) { all, t, off, today ->
            val range = Periods.range(PeriodType.MONTH, today, off)
            val ofTab = all.filter { it.isExtra == (t == WarrantyTab.EXTRAS) }
            val inMonth = ofTab.filter { it.createdAt in range }
            WarrantiesState(
                tab = t,
                monthOffset = off,
                monthLabel = Periods.label(PeriodType.MONTH, today, off),
                byModel = WarrantyPeriodSummary.byModel(inMonth),
                list = inMonth,
                extrasInStock = all.count { it.isExtra && it.saleId == null },
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WarrantiesState())

    fun setTab(t: WarrantyTab) { tab.value = t }
    fun previousMonth() = offset.update { it - 1 }
    fun nextMonth() = offset.update { if (it < 0) it + 1 else it }

    fun register(product: Product, quantity: Int) {
        val extra = tab.value == WarrantyTab.EXTRAS
        viewModelScope.launch {
            try {
                if (extra) repo.registerExtra(product.id, quantity) else repo.registerExchange(product.id, quantity)
                offset.value = 0
                message(
                    if (extra) "$quantity× ${product.model} extra: entrou no estoque com custo zero"
                    else "$quantity× ${product.model} trocada(s) em garantia"
                )
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }

    fun delete(w: WarrantyClaim) {
        viewModelScope.launch {
            try {
                repo.deleteWarranty(w.id)
                message(if (w.isExtra) "Extra excluída e retirada do estoque" else "Troca excluída")
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }
}
