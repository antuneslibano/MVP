package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.ChargeService
import br.com.lojabaterias.data.StoreRepository
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

enum class ChargeTab(val label: String) { OPEN("Na loja"), DELIVERED("Entregues"), ALL("Todas") }

data class ChargesState(
    val tab: ChargeTab = ChargeTab.OPEN,
    val query: String = "",
    val list: List<ChargeService> = emptyList(),
    val openCount: Int = 0,
    val unpaidOpenTotal: Long = 0,
    val loansOut: Int = 0,
    val loading: Boolean = true,
)

class ChargesViewModel(repo: StoreRepository) : MessageViewModel() {
    private val tab = MutableStateFlow(ChargeTab.OPEN)
    private val query = MutableStateFlow("")

    val state: StateFlow<ChargesState> = combine(repo.observeCharges(), tab, query) { all, t, q ->
        val term = q.trim()
        val filtered = all.filter {
            when (t) {
                ChargeTab.OPEN -> it.isOpen
                ChargeTab.DELIVERED -> !it.isOpen
                ChargeTab.ALL -> true
            }
        }.filter {
            term.isEmpty() || it.customerName.contains(term, true) || it.phone.contains(term) ||
                it.batteryDescription.contains(term, true)
        }
        val open = all.filter { it.isOpen }
        ChargesState(
            tab = t,
            query = q,
            list = filtered,
            openCount = open.size,
            unpaidOpenTotal = all.filter { !it.paid }.sumOf { it.price },
            loansOut = open.count { it.hasLoan },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChargesState())

    fun setTab(t: ChargeTab) { tab.value = t }
    fun setQuery(q: String) { query.value = q }
}

data class ChargeFormState(
    val id: Long? = null,
    val customerName: String = "",
    val phone: String = "",
    val batteryDescription: String = "",
    val receivedAt: Long = System.currentTimeMillis(),
    val price: Long = 0,
    val paid: Boolean = false,
    val method: PaymentMethod = PaymentMethod.PIX,
    val loan: Boolean = false,
    /** Empréstimo antigo com controle de estoque (não pode ser alterado). */
    val lockedLoanModel: String? = null,
    val note: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val isEdit: Boolean get() = id != null
}

class ChargeFormViewModel(private val repo: StoreRepository, private val chargeId: Long?) : MessageViewModel() {
    private val _state = MutableStateFlow(ChargeFormState(id = chargeId, loading = chargeId != null))
    val state: StateFlow<ChargeFormState> = _state.asStateFlow()

    init {
        if (chargeId != null) {
            viewModelScope.launch {
                val c = repo.getCharge(chargeId)
                if (c == null) {
                    message("Registro não encontrado")
                    _state.update { it.copy(loading = false, done = true) }
                } else {
                    _state.value = ChargeFormState(
                        id = c.id,
                        customerName = c.customerName,
                        phone = c.phone,
                        batteryDescription = c.batteryDescription,
                        receivedAt = c.receivedAt,
                        price = c.price,
                        paid = c.paid,
                        method = c.paymentMethod?.let { PaymentMethod.fromName(it) } ?: PaymentMethod.PIX,
                        loan = c.hasLoan,
                        lockedLoanModel = if (c.loanProductId != null) c.loanModel else null,
                        note = c.note.orEmpty(),
                    )
                }
            }
        }
    }

    fun update(transform: (ChargeFormState) -> ChargeFormState) = _state.update(transform)

    fun save() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                if (s.id == null) {
                    repo.createCharge(
                        s.customerName, s.phone, s.batteryDescription, s.receivedAt, s.price, s.paid,
                        s.method, s.loan, s.note,
                    )
                    message("Bateria recebida para carga")
                } else {
                    repo.updateCharge(
                        s.id, s.customerName, s.phone, s.batteryDescription, s.receivedAt, s.price, s.paid, s.method, s.note,
                        loaned = s.loan,
                    )
                    message("Registro atualizado")
                }
                _state.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                message(errorMessage(e))
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

class ChargeDetailViewModel(private val repo: StoreRepository, private val chargeId: Long) : MessageViewModel() {
    val charge: StateFlow<Loaded<ChargeService?>?> = repo.observeCharge(chargeId)
        .map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    fun markPaid(method: PaymentMethod) = act("Pagamento registrado") { repo.markChargePaid(chargeId, method) }
    fun markReady() = act("Marcada como pronta") { repo.markChargeReady(chargeId) }
    fun deliver(paidNow: PaymentMethod?) = act("Entregue ao cliente") { repo.deliverCharge(chargeId, paidNow) }
    fun delete(onDeleted: () -> Unit) = act("Registro excluído", onDeleted) { repo.deleteCharge(chargeId) }
}
