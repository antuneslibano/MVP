package br.com.lojabaterias.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.sync.SyncState
import br.com.lojabaterias.domain.CardFees
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.UpdateSection
import br.com.lojabaterias.ui.components.rememberSyncStatus
import br.com.lojabaterias.ui.components.syncColor
import br.com.lojabaterias.ui.components.syncLabel
import br.com.lojabaterias.ui.theme.dangerColor
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
    var confirmWipe by remember { mutableStateOf(false) }
    val sync = rememberSyncStatus()
    val fees by vm.fees.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    SubScreen(title = "Backup e sincronização", onBack = onBack) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            UpdateSection()
            FeesCard(
                current = fees,
                onSave = { credit, debit -> vm.saveFees(credit, debit) },
            )
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sincronização com a nuvem", style = MaterialTheme.typography.titleMedium)
                    CloudPasswordField(
                        needsPassword = sync.state == SyncState.NEEDS_PASSWORD || !vm.hasCloudPassword ||
                            sync.message.orEmpty().contains("Senha da nuvem incorreta"),
                        busy = busy,
                        onSave = { value, done -> vm.saveCloudPassword(value, done) },
                    )
                    Text(syncLabel(sync), style = MaterialTheme.typography.bodyLarge, color = syncColor(sync))
                    sync.lastSuccessAt?.let {
                        Text(
                            "Última sincronização: ${Periods.formatDateTime(it)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    sync.message?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Os celulares sincronizam sozinhos a cada 30 segundos e logo após cada venda ou alteração. " +
                            "Sem internet, o app continua funcionando e envia tudo quando a conexão voltar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryActionButton(
                        text = "Sincronizar agora",
                        enabled = !busy && sync.state != SyncState.DISABLED,
                        onClick = { vm.syncNow() },
                    )
                    if (sync.state == SyncState.CONFLICT) {
                        OutlinedButton(
                            onClick = { confirmWipe = true },
                            enabled = !busy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor()),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) { Text("Apagar dados deste celular e baixar da nuvem") }
                    }
                }
            }
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
                            .heightIn(min = 52.dp),
                    ) { Text("Restaurar de um arquivo") }
                }
            }
        }
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = "Apagar dados deste celular?",
            text = "Todos os dados deste celular serão apagados e substituídos pelos dados da nuvem " +
                "(os mesmos dos outros celulares da loja). Se este celular tiver dados importantes que não estão " +
                "na nuvem, salve um backup antes.",
            confirmLabel = "Apagar e baixar",
            destructive = true,
            onConfirm = {
                confirmWipe = false
                vm.wipeAndDownload()
            },
            onDismiss = { confirmWipe = false },
        )
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = "Restaurar backup?",
            text = "Os dados atuais do aplicativo serão apagados e substituídos pelos do arquivo — " +
                "inclusive na nuvem e nos outros celulares da loja. Esta ação não pode ser desfeita.",
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

@Composable
private fun FeesCard(current: CardFees, onSave: (String, String) -> Unit) {
    var credit by remember(current) { mutableStateOf(CardFees.formatPercent(current.creditBps).removeSuffix("%")) }
    var debit by remember(current) { mutableStateOf(CardFees.formatPercent(current.debitBps).removeSuffix("%")) }
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Taxas das maquininhas", style = MaterialTheme.typography.titleMedium)
            Text(
                "Descontadas do lucro de cada venda no crédito e no débito. Valem para as próximas vendas " +
                    "(as vendas antigas já receberam as taxas padrão de 7% e 2%).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = credit,
                    onValueChange = { credit = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                    label = { Text("Crédito (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = debit,
                    onValueChange = { debit = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                    label = { Text("Débito (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(onClick = { onSave(credit, debit) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar taxas") }
        }
    }
}

/** Campo da senha da nuvem: aparece aberto quando falta a senha; senão, fica um botão para trocar. */
@Composable
private fun CloudPasswordField(needsPassword: Boolean, busy: Boolean, onSave: (String, () -> Unit) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var value by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    if (!needsPassword && !editing) {
        TextButton(onClick = { editing = true }) { Text("Trocar a senha da nuvem deste celular") }
        return
    }
    Text(
        "Digite a senha da nuvem (a mesma em todos os celulares da loja). Ela fica guardada só neste celular.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = value,
        onValueChange = { value = it.take(100) },
        label = { Text("Senha da nuvem") },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Mostrar") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    PrimaryActionButton(
        text = "Salvar senha e conectar",
        enabled = !busy && value.isNotBlank(),
        onClick = { onSave(value) { value = ""; editing = false } },
    )
}
