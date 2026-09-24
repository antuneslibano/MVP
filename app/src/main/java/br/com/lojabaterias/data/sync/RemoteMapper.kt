package br.com.lojabaterias.data.sync

import br.com.lojabaterias.data.ChargeService
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.Sale
import br.com.lojabaterias.data.SaleItem
import br.com.lojabaterias.data.ScrapMovement
import br.com.lojabaterias.data.ScrapPrice
import br.com.lojabaterias.data.StockMovement
import br.com.lojabaterias.data.WarrantyClaim
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

    fun toJson(c: ChargeService) = JSONObject().apply {
        put("id", c.id); put("customer_name", c.customerName); put("phone", c.phone)
        put("battery_description", c.batteryDescription); put("received_at", c.receivedAt); put("price", c.price)
        put("paid", c.paid); put("paid_at", c.paidAt.orNull()); put("payment_method", c.paymentMethod.orNull())
        put("loan_product_id", c.loanProductId.orNull()); put("loan_model", c.loanModel.orNull())
        put("loan_movement_id", c.loanMovementId.orNull()); put("loan_return_movement_id", c.loanReturnMovementId.orNull())
        put("status", c.status); put("delivered_at", c.deliveredAt.orNull()); put("note", c.note.orNull())
        put("updated_at", c.updatedAt)
    }

    fun charge(o: JSONObject) = ChargeService(
        id = o.getLong("id"),
        customerName = o.getString("customer_name"),
        phone = o.optString("phone", ""),
        batteryDescription = o.optString("battery_description", ""),
        receivedAt = o.getLong("received_at"),
        price = o.getLong("price"),
        paid = o.optBoolean("paid", false),
        paidAt = o.longOrNull("paid_at"),
        paymentMethod = o.stringOrNull("payment_method"),
        loanProductId = o.longOrNull("loan_product_id"),
        loanModel = o.stringOrNull("loan_model"),
        loanMovementId = o.longOrNull("loan_movement_id"),
        loanReturnMovementId = o.longOrNull("loan_return_movement_id"),
        status = o.getString("status"),
        deliveredAt = o.longOrNull("delivered_at"),
        note = o.stringOrNull("note"),
        updatedAt = o.optLong("updated_at", 0),
        dirty = false,
    )

    fun toJson(w: WarrantyClaim) = JSONObject().apply {
        put("id", w.id); put("sale_id", w.saleId.orNull()); put("created_at", w.createdAt)
        put("customer_name", w.customerName); put("returned_product_id", w.returnedProductId.orNull())
        put("returned_model", w.returnedModel); put("defective", w.defective)
        put("replacement_product_id", w.replacementProductId.orNull()); put("replacement_model", w.replacementModel.orNull())
        put("replacement_cost", w.replacementCost); put("out_movement_id", w.outMovementId.orNull())
        put("difference_amount", w.differenceAmount); put("difference_method", w.differenceMethod.orNull())
        put("status", w.status); put("collected_at", w.collectedAt.orNull()); put("resolved_at", w.resolvedAt.orNull())
        put("factory_product_id", w.factoryProductId.orNull()); put("factory_model", w.factoryModel.orNull())
        put("in_movement_id", w.inMovementId.orNull()); put("refusal_notes", w.refusalNotes.orNull())
        put("used_destination", w.usedDestination.orNull()); put("used_destination_at", w.usedDestinationAt.orNull())
        put("used_sale_value", w.usedSaleValue); put("scrap_movement_id", w.scrapMovementId.orNull())
        put("note", w.note.orNull()); put("updated_at", w.updatedAt)
    }

    fun warranty(o: JSONObject) = WarrantyClaim(
        id = o.getLong("id"),
        saleId = o.longOrNull("sale_id"),
        createdAt = o.getLong("created_at"),
        customerName = o.optString("customer_name", ""),
        returnedProductId = o.longOrNull("returned_product_id"),
        returnedModel = o.getString("returned_model"),
        defective = o.optBoolean("defective", false),
        replacementProductId = o.longOrNull("replacement_product_id"),
        replacementModel = o.stringOrNull("replacement_model"),
        replacementCost = o.optLong("replacement_cost", 0),
        outMovementId = o.longOrNull("out_movement_id"),
        differenceAmount = o.optLong("difference_amount", 0),
        differenceMethod = o.stringOrNull("difference_method"),
        status = o.getString("status"),
        collectedAt = o.longOrNull("collected_at"),
        resolvedAt = o.longOrNull("resolved_at"),
        factoryProductId = o.longOrNull("factory_product_id"),
        factoryModel = o.stringOrNull("factory_model"),
        inMovementId = o.longOrNull("in_movement_id"),
        refusalNotes = o.stringOrNull("refusal_notes"),
        usedDestination = o.stringOrNull("used_destination"),
        usedDestinationAt = o.longOrNull("used_destination_at"),
        usedSaleValue = o.optLong("used_sale_value", 0),
        scrapMovementId = o.longOrNull("scrap_movement_id"),
        note = o.stringOrNull("note"),
        updatedAt = o.optLong("updated_at", 0),
        dirty = false,
    )
}
