package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.SaleWithItems
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.data.WarrantyClaim
import br.com.lojabaterias.data.WarrantyStatus
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.WarrantyCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WarrantyTab(val label: String) {
    PICKUP("Recolha"),
    FACTORY("Na fábrica"),
    USED("Usadas"),
    HISTORY("Histórico"),
}

data class WarrantiesState(
    val tab: WarrantyTab = WarrantyTab.PICKUP,
    val list: List<WarrantyClaim> = emptyList(),
    val pickupCount: Int = 0,
    val factoryCount: Int = 0,
    val usedCount: Int = 0,
    val loading: Boolean = true,
)

class WarrantiesViewModel(private val repo: StoreRepository) : MessageViewModel() {
    private val tab = MutableStateFlow(WarrantyTab.PICKUP)

    val state: StateFlow<WarrantiesState> = combine(repo.observeWarranties(), tab) { all, t ->
        WarrantiesState(
            tab = t,
            list = all.filter { w ->
                when (t) {
                    WarrantyTab.PICKUP -> w.status == WarrantyStatus.AWAITING_PICKUP
                    WarrantyTab.FACTORY -> w.status == WarrantyStatus.AT_FACTORY
                    WarrantyTab.USED -> w.isUsedInShop
                    WarrantyTab.HISTORY -> w.status == WarrantyStatus.NO_DEFECT || w.status == WarrantyStatus.REPLACED ||
                        (w.status == WarrantyStatus.DENIED && !w.isUsedInShop)
                }
            },
            pickupCount = all.count { it.status == WarrantyStatus.AWAITING_PICKUP },
            factoryCount = all.count { it.status == WarrantyStatus.AT_FACTORY },
            usedCount = all.count { it.isUsedInShop },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WarrantiesState())

    fun setTab(t: WarrantyTab) { tab.value = t }

    /** A fábrica recolheu as baterias informadas. */
    fun collect(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                repo.markWarrantiesCollected(ids)
                message(if (ids.size == 1) "Bateria marcada como recolhida" else "${ids.size} baterias marcadas como recolhidas")
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }
}

/** Escolha da venda para iniciar uma troca em garantia. */
class SalePickerViewModel(repo: StoreRepository) : MessageViewModel() {
    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val results: StateFlow<List<SaleWithItems>> = combine(repo.observeRecentSales(1000), query) { sales, q ->
        val term = q.trim()
        sales.filter { !it.sale.isCanceled }.filter { s ->
            term.isEmpty() || WarrantyCode.matches(s.sale.id, term) ||
                s.items.any { it.modelSnapshot.contains(term, ignoreCase = true) }
        }.take(200)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { query.value = q }
}

data class WarrantyFormState(
    val loading: Boolean = false,
    val sale: SaleWithItems? = null,
    val customerName: String = "",
    val returnedProductId: Long? = null,
    val returnedModel: String = "",
    /** Resultado do teste: null = ainda não respondido. */
    val defective: Boolean? = null,
    val replacement: Product? = null,
    val difference: Long = 0,
    val differenceMethod: PaymentMethod = PaymentMethod.PIX,
    val note: String = "",
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val canConfirm: Boolean
        get() = returnedModel.isNotBlank() && defective != null && (defective == false || replacement != null)
}

class WarrantyFormViewModel(private val repo: StoreRepository, private val saleId: Long?) : MessageViewModel() {
    private val _state = MutableStateFlow(WarrantyFormState(loading = saleId != null))
    val state: StateFlow<WarrantyFormState> = _state.asStateFlow()

    val products: StateFlow<List<Product>> = repo.observeProducts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (saleId != null) {
            viewModelScope.launch {
                val sale = repo.getSale(saleId)
                val item = sale?.items?.firstOrNull()
                _state.value = WarrantyFormState(
                    sale = sale,
                    returnedProductId = item?.productId,
                    returnedModel = item?.modelSnapshot.orEmpty(),
                )
                if (sale == null) message("Venda não encontrada")
            }
        }
    }

    fun setCustomer(name: String) = _state.update { it.copy(customerName = name) }
    fun setNote(note: String) = _state.update { it.copy(note = note) }
    fun setDifference(v: Long) = _state.update { it.copy(difference = v) }
    fun setMethod(m: PaymentMethod) = _state.update { it.copy(differenceMethod = m) }

    /** Bateria que o cliente trouxe (quando não há venda no sistema). */
    fun setReturned(p: Product) = _state.update {
        it.copy(returnedProductId = p.id, returnedModel = p.model).withDefaultReplacement(products.value)
    }

    fun setDefective(value: Boolean) = _state.update {
        it.copy(defective = value).let { s -> if (value) s.withDefaultReplacement(products.value) else s }
    }

    fun setReplacement(p: Product) = _state.update { it.copy(replacement = p) }

    /** Sugere a mesma bateria como troca (se houver estoque). */
    private fun WarrantyFormState.withDefaultReplacement(all: List<Product>): WarrantyFormState {
        if (replacement != null) return this
        val same = all.firstOrNull { it.id == returnedProductId && it.stock > 0 }
        return copy(replacement = same)
    }

    /** Diferença sugerida quando a troca é por uma bateria mais cara. */
    fun suggestedDifference(s: WarrantyFormState): Long {
        val newP = s.replacement ?: return 0
        if (newP.id == s.returnedProductId) return 0
        val soldPrice = s.sale?.items?.firstOrNull()?.unitPrice
        val oldPrice = soldPrice ?: products.value.firstOrNull { it.id == s.returnedProductId }?.pricePix ?: return 0
        return (newP.pricePix - oldPrice).coerceAtLeast(0)
    }

    fun confirm() {
        val s = _state.value
        if (s.saving || !s.canConfirm) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val defective = s.defective == true
                repo.createWarranty(
                    saleId = s.sale?.sale?.id,
                    customerName = s.customerName,
                    returnedProductId = s.returnedProductId,
                    returnedModel = s.returnedModel,
                    defective = defective,
                    replacementProductId = if (defective) s.replacement?.id else null,
                    differenceAmount = if (defective) s.difference else 0,
                    differenceMethod = s.differenceMethod,
                    note = s.note,
                )
                message(if (defective) "Troca registrada" else "Teste registrado (sem troca)")
                _state.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                message(errorMessage(e))
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

class WarrantyDetailViewModel(private val repo: StoreRepository, private val id: Long) : MessageViewModel() {
    val claim: StateFlow<Loaded<WarrantyClaim?>?> = repo.observeWarranty(id)
        .map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val products: StateFlow<List<Product>> = repo.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun act(success: String, after: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                message(success)
                after()
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }

    fun collected() = act("Marcada como recolhida pela fábrica") { repo.markWarrantiesCollected(listOf(id)) }
    fun replaced(productId: Long) = act("Reposição registrada: a bateria entrou no estoque") { repo.warrantyReplaced(id, productId) }
    fun refused(offered: String, reason: String) = act("Recusa registrada") { repo.warrantyOfferRefused(id, offered, reason) }
    fun denied() = act("Garantia negada: bateria na seção de usadas") { repo.warrantyDenied(id) }
    fun destination(dest: String, value: Long, amperage: Int?) =
        act("Destino registrado") { repo.setUsedDestination(id, dest, value, amperage) }
    fun delete(onDeleted: () -> Unit) = act("Atendimento excluído", onDeleted) { repo.deleteWarranty(id) }
}
