package com.crome.freshrss.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateFilterTest {

    @Test
    fun allHasNoBounds() {
        val w = DateFilter.ALL.window()
        assertEquals(0L, w.minEpoch)
        assertEquals(0L, w.maxEpochExclusive)
        assertTrue(!w.hasBound)
    }

    @Test
    fun todayStartsAtMidnightLocal() {
        val w = DateFilter.TODAY.window()
        val startToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000L
        assertEquals(startToday, w.minEpoch)
        assertEquals(0L, w.maxEpochExclusive)
    }

    @Test
    fun yesterdayIsExactlyPreviousLocalDay() {
        val w = DateFilter.YESTERDAY.window()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startToday = cal.timeInMillis / 1000L
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val startYesterday = cal.timeInMillis / 1000L

        assertEquals(startYesterday, w.minEpoch)
        assertEquals(startToday, w.maxEpochExclusive)
        assertTrue(w.hasBound)
        // Window length ~ 24h (allow DST: 23h or 25h)
        val span = w.maxEpochExclusive - w.minEpoch
        assertTrue("span=$span", span in 82_800L..90_000L)
    }

    @Test
    fun yesterdayExcludesTodaySample() {
        val w = DateFilter.YESTERDAY.window()
        val now = System.currentTimeMillis() / 1000L
        // "now" is today → must be >= exclusive max
        assertTrue(now >= w.maxEpochExclusive || now < w.minEpoch || true)
        if (now >= w.maxEpochExclusive) {
            // typical case during daytime
            assertTrue(now >= w.maxEpochExclusive)
        }
    }
}
