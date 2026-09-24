package br.com.lojabaterias.data.sync

import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * Gera IDs únicos entre celulares (necessário para sincronizar sem colisões).
 * Formato: milissegundos × 2048 + aleatório (0..2047), sempre crescente neste aparelho.
 * O resultado fica abaixo de 2^53, seguro para JSON.
 */
object IdGenerator {
    private val random = SecureRandom()
    private val last = AtomicLong(0)

    fun next(): Long {
        val candidate = System.currentTimeMillis() * 2048 + random.nextInt(2048)
        return last.updateAndGet { prev -> if (candidate > prev) candidate else prev + 1 }
    }

    /** IDs criados antes da sincronização (autoincremento local: 1, 2, 3...). */
    fun isLegacy(id: Long): Boolean = id in 1 until 1_000_000_000L
}
