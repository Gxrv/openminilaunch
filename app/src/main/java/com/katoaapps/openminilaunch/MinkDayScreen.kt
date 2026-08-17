package com.katoaapps.openminilaunch

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun MinkDayScreen(store: LauncherStore, isActive: Boolean, goHome: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { UsageInsightsRepository(context.applicationContext) }
    var showSocialApps by remember { mutableStateOf(false) }
    var permissionReturnToken by remember { mutableIntStateOf(0) }
    val summary by rememberMinkDaySummary(store, repository, isActive, permissionReturnToken)
    val errorMessage = summary.errorMessage
    val usageSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permissionReturnToken++ }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MINK’S DAY", letterSpacing = 1.6.sp, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Rust)
                    Text("Your tracked apps today", fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                IconButton(onClick = goHome) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Go Home") }
            }
        }
        item { MinkHero(summary) }
        if (summary.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
        } else if (errorMessage != null) {
            item {
                MinkErrorCard(errorMessage) { permissionReturnToken++ }
            }
        } else if (!summary.accessGranted) {
            item {
                UsageAccessCard(
                    onEnable = {
                        val intent = repository.accessSettingsIntent()
                        runCatching { usageSettings.launch(intent) }.onFailure {
                            context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                        }
                    },
                )
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MinkMetric("SOCIAL", formatDuration(summary.socialMillis), Icons.Default.Schedule, Modifier.weight(1f))
                    MinkMetric("GOAL", socialGoalLabel(store.socialGoalMinutes), Icons.Default.Flag, Modifier.weight(1f))
                    MinkMetric("OPENS", summary.socialOpensToday.toString(), Icons.Default.TouchApp, Modifier.weight(1f))
                }
            }
            if (summary.topApps.isNotEmpty()) {
                item { SectionLabel("TRACKED APP TRAIL") }
                items(summary.topApps, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.packageName, actions = null, size = 38.dp)
                        Column(Modifier.weight(1f).padding(start = 11.dp)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Tracked", color = Muted, fontSize = 12.sp)
                        }
                        Text(formatDuration(app.foregroundMillis), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { SectionLabel("MAKE IT YOURS") }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Daily social goal", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SOCIAL_GOAL_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = store.socialGoalMinutes == minutes,
                            onClick = { store.updateSocialGoalMinutes(minutes) },
                            label = { Text(socialGoalLabel(minutes)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Surface(
                    onClick = { showSocialApps = true },
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, tint = Rust)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text("Apps you want to limit", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (store.socialPackages.isEmpty()) "Automatic Android categories" else "${store.socialPackages.size} selected",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Lock, null, Modifier.size(17.dp), tint = Muted)
                Text(
                    "Activity and insights stay on this device and are never sent to Katoa Apps.",
                    Modifier.padding(start = 8.dp).weight(1f),
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
    if (showSocialApps) {
        SocialAppsDialog(store, repository) {
            showSocialApps = false
            permissionReturnToken++
        }
    }
}

@Composable
internal fun MinkHomeIcon(
    store: LauncherStore,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { UsageInsightsRepository(context.applicationContext) }
    val summary by rememberMinkDaySummary(store, repository, isActive)
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = if (summary.needsAttention()) {
                "Mink’s Day, insight needs attention"
            } else {
                "Mink’s Day"
            }
        },
    ) {
        BadgedBox(
            badge = {
                if (!summary.isLoading && summary.needsAttention()) {
                    Badge()
                }
            },
        ) {
            MinkSprite(summary.state, Modifier.size(32.dp))
        }
    }
}

@Composable
private fun rememberMinkDaySummary(
    store: LauncherStore,
    repository: UsageInsightsRepository,
    isActive: Boolean,
    externalRefreshToken: Int = 0,
): State<MinkDaySummary> {
    val context = LocalContext.current
    var lifecycleRefreshToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(isActive) {
        if (isActive) {
            lifecycleRefreshToken++
            while (true) {
                delay(5 * 60_000L)
                lifecycleRefreshToken++
            }
        }
    }
    DisposableEffect(context, isActive) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isActive) lifecycleRefreshToken++
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    return produceState(
        initialValue = MinkDaySummary.loading(),
        lifecycleRefreshToken,
        externalRefreshToken,
        store.socialGoalMinutes,
    ) {
        if (isActive) {
            val socialPackages = store.socialPackages.toSet()
            val socialGoalMinutes = store.socialGoalMinutes
            value = withContext(Dispatchers.IO) {
                runCatching { repository.summary(socialPackages, socialGoalMinutes) }
                    .getOrElse { MinkDaySummary.unavailable(repository.hasAccess()) }
            }
        }
    }
}

@Composable
private fun MinkHero(summary: MinkDaySummary) {
    val transition = rememberInfiniteTransition(label = "mink-breathe")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (summary.state == MinkState.SLEEPING) 1.5f else -5f,
        animationSpec = infiniteRepeatable(tween(1_300), RepeatMode.Reverse),
        label = "mink-bob",
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MinkSprite(summary.state, Modifier.size(164.dp).graphicsLayer { translationY = bob })
            Text(summary.headline, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(summary.detail, Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MinkMetric(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Rust)
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
    }
}

@Composable
private fun UsageAccessCard(onEnable: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, null, tint = Rust)
            Text("Optional Usage Access", Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
        }
        Text("Android can let MinkLauncher Open measure foreground activity for the social apps you track. Other apps are excluded from your trail and totals.", color = Muted, fontSize = 13.sp)
        Text("No activity history, tracked-app list, or insight is uploaded to Katoa Apps.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) { Text("Open Usage Access") }
    }
}

@Composable
private fun MinkErrorCard(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.errorContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Usage data unavailable", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, null)
            Text("Try again", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun MinkSprite(state: MinkState, modifier: Modifier = Modifier) {
    val sheet = ImageBitmap.imageResource(R.drawable.mink_states)
    val index = when (state) {
        MinkState.WALKING -> 0
        MinkState.PURPOSEFUL -> 1
        MinkState.PHONE -> 2
        MinkState.DISTRACTED -> 3
        MinkState.RESTING -> 4
        MinkState.SLEEPING -> 5
    }
    Canvas(modifier.aspectRatio(1f)) {
        val cellWidth = sheet.width / 3
        val cellHeight = sheet.height / 2
        drawImage(
            image = sheet,
            srcOffset = IntOffset((index % 3) * cellWidth, (index / 3) * cellHeight),
            srcSize = IntSize(cellWidth, cellHeight),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

@Composable
internal fun SocialAppsDialog(store: LauncherStore, repository: UsageInsightsRepository, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<LaunchableApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { repository.launchableApps() }
    }
    val visible = remember(apps, query) { apps.orEmpty().filter { it.label.contains(query.trim(), ignoreCase = true) } }
    val automaticPackages by produceState<Set<String>>(initialValue = emptySet(), apps) {
        value = withContext(Dispatchers.IO) { repository.automaticSocialPackages(apps.orEmpty()) }
    }
    val selectedPackages = effectiveTrackedPackages(store.socialPackages.toSet(), automaticPackages)
    LaunchedEffect(apps) {
        apps?.takeIf { it.isNotEmpty() }
            ?.let { store.reconcileSocialApps(it.map(LaunchableApp::packageName).toSet()) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Apps, null, tint = Rust) },
        title = { Text("Choose tracked apps") },
        text = {
            Column {
                Text(
                    if (store.socialPackages.isEmpty()) {
                        "Automatic mode currently recognizes ${automaticPackages.size} installed app${if (automaticPackages.size == 1) "" else "s"} as social. Select the apps you want to limit to replace Android’s categories."
                    } else {
                        "${store.socialPackages.size} selected. Only these apps appear in your trail and totals."
                    },
                    color = Muted,
                    fontSize = 13.sp,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Find an app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
                if (selectedPackages.isNotEmpty()) {
                    Text(
                        "TRACKED · TAP TO REMOVE",
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    LazyRow(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(apps.orEmpty().filter { it.packageName in selectedPackages }, key = { it.packageName }) { app ->
                            Column(
                                Modifier.width(70.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable {
                                        store.replaceSocialApps(selectedPackages - app.packageName)
                                    }.padding(horizontal = 4.dp, vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box {
                                    AppIcon(app.packageName, actions = null, size = 31.dp)
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.error,
                                    ) {
                                        Icon(Icons.Default.Close, "Remove ${app.label}", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onError)
                                    }
                                }
                                Text(
                                    app.label,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
                when {
                    apps == null -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                    visible.isEmpty() -> Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        Text(if (query.isBlank()) "No launchable apps found" else "No matching apps", color = Muted)
                    }
                    else -> LazyColumn(Modifier.heightIn(min = 140.dp, max = 330.dp)) {
                        items(visible, key = { it.packageName }) { app ->
                            val selected = app.packageName in selectedPackages
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                store.replaceSocialApps(
                                    if (selected) selectedPackages - app.packageName else selectedPackages + app.packageName,
                                )
                            }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.packageName, actions = null, size = 34.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                Text(app.label, maxLines = 1)
                                if (app.packageName in automaticPackages) {
                                    Text("Android default: Social", color = Rust, fontSize = 10.sp)
                                }
                            }
                            Checkbox(
                                checked = selected,
                                onCheckedChange = {
                                    store.replaceSocialApps(
                                        if (it) selectedPackages + app.packageName else selectedPackages - app.packageName,
                                    )
                                },
                            )
                        }
                    }
                        }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = store::clearSocialApps) { Text("Restore Android defaults") }
        },
    )
}
