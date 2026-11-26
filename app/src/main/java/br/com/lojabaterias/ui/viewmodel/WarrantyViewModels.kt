package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.ModelCount
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.data.WarrantyClaim
import br.com.lojabaterias.data.WarrantyPeriodSummary
import br.com.lojabaterias.data.WarrantyStatus
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.domain.PeriodType
import br.com.lojabaterias.domain.Periods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Abas da tela (a do Vitor saiu do app; os registros antigos continuam guardados). */
enum class WarrantyTab(val label: String, val status: String) {
    EXTRAS("Extras", WarrantyStatus.EXTRA),
    EXCHANGES("Garantias", WarrantyStatus.EXCHANGE),
}

data class WarrantiesState(
    val tab: WarrantyTab = WarrantyTab.EXTRAS,
    /** 0 = mês atual, -1 = mês passado... */
    val monthOffset: Int = 0,
    val monthLabel: String = "",
    /** Quantidade por modelo no mês (da aba escolhida). */
    val byModel: List<ModelCount> = emptyList(),
    /** Registros do mês (da aba escolhida), mais recentes primeiro. */
    val list: List<WarrantyClaim> = emptyList(),
    /** Os mesmos registros agrupados por modelo e dia (ex.: 2× BE50D). */
    val groups: List<WarrantyGroup> = emptyList(),
    /** Extras ainda no estoque (não vendidas), considerando todos os meses. */
    val extrasInStock: Int = 0,
    val loading: Boolean = true,
) {
    val total: Int get() = list.size
}

/** Baterias do mesmo modelo lançadas no mesmo dia. */
data class WarrantyGroup(val model: String, val createdAt: Long, val tab: WarrantyTab, val items: List<WarrantyClaim>) {
    val count: Int get() = items.size
    val soldCount: Int get() = items.count { it.saleId != null }
    val key: String get() = "${tab.name}-${items.first().id}"
}

class WarrantiesViewModel(private val repo: StoreRepository) : MessageViewModel() {
    private val tab = MutableStateFlow(WarrantyTab.EXTRAS)
    private val offset = MutableStateFlow(0)

    val products: StateFlow<List<Product>> = repo.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<WarrantiesState> =
        combine(repo.observeWarranties(), tab, offset, currentDateFlow()) { all, t, off, today ->
            val range = Periods.range(PeriodType.MONTH, today, off)
            val ofTab = all.filter { it.status == t.status }
            val inMonth = ofTab.filter { it.createdAt in range }
            WarrantiesState(
                tab = t,
                monthOffset = off,
                monthLabel = Periods.label(PeriodType.MONTH, today, off),
                byModel = WarrantyPeriodSummary.byModel(inMonth),
                list = inMonth,
                groups = inMonth.groupBy { it.returnedModel to Periods.formatDate(it.createdAt) }
                    .map { (key, items) -> WarrantyGroup(key.first, items.maxOf { it.createdAt }, t, items) }
                    .sortedByDescending { it.createdAt },
                extrasInStock = all.count { it.isExtra && it.saleId == null },
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WarrantiesState())

    init {
        viewModelScope.launch { runCatching { repo.repairExtras() } }
    }

    fun setTab(t: WarrantyTab) { tab.value = t }
    fun previousMonth() = offset.update { it - 1 }
    fun nextMonth() = offset.update { if (it < 0) it + 1 else it }

    fun register(product: Product, quantity: Int) {
        val t = tab.value
        viewModelScope.launch {
            try {
                when (t) {
                    WarrantyTab.EXTRAS -> repo.registerExtra(product.id, quantity)
                    WarrantyTab.EXCHANGES -> repo.registerExchange(product.id, quantity)
                }
                offset.value = 0
                message(
                    when (t) {
                        WarrantyTab.EXTRAS -> "$quantity× ${product.model} extra: entrou no estoque com custo zero"
                        WarrantyTab.EXCHANGES -> "$quantity× ${product.model} trocada(s) em garantia"
                    }
                )
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }

    fun delete(g: WarrantyGroup) {
        viewModelScope.launch {
            try {
                repo.deleteWarranties(g.items.map { it.id })
                message(
                    when (g.tab) {
                        WarrantyTab.EXTRAS -> "Extra excluída e retirada do estoque"
                        WarrantyTab.EXCHANGES -> "Troca excluída"
                    }
                )
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }
}
