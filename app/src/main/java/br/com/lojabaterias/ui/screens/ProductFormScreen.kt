package br.com.lojabaterias.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.lojabaterias.domain.Money
import br.com.lojabaterias.ui.components.ConfirmDialog
import br.com.lojabaterias.ui.components.EqualRow
import br.com.lojabaterias.ui.components.Gaps
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.SectionHeader
import br.com.lojabaterias.ui.components.ToastEffect
import br.com.lojabaterias.ui.theme.dangerColor
import br.com.lojabaterias.ui.viewmodel.ProductFormViewModel
import br.com.lojabaterias.ui.viewmodel.appViewModel

@Composable
fun ProductFormScreen(productId: Long?, onDone: () -> Unit, onDeleted: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "productform-$productId") { ProductFormViewModel(it.repository, productId) }
    val s by vm.state.collectAsStateWithLifecycle()
    ToastEffect(vm.messages)
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(s.done) {
        if (s.done) {
            if (s.deleted) onDeleted() else onDone()
        }
    }

    SubScreen(
        title = if (productId == null) "Nova bateria" else "Editar bateria",
        onBack = onBack,
        actions = {
            if (s.isEdit) {
                TextButton(onClick = { confirmDelete = true }) { Text("Excluir", color = dangerColor()) }
            }
        },
        bottomBar = {
            if (!s.loading) {
                BottomActionBar {
                    PrimaryActionButton(
                        text = if (s.saving) "Salvando..." else "Salvar",
                        onClick = vm::save,
                        enabled = !s.saving,
                    )
                }
            }
        },
    ) { inner ->
        if (s.loading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@SubScreen
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gaps.screen),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqualRow {
                OutlinedTextField(
                    value = s.model,
                    onValueChange = vm::setModel,
                    label = { Text("Modelo") },
                    placeholder = { Text("M60GD") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.weight(1.6f),
                )
                IntField(
                    value = s.amperage,
                    onValueChange = vm::setAmperage,
                    label = "Ah",
                    modifier = Modifier.weight(1f),
                )
            }

            SectionHeader("Preços")
            MoneyField(value = s.cost, onValueChange = { v -> vm.update { it.copy(cost = v) } }, label = "Custo de compra")
            MoneyField(
                value = s.pricePix,
                onValueChange = { v -> vm.update { it.copy(pricePix = v) } },
                label = "PIX / dinheiro",
                supportingText = if (s.pricePix > 0 && s.cost > 0) "Lucro ${Money.format(s.pricePix - s.cost)}" else null,
            )
            EqualRow {
                MoneyField(
                    value = s.priceDebit,
                    onValueChange = { v -> vm.update { it.copy(priceDebit = v) } },
                    label = "Débito",
                    modifier = Modifier.weight(1f),
                )
                MoneyField(
                    value = s.priceCredit,
                    onValueChange = { v -> vm.update { it.copy(priceCredit = v) } },
                    label = "Crédito",
                    modifier = Modifier.weight(1f),
                )
            }

            SectionHeader("Estoque")
            EqualRow {
                if (!s.isEdit) {
                    IntField(
                        value = s.stock,
                        onValueChange = { v -> vm.update { it.copy(stock = v) } },
                        label = "Quantidade inicial",
                        modifier = Modifier.weight(1f),
                    )
                }
                IntField(
                    value = s.minStock,
                    onValueChange = { v -> vm.update { it.copy(minStock = v) } },
                    label = "Alerta de baixo",
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir ${s.model}?",
            text = "As vendas já registradas continuam nos relatórios.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vm.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
