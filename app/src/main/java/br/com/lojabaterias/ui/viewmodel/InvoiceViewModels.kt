package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.BillDraft
import br.com.lojabaterias.data.Invoice
import br.com.lojabaterias.data.InvoiceBill
import br.com.lojabaterias.data.InvoiceItem
import br.com.lojabaterias.data.InvoiceWithBills
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.data.SupplierDebt
import br.com.lojabaterias.data.items
import br.com.lojabaterias.domain.Periods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class InvoiceTab(val label: String) { BILLS("Boletos"), NOTES("Notas") }

/** Boleto com a nota a que pertence (ex.: parcela 2 de 3 da nota 1234). */
data class BillRow(val bill: InvoiceBill, val invoice: Invoice, val index: Int, val count: Int)

data class InvoicesState(
    val tab: InvoiceTab = InvoiceTab.BILLS,
    val showPaid: Boolean = false,
    val bills: List<BillRow> = emptyList(),
    val invoices: List<InvoiceWithBills> = emptyList(),
    val debt: SupplierDebt = SupplierDebt(),
    val today: LocalDate = LocalDate.now(),
    val waitingCount: Int = 0,
    val loading: Boolean = true,
)

class InvoicesViewModel(private val repo: StoreRepository) : MessageViewModel() {
    private val tab = MutableStateFlow(InvoiceTab.BILLS)
    private val showPaid = MutableStateFlow(false)

