package com.dlunaunizar.bobitos.feature.calendar

import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarIntervalTest {
    @Test fun `month interval covers complete Monday first grid`() {
        val zone=ZoneId.of("Europe/Madrid"); val interval=YearMonth.of(2026,7).visibleInterval(zone)
        assertEquals(LocalDate.of(2026,6,29),interval.start.atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2026,8,3),interval.endExclusive.atZone(zone).toLocalDate())
    }
    @Test fun `all day end is exclusive and stable across DST`() {
        val interval=allDayInterval(LocalDate.of(2026,3,29),LocalDate.of(2026,3,29),ZoneId.of("Europe/Madrid"))
        assertEquals(LocalDate.of(2026,3,29),interval.start.atZone(ZoneId.of("Europe/Madrid")).toLocalDate())
        assertEquals(LocalDate.of(2026,3,30),interval.endExclusive.atZone(ZoneId.of("Europe/Madrid")).toLocalDate())
    }
    @Test fun `week interval starts on Monday and ends on next Monday`() {
        val zone=ZoneId.of("Europe/Madrid"); val interval=LocalDate.of(2026,7,16).visibleInterval(CalendarDisplayMode.WEEK,zone)
        assertEquals(LocalDate.of(2026,7,13),interval.start.atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2026,7,20),interval.endExclusive.atZone(zone).toLocalDate())
    }
    @Test fun `day interval stays on local dates across DST`() {
        val zone=ZoneId.of("Europe/Madrid"); val interval=LocalDate.of(2026,3,29).visibleInterval(CalendarDisplayMode.DAY,zone)
        assertEquals(LocalDate.of(2026,3,29),interval.start.atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2026,3,30),interval.endExclusive.atZone(zone).toLocalDate())
    }
    @Test fun `event starting before range still overlaps`() {
        val event=CalendarEvent("e","Viaje",null,false,Instant.parse("2026-07-01T00:00:00Z"),Instant.parse("2026-08-01T00:00:00Z"),null,null,"UTC",EventColor.BLUE,emptyList(),emptyList(),"u","U",Instant.EPOCH,"u",Instant.EPOCH)
        assertTrue(event.overlaps(Instant.parse("2026-07-15T00:00:00Z"),Instant.parse("2026-07-16T00:00:00Z")))
        assertFalse(event.overlaps(Instant.parse("2026-08-01T00:00:00Z"),Instant.parse("2026-08-02T00:00:00Z")))
    }
}
