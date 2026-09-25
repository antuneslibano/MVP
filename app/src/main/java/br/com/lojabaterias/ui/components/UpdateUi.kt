package br.com.lojabaterias.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.LojaApp
import br.com.lojabaterias.update.AppUpdater
import br.com.lojabaterias.update.UpdateState
import br.com.lojabaterias.ui.theme.dangerColor

@Composable
private fun rememberUpdater(): AppUpdater = (LocalContext.current.applicationContext as LojaApp).container.updater

/** Aviso no Início quando há versão nova (some quando não há nada a mostrar). */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val updater = rememberUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Installing, is UpdateState.NeedsPermission ->
            AppCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(16.dp)) { UpdateContent(updater, s, compact = true) }
            }
        is UpdateState.Error -> if (s.info != null) {
            AppCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(16.dp)) { UpdateContent(updater, s, compact = true) }
            }
        }
        else -> Unit
    }
}

/** Seção completa (tela Backup e sincronização). */
@Composable
fun UpdateSection() {
    val updater = rememberUpdater()
    val state by updater.state.collectAsStateWithLifecycle()
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Atualizações do aplicativo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Versão instalada: ${updater.currentVersionName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UpdateContent(updater, state, compact = false)
        }
    }
}

@Composable
private fun UpdateContent(updater: AppUpdater, s: UpdateState, compact: Boolean) {
    when (s) {
        is UpdateState.Available -> {
            Text("Nova versão disponível: ${s.info.versionName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Os dados continuam no celular. Toque em atualizar e confirme a instalação.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { updater.downloadAndInstall(s.info) },
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Atualizar agora") }
        }
        is UpdateState.Downloading -> {
            Text("Baixando ${s.info.versionName}... ${s.progress}%", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { s.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        is UpdateState.Installing -> {
            Text(
                "Instalando ${s.info.versionName}... Se aparecer a pergunta do Android, toque em \"Atualizar\".",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        is UpdateState.NeedsPermission -> {
            Text(
                "Primeira atualização: o Android precisa que você permita que o Art das Baterias instale atualizações.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Toque em \"Permitir\", ative a opção e volte para o app. Depois toque em \"Atualizar agora\".",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = { updater.openInstallPermissionSettings() }, modifier = Modifier.fillMaxWidth()) {
                Text("Permitir")
            }
            OutlinedButton(onClick = { updater.downloadAndInstall(s.info) }, modifier = Modifier.fillMaxWidth()) {
                Text("Atualizar agora")
            }
        }
        is UpdateState.Error -> {
            Text(s.message, style = MaterialTheme.typography.bodyMedium, color = dangerColor())
            val info = s.info
            if (info != null) {
                Button(onClick = { updater.downloadAndInstall(info) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Tentar de novo")
                }
            } else if (!compact) {
                OutlinedButton(onClick = { updater.check(force = true) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Verificar de novo")
                }
            }
        }
        UpdateState.Checking -> Text("Verificando...", style = MaterialTheme.typography.bodyMedium)
        UpdateState.UpToDate, UpdateState.Idle -> if (!compact) {
            if (s == UpdateState.UpToDate) {
                Text("Você está na versão mais recente.", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { updater.check(force = true) }, modifier = Modifier.fillMaxWidth()) {
                Text("Verificar atualização")
            }
        }
    }
    if (compact && s is UpdateState.Error) {
        TextButton(onClick = { updater.dismissError() }) { Text("Fechar") }
    }
}
