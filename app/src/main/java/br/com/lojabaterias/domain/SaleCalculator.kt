package br.com.lojabaterias.domain

data class SaleTotals(
    val grossAmount: Long,
    val discount: Long,
    val finalAmount: Long,
    val totalCost: Long,
    val grossProfit: Long,
)

object SaleCalculator {

    /**
     * Valor bruto = preço unitário × quantidade
     * Valor final = valor bruto − desconto
     * Custo total = custo unitário (histórico) × quantidade
     * Lucro bruto = valor final − custo total
     */
    fun compute(unitPrice: Long, quantity: Int, discount: Long, unitCost: Long): SaleTotals {
        require(quantity > 0) { "Quantidade deve ser maior que zero" }
        require(unitPrice >= 0) { "Preço inválido" }
        require(discount >= 0) { "Desconto inválido" }
        val gross = unitPrice * quantity
        require(discount <= gross) { "Desconto maior que o valor da venda" }
        val final = gross - discount
        val cost = unitCost * quantity
        return SaleTotals(
            grossAmount = gross,
            discount = discount,
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
}
