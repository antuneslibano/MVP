package br.com.lojabaterias.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.viewmodel.BackupViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val vm = appViewModel { BackupViewModel(it) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var confirmRestore by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    SubScreen(title = "Backup dos dados", onBack = onBack) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Salvar backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Gera um arquivo com todas as baterias, vendas e movimentações. " +
                            "Guarde no Google Drive, WhatsApp ou e-mail para não perder os dados se trocar de celular.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    PrimaryActionButton(
                        text = "Salvar backup",
                        enabled = !busy,
                        onClick = {
                            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
                            exportLauncher.launch("backup-art-das-baterias_$stamp.json")
                        },
                    )
                }
            }
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restaurar backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Substitui TODOS os dados atuais pelos dados do arquivo de backup escolhido.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) { Text("Restaurar de um arquivo") }
                }
            }
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "Restaurar backup?",
            text = "Os dados atuais do aplicativo serão apagados e substituídos pelos do arquivo. Esta ação não pode ser desfeita.",
            confirmLabel = "Escolher arquivo",
            destructive = true,
            onConfirm = {
                confirmRestore = false
                importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
            },
            onDismiss = { confirmRestore = false },
        )
    }
}
