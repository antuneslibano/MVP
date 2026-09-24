package br.com.lojabaterias.data

import androidx.room.withTransaction
import br.com.lojabaterias.data.sync.RemoteMapper
import br.com.lojabaterias.domain.CardFees
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Backup completo dos dados em JSON (produtos, vendas, itens e movimentações).
 * A restauração substitui todos os dados atuais.
 */
class BackupManager(private val db: AppDatabase, private val onChange: () -> Unit = {}) {

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
                    put("amperage", p.amperage)
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
                    put("scrapReturned", s.scrapReturned)
                    s.scrapAmperage?.let { put("scrapAmperage", it) }
                    put("scrapMissing", s.scrapMissing)
                    put("scrapCharge", s.scrapCharge)
                    put("cardFee", s.cardFee)
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

        root.put("scrapPrices", JSONArray().apply {
            db.scrapDao().getPrices().forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("amperage", p.amperage)
                    put("value", p.value)
                })
            }
        })
        // Carga e garantias (mesmo formato usado na nuvem)
        root.put("chargeServices", JSONArray().apply {
            db.chargeDao().getAll().forEach { put(RemoteMapper.toJson(it)) }
        })
        root.put("warrantyClaims", JSONArray().apply {
            db.warrantyDao().getAll().forEach { put(RemoteMapper.toJson(it)) }
        })
        root.put("scrapMovements", JSONArray().apply {
            db.scrapDao().getAllMovements().forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id)
                    put("dateTime", m.dateTime)
                    put("type", m.type)
                    put("amperage", m.amperage)
                    put("quantity", m.quantity)
                    put("amount", m.amount)
                    m.saleId?.let { put("saleId", it) }
                    m.note?.let { put("note", it) }
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
                amperage = o.optInt("amperage", 0),
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
                scrapReturned = o.optInt("scrapReturned", 0),
                scrapAmperage = if (o.has("scrapAmperage")) o.getInt("scrapAmperage") else null,
                scrapMissing = o.optInt("scrapMissing", 0),
                scrapCharge = o.optLong("scrapCharge", 0),
                cardFee = o.optLong("cardFee", 0),
            ).let { sale ->
                // Backups anteriores às taxas: aplica a taxa padrão das maquininhas e recalcula o lucro.
                if (o.has("cardFee")) sale else {
                    val fee = CardFees.DEFAULT.feeFor(sale.payment, sale.finalAmount)
                    sale.copy(cardFee = fee, grossProfit = sale.finalAmount - sale.totalCost - fee)
                }
            }
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

        // Backups da versão 1 não têm sucatas: as listas ficam vazias.
        val scrapPrices = root.optJSONArray("scrapPrices")?.objects().orEmpty().map { o ->
            ScrapPrice(id = o.getLong("id"), amperage = o.getInt("amperage"), value = o.getLong("value"))
        }
        val scrapMovements = root.optJSONArray("scrapMovements")?.objects().orEmpty().map { o ->
            ScrapMovement(
                id = o.getLong("id"),
                dateTime = o.getLong("dateTime"),
                type = o.getString("type"),
                amperage = o.getInt("amperage"),
                quantity = o.getInt("quantity"),
                amount = o.optLong("amount", 0),
                saleId = if (o.has("saleId")) o.getLong("saleId") else null,
                note = if (o.has("note")) o.getString("note") else null,
            )
        }

        val importNow = System.currentTimeMillis()
        val chargeList = root.optJSONArray("chargeServices")?.objects().orEmpty()
            .map { RemoteMapper.charge(it).copy(updatedAt = importNow, dirty = true) }
        val warrantyList = root.optJSONArray("warrantyClaims")?.objects().orEmpty()
            .map { RemoteMapper.warranty(it).copy(updatedAt = importNow, dirty = true) }

        db.withTransaction {
            // Para a sincronização: o que existia e não está no backup vira exclusão na nuvem;
            // tudo o que está no backup é marcado para envio (dirty).
            val sync = db.syncDao()
            val removed = mutableListOf<Tombstone>()
            fun gone(table: String, before: List<Long>, after: Set<Long>) {
                before.filter { it !in after }.forEach { removed += Tombstone(tableName = table, recordId = it) }
            }
            gone(SyncTables.PRODUCTS, sync.allProductIds(), products.map { it.id }.toSet())
            gone(SyncTables.SALES, sync.allSaleIds(), sales.map { it.id }.toSet())
            gone(SyncTables.SALE_ITEMS, sync.allSaleItemIds(), items.map { it.id }.toSet())
            gone(SyncTables.STOCK_MOVEMENTS, sync.allStockMovementIds(), movements.map { it.id }.toSet())
            gone(SyncTables.SCRAP_PRICES, sync.allScrapPriceIds(), scrapPrices.map { it.id }.toSet())
            gone(SyncTables.SCRAP_MOVEMENTS, sync.allScrapMovementIds(), scrapMovements.map { it.id }.toSet())
            gone(SyncTables.CHARGES, sync.allChargeIds(), chargeList.map { it.id }.toSet())
            gone(SyncTables.WARRANTIES, sync.allWarrantyIds(), warrantyList.map { it.id }.toSet())
            db.chargeDao().deleteAll()
            db.warrantyDao().deleteAll()

            db.scrapDao().deleteAllMovements()
            db.scrapDao().deleteAllPrices()
            db.saleDao().deleteAllItems()
            db.saleDao().deleteAllSales()
            db.movementDao().deleteAll()
            db.productDao().deleteAll()
            db.productDao().insertAll(products)
            db.saleDao().insertSales(sales)
            db.saleDao().insertItems(items)
            db.movementDao().insertAll(movements)
            db.scrapDao().insertPrices(scrapPrices)
            db.scrapDao().insertMovements(scrapMovements)
            db.chargeDao().insertAll(chargeList)
            db.warrantyDao().insertAll(warrantyList)
            sync.insertTombstones(removed)
            // O estoque é a soma das movimentações: se o backup tiver diferença, registra um ajuste de conciliação.
            val now = System.currentTimeMillis()
            products.forEach { p ->
                val diff = p.stock - sync.movementSum(p.id)
                if (diff != 0) {
                    db.movementDao().insert(
                        StockMovement(
                            productId = p.id,
                            dateTime = now,
                            type = MovementType.ADJUSTMENT,
                            quantity = diff,
                            stockAfter = p.stock,
                            note = "Conciliação automática (restauração de backup)",
                        )
                    )
                }
            }
        }
        onChange()
        return products.size to sales.size
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    companion object {
        const val FORMAT_VERSION = 3
    }
}
