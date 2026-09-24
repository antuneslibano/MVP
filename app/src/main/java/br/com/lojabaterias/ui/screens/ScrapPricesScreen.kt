package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.ScrapPrice
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.viewmodel.ScrapPricesViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@Composable
fun ScrapPricesScreen(onBack: () -> Unit) {
    val vm = appViewModel { ScrapPricesViewModel(it.repository) }
    val prices by vm.prices.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var editing by remember { mutableStateOf<ScrapPrice?>(null) }
    var creating by remember { mutableStateOf(false) }

    SubScreen(
        title = "Tabela de sucatas",
        onBack = onBack,
        bottomBar = { BottomActionBar { PrimaryActionButton(text = "Adicionar amperagem", onClick = { creating = true }) } },
    ) { inner ->
        val list = prices
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@SubScreen
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Valor da sucata por amperagem. É cobrado automaticamente na venda quando o cliente " +
                        "não deixa a sucata (pode ser alterado na hora) e usado para estimar o valor do estoque de sucatas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (list.isEmpty()) {
                item { EmptyState("Nenhuma amperagem cadastrada.\nToque em \"Adicionar amperagem\".") }
            }
            items(list, key = { it.id }) { price ->
                AppCard(onClick = { editing = price }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${price.amperage}Ah",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(Money.format(price.value), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (creating) {
        ScrapPriceDialog(
            price = null,
            onSave = { a, v -> vm.save(0, a, v) { creating = false } },
            onDelete = null,
            onDismiss = { creating = false },
        )
    }
    editing?.let { p ->
        ScrapPriceDialog(
            price = p,
            onSave = { a, v -> vm.save(p.id, a, v) { editing = null } },
            onDelete = { vm.delete(p.id) { editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ScrapPriceDialog(
    price: ScrapPrice?,
    onSave: (Int, Long) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var amperage by remember { mutableStateOf(price?.amperage?.toString() ?: "") }
    var value by remember { mutableLongStateOf(price?.value ?: 0L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (price == null) "Nova amperagem" else "Editar ${price.amperage}Ah") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntField(value = amperage, onValueChange = { amperage = it.take(3) }, label = "Amperagem (Ah)")
                MoneyField(value = value, onValueChange = { value = it }, label = "Valor da sucata")
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Remover da tabela", color = dangerColor()) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(amperage.toIntOrNull() ?: 0, value) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
