package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.SaleWithItems
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.data.WarrantyClaim
import br.com.lojabaterias.data.WarrantyStatus
import br.com.lojabaterias.domain.PaymentMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class WarrantyTab(val label: String) {
    PICKUP("Recolha"),
    FACTORY("Na fábrica"),
    USED("Usadas"),
    HISTORY("Histórico"),
}

data class WarrantiesState(
    val tab: WarrantyTab = WarrantyTab.PICKUP,
    /** Busca por número de série, modelo ou cliente (procura em todas as seções). */
    val query: String = "",
    val list: List<WarrantyClaim> = emptyList(),
    val pickupCount: Int = 0,
    val factoryCount: Int = 0,
    val usedCount: Int = 0,
    val loading: Boolean = true,
)

class WarrantiesViewModel(private val repo: StoreRepository) : MessageViewModel() {
    private val tab = MutableStateFlow(WarrantyTab.PICKUP)
    private val query = MutableStateFlow("")

    val state: StateFlow<WarrantiesState> = combine(repo.observeWarranties(), tab, query) { all, t, q ->
        val term = q.trim()
        WarrantiesState(
            tab = t,
            query = q,
            list = if (term.isNotEmpty()) all.filter { it.matches(term) } else all.filter { w ->
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
    fun setQuery(q: String) { query.value = q }

    private fun WarrantyClaim.matches(term: String): Boolean {
        val compact = term.replace(" ", "")
        return listOfNotNull(returnedSerial, replacementSerial).any { it.replace(" ", "").contains(compact, ignoreCase = true) } ||
            returnedModel.contains(term, ignoreCase = true) ||
            replacementModel?.contains(term, ignoreCase = true) == true ||
            customerName.contains(term, ignoreCase = true)
    }

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

data class WarrantyFormState(
    val loading: Boolean = false,
    val sale: SaleWithItems? = null,
    val customerName: String = "",
    val returnedProductId: Long? = null,
    val returnedModel: String = "",
    /** Número de série da bateria que o cliente trouxe. */
    val returnedSerial: String = "",
    /** Data da venda, conforme o papel da garantia. */
    val saleDate: LocalDate? = null,
    /** Resultado do teste: null = ainda não respondido. */
    val defective: Boolean? = null,
    val replacement: Product? = null,
    /** Número de série da bateria nova entregue. */
    val replacementSerial: String = "",
    val exchangeDate: LocalDate = LocalDate.now(),
    val difference: Long = 0,
    val differenceMethod: PaymentMethod = PaymentMethod.PIX,
    val note: String = "",
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val canConfirm: Boolean
        get() = returnedModel.isNotBlank() && defective != null &&
            (defective == false || (replacement != null && returnedSerial.isNotBlank() && saleDate != null && replacementSerial.isNotBlank()))
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
                    saleDate = sale?.sale?.dateTime?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() },
                )
                if (sale == null) message("Venda não encontrada")
            }
        }
    }

    fun setCustomer(name: String) = _state.update { it.copy(customerName = name) }
    fun setNote(note: String) = _state.update { it.copy(note = note) }
    fun setDifference(v: Long) = _state.update { it.copy(difference = v) }
    fun setMethod(m: PaymentMethod) = _state.update { it.copy(differenceMethod = m) }
    fun setReturnedSerial(v: String) = _state.update { it.copy(returnedSerial = v) }
    fun setSaleDate(d: LocalDate) = _state.update { it.copy(saleDate = d) }
    fun setReplacementSerial(v: String) = _state.update { it.copy(replacementSerial = v) }
    fun setExchangeDate(d: LocalDate) = _state.update { it.copy(exchangeDate = d) }

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
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        if (s.exchangeDate.isAfter(today)) return message("A data da troca não pode ser no futuro")
        if (s.saleDate != null && s.saleDate.isAfter(s.exchangeDate)) return message("A data da venda é depois da data da troca")
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val defective = s.defective == true
                val at = if (!defective || s.exchangeDate == today) System.currentTimeMillis()
                else s.exchangeDate.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()
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
                    returnedSerial = s.returnedSerial,
                    returnedSaleDate = s.saleDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
                    replacementSerial = if (defective) s.replacementSerial else null,
                    at = at,
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
