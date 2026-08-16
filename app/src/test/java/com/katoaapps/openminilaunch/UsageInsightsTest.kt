package com.katoaapps.openminilaunch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageInsightsTest {
    @Test
    fun sessionCrossingMidnightIsClampedAndFirstRealSwitchCounts() {
        val analysis = analyzeUsageTimeline(
            dayStart = 1_000L,
            now = 5_000L,
            events = listOf(
                event(500L, "reader", UsageEventKind.FOREGROUND),
                event(2_000L, "reader", UsageEventKind.BACKGROUND),
                event(2_100L, "chat", UsageEventKind.FOREGROUND),
                event(3_100L, "chat", UsageEventKind.BACKGROUND),
            ),
            ignoredPackages = emptySet(),
        )

        assertEquals(1_000L, analysis.packageDurations["reader"])
        assertEquals(1_000L, analysis.packageDurations["chat"])
        assertEquals(1, analysis.switchesToday)
    }

    @Test
    fun longestSessionIsNotCumulativeUsage() {
        val analysis = analyzeUsageTimeline(
            dayStart = 0L,
            now = 10_000L,
            events = listOf(
                event(1_000L, "docs", UsageEventKind.FOREGROUND),
                event(3_000L, "docs", UsageEventKind.BACKGROUND),
                event(5_000L, "docs", UsageEventKind.FOREGROUND),
                event(8_000L, "docs", UsageEventKind.BACKGROUND),
            ),
            ignoredPackages = emptySet(),
        )

        assertEquals(5_000L, analysis.packageDurations["docs"])
        assertEquals(3_000L, analysis.longestSessions["docs"])
    }

    @Test
    fun duplicateForegroundEventsDoNotResetOrCreateFakeSwitches() {
        val analysis = analyzeUsageTimeline(
            dayStart = 0L,
            now = 5_000L,
            events = listOf(
                event(1_000L, "maps", UsageEventKind.FOREGROUND),
                event(1_100L, "maps", UsageEventKind.FOREGROUND),
                event(4_000L, "maps", UsageEventKind.BACKGROUND),
            ),
            ignoredPackages = emptySet(),
        )

        assertEquals(3_000L, analysis.packageDurations["maps"])
        assertEquals(0, analysis.switchesToday)
    }

    @Test
    fun ignoredSystemSurfacesDoNotInflateSwitches() {
        val analysis = analyzeUsageTimeline(
            dayStart = 0L,
            now = 5_000L,
            events = listOf(
                event(1_000L, "mail", UsageEventKind.FOREGROUND),
                event(2_000L, "systemui", UsageEventKind.FOREGROUND),
                event(2_500L, "mail", UsageEventKind.FOREGROUND),
                event(3_000L, "chat", UsageEventKind.FOREGROUND),
            ),
            ignoredPackages = setOf("systemui"),
        )

        assertEquals(1, analysis.switchesToday)
    }

    @Test
    fun screenSessionCrossingMidnightIsCounted() {
        val analysis = analyzeUsageTimeline(
            dayStart = 1_000L,
            now = 5_000L,
            events = listOf(
                event(500L, null, UsageEventKind.SCREEN_ON),
                event(2_500L, null, UsageEventKind.SCREEN_OFF),
            ),
            ignoredPackages = emptySet(),
        )

        assertEquals(1_500L, analysis.screenMillis)
    }

    @Test
    fun screenStatsOverrideIsBoundedToElapsedDay() {
        val analysis = analyzeUsageTimeline(
            dayStart = 1_000L,
            now = 5_000L,
            events = emptyList(),
            ignoredPackages = emptySet(),
            screenMillisOverride = 99_000L,
        )

        assertEquals(4_000L, analysis.screenMillis)
    }

    @Test
    fun stateUsesContinuousFocusAndPrioritizesHighScreenTime() {
        assertEquals(
            MinkState.PURPOSEFUL,
            chooseMinkState(14, 0, 60, 2 * HOUR, 2, 50 * MINUTE),
        )
        assertEquals(
            MinkState.RESTING,
            chooseMinkState(14, 0, 60, 7 * HOUR, 2, 50 * MINUTE),
        )
    }

    @Test
    fun socialShareNeedsMeaningfulUseBeforeChangingState() {
        assertEquals(
            MinkState.WALKING,
            chooseMinkState(14, 5 * MINUTE, 60, 8 * MINUTE, 0, 0),
        )
        assertEquals(
            MinkState.PHONE,
            chooseMinkState(14, 35 * MINUTE, 60, 60 * MINUTE, 0, 0),
        )
    }

    @Test
    fun attentionIsReservedForActionableOrFailedInsights() {
        assertTrue(summary(MinkState.PHONE).needsAttention())
        assertTrue(summary(MinkState.DISTRACTED).needsAttention())
        assertTrue(summary(MinkState.RESTING).needsAttention())
        assertFalse(summary(MinkState.PURPOSEFUL).needsAttention())
        assertFalse(summary(MinkState.WALKING, accessGranted = false).needsAttention())
        assertTrue(summary(MinkState.WALKING, errorMessage = "Unavailable").needsAttention())
    }

    private fun summary(
        state: MinkState,
        accessGranted: Boolean = true,
        errorMessage: String? = null,
    ) = MinkDaySummary(
        accessGranted = accessGranted,
        state = state,
        headline = "Headline",
        detail = "Detail",
        errorMessage = errorMessage,
    )

    private fun event(timestamp: Long, packageName: String?, kind: UsageEventKind) =
        UsageTimelineEvent(timestamp, packageName, kind)

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
