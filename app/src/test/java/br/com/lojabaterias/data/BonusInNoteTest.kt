package br.com.lojabaterias.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Bonificação dentro da própria nota de compra (ex.: 10 compradas + 1 de bonificação). */
class BonusInNoteTest {

    @Test
    fun bonusLineSharesWhatWasPaid() {
        val items = listOf(
            InvoiceItem.fromTotals("M60GD", 10, 380_000, 0, productId = 1),
            InvoiceItem.fromTotals("M60GD", 1, 38_000, 0, productId = 1, bonus = true),
        )
        // Pago: R$ 3.800 por 11 baterias = R$ 345,45 cada
        val costs = BonusCost.spreadNote(items, emptyList()).purchase
        assertEquals(listOf(34_545L, 34_545L), costs)
    }

    @Test
    fun bonusLineWithoutValueUsesSameModelPrice() {
        val items = listOf(
            InvoiceItem.fromTotals("M60GD", 10, 380_000, 0, productId = 1),
            InvoiceItem.fromTotals("M60GD", 2, 0, 0, productId = 1, bonus = true),
        )
        val costs = BonusCost.spreadNote(items, emptyList()).purchase
        assertEquals(listOf(31_667L, 31_667L), costs)
    }

    @Test
    fun bonusLineIsNotPaid() {
        val bonus = InvoiceItem.fromTotals("M60GD", 1, 38_000, 5_000, productId = 1, bonus = true)
        assertEquals(0L, bonus.subtotal)
        assertEquals(0L, bonus.unitCost)
    }

    @Test
    fun noteWithoutBonusKeepsItsCost() {
        val items = listOf(InvoiceItem.fromTotals("M60GD", 4, 100_000, 10_000, productId = 1))
        assertEquals(listOf(22_500L), BonusCost.spreadNote(items, emptyList()).purchase)
    }
}
