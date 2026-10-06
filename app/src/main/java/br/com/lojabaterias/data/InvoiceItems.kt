package br.com.lojabaterias.data

import org.json.JSONArray
import org.json.JSONObject

/** Converte as baterias da nota fiscal para texto (guardado na coluna "items") e de volta. */
object InvoiceItems {
    fun encode(items: List<InvoiceItem>): String = JSONArray().apply {
        items.forEach { i ->
            put(
                JSONObject()
                    .put("model", i.model)
                    .put("quantity", i.quantity)
                    .put("unit_cost", i.unitCost)
                    .put("product_id", i.productId ?: JSONObject.NULL)
                    .put("received", i.received ?: JSONObject.NULL)
                    .put("movement_id", i.movementId ?: JSONObject.NULL)
                    .put("gross_total", i.grossTotal ?: JSONObject.NULL)
                    .put("discount_total", i.discountTotal)
                    .put("bonus_for", i.bonusFor ?: JSONObject.NULL)
            )
        }
    }.toString()

    fun decode(text: String): List<InvoiceItem> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).map { k ->
            val o = arr.getJSONObject(k)
            InvoiceItem(
                model = o.optString("model"),
                quantity = o.optInt("quantity"),
                unitCost = o.optLong("unit_cost"),
                productId = if (o.isNull("product_id")) null else o.optLong("product_id"),
                received = if (o.isNull("received")) null else o.optInt("received"),
                movementId = if (o.isNull("movement_id")) null else o.optLong("movement_id"),
                grossTotal = when {
                    !o.isNull("gross_total") && o.has("gross_total") -> o.optLong("gross_total")
                    // Versão 1.0.39–1.0.41: valor e desconto por bateria
                    o.has("list_price") && !o.isNull("list_price") -> o.optLong("list_price") * o.optInt("quantity")
                    else -> null
                },
                discountTotal = if (o.has("discount_total")) o.optLong("discount_total", 0)
                else o.optLong("unit_discount", 0) * o.optInt("quantity"),
                bonusFor = if (o.isNull("bonus_for") || !o.has("bonus_for")) null else o.optLong("bonus_for"),
            )
        }
    }.getOrDefault(emptyList())
}

val Invoice.items: List<InvoiceItem> get() = InvoiceItems.decode(itemsJson)

/** Nota de compra cujo custo esta bonificação baixa (null = não é bonificação ligada). */
val Invoice.bonusFor: Long? get() = items.firstNotNullOfOrNull { it.bonusFor }

/**
 * Bonificação baixa o custo da compra: o que foi pago na nota de compra é dividido por todas as
 * baterias, as compradas e as ganhas. Ex.: 10 × R$ 380 + 1 de bonificação → 11 × R$ 345,45
 * (R$ 3.800 ÷ 11). Com modelos diferentes, cada bateria fica com a mesma proporção do seu valor na nota.
 */
object BonusCost {
    /** Custo de cada bateria, linha a linha: [purchase] da nota de compra e [bonuses] de cada bonificação. */
    data class Result(val purchase: List<Long>, val bonuses: List<List<Long>>)

    /** Quantas baterias da bonificação contam: as que chegaram (ou todas, se ainda não chegou). */
    private fun units(line: InvoiceItem): Int = line.received ?: line.quantity

    fun spread(purchase: List<InvoiceItem>, bonuses: List<List<InvoiceItem>>): Result {
        val paid = purchase.sumOf { it.subtotal }.toDouble()
        val boughtUnits = purchase.sumOf { it.quantity }
        val average = if (boughtUnits > 0) paid / boughtUnits else 0.0
        // Valor de cada bateria ganha: o da própria nota; se veio sem valor, o da mesma bateria na compra
        fun value(line: InvoiceItem): Double = when {
            line.quantity > 0 && line.subtotal > 0 -> line.subtotal.toDouble() / line.quantity
            else -> purchase.filter { it.productId != null && it.productId == line.productId && it.quantity > 0 }
                .takeIf { it.isNotEmpty() }?.let { same -> same.sumOf { it.subtotal }.toDouble() / same.sumOf { it.quantity } }
                ?: average
        }
        val bonusValue = bonuses.sumOf { lines -> lines.sumOf { value(it) * units(it) } }
        val factor = if (paid + bonusValue > 0) paid / (paid + bonusValue) else 1.0
        return Result(
            purchase = purchase.map { if (it.quantity > 0) Math.round(it.subtotal.toDouble() / it.quantity * factor) else it.unitCost },
            bonuses = bonuses.map { lines -> lines.map { Math.round(value(it) * factor) } },
        )
    }
}
