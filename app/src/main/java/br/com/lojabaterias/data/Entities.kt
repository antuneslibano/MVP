package br.com.lojabaterias.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.PriceTable

/** Valores monetários sempre em centavos. */
@Entity(
    tableName = "products",
    indices = [Index(value = ["model"], unique = true)],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val model: String,
    @ColumnInfo(name = "cost") val cost: Long,
    @ColumnInfo(name = "price_pix") val pricePix: Long,
    @ColumnInfo(name = "price_debit") val priceDebit: Long,
    @ColumnInfo(name = "price_credit") val priceCredit: Long,
    val stock: Int,
    @ColumnInfo(name = "min_stock") val minStock: Int = DEFAULT_MIN_STOCK,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    val prices: PriceTable get() = PriceTable(pricePix, priceDebit, priceCredit)
    val isOutOfStock: Boolean get() = stock <= 0
    val isLowStock: Boolean get() = stock in 1..minStock

    companion object {
        const val DEFAULT_MIN_STOCK = 2
    }
}

object SaleStatus {
    const val ACTIVE = "ACTIVE"
    const val CANCELED = "CANCELED"
}

@Entity(
    tableName = "sales",
    indices = [Index("date_time"), Index("status")],
)
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_time") val dateTime: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "gross_amount") val grossAmount: Long,
    val discount: Long,
    @ColumnInfo(name = "final_amount") val finalAmount: Long,
    @ColumnInfo(name = "total_cost") val totalCost: Long,
    @ColumnInfo(name = "gross_profit") val grossProfit: Long,
    val status: String = SaleStatus.ACTIVE,
    @ColumnInfo(name = "canceled_at") val canceledAt: Long? = null,
) {
    val payment: PaymentMethod get() = PaymentMethod.fromName(paymentMethod)
    val isCanceled: Boolean get() = status == SaleStatus.CANCELED
}

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["sale_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sale_id"), Index("product_id")],
)
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sale_id") val saleId: Long,
    /** Sem FK para permitir manter o histórico mesmo que o produto seja excluído. */
    @ColumnInfo(name = "product_id") val productId: Long,
    /** Modelo no momento da venda. */
    @ColumnInfo(name = "model_snapshot") val modelSnapshot: String,
    val quantity: Int,
    @ColumnInfo(name = "unit_price") val unitPrice: Long,
    /** Custo unitário no momento da venda (custo histórico). */
    @ColumnInfo(name = "unit_cost") val unitCost: Long,
    val subtotal: Long,
)

data class SaleWithItems(
    @Embedded val sale: Sale,
    @Relation(parentColumn = "id", entityColumn = "sale_id")
    val items: List<SaleItem>,
) {
    val modelsLabel: String get() = items.joinToString(", ") { it.modelSnapshot }
    val quantity: Int get() = items.sumOf { it.quantity }
}

object MovementType {
    const val INITIAL = "INITIAL"
    const val ENTRY = "ENTRY"
    const val ADJUSTMENT = "ADJUSTMENT"
    const val SALE = "SALE"
    const val SALE_EDIT = "SALE_EDIT"
    const val SALE_CANCEL = "SALE_CANCEL"

    fun label(type: String): String = when (type) {
        INITIAL -> "Estoque inicial"
        ENTRY -> "Entrada"
        ADJUSTMENT -> "Ajuste"
        SALE -> "Venda"
        SALE_EDIT -> "Edição de venda"
        SALE_CANCEL -> "Cancelamento de venda"
        else -> type
    }
}

@Entity(
    tableName = "stock_movements",
    indices = [Index("product_id"), Index("date_time")],
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "date_time") val dateTime: Long,
    val type: String,
    /** Variação do estoque (+ entrada, − saída). */
    val quantity: Int,
    @ColumnInfo(name = "stock_after") val stockAfter: Int,
    @ColumnInfo(name = "sale_id") val saleId: Long? = null,
    val note: String? = null,
    /** Custo unitário informado na entrada (opcional). */
    @ColumnInfo(name = "unit_cost") val unitCost: Long? = null,
)

/** Totais agregados para o dashboard. */
data class PeriodSummary(
    val count: Int = 0,
    val revenue: Long = 0,
    val profit: Long = 0,
)
