package com.katoaapps.openminilaunch

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

internal enum class MinkState { WALKING, PURPOSEFUL, PHONE, DISTRACTED, RESTING, SLEEPING }

internal enum class UsageEventKind { FOREGROUND, BACKGROUND, SCREEN_ON, SCREEN_OFF }

internal data class UsageTimelineEvent(
    val timestamp: Long,
    val packageName: String?,
    val kind: UsageEventKind,
)

internal data class UsageTimelineAnalysis(
    val packageDurations: Map<String, Long>,
    val longestSessions: Map<String, Long>,
    val screenMillis: Long,
    val switchesToday: Int,
    val switchesLastHour: Int,
)

internal data class MinkAppUsage(
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
    val social: Boolean,
)

internal data class MinkDaySummary(
    val accessGranted: Boolean,
    val state: MinkState,
    val screenMillis: Long = 0,
    val socialMillis: Long = 0,
    val switchesToday: Int = 0,
    val switchesLastHour: Int = 0,
    val topApps: List<MinkAppUsage> = emptyList(),
    val headline: String,
    val detail: String,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    companion object {
        fun loading(nowMillis: Long = System.currentTimeMillis()): MinkDaySummary {
            val hour = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).hour
            return MinkDaySummary(
                accessGranted = false,
                state = if (hour >= 22 || hour < 5) MinkState.SLEEPING else MinkState.WALKING,
                headline = if (hour >= 22 || hour < 5) "Mink made it home" else "Mink is checking the trail",
                detail = "Looking at today’s activity on this device…",
                isLoading = true,
            )
        }

        fun unavailable(accessGranted: Boolean, nowMillis: Long = System.currentTimeMillis()): MinkDaySummary {
            val hour = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).hour
            return MinkDaySummary(
                accessGranted = accessGranted,
                state = if (hour >= 22 || hour < 5) MinkState.SLEEPING else MinkState.WALKING,
                headline = "Mink lost the trail",
                detail = "Today’s activity could not be read just now.",
                errorMessage = "MinkLauncher Open couldn’t read Android’s usage data. Try again, or check Usage Access in Settings.",
            )
        }
    }
}

internal fun MinkDaySummary.needsAttention(): Boolean = errorMessage != null || accessGranted && when (state) {
    MinkState.PHONE, MinkState.DISTRACTED, MinkState.RESTING -> true
    MinkState.WALKING, MinkState.PURPOSEFUL, MinkState.SLEEPING -> false
}

