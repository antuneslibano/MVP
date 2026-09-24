package br.com.lojabaterias.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(private val container: AppContainer) : MessageViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun export(uri: Uri) = runTask("Backup salvo com sucesso") {
        val resolver = container.app.contentResolver
        val out = resolver.openOutputStream(uri, "wt") ?: error("Não foi possível abrir o arquivo")
        out.use { container.backupManager.export(it) }
    }

    fun import(uri: Uri) = runTask(null) {
        val resolver = container.app.contentResolver
        val input = resolver.openInputStream(uri) ?: error("Não foi possível abrir o arquivo")
        val (products, sales) = input.use { container.backupManager.import(it) }
        message("Backup restaurado: $products baterias e $sales vendas")
    }

    /** Sincroniza agora (botão). */
    fun syncNow() = runTask(null) {
        val ok = container.syncManager.syncNow()
        message(if (ok) "Sincronizado com a nuvem" else "Não foi possível sincronizar agora")
    }

    /** Apaga os dados deste celular e baixa tudo da nuvem. */
    fun wipeAndDownload() = runTask(null) {
        val ok = container.syncManager.wipeLocalAndDownload()
        message(if (ok) "Dados baixados da nuvem" else "Dados locais apagados. Aguardando conexão para baixar da nuvem.")
    }

    private fun runTask(successMessage: String?, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                successMessage?.let { message(it) }
            } catch (e: Exception) {
                message(errorMessage(e))
            } finally {
                _busy.value = false
            }
        }
    }
}
