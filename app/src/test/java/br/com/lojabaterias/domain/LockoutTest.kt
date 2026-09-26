package br.com.lojabaterias.domain

import br.com.lojabaterias.security.Lockout
import org.junit.Assert.assertEquals
import org.junit.Test

class LockoutTest {
    @Test
    fun blocksAfterFiveFailuresDoublingUpToFifteenMinutes() {
        assertEquals(0L, Lockout.delayMillis(4))
        assertEquals(30_000L, Lockout.delayMillis(5))
        assertEquals(60_000L, Lockout.delayMillis(6))
        assertEquals(120_000L, Lockout.delayMillis(7))
        assertEquals(15 * 60_000L, Lockout.delayMillis(20))
        assertEquals(15 * 60_000L, Lockout.delayMillis(1_000))
    }
}
