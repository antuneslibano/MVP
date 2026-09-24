package br.com.lojabaterias.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import br.com.lojabaterias.data.sync.IdGenerator
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.PriceTable

/** Valores monetários sempre em centavos. */
@Entity(
    tableName = "products",
    indices = [Index(value = ["model"], unique = true)],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = IdGenerator.next(),
    val model: String,
    @ColumnInfo(name = "cost") val cost: Long,
    @ColumnInfo(name = "price_pix") val pricePix: Long,
    @ColumnInfo(name = "price_debit") val priceDebit: Long,
    @ColumnInfo(name = "price_credit") val priceCredit: Long,
    val stock: Int,
    @ColumnInfo(name = "min_stock") val minStock: Int = DEFAULT_MIN_STOCK,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /** Amperagem (Ah). 0 = não informada. */
    @ColumnInfo(name = "amperage", defaultValue = "0") val amperage: Int = 0,
    /** Controle de sincronização: momento da última alteração local. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** Controle de sincronização: alteração ainda não enviada para a nuvem. */
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
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
    @PrimaryKey(autoGenerate = true) val id: Long = IdGenerator.next(),
    @ColumnInfo(name = "date_time") val dateTime: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "gross_amount") val grossAmount: Long,
    val discount: Long,
    @ColumnInfo(name = "final_amount") val finalAmount: Long,
    @ColumnInfo(name = "total_cost") val totalCost: Long,
    @ColumnInfo(name = "gross_profit") val grossProfit: Long,
    val status: String = SaleStatus.ACTIVE,
    @ColumnInfo(name = "canceled_at") val canceledAt: Long? = null,
    /** Sucatas deixadas pelo cliente nesta venda. */
    @ColumnInfo(name = "scrap_returned", defaultValue = "0") val scrapReturned: Int = 0,
    /** Amperagem das sucatas deixadas. */
    @ColumnInfo(name = "scrap_amperage") val scrapAmperage: Int? = null,
    /** Sucatas que o cliente não deixou. */
    @ColumnInfo(name = "scrap_missing", defaultValue = "0") val scrapMissing: Int = 0,
    /** Valor cobrado pelas sucatas faltantes (já incluído no valor final). */
    @ColumnInfo(name = "scrap_charge", defaultValue = "0") val scrapCharge: Long = 0,
    /** Taxa da maquininha (crédito/débito), já descontada do lucro bruto. */
    @ColumnInfo(name = "card_fee", defaultValue = "0") val cardFee: Long = 0,
    /** Controle de sincronização: momento da última alteração local. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** Controle de sincronização: alteração ainda não enviada para a nuvem. */
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
) {
    val payment: PaymentMethod get() = PaymentMethod.fromName(paymentMethod)
    val isCanceled: Boolean get() = status == SaleStatus.CANCELED

    /** false para vendas registradas antes do controle de sucatas. */
    val hasScrapInfo: Boolean get() = scrapReturned > 0 || scrapMissing > 0
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
    @PrimaryKey(autoGenerate = true) val id: Long = IdGenerator.next(),
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
    /** Controle de sincronização: momento da última alteração local. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** Controle de sincronização: alteração ainda não enviada para a nuvem. */
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
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
    const val LOAN_OUT = "LOAN_OUT"
    const val LOAN_RETURN = "LOAN_RETURN"
    const val WARRANTY_OUT = "WARRANTY_OUT"
    const val WARRANTY_IN = "WARRANTY_IN"

    fun label(type: String): String = when (type) {
        LOAN_OUT -> "Emprestada (carga)"
        LOAN_RETURN -> "Devolvida (carga)"
        WARRANTY_OUT -> "Troca em garantia"
        WARRANTY_IN -> "Reposição da fábrica"
        INITIAL -> "Estoque inicial"
        ENTRY -> "Entrada"
        ADJUSTMENT -> "Ajuste"
        SALE -> "Venda"
        SALE_EDIT -> "Edição de venda"
        SALE_CANCEL -> "Cancelamento de venda"
        else -> type
    }

    /** Tipos que podem ser excluídos individualmente (os ligados a vendas são excluídos com a venda). */
    fun isDeletable(type: String): Boolean = type in setOf(INITIAL, ENTRY, ADJUSTMENT)
}

@Entity(
    tableName = "stock_movements",
    indices = [Index("product_id"), Index("date_time")],
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = IdGenerator.next(),
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
    /** Controle de sincronização: momento da última alteração local. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** Controle de sincronização: alteração ainda não enviada para a nuvem. */
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
)

