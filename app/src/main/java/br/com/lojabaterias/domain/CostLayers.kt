package br.com.lojabaterias.domain

/** Lote do estoque: [quantity] baterias que custaram [unitCost] cada. */
data class CostLayer(val quantity: Int, val unitCost: Long) {
    val total: Long get() = unitCost * quantity
}

/**
 * Custo por lotes, "primeiro que entra, primeiro que sai":
 * as baterias que continuam no estoque são as das entradas mais recentes, e cada venda
 * leva as mais antigas. Ex.: 5 a R$ 672 + 5 a R$ 650 → as 5 primeiras vendas custam R$ 672.
 */
object CostLayers {

    /**
     * Monta os lotes (do mais antigo para o mais novo) das [stock] baterias em estoque.
     * [entries] são as entradas com custo (quantidade, custo unitário), da mais antiga para a mais nova.
     * Se as entradas não cobrirem o estoque todo, o que faltar vira um lote mais antigo com o custo [fallback].
     */
    fun build(entries: List<Pair<Int, Long>>, stock: Int, fallback: Long): List<CostLayer> {
        if (stock <= 0) return emptyList()
        var remaining = stock
        val newestFirst = mutableListOf<CostLayer>()
        for ((qty, cost) in entries.asReversed()) {
            if (remaining <= 0) break
            if (qty <= 0) continue
            val take = minOf(qty, remaining)
            newestFirst += CostLayer(take, cost)
            remaining -= take
        }
        if (remaining > 0) newestFirst += CostLayer(remaining, fallback)
        return merge(newestFirst.asReversed())
    }

    /** Custo total de [quantity] baterias tiradas dos lotes mais antigos. */
    fun costOf(layers: List<CostLayer>, quantity: Int, fallback: Long): Long {
        var remaining = quantity
        var total = 0L
        for (layer in layers) {
            if (remaining <= 0) break
            val take = minOf(layer.quantity, remaining)
            total += take * layer.unitCost
            remaining -= take
        }
        if (remaining > 0) total += remaining * fallback
        return total
    }

    /** Custo médio de cada uma das [quantity] próximas baterias a sair (arredondado). */
    fun unitCostOf(layers: List<CostLayer>, quantity: Int, fallback: Long): Long {
        if (quantity <= 0) return layers.firstOrNull()?.unitCost ?: fallback
        return (costOf(layers, quantity, fallback) + quantity / 2) / quantity
    }

    /** Junta lotes vizinhos com o mesmo custo. */
    private fun merge(layers: List<CostLayer>): List<CostLayer> {
        val out = mutableListOf<CostLayer>()
        for (l in layers) {
            val last = out.lastOrNull()
            if (last != null && last.unitCost == l.unitCost) out[out.lastIndex] = last.copy(quantity = last.quantity + l.quantity)
            else out += l
        }
        return out
    }
}
