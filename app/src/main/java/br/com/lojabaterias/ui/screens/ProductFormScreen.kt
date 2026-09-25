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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import br.com.lojabaterias.ui.components.IntField
import br.com.lojabaterias.ui.components.MoneyField
import br.com.lojabaterias.ui.components.SectionTitle
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
        title = if (productId == null) "Cadastrar bateria" else "Editar bateria",
        onBack = onBack,
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = s.model,
                onValueChange = vm::setModel,
                label = { Text("Modelo (ex.: M60GD)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            IntField(
                value = s.amperage,
                onValueChange = vm::setAmperage,
                label = "Amperagem (Ah)",
                supportingText = "Usada para sugerir a sucata e o valor cobrado quando o cliente não deixa sucata",
            )

            SectionTitle("Custo e preços")
            MoneyField(value = s.cost, onValueChange = { v -> vm.update { it.copy(cost = v) } }, label = "Custo de compra")
            MoneyField(
                value = s.pricePix,
                onValueChange = { v -> vm.update { it.copy(pricePix = v) } },
                label = "Preço PIX (também usado no dinheiro)",
                supportingText = if (s.pricePix > 0) "Lucro no PIX: ${Money.format(s.pricePix - s.cost)}" else null,
            )
            MoneyField(
                value = s.priceDebit,
                onValueChange = { v -> vm.update { it.copy(priceDebit = v) } },
                label = "Preço débito",
            )
            MoneyField(
                value = s.priceCredit,
                onValueChange = { v -> vm.update { it.copy(priceCredit = v) } },
                label = "Preço crédito",
            )

            SectionTitle("Estoque")
            if (!s.isEdit) {
                IntField(
                    value = s.stock,
                    onValueChange = { v -> vm.update { it.copy(stock = v) } },
                    label = "Quantidade inicial em estoque",
                )
            } else {
                Text(
                    "Estoque atual: ${s.stock}. Para alterar, use \"Entrada\" ou \"Ajustar estoque\" na tela da bateria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IntField(
                value = s.minStock,
                onValueChange = { v -> vm.update { it.copy(minStock = v) } },
                label = "Alerta de estoque baixo (até)",
                imeAction = ImeAction.Done,
                supportingText = "Destaca o modelo quando o estoque chegar a essa quantidade",
            )

            if (s.isEdit) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor()),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Excluir bateria") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Excluir ${s.model}?",
            text = "A bateria e seu histórico de movimentações serão removidos. As vendas já registradas continuam nos relatórios.",
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
