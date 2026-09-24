package br.com.lojabaterias.data

import android.content.Context
import br.com.lojabaterias.domain.CardFees
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Taxas das maquininhas configuradas neste celular (padrão: crédito 7%, débito 2%). */
class FeeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("fees", Context.MODE_PRIVATE)

    private val _fees = MutableStateFlow(
        CardFees(
            creditBps = prefs.getInt(KEY_CREDIT, CardFees.DEFAULT_CREDIT_BPS),
            debitBps = prefs.getInt(KEY_DEBIT, CardFees.DEFAULT_DEBIT_BPS),
        )
    )
    val fees: StateFlow<CardFees> = _fees.asStateFlow()

    val current: CardFees get() = _fees.value

    fun save(fees: CardFees) {
        prefs.edit().putInt(KEY_CREDIT, fees.creditBps).putInt(KEY_DEBIT, fees.debitBps).apply()
        _fees.value = fees
    }

    companion object {
        private const val KEY_CREDIT = "credit_bps"
        private const val KEY_DEBIT = "debit_bps"
    }
}
