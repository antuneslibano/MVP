package br.com.lojabaterias.domain

data class SaleTotals(
    val grossAmount: Long,
    val discount: Long,
    /** Valor cobrado pelas sucatas que o cliente não deixou. */
    val scrapCharge: Long,
    val finalAmount: Long,
    val totalCost: Long,
    val grossProfit: Long,
)

object SaleCalculator {

    /**
     * Valor bruto = preço unitário × quantidade
     * Valor final = valor bruto − desconto + cobrança de sucata faltante
     * Custo total = custo unitário (histórico) × quantidade
     * Lucro bruto = valor final − custo total
     */
    fun compute(unitPrice: Long, quantity: Int, discount: Long, unitCost: Long, scrapCharge: Long = 0): SaleTotals {
        require(quantity > 0) { "Quantidade deve ser maior que zero" }
        require(unitPrice >= 0) { "Preço inválido" }
        require(discount >= 0) { "Desconto inválido" }
        require(scrapCharge >= 0) { "Valor da sucata inválido" }
        val gross = unitPrice * quantity
        require(discount <= gross) { "Desconto maior que o valor da venda" }
        val final = gross - discount + scrapCharge
        val cost = unitCost * quantity
        return SaleTotals(
            grossAmount = gross,
            discount = discount,
            scrapCharge = scrapCharge,
            finalAmount = final,
            totalCost = cost,
            grossProfit = final - cost,
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