    val state: StateFlow<InvoicesState> = combine(repo.observeInvoices(), tab, showPaid, currentDateFlow()) { all, t, paid, today ->
        val rows = all.flatMap { inv ->
            val sorted = inv.sortedBills
            sorted.mapIndexed { i, b -> BillRow(b, inv.invoice, i + 1, sorted.size) }
        }
        InvoicesState(
            tab = t,
            showPaid = paid,
            bills = if (paid) {
                rows.filter { it.bill.isPaid }.sortedByDescending { it.bill.paidAt }
            } else {
                rows.filter { !it.bill.isPaid }.sortedBy { it.bill.dueDate }
            },
            invoices = all,
            debt = SupplierDebt.from(all.flatMap { it.bills }, today),
            today = today,
            waitingCount = all.count { !it.invoice.isReceived },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvoicesState())

    fun setTab(t: InvoiceTab) { tab.value = t }
    fun setShowPaid(v: Boolean) { showPaid.value = v }

    fun setPaid(billId: Long, paid: Boolean) {
        viewModelScope.launch {
            try {
                repo.setInvoiceBillPaid(billId, paid)
                message(if (paid) "Boleto marcado como pago" else "Pagamento desfeito")
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }
}

/** Bateria no formulário da nota. */
data class ItemDraft(
    val key: Long,
    val productId: Long?,
    val model: String,
    val quantity: Int,
    /** Subtotal da linha na nota, sem desconto. */
    val grossTotal: Long,
    /** Desconto da linha inteira. */
    val discountTotal: Long = 0,
) {
    /** Subtotal com desconto. */
    val subtotal: Long get() = (grossTotal - discountTotal).coerceAtLeast(0)
    /** Custo de cada bateria: subtotal com desconto ÷ quantidade. */
    val unitCost: Long get() = if (quantity > 0) (subtotal + quantity / 2) / quantity else 0
}

/** Boleto no formulário da nota. */
data class BillDraftUi(val key: Long, val id: Long?, val dueDate: LocalDate, val amount: Long, val paidAt: Long?) {
    val isPaid: Boolean get() = paidAt != null
}

data class InvoiceFormState(
    val id: Long? = null,
    val number: String = "",
    val supplier: String = "",
    val issueDate: LocalDate = LocalDate.now(),
    val items: List<ItemDraft> = emptyList(),
    /** Frete, impostos e outros valores da nota além das baterias. */
    val extras: Long = 0,
    val bills: List<BillDraftUi> = emptyList(),
    val received: Boolean = false,
    val note: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val isEdit: Boolean get() = id != null
    val itemsTotal: Long get() = items.sumOf { it.subtotal }
    val units: Int get() = items.sumOf { it.quantity }
    val total: Long get() = itemsTotal + extras
    val billsTotal: Long get() = bills.sumOf { it.amount }
    /** Quanto falta (positivo) ou sobra (negativo) nos boletos para bater com o total. */
    val billsDifference: Long get() = total - billsTotal
}

class InvoiceFormViewModel(private val repo: StoreRepository, private val invoiceId: Long?) : MessageViewModel() {
    private val _state = MutableStateFlow(InvoiceFormState(id = invoiceId, loading = invoiceId != null))
    val state: StateFlow<InvoiceFormState> = _state.asStateFlow()
    private var nextKey = 1L
    private fun key() = nextKey++

    val products: StateFlow<List<Product>> = repo.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val all = repo.observeInvoices().first()
            if (invoiceId == null) {
                // Sugere o mesmo fornecedor da última nota
                val last = all.maxByOrNull { it.invoice.issueDate }?.invoice?.supplier.orEmpty()
                _state.update { it.copy(supplier = last) }
                return@launch
            }
            val inv = all.firstOrNull { it.invoice.id == invoiceId }
            if (inv == null) {
                message("Nota não encontrada")
                _state.update { it.copy(loading = false, done = true) }
                return@launch
            }
            val i = inv.invoice
            val items = i.items.map { ItemDraft(key(), it.productId, it.model, it.quantity, it.grossTotal ?: it.subtotal, it.discountTotal) }
            _state.value = InvoiceFormState(
                id = i.id,
                number = i.number,
                supplier = i.supplier,
                issueDate = Periods.toLocalDateTime(i.issueDate).toLocalDate(),
                items = items,
                extras = i.total - items.sumOf { it.subtotal },
                bills = inv.sortedBills.map {
                    BillDraftUi(key(), it.id, Periods.toLocalDateTime(it.dueDate).toLocalDate(), it.amount, it.paidAt)
                },
                received = i.isReceived,
                note = i.note.orEmpty(),
            )
        }
    }

    fun update(transform: (InvoiceFormState) -> InvoiceFormState) = _state.update(transform)

    fun addItem(p: Product) = _state.update { s ->
        val existing = s.items.firstOrNull { it.productId == p.id }
        if (existing != null) {
            s.copy(items = s.items.map { if (it.key == existing.key) it.copy(quantity = it.quantity + 1) else it })
        } else {
            s.copy(items = s.items + ItemDraft(key(), p.id, p.model, 1, 0))
        }
    }

    fun updateItem(key: Long, transform: (ItemDraft) -> ItemDraft) =
        _state.update { s -> s.copy(items = s.items.map { if (it.key == key) transform(it) else it }) }

    fun removeItem(key: Long) = _state.update { s -> s.copy(items = s.items.filter { it.key != key }) }

    /**
     * Monta os boletos: [count] parcelas iguais, a primeira em [first] e as outras a cada [intervalDays] dias.
     * Boletos já pagos são mantidos; as novas parcelas dividem o que falta.
     */
    fun generateBills(count: Int, first: LocalDate, intervalDays: Int) = _state.update { s ->
        val paid = s.bills.filter { it.isPaid }
        val remaining = (s.total - paid.sumOf { it.amount }).coerceAtLeast(0)
        val parts = SupplierDebt.split(remaining, count)
        s.copy(bills = paid + parts.mapIndexed { i, amount ->
            BillDraftUi(key(), null, first.plusDays(intervalDays.toLong() * i), amount, null)
        })
    }

    fun updateBill(key: Long, transform: (BillDraftUi) -> BillDraftUi) =
        _state.update { s -> s.copy(bills = s.bills.map { if (it.key == key) transform(it) else it }) }

    fun addBill() = _state.update { s ->
        val last = s.bills.maxByOrNull { it.dueDate }?.dueDate ?: s.issueDate
        s.copy(bills = s.bills + BillDraftUi(key(), null, last.plusDays(30), s.billsDifference.coerceAtLeast(0), null))
    }

    fun removeBill(key: Long) = _state.update { s -> s.copy(bills = s.bills.filter { it.key != key }) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repo.saveInvoice(
                    id = s.id,
                    number = s.number,
                    supplier = s.supplier,
                    issueDate = Periods.toMillis(s.issueDate),
                    items = s.items.map { InvoiceItem.fromTotals(it.model, it.quantity, it.grossTotal, it.discountTotal, it.productId) },
                    total = s.total,
                    bills = s.bills.sortedBy { it.dueDate }.map { BillDraft(it.id, Periods.toMillis(it.dueDate), it.amount, it.paidAt) },
                    alreadyReceived = s.received,
                    note = s.note,
                )
                message(if (s.isEdit) "Nota atualizada" else "Nota lançada")
                _state.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                message(errorMessage(e))
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

class InvoiceDetailViewModel(private val repo: StoreRepository, private val invoiceId: Long) : MessageViewModel() {
    val invoice: StateFlow<Loaded<InvoiceWithBills?>?> = repo.observeInvoice(invoiceId)
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

    fun markReceived(note: String, quantities: List<Int>, addToStock: Boolean) =
        act(if (addToStock) "Baterias recebidas e lançadas no estoque" else "Baterias recebidas") {
            repo.markInvoiceReceived(invoiceId, note, quantities, addToStock)
        }
    fun markWaiting() = act("Voltou para \"aguardando baterias\"") { repo.markInvoiceWaiting(invoiceId) }
    fun setPaid(billId: Long, paid: Boolean) =
        act(if (paid) "Boleto marcado como pago" else "Pagamento desfeito") { repo.setInvoiceBillPaid(billId, paid) }
    fun delete(onDeleted: () -> Unit) = act("Nota excluída", onDeleted) { repo.deleteInvoice(invoiceId) }
}
