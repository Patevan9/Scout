package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoutTimeOfDayTest {

    // --- spokenCategory(): "is it morning or night" -- always exactly one of
    // morning / afternoon / evening / night, never HabitLayer's own internal
    // labels ("quiet hours", "late night") and never a fifth/sixth option. ---

    @Test fun `2am is night`() {
        assertEquals("night", ScoutTimeOfDay.spokenCategory(2))
    }

    @Test fun `7am is morning (spokenCategory)`() {
        assertEquals("morning", ScoutTimeOfDay.spokenCategory(7))
    }

    @Test fun `1pm (midday slot) collapses to afternoon`() {
        assertEquals("afternoon", ScoutTimeOfDay.spokenCategory(13))
    }

    @Test fun `3pm is afternoon (spokenCategory)`() {
        assertEquals("afternoon", ScoutTimeOfDay.spokenCategory(15))
    }

    @Test fun `7pm is evening (spokenCategory)`() {
        assertEquals("evening", ScoutTimeOfDay.spokenCategory(19))
    }

    @Test fun `11pm (late slot) collapses to night`() {
        assertEquals("night", ScoutTimeOfDay.spokenCategory(23))
    }

    // Boundary hours -- each HabitLayer.TIME_SLOTS entry is [startHour, endHour),
    // so the start hour belongs to the new slot and one hour earlier belongs to
    // the previous one.

    @Test fun `hour 6 (morning's start) is morning, not night`() {
        assertEquals("morning", ScoutTimeOfDay.spokenCategory(6))
    }

    @Test fun `hour 5 (one hour before morning) is still night`() {
        assertEquals("night", ScoutTimeOfDay.spokenCategory(5))
    }

    @Test fun `hour 12 (midday's start) is afternoon`() {
        assertEquals("afternoon", ScoutTimeOfDay.spokenCategory(12))
    }

    @Test fun `hour 14 (afternoon's own start) is afternoon`() {
        assertEquals("afternoon", ScoutTimeOfDay.spokenCategory(14))
    }

    @Test fun `hour 18 (evening's start) is evening (spokenCategory)`() {
        assertEquals("evening", ScoutTimeOfDay.spokenCategory(18))
    }

    @Test fun `hour 17 (one hour before evening) is still afternoon`() {
        assertEquals("afternoon", ScoutTimeOfDay.spokenCategory(17))
    }

    @Test fun `hour 22 (late's start) is night`() {
        assertEquals("night", ScoutTimeOfDay.spokenCategory(22))
    }

    @Test fun `hour 21 (one hour before late) is still evening (spokenCategory)`() {
        assertEquals("evening", ScoutTimeOfDay.spokenCategory(21))
    }

    @Test fun `spokenCategory never returns a HabitLayer internal label`() {
        val allowed = setOf("morning", "afternoon", "evening", "night")
        for (hour in 0..23) {
            assertEquals(
                "hour $hour should map to one of morning/afternoon/evening/night",
                true,
                ScoutTimeOfDay.spokenCategory(hour) in allowed
            )
        }
    }

    // --- descriptiveLabel(): "what time of day is it" -- finer-grained,
    // distinct wording from HabitLayer's own `label` field. ---

    @Test fun `2am is early morning, not HabitLayer's own quiet hours label`() {
        assertEquals("early morning", ScoutTimeOfDay.descriptiveLabel(2))
    }

    @Test fun `7am is morning`() {
        assertEquals("morning", ScoutTimeOfDay.descriptiveLabel(7))
    }

    @Test fun `1pm is midday`() {
        assertEquals("midday", ScoutTimeOfDay.descriptiveLabel(13))
    }

    @Test fun `3pm is afternoon`() {
        assertEquals("afternoon", ScoutTimeOfDay.descriptiveLabel(15))
    }

    @Test fun `7pm is evening`() {
        assertEquals("evening", ScoutTimeOfDay.descriptiveLabel(19))
    }

    @Test fun `11pm is late night`() {
        assertEquals("late night", ScoutTimeOfDay.descriptiveLabel(23))
    }

    // Same boundary hours as above, for the descriptive mapping.

    @Test fun `hour 6 (morning's start) is morning`() {
        assertEquals("morning", ScoutTimeOfDay.descriptiveLabel(6))
    }

    @Test fun `hour 5 (one hour before morning) is still early morning`() {
        assertEquals("early morning", ScoutTimeOfDay.descriptiveLabel(5))
    }

    @Test fun `hour 12 (midday's start) is midday`() {
        assertEquals("midday", ScoutTimeOfDay.descriptiveLabel(12))
    }

    @Test fun `hour 14 (afternoon's start) is afternoon`() {
        assertEquals("afternoon", ScoutTimeOfDay.descriptiveLabel(14))
    }

    @Test fun `hour 18 (evening's start) is evening`() {
        assertEquals("evening", ScoutTimeOfDay.descriptiveLabel(18))
    }

    @Test fun `hour 22 (late's start) is late night`() {
        assertEquals("late night", ScoutTimeOfDay.descriptiveLabel(22))
    }

    @Test fun `hour 21 (one hour before late) is still evening`() {
        assertEquals("evening", ScoutTimeOfDay.descriptiveLabel(21))
    }

    @Test fun `every hour of the day resolves to a real HabitLayer slot for both mappings`() {
        for (hour in 0..23) {
            assertEquals(
                "hour $hour should resolve from HabitLayer.TIME_SLOTS",
                true,
                com.example.scoutface.HabitLayer.TIME_SLOTS.any { hour >= it.startHour && hour < it.endHour }
            )
        }
    }
}
