package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.SaleWithItems
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.data.WarrantyClaim
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

    /** Exclui a venda definitivamente. [onDeleted] é chamado para fechar a tela. */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.deleteSale(saleId)
                message("Venda excluída")
                onDeleted()
            } catch (e: Exception) {
                message(errorMessage(e))
            }
        }
    }

    /** Baterias extras (custo zero) que saíram nesta venda. */
    val extras: StateFlow<List<WarrantyClaim>> = repo.observeWarrantiesForSale(saleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
