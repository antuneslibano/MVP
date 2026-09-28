package br.com.lojabaterias.domain

data class SaleTotals(
    val grossAmount: Long,
    val discount: Long,
    /** Valor cobrado pelas sucatas que o cliente não deixou. */
    val scrapCharge: Long,
    /** Taxa da maquininha (crédito/débito) sobre o valor final. */
    val cardFee: Long,
    val finalAmount: Long,
    val totalCost: Long,
    val grossProfit: Long,
)

object SaleCalculator {

    /**
     * Valor bruto = preço unitário × quantidade
     * Valor final = valor bruto − desconto + cobrança de sucata faltante
     * Custo total = custo unitário (histórico) × (quantidade − extras de custo zero) + casco cobrado
     *   (o valor do casco entra no faturamento, mas é custo: serve para repor o casco que o cliente não deixou)
     * Taxa da maquininha = valor final × taxa da forma de pagamento
     * Lucro bruto = valor final − custo total − taxa da maquininha
     */
    fun compute(
        unitPrice: Long,
        quantity: Int,
        discount: Long,
        unitCost: Long,
        scrapCharge: Long = 0,
        feeBps: Int = 0,
        freeUnits: Int = 0,
    ): SaleTotals {
        require(quantity > 0) { "Quantidade deve ser maior que zero" }
        require(unitPrice >= 0) { "Preço inválido" }
        require(discount >= 0) { "Desconto inválido" }
        require(scrapCharge >= 0) { "Valor da sucata inválido" }
        val gross = unitPrice * quantity
        require(discount <= gross) { "Desconto maior que o valor da venda" }
        val final = gross - discount + scrapCharge
        val cost = unitCost * (quantity - freeUnits.coerceIn(0, quantity)) + scrapCharge
        val fee = CardFees.feeOf(final, feeBps)
        return SaleTotals(
            grossAmount = gross,
            discount = discount,
            scrapCharge = scrapCharge,
            cardFee = fee,
            finalAmount = final,
            totalCost = cost,
            grossProfit = final - cost - fee,
        )
    }

    /** Retorna uma mensagem de erro de validação, ou null se estiver tudo certo. */
    fun validate(unitPrice: Long, quantity: Int, discount: Long, availableStock: Int): String? = when {
        quantity <= 0 -> "Informe a quantidade"
        quantity > availableStock -> "Estoque insuficiente (disponível: $availableStock)"
        unitPrice <= 0 -> "Informe o preço"
        discount < 0 -> "Desconto inválido"
        discount > unitPrice * quantity -> "Desconto maior que o valor da venda"
        else -> null
    }

    /**
     * Valida as informações de sucata da venda.
     * [returned] = sucatas deixadas pelo cliente; [missing] = sucatas que faltaram.
     * 0 e 0 significa "não informado" (vendas antigas, anteriores ao controle de sucatas).
     */
    fun validateScrap(quantity: Int, returned: Int, missing: Int, amperage: Int?, charge: Long): String? = when {
        returned < 0 || missing < 0 -> "Quantidade de sucatas inválida"
        returned == 0 && missing == 0 -> if (charge != 0L) "Valor de sucata sem sucata faltante" else null
        returned + missing != quantity -> "Sucatas deixadas + faltantes devem somar a quantidade vendida"
        returned > 0 && (amperage == null || amperage <= 0) -> "Informe a amperagem da sucata deixada"
        charge < 0 -> "Valor da sucata inválido"
        missing == 0 && charge > 0 -> "Não há sucata faltante para cobrar"
        else -> null
    }
}

/**
 * Pagamento dividido: cada parte é uma forma de pagamento com o valor que o cliente pagou nela.
 * O total da venda é a soma das partes (a loja decide na hora quanto cobrar em cada forma).
 */
object SplitPayment {

    /**
     * Totais da venda com pagamento dividido. O preço de referência é o à vista ([cashUnitPrice]);
     * se o total cobrado for menor, a diferença entra como desconto; se for maior, como acréscimo.
     * A taxa da maquininha é calculada só sobre as partes no cartão.
     */
    fun compute(
        cashUnitPrice: Long,
        quantity: Int,
        unitCost: Long,
        scrapCharge: Long,
        parts: List<Pair<PaymentMethod, Long>>,
        feeBps: (PaymentMethod) -> Int,
        freeUnits: Int = 0,
    ): SaleTotals {
        require(quantity > 0) { "Quantidade deve ser maior que zero" }
        val gross = cashUnitPrice * quantity
        val final = parts.sumOf { it.second }
        val base = SaleCalculator.compute(cashUnitPrice, quantity, 0, unitCost, scrapCharge, 0, freeUnits)
        val fee = parts.sumOf { (m, amount) -> CardFees.feeOf(amount, feeBps(m)) }
        return base.copy(
            discount = (gross + scrapCharge - final).coerceAtLeast(0),
            finalAmount = final,
            cardFee = fee,
            grossProfit = final - base.totalCost - fee,
        )
    }

    fun validate(parts: List<Pair<PaymentMethod, Long>>): String? = when {
        parts.size < 2 -> "Adicione pelo menos duas formas de pagamento"
        parts.any { it.second <= 0 } -> "Informe o valor de cada forma de pagamento"
        else -> null
    }

    /** Quanto falta para fechar a venda pelo preço [dueInMethod] (ex.: preço à vista ou do cartão). */
    fun remaining(dueInMethod: Long, othersTotal: Long): Long = (dueInMethod - othersTotal).coerceAtLeast(0)

    /**
     * Quanto falta, proporcionalmente, na forma [method]: o que já foi pago é convertido para o preço à vista
     * e o que sobra é cobrado no preço da forma escolhida.
     */
    fun proportional(prices: PriceTable, quantity: Int, scrapCharge: Long, others: List<Pair<PaymentMethod, Long>>, method: PaymentMethod): Long {
        val cash = prices.pix * quantity + scrapCharge
        if (prices.pix <= 0) return remaining(cash, others.sumOf { it.second })
        val paidInCash = others.sumOf { (m, amount) ->
            val price = prices.priceFor(m).takeIf { it > 0 } ?: prices.pix
            amount.toDouble() * prices.pix / price
        }
        val left = (cash - paidInCash).coerceAtLeast(0.0)
        val price = prices.priceFor(method).takeIf { it > 0 } ?: prices.pix
        return Math.round(left * price / prices.pix)
    }
}
