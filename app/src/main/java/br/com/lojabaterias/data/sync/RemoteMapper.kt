package br.com.lojabaterias.data.sync

import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.Sale
import br.com.lojabaterias.data.SaleItem
import br.com.lojabaterias.data.ScrapMovement
import br.com.lojabaterias.data.ScrapPrice
import br.com.lojabaterias.data.StockMovement
import org.json.JSONObject

/** Conversão entre as entidades locais e as linhas das tabelas na nuvem (mesmos nomes de colunas). */
object RemoteMapper {

    private fun JSONObject.longOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun Any?.orNull(): Any = this ?: JSONObject.NULL

    fun toJson(p: Product) = JSONObject().apply {
        put("id", p.id); put("model", p.model); put("cost", p.cost)
        put("price_pix", p.pricePix); put("price_debit", p.priceDebit); put("price_credit", p.priceCredit)
        put("stock", p.stock); put("min_stock", p.minStock); put("created_at", p.createdAt)
        put("amperage", p.amperage); put("updated_at", p.updatedAt)
    }

    fun product(o: JSONObject) = Product(
        id = o.getLong("id"),
        model = o.getString("model"),
        cost = o.getLong("cost"),
        pricePix = o.getLong("price_pix"),
        priceDebit = o.getLong("price_debit"),
        priceCredit = o.getLong("price_credit"),
        stock = o.optInt("stock", 0),
        minStock = o.optInt("min_stock", Product.DEFAULT_MIN_STOCK),
        createdAt = o.optLong("created_at", 0),
        amperage = o.optInt("amperage", 0),
        updatedAt = o.optLong("updated_at", 0),
        dirty = false,
    )

    fun toJson(s: Sale) = JSONObject().apply {
        put("id", s.id); put("date_time", s.dateTime); put("payment_method", s.paymentMethod)
        put("gross_amount", s.grossAmount); put("discount", s.discount); put("final_amount", s.finalAmount)
        put("total_cost", s.totalCost); put("gross_profit", s.grossProfit); put("status", s.status)
        put("canceled_at", s.canceledAt.orNull()); put("scrap_returned", s.scrapReturned)
        put("scrap_amperage", s.scrapAmperage.orNull()); put("scrap_missing", s.scrapMissing)
        put("scrap_charge", s.scrapCharge); put("updated_at", s.updatedAt)
    }

    fun sale(o: JSONObject) = Sale(
        id = o.getLong("id"),
        dateTime = o.getLong("date_time"),
        paymentMethod = o.getString("payment_method"),
        grossAmount = o.getLong("gross_amount"),
        discount = o.getLong("discount"),
        finalAmount = o.getLong("final_amount"),
        totalCost = o.getLong("total_cost"),
        grossProfit = o.getLong("gross_profit"),
        status = o.getString("status"),
        canceledAt = o.longOrNull("canceled_at"),
        scrapReturned = o.optInt("scrap_returned", 0),
        scrapAmperage = o.intOrNull("scrap_amperage"),
        scrapMissing = o.optInt("scrap_missing", 0),
        scrapCharge = o.optLong("scrap_charge", 0),
        updatedAt = o.optLong("updated_at", 0),
        dirty = false,
    )

    fun toJson(i: SaleItem) = JSONObject().apply {
        put("id", i.id); put("sale_id", i.saleId); put("product_id", i.productId)
        put("model_snapshot", i.modelSnapshot); put("quantity", i.quantity); put("unit_price", i.unitPrice)
        put("unit_cost", i.unitCost); put("subtotal", i.subtotal); put("updated_at", i.updatedAt)
    }

    fun saleItem(o: JSONObject) = SaleItem(
        id = o.getLong("id"),
        saleId = o.getLong("sale_id"),
        productId = o.getLong("product_id"),
        modelSnapshot = o.getString("model_snapshot"),
        quantity = o.getInt("quantity"),
        unitPrice = o.getLong("unit_price"),
        unitCost = o.getLong("unit_cost"),
        subtotal = o.getLong("subtotal"),
        updatedAt = o.optLong("updated_at", 0),
        dirty = false,
    )

    fun toJson(m: StockMovement) = JSONObject().apply {
        put("id", m.id); put("product_id", m.productId); put("date_time", m.dateTime); put("type", m.type)
        put("quantity", m.quantity); put("stock_after", m.stockAfter); put("sale_id", m.saleId.orNull())
        put("note", m.note.orNull()); put("unit_cost", m.unitCost.orNull()); put("updated_at", m.updatedAt)
    }

    fun stockMovement(o: JSONObject) = StockMovement(
        id = o.getLong("id"),
        productId = o.getLong("product_id"),
        dateTime = o.getLong("date_time"),
        type = o.getString("type"),
        quantity = o.getInt("quantity"),
        stockAfter = o.optInt("stock_after", 0),
        saleId = o.longOrNull("sale_id"),
        note = o.stringOrNull("note"),
        unitCost = o.longOrNull("unit_cost"),
        updatedAt = o.optLong("updated_at", 0),
        dirty = false,
    )

    fun toJson(p: ScrapPrice) = JSONObject().apply {
        put("id", p.id); put("amperage", p.amperage); put("value", p.value); put("updated_at", p.updatedAt)
    }

    fun scrapPrice(o: JSONObject) = ScrapPrice(
        id = o.getLong("id"),
        amperage = o.getInt("amperage"),
        value = o.getLong("value"),
        updatedAt = o.optLong("updated_at", 0),
        dirty = false,
    )

    fun toJson(m: ScrapMovement) = JSONObject().apply {
        put("id", m.id); put("date_time", m.dateTime); put("type", m.type); put("amperage", m.amperage)
        put("quantity", m.quantity); put("amount", m.amount); put("sale_id", m.saleId.orNull())
        put("note", m.note.orNull()); put("updated_at", m.updatedAt)
    }

    fun scrapMovement(o: JSONObject) = ScrapMovement(
        id = o.getLong("id"),
        dateTime = o.getLong("date_time"),
        type = o.getString("type"),
        amperage = o.getInt("amperage"),
        quantity = o.getInt("quantity"),
        amount = o.optLong("amount", 0),
        saleId = o.longOrNull("sale_id"),
        note = o.stringOrNull("note"),
        updatedAt = o.optLong("updated_at", 0),
        dirty = false,
    )
}
