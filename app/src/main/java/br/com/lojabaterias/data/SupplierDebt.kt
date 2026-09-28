package br.com.lojabaterias.data

import br.com.lojabaterias.domain.Periods
import java.time.LocalDate
import java.time.ZoneId

/** Quanto a loja deve aos fornecedores (boletos das notas fiscais ainda não pagos). */
data class SupplierDebt(
    val open: Long = 0,
    val openCount: Int = 0,
    val overdue: Long = 0,
    val overdueCount: Int = 0,
    /** Vencem de hoje até daqui a 7 dias. */
    val dueSoon: Long = 0,
    val dueSoonCount: Int = 0,
) {
    companion object {
        fun from(bills: List<InvoiceBill>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): SupplierDebt {
            val open = bills.filter { !it.isPaid }
            val start = Periods.toMillis(today, zone)
            val soonEnd = Periods.toMillis(today.plusDays(8), zone)
            val overdue = open.filter { it.dueDate < start }
            val soon = open.filter { it.dueDate in start until soonEnd }
            return SupplierDebt(
                open = open.sumOf { it.amount },
                openCount = open.size,
                overdue = overdue.sumOf { it.amount },
                overdueCount = overdue.size,
                dueSoon = soon.sumOf { it.amount },
                dueSoonCount = soon.size,
            )
        }

        /** Divide [total] em [count] boletos iguais (os centavos que sobram vão no primeiro). */
        fun split(total: Long, count: Int): List<Long> {
            if (count <= 0) return emptyList()
            val part = total / count
            val rest = total - part * count
            return List(count) { i -> if (i == 0) part + rest else part }
        }
    }
}
