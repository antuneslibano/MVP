package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.BusinessException
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.SaleCalculator
import br.com.lojabaterias.domain.SaleTotals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SaleFormState(
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    /** Produto selecionado (em edição pode ser null se o produto foi excluído). */
    val product: Product? = null,
    val model: String = "",
    val unitCost: Long = 0,
    /** Quantidade máxima permitida (estoque atual + quantidade já vendida, na edição). */
    val available: Int = 0,
    val method: PaymentMethod = PaymentMethod.PIX,
    val quantity: Int = 1,
    val unitPrice: Long = 0,
    val discount: Long = 0,
    val dateTime: Long = System.currentTimeMillis(),
    /** Se o usuário alterou a data/hora manualmente (senão usa "agora" ao confirmar). */
    val dateTimeEdited: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val hasSelection: Boolean get() = model.isNotEmpty()

    val totals: SaleTotals?
        get() = if (quantity > 0 && discount <= unitPrice * quantity) {
            SaleCalculator.compute(unitPrice, quantity, discount, unitCost)
        } else {
            null
        }

    val validationError: String?
        get() = SaleCalculator.validate(unitPrice, quantity, discount, available)
}

class SaleFormViewModel(
    private val repo: StoreRepository,
    private val saleId: Long?,
    initialProductId: Long?,
) : MessageViewModel() {

    private val _form = MutableStateFlow(SaleFormState(isEdit = saleId != null, loading = saleId != null))
    val form: StateFlow<SaleFormState> = _form.asStateFlow()

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    /** Resultados da busca: produtos com estoque primeiro. */
    val results: StateFlow<List<Product>> = combine(repo.observeProducts(), query) { list, q ->
        val term = q.trim()
        list.filter { term.isEmpty() || it.model.contains(term, ignoreCase = true) }
            .sortedWith(compareBy<Product> { it.isOutOfStock }.thenBy { it.model.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (saleId != null) {
            viewModelScope.launch { loadSale(saleId) }
        } else if (initialProductId != null) {
            viewModelScope.launch { repo.getProduct(initialProductId)?.let { selectProduct(it) } }
        }
    }

    private suspend fun loadSale(id: Long) {
        val sale = repo.getSale(id)
        if (sale == null) {
            message("Venda não encontrada")
            _form.update { it.copy(loading = false, done = true) }
            return
        }
        val item = sale.items.firstOrNull()
        val product = item?.let { repo.getProduct(it.productId) }
        _form.value = SaleFormState(
            isEdit = true,
            loading = false,
            product = product,
            model = item?.modelSnapshot ?: "",
            unitCost = item?.unitCost ?: 0,
            available = (item?.quantity ?: 0) + (product?.stock ?: 0),
            method = sale.sale.payment,
            quantity = item?.quantity ?: 1,
            unitPrice = item?.unitPrice ?: 0,
            discount = sale.sale.discount,
            dateTime = sale.sale.dateTime,
            dateTimeEdited = true,
        )
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun selectProduct(product: Product) {
        _form.update {
            it.copy(
                product = product,
                model = product.model,
                unitCost = product.cost,
                available = product.stock,
                quantity = if (product.stock > 0) 1 else 0,
                unitPrice = product.prices.priceFor(it.method),
                discount = 0,
            )
        }
    }

    fun clearProduct() {
        if (_form.value.isEdit) return
        _form.update { SaleFormState(method = it.method) }
    }

    /** Ao trocar a forma de pagamento, aplica automaticamente o preço de tabela correspondente. */
    fun selectMethod(method: PaymentMethod) {
        _form.update { s ->
            val price = s.product?.prices?.priceFor(method) ?: s.unitPrice
            s.copy(method = method, unitPrice = price)
        }
    }

    fun setQuantity(q: Int) = _form.update { it.copy(quantity = q) }
    fun setUnitPrice(v: Long) = _form.update { it.copy(unitPrice = v) }
    fun setDiscount(v: Long) = _form.update { it.copy(discount = v) }
    fun setDateTime(millis: Long) = _form.update { it.copy(dateTime = millis, dateTimeEdited = true) }

    fun confirm() {
        val s = _form.value
        if (s.saving || s.done) return
        s.validationError?.let { message(it); return }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val dateTime = if (s.dateTimeEdited) s.dateTime else System.currentTimeMillis()
                if (saleId != null) {
                    repo.updateSale(saleId, s.quantity, s.method, s.unitPrice, s.discount, dateTime)
                    message("Venda atualizada")
                } else {
                    val productId = s.product?.id ?: throw BusinessException("Selecione a bateria")
                    repo.registerSale(productId, s.quantity, s.method, s.unitPrice, s.discount, dateTime)
                    message("Venda registrada")
                }
                _form.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                message(errorMessage(e))
                _form.update { it.copy(saving = false) }
            }
        }
    }
}
