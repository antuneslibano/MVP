package br.com.lojabaterias.ui.viewmodel

import androidx.lifecycle.viewModelScope
import br.com.lojabaterias.data.BusinessException
import br.com.lojabaterias.data.Product
import br.com.lojabaterias.data.ScrapInput
import br.com.lojabaterias.data.StoreRepository
import br.com.lojabaterias.domain.CardFees
import br.com.lojabaterias.domain.CostLayer
import br.com.lojabaterias.domain.CostLayers
import br.com.lojabaterias.domain.PaymentMethod
import br.com.lojabaterias.domain.PriceTable
import br.com.lojabaterias.domain.SaleCalculator
import br.com.lojabaterias.domain.SplitPayment
import br.com.lojabaterias.domain.Scrap
import br.com.lojabaterias.domain.SaleTotals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SaleFormState(
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    /** Produto selecionado (em edição pode ser null se o produto foi excluído). */
    val product: Product? = null,
    val model: String = "",
    val unitCost: Long = 0,
    /** Quantidade máxima permitida (estoque atual + quantidade já vendida, na edição). */
    val available: Int = 0,
    val method: PaymentMethod = PaymentMethod.PIX,
    val quantity: Int = 1,
    val unitPrice: Long = 0,
    val discount: Long = 0,
    val dateTime: Long = System.currentTimeMillis(),
    /** Se o usuário alterou a data/hora manualmente (senão usa "agora" ao confirmar). */
    val dateTimeEdited: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    // ----- Sucata
    /** Venda antiga, registrada antes do controle de sucatas (a sucata não é informada). */
    val scrapLegacy: Boolean = false,
    /** Sucatas deixadas pelo cliente (0 = sem sucata). */
    val scrapReturned: Int = 0,
    /** Amperagem da sucata deixada (texto digitado). */
    val scrapAmperage: String = "",
    /** Valor cobrado pelas sucatas faltantes. */
    val scrapCharge: Long = 0,
    /** Se o usuário alterou o valor cobrado manualmente. */
    val scrapChargeEdited: Boolean = false,
    /** O cliente levou vale do casco (o valor do vale é o valor cobrado pela sucata). */
    val scrapVoucher: Boolean = false,
    /** Amperagem da bateria vendida (usada para sugerir sucata e valor). */
    val batteryAmperage: Int = 0,
    /** Taxas das maquininhas em vigor. */
    val fees: CardFees = CardFees.DEFAULT,
    /** Baterias extras (custo zero) deste modelo que podem sair nesta venda. */
    val freeAvailable: Int = 0,
    /** Pagamento dividido em várias formas (ex.: parte no dinheiro, parte no crédito). */
    val split: Boolean = false,
    val parts: List<PaymentPart> = emptyList(),
    /** Lotes do estoque da bateria (venda nova): as mais antigas saem primeiro. */
    val costLayers: List<CostLayer>? = null,
) {
    /** Custo de cada bateria desta venda: pelos lotes (venda nova) ou o gravado na venda (edição). */
    val effectiveUnitCost: Long
        get() = costLayers?.let { CostLayers.unitCostOf(it, (quantity - freeUnits).coerceAtLeast(0), unitCost) } ?: unitCost

    val partPairs: List<Pair<PaymentMethod, Long>> get() = parts.map { it.method to it.amount }
    val partsTotal: Long get() = parts.sumOf { it.amount }

    val freeUnits: Int get() = minOf(quantity, freeAvailable).coerceAtLeast(0)

    val hasSelection: Boolean get() = model.isNotEmpty()

    val scrapMissing: Int get() = if (scrapLegacy) 0 else (quantity - scrapReturned).coerceAtLeast(0)

    val scrapInput: ScrapInput
        get() = if (scrapLegacy || quantity <= 0) {
            ScrapInput.NONE
        } else {
            ScrapInput(
                returned = scrapReturned,
                missing = scrapMissing,
                amperage = if (scrapReturned > 0) scrapAmperage.toIntOrNull() ?: 0 else 0,
                charge = if (scrapMissing > 0) scrapCharge else 0,
                voucher = scrapVoucher,
            )
        }

    val totals: SaleTotals?
        get() = if (split && quantity > 0) {
            SplitPayment.compute(unitPrice, quantity, effectiveUnitCost, scrapInput.charge, partPairs, fees::rateFor, freeUnits)
        } else if (quantity > 0 && discount <= unitPrice * quantity) {
            SaleCalculator.compute(unitPrice, quantity, discount, effectiveUnitCost, scrapInput.charge, fees.rateFor(method), freeUnits)
        } else {
            null
        }

    val validationError: String?
        get() = SaleCalculator.validate(unitPrice, quantity, if (split) 0 else discount, available)
            ?: (if (split) SplitPayment.validate(partPairs) else null)
            ?: scrapInput.let { SaleCalculator.validateScrap(quantity, it.returned, it.missing, it.amperageOrNull, it.charge) }
}

