package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.ui.components.AppCard
import br.com.lojabaterias.ui.components.EmptyState
import br.com.lojabaterias.ui.components.SearchField
import br.com.lojabaterias.ui.components.StockBadge
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.theme.warningColor
import br.com.lojabaterias.ui.viewmodel.StockViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(onOpenProduct: (Long) -> Unit, onNewProduct: () -> Unit, onMovements: () -> Unit) {
    val vm = appViewModel { StockViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estoque") },
                actions = { TextButton(onClick = onMovements) { Text("Movimentações") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewProduct,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Cadastrar bateria") },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            SearchField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = "Buscar modelo...",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val warning = warningColor()
            val danger = dangerColor()
            Text(
                buildAnnotatedString {
                    append("${state.totalModels} modelos • ${state.totalUnits} baterias")
                    if (state.lowCount > 0) withStyle(SpanStyle(color = warning)) { append(" • ${state.lowCount} baixo") }
                    if (state.zeroCount > 0) withStyle(SpanStyle(color = danger)) { append(" • ${state.zeroCount} zerado") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.loading && state.products.isEmpty()) {
                    item {
                        EmptyState(
                            if (state.query.isBlank()) "Nenhuma bateria cadastrada.\nToque em \"Cadastrar bateria\"."
                            else "Nenhum modelo encontrado para \"${state.query.trim()}\"."
                        )
                    }
                }
                items(state.products, key = { it.id }) { product ->
                    StockRow(product, onClick = { onOpenProduct(product.id) })
                }
            }
        }
    }
}

@Composable
private fun StockRow(product: Product, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    product.model + if (product.amperage > 0) "  •  ${product.amperage}Ah" else "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Custo ${Money.format(product.cost)} • PIX ${Money.format(product.pricePix)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Débito ${Money.format(product.priceDebit)} • Crédito ${Money.format(product.priceCredit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            StockBadge(product, large = true)
        }
    }
}