internal fun analyzeUsageTimeline(
    dayStart: Long,
    now: Long,
    events: List<UsageTimelineEvent>,
    ignoredPackages: Set<String>,
    screenMillisOverride: Long? = null,
): UsageTimelineAnalysis {
    if (now <= dayStart) return UsageTimelineAnalysis(emptyMap(), emptyMap(), 0, 0, 0)
    val sorted = events.asSequence().filter { it.timestamp <= now }.sortedBy(UsageTimelineEvent::timestamp).toList()
    val durations = mutableMapOf<String, Long>()
    val longest = mutableMapOf<String, Long>()
    val activeStarts = mutableMapOf<String, Long>()
    val foregroundEntries = mutableListOf<Pair<Long, String>>()
    var screenWasOn = false
    var lastForegroundBeforeDay: String? = null
    sorted.asSequence().filter { it.timestamp < dayStart }.forEach { event ->
        when (event.kind) {
            UsageEventKind.SCREEN_ON -> screenWasOn = true
            UsageEventKind.SCREEN_OFF -> screenWasOn = false
            UsageEventKind.FOREGROUND -> event.packageName?.takeUnless { it in ignoredPackages }?.let { packageName ->
                activeStarts[packageName] = dayStart
                lastForegroundBeforeDay = packageName
            }
            UsageEventKind.BACKGROUND -> event.packageName?.takeUnless { it in ignoredPackages }?.let { packageName ->
                activeStarts.remove(packageName)
                if (lastForegroundBeforeDay == packageName) lastForegroundBeforeDay = null
            }
        }
    }
    lastForegroundBeforeDay?.takeIf(activeStarts::containsKey)?.let { foregroundEntries += dayStart to it }
    var screenOnAt: Long? = if (screenWasOn) dayStart else null
    var screenMillis = 0L

    sorted.asSequence().filter { it.timestamp >= dayStart }.forEach { event ->
        val eventTime = event.timestamp.coerceAtMost(now)
        when (event.kind) {
            UsageEventKind.SCREEN_ON -> if (screenOnAt == null) screenOnAt = eventTime
            UsageEventKind.SCREEN_OFF -> {
                val began = screenOnAt ?: dayStart
                began?.let { if (eventTime > it) screenMillis += eventTime - it }
                screenOnAt = null
            }
            UsageEventKind.FOREGROUND -> {
                val packageName = event.packageName ?: return@forEach
                if (packageName in ignoredPackages) return@forEach
                if (packageName !in activeStarts) {
                    activeStarts[packageName] = eventTime
                    foregroundEntries += eventTime to packageName
                }
            }
            UsageEventKind.BACKGROUND -> {
                val packageName = event.packageName ?: return@forEach
                if (packageName in ignoredPackages) return@forEach
                val began = activeStarts.remove(packageName) ?: dayStart
                began?.let { addSession(packageName, it, eventTime, durations, longest) }
            }
        }
    }
    activeStarts.forEach { (packageName, began) -> addSession(packageName, began, now, durations, longest) }
    screenOnAt?.let { if (now > it) screenMillis += now - it }

    val compactEntries = foregroundEntries.fold(mutableListOf<Pair<Long, String>>()) { result, entry ->
        if (result.lastOrNull()?.second != entry.second) result += entry
        result
    }
    val switchesToday = (compactEntries.size - 1).coerceAtLeast(0)
    val lastHourStart = now - 3_600_000L
    val switchesLastHour = compactEntries.indices.count { index ->
        index > 0 && compactEntries[index].first >= lastHourStart && compactEntries[index - 1].second != compactEntries[index].second
    }
    val boundedOverride = screenMillisOverride?.coerceIn(0L, now - dayStart)
    return UsageTimelineAnalysis(
        packageDurations = durations,
        longestSessions = longest,
        screenMillis = boundedOverride?.takeIf { it > 0L } ?: screenMillis.coerceAtMost(now - dayStart),
        switchesToday = switchesToday,
        switchesLastHour = switchesLastHour,
    )
}

private fun addSession(
    packageName: String,
    began: Long,
    ended: Long,
    durations: MutableMap<String, Long>,
    longest: MutableMap<String, Long>,
) {
    if (ended <= began) return
    val duration = ended - began
    durations[packageName] = (durations[packageName] ?: 0L) + duration
    longest[packageName] = maxOf(longest[packageName] ?: 0L, duration)
}

internal fun chooseMinkState(
    hour: Int,
    socialMillis: Long,
    socialGoalMinutes: Int,
    screenMillis: Long,
    switchesLastHour: Int,
    longestNonSocialSession: Long,
): MinkState = when {
    hour >= 22 || hour < 5 -> MinkState.SLEEPING
    socialMillis >= socialGoalMinutes * 60_000L || isSocialDominant(socialMillis, screenMillis) -> MinkState.PHONE
    switchesLastHour >= 14 -> MinkState.DISTRACTED
    screenMillis >= 6 * 60 * 60_000L -> MinkState.RESTING
    longestNonSocialSession >= 45 * 60_000L -> MinkState.PURPOSEFUL
    else -> MinkState.WALKING
}

internal fun isSocialDominant(socialMillis: Long, screenMillis: Long): Boolean =
    socialMillis >= 30 * 60_000L && screenMillis > 0L && socialMillis * 100 / screenMillis >= 50

