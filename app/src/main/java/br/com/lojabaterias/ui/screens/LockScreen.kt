package br.com.lojabaterias.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.lojabaterias.security.AccessControl
import br.com.lojabaterias.ui.components.AppIcons
import br.com.lojabaterias.ui.theme.dangerColor

private const val MAX_PIN = 12

/** Tela de senha exibida ao abrir o app (e ao voltar depois de um tempo em segundo plano). */
@Composable
fun LockScreen(onUnlock: (pin: String) -> Unit) {
    var recovering by rememberSaveable { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    // Com o app bloqueado, "voltar" apenas minimiza o app.
    BackHandler {
        if (recovering) recovering = false else activity?.moveTaskToBack(true)
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            if (recovering) {
                RecoverPin(onBack = { recovering = false })
            } else {
                PinEntry(onUnlock = onUnlock, onForgot = { recovering = true })
            }
        }
    }
}

@Composable
private fun Header(subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Battery, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Art das Baterias", style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PinEntry(onUnlock: (String) -> Unit, onForgot: () -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        if (AccessControl.checkPin(pin)) {
            val ok = pin
            pin = ""
            error = false
            onUnlock(ok)
        } else {
            error = true
            pin = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header("Digite a senha de acesso")
        Spacer(Modifier.height(24.dp))
        // Indicador dos dígitos digitados
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(20.dp)) {
            repeat(pin.length) {
                Box(
                    Modifier
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
        Text(
            if (error) "Senha incorreta" else " ",
            color = dangerColor(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 320.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        val isOk = key == "OK"
                        val onClick: () -> Unit = {
                            when (key) {
                                "⌫" -> pin = pin.dropLast(1)
                                "OK" -> submit()
                                else -> if (pin.length < MAX_PIN) {
                                    pin += key
                                    error = false
                                }
                            }
                            Unit
                        }
                        val mod = Modifier
                            .weight(1f)
                            .height(64.dp)
                        if (isOk) {
                            Button(onClick = onClick, enabled = pin.isNotEmpty(), modifier = mod) {
                                Text("OK", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            FilledTonalButton(onClick = onClick, modifier = mod) {
                                Text(key, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onForgot) { Text("Esqueci a senha") }
    }
}

@Composable
private fun RecoverPin(onBack: () -> Unit) {
    var answer by rememberSaveable { mutableStateOf("") }
    var revealed by rememberSaveable { mutableStateOf<String?>(null) }
    var wrong by rememberSaveable { mutableStateOf(false) }

    fun check() {
        val pin = AccessControl.recoverPin(answer)
        revealed = pin
        wrong = pin == null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header("Recuperar senha")
        Spacer(Modifier.height(24.dp))
        val pin = revealed
        if (pin != null) {
            Text("Sua senha de acesso é:", style = MaterialTheme.typography.bodyLarge)
            Text(
                pin,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Voltar e entrar") }
        } else {
            Text(
                AccessControl.SECURITY_QUESTION,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it.take(60); wrong = false },
                label = { Text("Resposta") },
                singleLine = true,
                isError = wrong,
                supportingText = if (wrong) {
                    { Text("Resposta incorreta") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { check() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { check() },
                enabled = answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Verificar resposta") }
            TextButton(onClick = onBack) { Text("Voltar") }
        }
    }
}
