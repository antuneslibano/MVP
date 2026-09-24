package br.com.lojabaterias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarrantyCodeTest {
    @Test
    fun codeIsStableAndSearchable() {
        val id = 3_690_000_000_123_456L
        val code = WarrantyCode.of(id)
        assertTrue(code.startsWith("G-"))
        assertEquals(code, WarrantyCode.of(id))
        assertTrue(WarrantyCode.matches(id, code))
        assertTrue(WarrantyCode.matches(id, code.lowercase().removePrefix("g-")))
        assertFalse(WarrantyCode.matches(id, "ZZZZZZZZ"))
        assertEquals("G-1", WarrantyCode.of(1))
    }
}
