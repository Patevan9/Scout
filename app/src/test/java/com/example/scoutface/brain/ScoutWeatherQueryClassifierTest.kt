package com.example.scoutface.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoutWeatherQueryClassifierTest {

    private fun classify(q: String) = ScoutWeatherQueryClassifier.classify(q)

    @Test fun `plain today is CURRENT`() {
        val c = classify("what is the weather today")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.CURRENT, c.type)
        assertNull(c.specificDay)
    }

    @Test fun `today going to be phrasing is CURRENT, not a same-day weekday lookup`() {
        val c = classify("what is the weather going to be today")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.CURRENT, c.type)
        assertNull(c.specificDay)
    }

    @Test fun `what will the weather be today is CURRENT`() {
        val c = classify("what will the weather be today")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.CURRENT, c.type)
        assertNull(c.specificDay)
    }

    @Test fun `later today phrasing is CURRENT`() {
        val c = classify("what will it be like later today")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.CURRENT, c.type)
        assertNull(c.specificDay)
    }

    @Test fun `rest of today phrasing is CURRENT`() {
        val c = classify("what about the rest of today")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.CURRENT, c.type)
        assertNull(c.specificDay)
    }

    @Test fun `genuine named weekday is SPECIFIC_DAY`() {
        val c = classify("what is the weather thursday")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.SPECIFIC_DAY, c.type)
        assertEquals("Thursday", c.specificDay)
    }

    @Test fun `tomorrow is TOMORROW`() {
        val c = classify("what is the weather tomorrow")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.TOMORROW, c.type)
        assertNull(c.specificDay)
    }

    @Test fun `tonight is TONIGHT`() {
        val c = classify("what is the weather tonight")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.TONIGHT, c.type)
        assertNull(c.specificDay)
    }

    @Test fun `this week wording is WEEK`() {
        val c = classify("weather this week")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.WEEK, c.type)
        assertNull(c.specificDay)
    }

    @Test fun `plain weather with no qualifiers is CURRENT`() {
        val c = classify("what is the weather")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.CURRENT, c.type)
        assertNull(c.specificDay)
    }

    // --- Extra regression coverage ---

    @Test fun `forecast wording is WEEK`() {
        val c = classify("give me the forecast")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.WEEK, c.type)
    }

    @Test fun `a named weekday always wins over today wording`() {
        // Not a realistic utterance on its own, but guards the priority
        // rule explicitly: specificDay != null must always suppress the
        // today-phrasing branch, never the other way around.
        val c = classify("what is the weather going to be like thursday")
        assertEquals(ScoutWeatherQueryClassifier.QueryType.SPECIFIC_DAY, c.type)
        assertEquals("Thursday", c.specificDay)
    }
}