/** Valor de referência da sucata por amperagem. */
@Entity(
    tableName = "scrap_prices",
    indices = [Index(value = ["amperage"], unique = true)],
)
data class ScrapPrice(
    @PrimaryKey(autoGenerate = true) val id: Long = IdGenerator.next(),
    val amperage: Int,
    /** Valor da sucata em centavos. */
    val value: Long,
    /** Controle de sincronização: momento da última alteração local. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** Controle de sincronização: alteração ainda não enviada para a nuvem. */
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
)

object ScrapMovementType {
    const val SALE_IN = "SALE_IN"
    const val MANUAL_IN = "MANUAL_IN"
    const val PURCHASE = "PURCHASE"
    const val SOLD = "SOLD"
    const val ADJUSTMENT = "ADJUSTMENT"
    const val SALE_EDIT = "SALE_EDIT"
    const val SALE_CANCEL = "SALE_CANCEL"

    fun label(type: String): String = when (type) {
        SALE_IN -> "Recebida na venda"
        MANUAL_IN -> "Entrada manual"
        PURCHASE -> "Compra de sucatas"
        SOLD -> "Venda de sucatas"
        ADJUSTMENT -> "Ajuste"
        SALE_EDIT -> "Edição de venda"
        SALE_CANCEL -> "Cancelamento de venda"
        else -> type
    }

    /** Tipos que podem ser excluídos individualmente (os ligados a vendas são excluídos com a venda). */
    fun isDeletable(type: String): Boolean = type in setOf(MANUAL_IN, PURCHASE, SOLD, ADJUSTMENT)
}

/** Movimentação do estoque de sucatas. O estoque é a soma das quantidades por amperagem. */
@Entity(
    tableName = "scrap_movements",
    indices = [Index("date_time"), Index("amperage"), Index("sale_id")],
)
data class ScrapMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = IdGenerator.next(),
    @ColumnInfo(name = "date_time") val dateTime: Long,
    val type: String,
    val amperage: Int,
    /** Variação (+ entrada, − saída). */
    val quantity: Int,
    /** Valor recebido (venda de sucatas) ou pago (compra de sucatas), em centavos. */
    val amount: Long = 0,
    @ColumnInfo(name = "sale_id") val saleId: Long? = null,
    val note: String? = null,
    /** Controle de sincronização: momento da última alteração local. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** Controle de sincronização: alteração ainda não enviada para a nuvem. */
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
)

/** Estoque de sucatas de uma amperagem. */
data class ScrapStock(
    val amperage: Int,
    val quantity: Int,
)

/** Sucatas vendidas (ao reciclador) em um período. */
data class ScrapSoldSummary(
    val quantity: Int = 0,
    val amount: Long = 0,
)

object ChargeStatus {
    const val IN_SHOP = "IN_SHOP"
    const val READY = "READY"
    const val DELIVERED = "DELIVERED"

    fun label(status: String): String = when (status) {
        IN_SHOP -> "Na carga"
        READY -> "Pronta"
        DELIVERED -> "Entregue"
        else -> status
    }
}

/** Bateria de cliente recebida para carga. */
@Entity(tableName = "charge_services", indices = [Index("received_at"), Index("status")])
data class ChargeService(
    @PrimaryKey(autoGenerate = true) val id: Long = IdGenerator.next(),
    @ColumnInfo(name = "customer_name") val customerName: String,
    val phone: String = "",
    /** Descrição da bateria do cliente (modelo, marca...). */
    @ColumnInfo(name = "battery_description") val batteryDescription: String = "",
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    /** Valor cobrado pela carga, em centavos. */
    val price: Long,
    val paid: Boolean = false,
    @ColumnInfo(name = "paid_at") val paidAt: Long? = null,
    @ColumnInfo(name = "payment_method") val paymentMethod: String? = null,
    /** Bateria da loja emprestada ao cliente enquanto a dele carrega. */
    @ColumnInfo(name = "loan_product_id") val loanProductId: Long? = null,
    @ColumnInfo(name = "loan_model") val loanModel: String? = null,
    @ColumnInfo(name = "loan_movement_id") val loanMovementId: Long? = null,
    @ColumnInfo(name = "loan_return_movement_id") val loanReturnMovementId: Long? = null,
    val status: String = ChargeStatus.IN_SHOP,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Long? = null,
    val note: String? = null,
    /** Controle de sincronização: momento da última alteração local. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** Controle de sincronização: alteração ainda não enviada para a nuvem. */
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
) {
    val hasLoan: Boolean get() = loanProductId != null
    val isOpen: Boolean get() = status != ChargeStatus.DELIVERED
}

