package br.com.lojabaterias.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {

    @Test
    fun pluralizes() {
        assertEquals("1 venda", Labels.sales(1))
        assertEquals("4 vendas", Labels.sales(4))
        assertEquals("0 baterias", Labels.batteries(0))
        assertEquals("1 bateria", Labels.batteries(1))
        assertEquals("4 vendas • 5 baterias", Labels.salesAndBatteries(4, 5))
    }
}
