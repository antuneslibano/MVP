package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.SaleWithItems
import br.com.lojabaterias.data.StoreRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** null = carregando; Loaded(null) = não encontrada. */
data class Loaded<T>(val value: T)

class SaleDetailViewModel(private val repo: StoreRepository, private val saleId: Long) : MessageViewModel() {

    val sale: StateFlow<Loaded<SaleWithItems?>?> = repo.observeSale(saleId)
        .map { Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cancel() {
        viewModelScope.launch {
            try {
                repo.cancelSale(saleId)
                message("Venda cancelada e estoque devolvido")
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }
}
