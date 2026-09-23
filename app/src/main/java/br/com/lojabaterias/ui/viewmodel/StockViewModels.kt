package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.BusinessException
import br.com.lojabaterias.data.MovementWithModel
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StockState(
    val query: String = "",
    val products: List<Product> = emptyList(),
    val totalModels: Int = 0,
    val totalUnits: Int = 0,
    val lowCount: Int = 0,
    val zeroCount: Int = 0,
    val loading: Boolean = true,
)

class StockViewModel(repo: StoreRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<StockState> = combine(repo.observeProducts(), query) { all, q ->
        val term = q.trim()
        StockState(
            query = q,
            products = if (term.isEmpty()) all else all.filter { it.model.contains(term, ignoreCase = true) },
            totalModels = all.size,
            totalUnits = all.sumOf { it.stock },
            lowCount = all.count { it.isLowStock },
            zeroCount = all.count { it.isOutOfStock },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockState())

    fun setQuery(q: String) {
        query.value = q
    }
}

class ProductDetailViewModel(private val repo: StoreRepository, private val productId: Long) : MessageViewModel() {

    val product: StateFlow<Loaded<Product?>?> = repo.observeProduct(productId)
        .map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val movements: StateFlow<List<MovementWithModel>> = repo.observeMovements(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Retorna true pelo callback se deu certo (para fechar o diálogo). */
    fun addStock(quantity: Int, newCost: Long?, note: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.addStock(productId, quantity, newCost, note)
                message("Entrada de $quantity registrada")
                onSuccess()
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }

    fun adjustStock(newStock: Int, note: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.adjustStock(productId, newStock, note)
                message("Estoque ajustado para $newStock")
                onSuccess()
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }
}

data class ProductFormState(
    val id: Long = 0,
    val model: String = "",
    val cost: Long = 0,
    val pricePix: Long = 0,
    val priceDebit: Long = 0,
    val priceCredit: Long = 0,
    val stock: String = "",
    val minStock: String = Product.DEFAULT_MIN_STOCK.toString(),
    val createdAt: Long = 0,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    val deleted: Boolean = false,
) {
    val isEdit: Boolean get() = id != 0L
}

class ProductFormViewModel(private val repo: StoreRepository, productId: Long?) : MessageViewModel() {

    private val _state = MutableStateFlow(ProductFormState(loading = productId != null))
    val state: StateFlow<ProductFormState> = _state.asStateFlow()

    init {
        if (productId != null) {
            viewModelScope.launch {
                val p = repo.getProduct(productId)
                if (p == null) {
                    message("Produto não encontrado")
                    _state.update { it.copy(loading = false, done = true) }
                } else {
                    _state.value = ProductFormState(
                        id = p.id,
                        model = p.model,
                        cost = p.cost,
                        pricePix = p.pricePix,
                        priceDebit = p.priceDebit,
                        priceCredit = p.priceCredit,
                        stock = p.stock.toString(),
                        minStock = p.minStock.toString(),
                        createdAt = p.createdAt,
                    )
                }
            }
        }
    }

    fun update(transform: (ProductFormState) -> ProductFormState) = _state.update(transform)

    fun save() {
        val s = _state.value
        if (s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            try {
                val model = s.model.trim().uppercase()
                if (model.isEmpty()) throw BusinessException("Informe o modelo")
                if (s.pricePix <= 0 || s.priceDebit <= 0 || s.priceCredit <= 0) {
                    throw BusinessException("Informe os preços PIX, débito e crédito")
                }
                val product = Product(
                    id = s.id,
                    model = model,
                    cost = s.cost,
                    pricePix = s.pricePix,
                    priceDebit = s.priceDebit,
                    priceCredit = s.priceCredit,
                    stock = s.stock.toIntOrNull() ?: 0,
                    minStock = s.minStock.toIntOrNull() ?: Product.DEFAULT_MIN_STOCK,
                    createdAt = if (s.isEdit) s.createdAt else System.currentTimeMillis(),
                )
                repo.saveProduct(product)
                message(if (s.isEdit) "Bateria atualizada" else "Bateria cadastrada")
                _state.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                message(errorMessage(e))
                _state.update { it.copy(saving = false) }
            }
        }
    }

    fun delete() {
        val id = _state.value.id
        if (id == 0L) return
        viewModelScope.launch {
            try {
                repo.deleteProduct(id)
                message("Bateria excluída")
                _state.update { it.copy(done = true, deleted = true) }
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }
}

class MovementsViewModel(repo: StoreRepository) : ViewModel() {
    val movements: StateFlow<List<MovementWithModel>?> = repo.observeRecentMovements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
