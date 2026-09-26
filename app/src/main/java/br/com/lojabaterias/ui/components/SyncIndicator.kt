package br.com.lojabaterias.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.LojaApp
import br.com.lojabaterias.data.sync.SyncState
import br.com.lojabaterias.data.sync.SyncStatus
import br.com.lojabaterias.domain.Periods
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.profitColor
import br.com.lojabaterias.ui.theme.warningColor

/** Texto curto do estado da sincronização. */
fun syncLabel(s: SyncStatus): String {
    val pending = if (s.pending > 0) " • ${s.pending} pendente(s)" else ""
    return when (s.state) {
        SyncState.DISABLED -> "Nuvem desativada"
        SyncState.WAITING_LOGIN -> "Conectando à nuvem..."
        SyncState.NEEDS_PASSWORD -> "Falta a senha da nuvem"
        SyncState.SYNCING -> "Sincronizando..."
        SyncState.OK -> "Sincronizado" + (s.lastSuccessAt?.let { " às ${Periods.formatTime(it)}" } ?: "") + pending
        SyncState.OFFLINE -> "Sem internet$pending"
        SyncState.ERROR -> "Erro na sincronização$pending"
        SyncState.CONFLICT -> "Conflito de dados — veja em Backup e sincronização"
    }
}

@Composable
fun syncColor(s: SyncStatus): Color = when (s.state) {
    SyncState.OK -> profitColor()
    SyncState.SYNCING, SyncState.WAITING_LOGIN, SyncState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
    SyncState.OFFLINE -> warningColor()
    SyncState.ERROR, SyncState.CONFLICT, SyncState.NEEDS_PASSWORD -> dangerColor()
}

@Composable
fun rememberSyncStatus(): SyncStatus {
    val manager = (LocalContext.current.applicationContext as LojaApp).container.syncManager
    val status by manager.status.collectAsStateWithLifecycle()
    return status
}

/** Linha pequena com bolinha colorida + estado da sincronização. */
@Composable
fun SyncIndicator(modifier: Modifier = Modifier) {
    val s = rememberSyncStatus()
    val color = syncColor(s)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            syncLabel(s),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
