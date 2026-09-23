package br.com.lojabaterias.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

enum class PeriodType(val label: String) {
    DAY("Diário"),
    WEEK("Semanal"),
    MONTH("Mensal"),
}

/** Intervalo de tempo [start, end) em epoch millis. */
data class DateRange(val start: Long, val end: Long) {
    operator fun contains(millis: Long): Boolean = millis in start until end
}

object Periods {

    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val MONTHS = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )

    /** A semana começa na segunda-feira. */
    fun startOf(type: PeriodType, date: LocalDate): LocalDate = when (type) {
        PeriodType.DAY -> date
        PeriodType.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        PeriodType.MONTH -> date.withDayOfMonth(1)
    }

    fun next(type: PeriodType, start: LocalDate, amount: Long = 1): LocalDate = when (type) {
        PeriodType.DAY -> start.plusDays(amount)
        PeriodType.WEEK -> start.plusWeeks(amount)
        PeriodType.MONTH -> start.plusMonths(amount)
    }

    /** Período que contém [date], deslocado por [offset] períodos (0 = atual, -1 = anterior). */
    fun range(type: PeriodType, date: LocalDate, offset: Int = 0, zone: ZoneId = ZoneId.systemDefault()): DateRange {
        val start = next(type, startOf(type, date), offset.toLong())
        val end = next(type, start)
        return DateRange(toMillis(start, zone), toMillis(end, zone))
    }

    fun dayRange(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DateRange =
        range(PeriodType.DAY, date, 0, zone)

    fun label(type: PeriodType, date: LocalDate, offset: Int): String {
        val start = next(type, startOf(type, date), offset.toLong())
        return when (type) {
            PeriodType.DAY -> when (offset) {
                0 -> "Hoje, ${start.format(DATE)}"
                -1 -> "Ontem, ${start.format(DATE)}"
                else -> start.format(DATE)
            }
            PeriodType.WEEK -> {
                val end = start.plusDays(6)
                "${start.format(DATE)} a ${end.format(DATE)}"
            }
            PeriodType.MONTH -> "${MONTHS[start.monthValue - 1]} de ${start.year}"
        }
    }

    fun toMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun toLocalDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    fun formatDateTime(millis: Long): String = toLocalDateTime(millis).format(DATE_TIME)
    fun formatDate(millis: Long): String = toLocalDateTime(millis).format(DATE)
    fun formatTime(millis: Long): String = toLocalDateTime(millis).format(TIME)
}