/** Uma linha do pagamento dividido. */
data class PaymentPart(val method: PaymentMethod, val amount: Long = 0)

/** Sugestão de valor para uma linha do pagamento dividido. */
data class PartSuggestion(val label: String, val amount: Long)

class SaleFormViewModel(
    private val repo: StoreRepository,
    private val saleId: Long?,
    initialProductId: Long?,
) : MessageViewModel() {

    private val _form = MutableStateFlow(
        SaleFormState(isEdit = saleId != null, loading = saleId != null, fees = repo.cardFees)
    )
    val form: StateFlow<SaleFormState> = _form.asStateFlow()

    /** Tabela de valores de sucata (amperagem → valor). */
    val scrapPrices: StateFlow<Map<Int, Long>> = repo.observeScrapPrices()
        .map { list -> list.associate { it.amperage to it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    /** Resultados da busca: produtos com estoque primeiro. */
    val results: StateFlow<List<Product>> = combine(repo.observeProducts(), query) { list, q ->
        val term = q.trim()
        list.filter { term.isEmpty() || it.model.contains(term, ignoreCase = true) }
            .sortedWith(compareBy<Product> { it.isOutOfStock }.thenBy { it.model.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Quando a tabela de sucatas carregar/mudar, atualiza o valor sugerido.
        viewModelScope.launch { scrapPrices.collect { _form.update { it.withScrapDefaults() } } }
        if (saleId != null) {
            viewModelScope.launch { loadSale(saleId) }
        } else if (initialProductId != null) {
            viewModelScope.launch { repo.getProduct(initialProductId)?.let { selectProduct(it) } }
        }
    }

    private suspend fun loadSale(id: Long) {
        runCatching { repo.repairExtras() }
        val sale = repo.getSale(id)
        if (sale == null) {
            message("Venda não encontrada")
            _form.update { it.copy(loading = false, done = true) }
            return
        }
        val item = sale.items.firstOrNull()
        val product = item?.let { repo.getProduct(it.productId) }
        _form.value = SaleFormState(
            isEdit = true,
            loading = false,
            product = product,
            model = item?.modelSnapshot ?: "",
            unitCost = item?.unitCost ?: 0,
            available = (item?.quantity ?: 0) + (product?.stock ?: 0),
            method = sale.sale.payment,
            quantity = item?.quantity ?: 1,
            unitPrice = item?.unitPrice ?: 0,
            discount = sale.sale.discount,
            dateTime = sale.sale.dateTime,
            dateTimeEdited = true,
            scrapLegacy = !sale.sale.hasScrapInfo,
            scrapReturned = sale.sale.scrapReturned,
            scrapAmperage = sale.sale.scrapAmperage?.toString() ?: "",
            scrapCharge = sale.sale.scrapCharge,
            scrapChargeEdited = true,
            scrapVoucher = repo.hasVoucher(id),
            batteryAmperage = batteryAmperage(product, item?.modelSnapshot ?: ""),
            fees = repo.cardFees,
            freeAvailable = item?.let { repo.freeExtraCount(it.productId, id) } ?: 0,
            split = sale.isSplit,
            parts = sale.payments.map { PaymentPart(PaymentMethod.fromName(it.method), it.amount) },
        )
    }

    private fun batteryAmperage(product: Product?, model: String): Int =
        product?.amperage?.takeIf { it > 0 } ?: Scrap.guessAmperage(model) ?: 0

    /** Recalcula o valor sugerido da sucata faltante (se o usuário não alterou manualmente). */
    private fun SaleFormState.withScrapDefaults(): SaleFormState {
        if (scrapLegacy || scrapChargeEdited) return this
        val suggested = Scrap.priceFor(batteryAmperage, scrapPrices.value) * scrapMissing
        return copy(scrapCharge = suggested)
    }

    /** Valor de tabela sugerido para a sucata faltante. */
    fun suggestedScrapCharge(s: SaleFormState): Long = Scrap.priceFor(s.batteryAmperage, scrapPrices.value) * s.scrapMissing

    fun setQuery(q: String) {
        query.value = q
    }

    fun selectProduct(product: Product) {
        _form.update {
            it.copy(
                product = product,
                model = product.model,
                unitCost = product.cost,
                costLayers = null,
                available = product.stock,
                quantity = if (product.stock > 0) 1 else 0,
                unitPrice = product.prices.priceFor(it.method),
                discount = 0,
                // Padrão: o cliente deixou a sucata da mesma amperagem da bateria comprada.
                scrapLegacy = false,
                scrapReturned = if (product.stock > 0) 1 else 0,
                scrapAmperage = batteryAmperage(product, product.model).takeIf { a -> a > 0 }?.toString() ?: "",
                scrapCharge = 0,
                scrapChargeEdited = false,
                scrapVoucher = false,
                batteryAmperage = batteryAmperage(product, product.model),
                freeAvailable = 0,
            ).withScrapDefaults()
        }
        viewModelScope.launch {
            runCatching { repo.repairExtras() }
            val free = repo.freeExtraCount(product.id)
            val layers = repo.costLayers(product.id)
            _form.update { if (it.product?.id == product.id) it.copy(freeAvailable = free, costLayers = layers) else it }
        }
    }

    fun clearProduct() {
        if (_form.value.isEdit) return
        _form.update { SaleFormState(method = it.method, fees = it.fees) }
    }

    /** Ao trocar a forma de pagamento, aplica automaticamente o preço de tabela correspondente. */
    fun selectMethod(method: PaymentMethod) {
        _form.update { s ->
            val price = s.product?.prices?.priceFor(method) ?: s.unitPrice
            s.copy(method = method, unitPrice = price)
        }
    }

    fun setQuantity(q: Int) = _form.update { s ->
        // Se todas as sucatas tinham sido deixadas, acompanha a nova quantidade.
        val returned = if (s.scrapReturned >= s.quantity) q else s.scrapReturned.coerceAtMost(q)
        s.copy(quantity = q, scrapReturned = returned).withScrapDefaults()
    }

    /** "Deixou sucata" (todas) ou "Sem sucata". */
    fun setScrapLeft(left: Boolean) = _form.update { s ->
        val amperage = if (left && s.scrapAmperage.isBlank() && s.batteryAmperage > 0) s.batteryAmperage.toString() else s.scrapAmperage
        s.copy(scrapReturned = if (left) s.quantity else 0, scrapAmperage = amperage).withScrapDefaults()
    }

    fun setScrapReturned(n: Int) = _form.update { it.copy(scrapReturned = n.coerceIn(0, it.quantity)).withScrapDefaults() }
    fun setScrapAmperage(text: String) = _form.update { it.copy(scrapAmperage = text.filter { c -> c.isDigit() }.take(3)) }
    fun setScrapCharge(v: Long) = _form.update { it.copy(scrapCharge = v, scrapChargeEdited = true) }
    fun resetScrapCharge() = _form.update { it.copy(scrapChargeEdited = false).withScrapDefaults() }
    fun setScrapVoucher(v: Boolean) = _form.update { it.copy(scrapVoucher = v) }
    fun setUnitPrice(v: Long) = _form.update { it.copy(unitPrice = v) }

    // ------------------------------------------------------------ Pagamento dividido

    /** Liga/desliga o pagamento dividido. O preço de referência passa a ser o à vista (PIX/dinheiro). */
    fun setSplit(on: Boolean) = _form.update { s ->
        if (on) {
            s.copy(
                split = true,
                unitPrice = s.product?.prices?.pix ?: s.unitPrice,
                discount = 0,
                parts = s.parts.takeIf { it.size >= 2 }
                    ?: listOf(PaymentPart(PaymentMethod.DINHEIRO), PaymentPart(PaymentMethod.CREDITO)),
            )
        } else {
            val main = s.parts.maxByOrNull { it.amount }?.method ?: s.method
            s.copy(split = false, parts = emptyList(), method = main, unitPrice = s.product?.prices?.priceFor(main) ?: s.unitPrice)
        }
    }

    fun setPartMethod(index: Int, m: PaymentMethod) = updatePart(index) { it.copy(method = m) }
    fun setPartAmount(index: Int, v: Long) = updatePart(index) { it.copy(amount = v) }

    fun addPart() = _form.update { s ->
        val next = PaymentMethod.entries.firstOrNull { m -> s.parts.none { it.method == m } } ?: PaymentMethod.PIX
        s.copy(parts = s.parts + PaymentPart(next))
    }

    fun removePart(index: Int) = _form.update { s ->
        if (s.parts.size <= 2) s else s.copy(parts = s.parts.filterIndexed { i, _ -> i != index })
    }

    private fun updatePart(index: Int, f: (PaymentPart) -> PaymentPart) = _form.update { s ->
        s.copy(parts = s.parts.mapIndexed { i, p -> if (i == index) f(p) else p })
    }

    /** Sugestões para a linha [index]: quanto falta pelo preço à vista, pelo preço da forma escolhida e proporcional. */
    fun suggestions(s: SaleFormState, index: Int): List<PartSuggestion> {
        val part = s.parts.getOrNull(index) ?: return emptyList()
        val others = s.partPairs.filterIndexed { i, _ -> i != index }
        val othersTotal = others.sumOf { it.second }
        val scrap = s.scrapInput.charge
        val prices = s.product?.prices ?: PriceTable(s.unitPrice, s.unitPrice, s.unitPrice)
        val list = mutableListOf(
            PartSuggestion("Falta (preço à vista)", SplitPayment.remaining(prices.pix * s.quantity + scrap, othersTotal)),
        )
        if (prices.priceFor(part.method) != prices.pix) {
            list += PartSuggestion(
                "Falta (preço ${part.method.label.lowercase()})",
                SplitPayment.remaining(prices.priceFor(part.method) * s.quantity + scrap, othersTotal),
            )
            list += PartSuggestion("Proporcional", SplitPayment.proportional(prices, s.quantity, scrap, others, part.method))
        }
        return list.filter { it.amount > 0 }.distinctBy { it.amount }
    }

    fun setDiscount(v: Long) = _form.update { it.copy(discount = v) }
    fun setDateTime(millis: Long) = _form.update { it.copy(dateTime = millis, dateTimeEdited = true) }

    fun confirm() {
        val s = _form.value
        if (s.saving || s.done) return
        s.validationError?.let { message(it); return }
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val dateTime = if (s.dateTimeEdited) s.dateTime else System.currentTimeMillis()
                if (saleId != null) {
                    repo.updateSale(
                        saleId, s.quantity, s.method, s.unitPrice, if (s.split) 0 else s.discount, dateTime, s.scrapInput,
                        if (s.split) s.partPairs else emptyList(),
                    )
                    message("Venda atualizada")
                } else {
                    val productId = s.product?.id ?: throw BusinessException("Selecione a bateria")
                    repo.registerSale(
                        productId, s.quantity, s.method, s.unitPrice, if (s.split) 0 else s.discount, dateTime, s.scrapInput,
                        if (s.split) s.partPairs else emptyList(),
                    )
                    message("Venda registrada")
                }
                _form.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                message(errorMessage(e))
                _form.update { it.copy(saving = false) }
            }
        }
    }
}
