package br.com.lojabaterias.security

import android.content.Context

/**
 * Bloqueio contra tentativas repetidas de senha (e de resposta da pergunta secreta).
 * Depois de [FREE_ATTEMPTS] erros, cada erro bloqueia por 30 s, dobrando até 15 min. Sobrevive a fechar o app.
 */
class Lockout(context: Context, private val now: () -> Long = System::currentTimeMillis) {
    private val prefs = context.getSharedPreferences("lockout", Context.MODE_PRIVATE)

    /** Milissegundos que ainda faltam de bloqueio (0 = liberado). */
    fun remainingMillis(): Long = (prefs.getLong(KEY_UNTIL, 0) - now()).coerceAtLeast(0)

    fun registerFailure() {
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        prefs.edit()
            .putInt(KEY_FAILURES, failures)
            .putLong(KEY_UNTIL, now() + delayMillis(failures))
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val FREE_ATTEMPTS = 5
        private const val KEY_FAILURES = "failures"
        private const val KEY_UNTIL = "until"

        fun delayMillis(failures: Int): Long {
            if (failures < FREE_ATTEMPTS) return 0
            val step = (failures - FREE_ATTEMPTS).coerceAtMost(10)
            return minOf(30_000L shl step, 15 * 60_000L)
        }
    }
}
