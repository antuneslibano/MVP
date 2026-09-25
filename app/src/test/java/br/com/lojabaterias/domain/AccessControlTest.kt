package br.com.lojabaterias.domain

import br.com.lojabaterias.security.AccessControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessControlTest {

    @Test
    fun wrongPinIsRejected() {
        assertFalse(AccessControl.checkPin(""))
        assertFalse(AccessControl.checkPin("0000"))
        assertFalse(AccessControl.checkPin("12345678"))
    }

    @Test
    fun correctAnswerRevealsTheRealPin() {
        val pin = AccessControl.recoverPin("Baltazar")
        assertNotNull(pin)
        assertEquals(6, pin!!.length)
        assertTrue(AccessControl.checkPin(pin))
        // variações de digitação aceitas
        assertEquals(pin, AccessControl.recoverPin("  baltazar "))
        assertEquals(pin, AccessControl.recoverPin("BALTAZAR"))
    }

    @Test
    fun wrongAnswerRevealsNothing() {
        assertNull(AccessControl.recoverPin("Piu-piu"))
        assertNull(AccessControl.recoverPin(""))
    }
}
