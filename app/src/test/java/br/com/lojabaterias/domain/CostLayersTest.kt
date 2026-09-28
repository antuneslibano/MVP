package br.com.lojabaterias.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CostLayersTest {

    @Test
    fun olderBatteriesLeaveFirst() {
        // 5 a R$ 672 (antigas) + 5 a R$ 650 (nota nova)
        val entries = listOf(5 to 67_200L, 5 to 65_000L)
        val layers = CostLayers.build(entries, stock = 10, fallback = 65_000)
        assertEquals(listOf(CostLayer(5, 67_200), CostLayer(5, 65_000)), layers)
        assertEquals(67_200L, CostLayers.unitCostOf(layers, 1, 0))
        assertEquals(5 * 67_200L + 2 * 65_000L, CostLayers.costOf(layers, 7, 0))

        // Depois de vender 6, sobram 4 (todas da nota nova)
        val after = CostLayers.build(entries, stock = 4, fallback = 65_000)
        assertEquals(listOf(CostLayer(4, 65_000)), after)
    }

    @Test
    fun uncoveredStockUsesFallback() {
        val layers = CostLayers.build(listOf(2 to 50_000L), stock = 5, fallback = 40_000)
        assertEquals(listOf(CostLayer(3, 40_000), CostLayer(2, 50_000)), layers)
        assertEquals(0, CostLayers.build(emptyList(), 0, 1).size)
        assertEquals(44_000L, CostLayers.unitCostOf(layers, 5, 0))
    }
}
