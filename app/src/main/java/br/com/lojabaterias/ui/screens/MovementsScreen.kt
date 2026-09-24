package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.lojabaterias.data.MovementType
import br.com.lojabaterias.data.MovementWithModel
import br.com.lojabaterias.ui.components.ToastEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.viewmodel.MovementsViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@Composable
fun MovementsScreen(onBack: () -> Unit) {
    val vm = appViewModel { MovementsViewModel(it.repository) }
    val movements by vm.movements.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var toDelete by remember { mutableStateOf<MovementWithModel?>(null) }

    SubScreen(title = "Movimentações de estoque", onBack = onBack) { inner ->
        val list = movements
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.padding(inner)) { EmptyState("Nenhuma movimentação registrada.") }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.movement.id }) { m ->
                    MovementRow(
                        m,
                        showModel = true,
                        onDelete = if (MovementType.isDeletable(m.movement.type)) ({ toDelete = m }) else null,
                    )
                }
            }
        }
    }

    toDelete?.let { m ->
        DeleteMovementDialog(m, onConfirm = { vm.deleteMovement(m.movement.id) { toDelete = null } }, onDismiss = { toDelete = null })
    }
}
