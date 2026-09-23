package br.com.lojabaterias.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Backup completo dos dados em JSON (produtos, vendas, itens e movimentações).
 * A restauração substitui todos os dados atuais.
 */
class BackupManager(private val db: AppDatabase) {

    suspend fun export(output: OutputStream) {
        val root = JSONObject()
        root.put("app", "loja-baterias")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("products", JSONArray().apply {
            db.productDao().getAll().forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("model", p.model)
                    put("cost", p.cost)
                    put("pricePix", p.pricePix)
                    put("priceDebit", p.priceDebit)
                    put("priceCredit", p.priceCredit)
                    put("stock", p.stock)
                    put("minStock", p.minStock)
                    put("createdAt", p.createdAt)
                })
            }
        })
        root.put("sales", JSONArray().apply {
            db.saleDao().getAllSales().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("dateTime", s.dateTime)
                    put("paymentMethod", s.paymentMethod)
                    put("grossAmount", s.grossAmount)
                    put("discount", s.discount)
                    put("finalAmount", s.finalAmount)
                    put("totalCost", s.totalCost)
                    put("grossProfit", s.grossProfit)
                    put("status", s.status)
                    s.canceledAt?.let { put("canceledAt", it) }
                })
            }
        })
        root.put("saleItems", JSONArray().apply {
            db.saleDao().getAllItems().forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id)
                    put("saleId", i.saleId)
                    put("productId", i.productId)
                    put("modelSnapshot", i.modelSnapshot)
                    put("quantity", i.quantity)
                    put("unitPrice", i.unitPrice)
                    put("unitCost", i.unitCost)
                    put("subtotal", i.subtotal)
                })
            }
        })
        root.put("movements", JSONArray().apply {
            db.movementDao().getAll().forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("productId", m.productId)
                    put("dateTime", m.dateTime)
                    put("type", m.type)
                    put("quantity", m.quantity)
                    put("stockAfter", m.stockAfter)
                    m.saleId?.let { put("saleId", it) }
                    m.note?.let { put("note", it) }
                    m.unitCost?.let { put("unitCost", it) }
                })
            }
        })

        output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString()) }
    }

    /** Retorna a quantidade de produtos e vendas restaurados. */
    suspend fun import(input: InputStream): Pair<Int, Int> {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BusinessException("Arquivo de backup inválido")
        }
        if (root.optString("app") != "loja-baterias") throw BusinessException("Arquivo de backup inválido")

        val products = root.getJSONArray("products").objects().map { o ->
            Product(
                id = o.getLong("id"),
                model = o.getString("model"),
                cost = o.getLong("cost"),
                pricePix = o.getLong("pricePix"),
                priceDebit = o.getLong("priceDebit"),
                priceCredit = o.getLong("priceCredit"),
                stock = o.getInt("stock"),
                minStock = o.optInt("minStock", Product.DEFAULT_MIN_STOCK),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
        }
        val sales = root.getJSONArray("sales").objects().map { o ->
            Sale(
                id = o.getLong("id"),
                dateTime = o.getLong("dateTime"),
                paymentMethod = o.getString("paymentMethod"),
                grossAmount = o.getLong("grossAmount"),
                discount = o.getLong("discount"),
                finalAmount = o.getLong("finalAmount"),
                totalCost = o.getLong("totalCost"),
                grossProfit = o.getLong("grossProfit"),
                status = o.getString("status"),
                canceledAt = if (o.has("canceledAt")) o.getLong("canceledAt") else null,
            )
        }
        val items = root.getJSONArray("saleItems").objects().map { o ->
            SaleItem(
                id = o.getLong("id"),
                saleId = o.getLong("saleId"),
                productId = o.getLong("productId"),
                modelSnapshot = o.getString("modelSnapshot"),
                quantity = o.getInt("quantity"),
                unitPrice = o.getLong("unitPrice"),
                unitCost = o.getLong("unitCost"),
                subtotal = o.getLong("subtotal"),
            )
        }
        val movements = root.getJSONArray("movements").objects().map { o ->
            StockMovement(
                id = o.getLong("id"),
                productId = o.getLong("productId"),
                dateTime = o.getLong("dateTime"),
                type = o.getString("type"),
                quantity = o.getInt("quantity"),
                stockAfter = o.getInt("stockAfter"),
                saleId = if (o.has("saleId")) o.getLong("saleId") else null,
                note = if (o.has("note")) o.getString("note") else null,
                unitCost = if (o.has("unitCost")) o.getLong("unitCost") else null,
            )
        }

        db.withTransaction {
            db.saleDao().deleteAllItems()
            db.saleDao().deleteAllSales()
            db.movementDao().deleteAll()
            db.productDao().deleteAll()
            db.productDao().insertAll(products)
            db.saleDao().insertSales(sales)
            db.saleDao().insertItems(items)
            db.movementDao().insertAll(movements)
        }
        return products.size to sales.size
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    companion object {
        const val FORMAT_VERSION = 1
    }
}
