package br.com.lojabaterias.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PeriodsTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val wednesday = LocalDate.of(2026, 9, 23)

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 9, 21), Periods.startOf(PeriodType.WEEK, wednesday))
        assertEquals(LocalDate.of(2026, 9, 21), Periods.startOf(PeriodType.WEEK, LocalDate.of(2026, 9, 21)))
        assertEquals(LocalDate.of(2026, 9, 21), Periods.startOf(PeriodType.WEEK, LocalDate.of(2026, 9, 27)))
    }

    @Test
    fun rangesAreHalfOpen() {
        val day = Periods.range(PeriodType.DAY, wednesday, 0, zone)
        assertTrue(Periods.toMillis(wednesday, zone) in day)
        assertFalse(Periods.toMillis(wednesday.plusDays(1), zone) in day)

        val month = Periods.range(PeriodType.MONTH, wednesday, 0, zone)
        assertEquals(Periods.toMillis(LocalDate.of(2026, 9, 1), zone), month.start)
        assertEquals(Periods.toMillis(LocalDate.of(2026, 10, 1), zone), month.end)
    }

    @Test
    fun offsetsMoveToPreviousPeriods() {
        val prevMonth = Periods.range(PeriodType.MONTH, wednesday, -1, zone)
        assertEquals(Periods.toMillis(LocalDate.of(2026, 8, 1), zone), prevMonth.start)
        assertEquals("Agosto de 2026", Periods.label(PeriodType.MONTH, wednesday, -1))
        assertEquals("21/09/2026 a 27/09/2026", Periods.label(PeriodType.WEEK, wednesday, 0))
    }
}
