package br.com.lojabaterias.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.AppContainer
import br.com.lojabaterias.domain.CardFees
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

    /** A senha da nuvem já foi digitada neste celular? */
    val hasCloudPassword: Boolean get() = container.syncManager.hasCloudPassword

    /** Salva a senha da nuvem deste celular e tenta conectar. */
    fun saveCloudPassword(value: String, onDone: () -> Unit) {
        if (value.isBlank()) return message("Digite a senha da nuvem")
        runTask(null) {
            val ok = container.syncManager.setCloudPassword(value)
            message(if (ok) "Conectado à nuvem" else "Senha salva, mas não conectou. Veja a mensagem na tela.")
            onDone()
        }
    }

    /** Apaga os dados deste celular e baixa tudo da nuvem. */
    fun wipeAndDownload() = runTask(null) {
        val ok = container.syncManager.wipeLocalAndDownload()
        message(if (ok) "Dados baixados da nuvem" else "Dados locais apagados. Aguardando conexão para baixar da nuvem.")
    }

    /** Taxas atuais das maquininhas. */
    val fees = container.feeSettings.fees

    fun saveFees(creditText: String, debitText: String) {
        val credit = CardFees.parsePercent(creditText)
        val debit = CardFees.parsePercent(debitText)
        if (credit == null || debit == null) {
            message("Informe porcentagens válidas (ex.: 7 ou 2,5)")
            return
        }
        container.feeSettings.save(CardFees(credit, debit))
        message("Taxas salvas: crédito ${CardFees.formatPercent(credit)}, débito ${CardFees.formatPercent(debit)}")
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
