package br.com.lojabaterias.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.lojabaterias.AppContainer
import br.com.lojabaterias.LojaApp
import br.com.lojabaterias.data.BusinessException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Cria um ViewModel com acesso ao [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as LojaApp).container
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}

/** Base com canal de mensagens curtas para a interface. */
abstract class MessageViewModel : ViewModel() {
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    protected fun message(text: String) {
        _messages.trySend(text)
    }

    protected fun errorMessage(e: Throwable): String =
        if (e is BusinessException) e.message ?: "Erro" else "Erro inesperado: ${e.message ?: e.javaClass.simpleName}"
}
