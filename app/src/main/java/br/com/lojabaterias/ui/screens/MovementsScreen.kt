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
                items(list, key = { it.movement.id }) { MovementRow(it, showModel = true) }
            }
        }
    }
}
