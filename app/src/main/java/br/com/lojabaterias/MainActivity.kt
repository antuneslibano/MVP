package br.com.lojabaterias

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.lojabaterias.ui.LojaNavHost
import br.com.lojabaterias.ui.screens.LockScreen
import br.com.lojabaterias.ui.theme.LojaTheme

class MainActivity : ComponentActivity() {

    /** App bloqueado pela senha. Bloqueia ao abrir e ao voltar após [LOCK_AFTER_MS] em segundo plano. */
    private var locked by mutableStateOf(true)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ao girar a tela (ou recriar a Activity) mantém o estado de desbloqueio.
        locked = savedInstanceState?.getBoolean(KEY_LOCKED, true) ?: true
        stoppedAt = savedInstanceState?.getLong(KEY_STOPPED_AT, 0L) ?: 0L
        setContent {
            LojaTheme {
                Box {
                    // A navegação continua por baixo, preservando a tela onde o usuário estava.
                    LojaNavHost()
                    if (locked) LockScreen(onUnlock = { locked = false })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > LOCK_AFTER_MS) locked = true
        stoppedAt = 0L
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked)
        outState.putLong(KEY_STOPPED_AT, stoppedAt)
    }

    companion object {
        private const val KEY_LOCKED = "locked"
        private const val KEY_STOPPED_AT = "stopped_at"

        /** Tempo em segundo plano para pedir a senha de novo (permite escolher arquivos de backup/PDF sem bloquear). */
        private const val LOCK_AFTER_MS = 2 * 60 * 1000L
    }
}
