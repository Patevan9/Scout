package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoutTimeOfDayTest {

    @Test fun `2am is quiet hours, HabitLayer's label for its night slot`() {
        assertEquals("quiet hours", ScoutTimeOfDay.currentLabel(2))
    }

    @Test fun `7am is morning`() {
        assertEquals("morning", ScoutTimeOfDay.currentLabel(7))
    }

    @Test fun `1pm is midday`() {
        assertEquals("midday", ScoutTimeOfDay.currentLabel(13))
    }

    @Test fun `3pm is afternoon`() {
        assertEquals("afternoon", ScoutTimeOfDay.currentLabel(15))
    }

    @Test fun `7pm is evening`() {
        assertEquals("evening", ScoutTimeOfDay.currentLabel(19))
    }

    @Test fun `11pm is late night`() {
        assertEquals("late night", ScoutTimeOfDay.currentLabel(23))
    }

    // Boundary hours -- each TIME_SLOTS entry is [startHour, endHour), so the
    // start hour belongs to the new slot and one hour earlier belongs to the
    // previous one. Confirms ScoutTimeOfDay's lookup matches HabitLayer's own
    // slot boundaries exactly, not an off-by-one reinterpretation of them.

    @Test fun `hour 6 (morning's start) is morning, not night`() {
        assertEquals("morning", ScoutTimeOfDay.currentLabel(6))
    }

    @Test fun `hour 5 (one hour before morning) is still quiet hours`() {
        assertEquals("quiet hours", ScoutTimeOfDay.currentLabel(5))
    }

    @Test fun `every hour of the day resolves to a real HabitLayer label, never the fallback`() {
        for (hour in 0..23) {
            assertEquals(
                "hour $hour should resolve from HabitLayer.TIME_SLOTS",
                true,
                com.example.scoutface.HabitLayer.TIME_SLOTS.any { hour >= it.startHour && hour < it.endHour }
            )
        }
    }
}
