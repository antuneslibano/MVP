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
                    .put("line_total", i.lineTotal ?: JSONObject.NULL)
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
                lineTotal = if (o.isNull("line_total")) null else o.optLong("line_total"),
            )
        }
    }.getOrDefault(emptyList())
}

val Invoice.items: List<InvoiceItem> get() = InvoiceItems.decode(itemsJson)
