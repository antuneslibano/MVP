package br.com.lojabaterias.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.sync.SyncState
import br.com.lojabaterias.domain.CardFees
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.Dot
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.Hint
import br.com.lojabaterias.ui.components.PanelCard
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.components.UpdateSection
import br.com.lojabaterias.ui.components.rememberSyncStatus
import br.com.lojabaterias.ui.components.syncColor
import br.com.lojabaterias.ui.components.syncLabel
import br.com.lojabaterias.ui.theme.dangerColor
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

    SubScreen(title = "Ajustes", onBack = onBack) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gaps.screen),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            SectionHeader("Nuvem")
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(syncColor(sync))
                    Text(syncLabel(sync), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 8.dp))
                }
                sync.lastSuccessAt?.let { Hint("Última: ${Periods.formatDateTime(it)}", Modifier.padding(top = 2.dp)) }
                sync.message?.let { Hint(it, Modifier.padding(top = 2.dp)) }
                FilledTonalButton(
                    onClick = { vm.syncNow() },
                    enabled = !busy && sync.state != SyncState.DISABLED,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp),
                ) { Text("Sincronizar agora") }
                if (sync.state == SyncState.CONFLICT) {
                    OutlinedButton(
                        onClick = { confirmWipe = true },
                        enabled = !busy,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor()),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Apagar deste celular e baixar da nuvem") }
                }
            }

            SectionHeader("Taxas das maquininhas")
            FeesCard(current = fees, onSave = { credit, debit -> vm.saveFees(credit, debit) })

            SectionHeader("Backup")
            PanelCard {
                EqualRow {
                    FilledTonalButton(
                        onClick = {
                            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
                            exportLauncher.launch("backup-art-das-baterias_$stamp.json")
                        },
                        enabled = !busy,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Salvar") }
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        enabled = !busy,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text("Restaurar") }
                }
            }

            SectionHeader("Aplicativo")
            UpdateSection()
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = "Apagar dados deste celular?",
            text = "Eles serão substituídos pelos dados da nuvem. Se houver algo só aqui, salve um backup antes.",
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
            text = "Todos os dados atuais, inclusive na nuvem e nos outros celulares, serão substituídos pelos do arquivo.",
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
    PanelCard {
        EqualRow {
            OutlinedTextField(
                value = credit,
                onValueChange = { credit = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                label = { Text("Crédito (%)") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = debit,
                onValueChange = { debit = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) },
                label = { Text("Débito (%)") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Hint("Valem para as próximas vendas.", Modifier.padding(top = 6.dp))
        OutlinedButton(
            onClick = { onSave(credit, debit) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) { Text("Salvar taxas") }
    }
}