internal class UsageInsightsRepository(private val context: Context) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val packageManager = context.packageManager
    private val labelCache = ConcurrentHashMap<String, String>()
    private val socialCategoryCache = ConcurrentHashMap<String, Boolean>()
    private val homePackages: Set<String> by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(homeIntent, 0).mapTo(mutableSetOf()) { it.activityInfo.packageName }
    }

    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessSettingsIntent(): Intent {
        val usageIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        return if (usageIntent.resolveActivity(packageManager) != null) usageIntent else Intent(Settings.ACTION_SETTINGS)
    }

    fun summary(
        socialPackages: Set<String>,
        socialGoalMinutes: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): MinkDaySummary {
        val zone = ZoneId.systemDefault()
        val hour = Instant.ofEpochMilli(nowMillis).atZone(zone).hour
        if (!hasAccess()) return noAccessSummary(hour)
        val dayStart = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val events = readTimelineEvents(dayStart - EVENT_LOOKBACK_MILLIS, nowMillis)
        val ignored = events.mapNotNull(UsageTimelineEvent::packageName).filterTo(mutableSetOf(), ::isIgnoredPackage)
        val analysis = analyzeUsageTimeline(
            dayStart = dayStart,
            now = nowMillis,
            events = events,
            ignoredPackages = ignored,
            screenMillisOverride = queryScreenTime(dayStart, nowMillis),
        )
        val allApps = analysis.packageDurations.map { (packageName, duration) ->
            MinkAppUsage(packageName, appLabel(packageName), duration, isSocial(packageName, socialPackages))
        }.sortedByDescending(MinkAppUsage::foregroundMillis)
        val measuredSocialMillis = allApps.filter(MinkAppUsage::social).sumOf(MinkAppUsage::foregroundMillis)
        val socialMillis = if (analysis.screenMillis > 0L) measuredSocialMillis.coerceAtMost(analysis.screenMillis) else measuredSocialMillis
        val longestNonSocial = allApps.asSequence().filterNot(MinkAppUsage::social)
            .maxByOrNull { analysis.longestSessions[it.packageName] ?: 0L }
        val longestNonSocialMillis = longestNonSocial?.let { analysis.longestSessions[it.packageName] } ?: 0L
        val state = chooseMinkState(
            hour = hour,
            socialMillis = socialMillis,
            socialGoalMinutes = socialGoalMinutes,
            screenMillis = analysis.screenMillis,
            switchesLastHour = analysis.switchesLastHour,
            longestNonSocialSession = longestNonSocialMillis,
        )
        val topSocial = allApps.firstOrNull(MinkAppUsage::social)
        val (headline, detail) = stateCopy(
            state = state,
            socialMillis = socialMillis,
            socialGoalMinutes = socialGoalMinutes,
            switchesLastHour = analysis.switchesLastHour,
            screenMillis = analysis.screenMillis,
            longestNonSocial = longestNonSocial,
            longestNonSocialMillis = longestNonSocialMillis,
            topSocial = topSocial,
            topApp = allApps.firstOrNull { it.foregroundMillis >= DISPLAY_THRESHOLD_MILLIS },
        )
        return MinkDaySummary(
            accessGranted = true,
            state = state,
            screenMillis = analysis.screenMillis,
            socialMillis = socialMillis,
            switchesToday = analysis.switchesToday,
            switchesLastHour = analysis.switchesLastHour,
            topApps = allApps.filter { it.foregroundMillis >= DISPLAY_THRESHOLD_MILLIS }.take(5),
            headline = headline,
            detail = detail,
        )
    }

    fun launchableApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .map { LaunchableApp(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy(LaunchableApp::packageName)
            .sortedBy { it.label.lowercase() }
    }

    fun automaticSocialPackages(apps: List<LaunchableApp>): Set<String> = apps.asSequence()
        .map(LaunchableApp::packageName)
        .filter { isSocial(it, emptySet()) }
        .toSet()

    private fun readTimelineEvents(begin: Long, end: Long): List<UsageTimelineEvent> {
        val result = mutableListOf<UsageTimelineEvent>()
        val event = UsageEvents.Event()
        usageStats.queryEvents(begin, end).let { events ->
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // RESUMED/PAUSED retain the same event values used by the pre-29
                // MOVE_TO_FOREGROUND/BACKGROUND names, so one branch covers minSdk 26+.
                val kind = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventKind.FOREGROUND
                    UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventKind.BACKGROUND
                    UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEventKind.SCREEN_ON
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventKind.SCREEN_OFF
                    else -> null
                }
                if (kind != null) result += UsageTimelineEvent(event.timeStamp, event.packageName, kind)
            }
        }
        return result
    }

    private fun queryScreenTime(begin: Long, end: Long): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            usageStats.queryEventStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
                .filter { it.eventType == UsageEvents.Event.SCREEN_INTERACTIVE }
                .sumOf { it.totalTime }
        }.getOrNull()
    }

    private fun noAccessSummary(hour: Int) = MinkDaySummary(
        accessGranted = false,
        state = if (hour >= 22 || hour < 5) MinkState.SLEEPING else MinkState.WALKING,
        headline = if (hour >= 22 || hour < 5) "Mink made it home" else "Mink is ready for the day",
        detail = "Enable optional Usage Access to turn today’s device activity into local, private insights.",
    )

    private fun stateCopy(
        state: MinkState,
        socialMillis: Long,
        socialGoalMinutes: Int,
        switchesLastHour: Int,
        screenMillis: Long,
        longestNonSocial: MinkAppUsage?,
        longestNonSocialMillis: Long,
        topSocial: MinkAppUsage?,
        topApp: MinkAppUsage?,
    ): Pair<String, String> = when (state) {
        MinkState.SLEEPING -> "Mink made it home" to "Today is tucked away. Tomorrow starts with a clean trail."
        MinkState.PHONE -> "Mink stopped to scroll" to if (topSocial != null) {
            if (socialMillis >= socialGoalMinutes * 60_000L) {
                "${topSocial.label} led social time today. You’re ${formatDuration(socialMillis)} into a $socialGoalMinutes-minute goal."
            } else {
                val ratio = if (screenMillis > 0L) (socialMillis * 100 / screenMillis).coerceAtMost(100L) else 0L
                "${topSocial.label} led social time. Social apps make up $ratio% of today’s screen time."
            }
        } else "Social time passed today’s $socialGoalMinutes-minute goal."
        MinkState.DISTRACTED -> "Mink keeps changing trails" to "$switchesLastHour app switches in the last hour may be making it harder to settle in."
        MinkState.RESTING -> "Mink could use a pause" to "You’ve had ${formatDuration(screenMillis)} of screen time today. A short off-screen break might feel good."
        MinkState.PURPOSEFUL -> "Mink found a steady trail" to "Your longest uninterrupted stretch was ${formatDuration(longestNonSocialMillis)} in ${longestNonSocial?.label ?: "one app"}."
        MinkState.WALKING -> "Mink is moving along" to if (topApp == null) {
            "There isn’t enough activity yet to describe today’s trail."
        } else "${topApp.label} is your most-used app so far at ${formatDuration(topApp.foregroundMillis)}."
    }

    private fun isSocial(packageName: String, selected: Set<String>): Boolean {
        if (packageName in selected) return true
        if (selected.isNotEmpty()) return false
        return socialCategoryCache.getOrPut(packageName) {
            val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
            info?.category == ApplicationInfo.CATEGORY_SOCIAL
        }
    }

    private fun appLabel(packageName: String): String = labelCache.getOrPut(packageName) {
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }

    private fun isIgnoredPackage(packageName: String): Boolean = packageName == context.packageName ||
        packageName == "com.android.systemui" ||
        packageName.contains("permissioncontroller", ignoreCase = true) ||
        packageName in homePackages ||
        packageName.contains("launcher", ignoreCase = true)

    private companion object {
        const val EVENT_LOOKBACK_MILLIS = 24 * 60 * 60_000L
        const val DISPLAY_THRESHOLD_MILLIS = 60_000L
    }
}

internal fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000L).coerceAtLeast(0)
    val hours = minutes / 60
    val remaining = minutes % 60
    return when {
        hours > 0 && remaining > 0 -> "${hours}h ${remaining}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