object WarrantyStatus {
    /** Testada e sem defeito: não houve troca. */
    const val NO_DEFECT = "NO_DEFECT"
    /** Trocada; bateria do cliente aguardando a fábrica recolher. */
    const val AWAITING_PICKUP = "AWAITING_PICKUP"
    /** Recolhida pela fábrica (em análise / aguardando reposição). */
    const val AT_FACTORY = "AT_FACTORY"
    /** A fábrica repôs uma bateria. */
    const val REPLACED = "REPLACED"
    /** A fábrica negou a garantia: a bateria usada voltou para a loja. */
    const val DENIED = "DENIED"

    fun label(status: String): String = when (status) {
        NO_DEFECT -> "Testada sem defeito"
        AWAITING_PICKUP -> "Aguardando recolha"
        AT_FACTORY -> "Na fábrica"
        REPLACED -> "Reposta pela fábrica"
        DENIED -> "Garantia negada (usada na loja)"
        else -> status
    }
}

object UsedDestination {
    const val SCRAP = "SCRAP"
    const val SOLD = "SOLD"
    const val DISCARDED = "DISCARDED"

    fun label(d: String?): String = when (d) {
        SCRAP -> "Virou sucata"
        SOLD -> "Vendida como usada"
        DISCARDED -> "Descartada"
        else -> "Na loja"
    }
}

/**
 * Atendimento de garantia (uma bateria).
 * Guarda o teste, a troca, a ida para a fábrica e o desfecho.
 */
@Entity(tableName = "warranty_claims", indices = [Index("sale_id"), Index("status"), Index("created_at")])
data class WarrantyClaim(
    @PrimaryKey(autoGenerate = true) val id: Long = IdGenerator.next(),
    /** Venda original (null quando a venda não está no sistema). */
    @ColumnInfo(name = "sale_id") val saleId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "customer_name") val customerName: String = "",
    /** Bateria que o cliente trouxe. */
    @ColumnInfo(name = "returned_product_id") val returnedProductId: Long? = null,
    @ColumnInfo(name = "returned_model") val returnedModel: String,
    /** Resultado do teste. */
    val defective: Boolean,
    /** Bateria nova entregue ao cliente. */
    @ColumnInfo(name = "replacement_product_id") val replacementProductId: Long? = null,
    @ColumnInfo(name = "replacement_model") val replacementModel: String? = null,
    @ColumnInfo(name = "replacement_cost") val replacementCost: Long = 0,
    @ColumnInfo(name = "out_movement_id") val outMovementId: Long? = null,
    /** Diferença paga pelo cliente (bateria melhor/diferente). */
    @ColumnInfo(name = "difference_amount") val differenceAmount: Long = 0,
    @ColumnInfo(name = "difference_method") val differenceMethod: String? = null,
    val status: String,
    @ColumnInfo(name = "collected_at") val collectedAt: Long? = null,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null,
    /** Bateria enviada pela fábrica e aceita. */
    @ColumnInfo(name = "factory_product_id") val factoryProductId: Long? = null,
    @ColumnInfo(name = "factory_model") val factoryModel: String? = null,
    @ColumnInfo(name = "in_movement_id") val inMovementId: Long? = null,
    /** Registro das reposições recusadas (modelo diferente etc.). */
    @ColumnInfo(name = "refusal_notes") val refusalNotes: String? = null,
    /** Destino da bateria usada quando a garantia é negada. */
    @ColumnInfo(name = "used_destination") val usedDestination: String? = null,
    @ColumnInfo(name = "used_destination_at") val usedDestinationAt: Long? = null,
    @ColumnInfo(name = "used_sale_value") val usedSaleValue: Long = 0,
    @ColumnInfo(name = "scrap_movement_id") val scrapMovementId: Long? = null,
    val note: String? = null,
    /** Controle de sincronização: momento da última alteração local. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** Controle de sincronização: alteração ainda não enviada para a nuvem. */
    @ColumnInfo(name = "dirty", defaultValue = "1") val dirty: Boolean = true,
) {
    val isUsedInShop: Boolean get() = status == WarrantyStatus.DENIED && usedDestination == null
}

/** Registro de exclusão local, ainda não enviado para a nuvem. */
@Entity(tableName = "tombstones")
data class Tombstone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "record_id") val recordId: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long = System.currentTimeMillis(),
)

/** Totais agregados para o dashboard. */
data class PeriodSummary(
    /** Quantidade de vendas (atendimentos). */
    val count: Int = 0,
    /** Quantidade de baterias vendidas (soma das quantidades dos itens). */
    val units: Int = 0,
    val revenue: Long = 0,
    val profit: Long = 0,
)
