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
            )
        }
    }.getOrDefault(emptyList())
}

val Invoice.items: List<InvoiceItem> get() = InvoiceItems.decode(itemsJson)
