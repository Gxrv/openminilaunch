@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.katoaapps.openminilaunch

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val TODO_ITEMS_PER_PAGE = 3

@Composable
internal fun HomeScreen(
    store: LauncherStore,
    actions: DeviceActions,
    openSettings: () -> Unit,
    openTodos: () -> Unit,
    openHub: () -> Unit,
    openMinkDay: () -> Unit,
    minkStatusActive: Boolean,
    onMagicExpandedChange: (Boolean) -> Unit,
    keyboardInputEnabled: Boolean,
) {
    val context = LocalContext.current
    var drawerOpen by remember { mutableStateOf(false) }
    var todoJumpToken by remember { mutableIntStateOf(0) }
    var flyingTodo by remember { mutableStateOf<String?>(null) }
    var flightActive by remember { mutableStateOf(false) }
    var widgetCenter by remember { mutableStateOf(Offset.Zero) }
    var magicCenter by remember { mutableStateOf(Offset.Zero) }
    var magicExpanded by remember { mutableStateOf(false) }
    var showLockDisclosure by remember { mutableStateOf(false) }
    val flightProgress = remember { Animatable(0f) }
    val lockServiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (actions.isLockServiceEnabled()) actions.lockDevice()
    }

    fun lockFromHome() {
        if (!actions.supportsLockScreenAction()) {
            Toast.makeText(context, "Double-tap lock requires Android 9 or newer", Toast.LENGTH_SHORT).show()
        } else if (!actions.lockDevice()) {
            showLockDisclosure = true
        }
    }

    LaunchedEffect(flyingTodo) {
        if (flyingTodo != null && widgetCenter != Offset.Zero && magicCenter != Offset.Zero) {
            flightProgress.snapTo(0f)
            flightActive = true
            flightProgress.animateTo(1f, tween(1_300, easing = FastOutSlowInEasing))
            flightActive = false
            flyingTodo = null
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var distance = 0f
            detectVerticalDragGestures(
                onDragStart = { distance = 0f },
                onVerticalDrag = { _, amount -> if (amount > 0) distance += amount },
                onDragEnd = { if (distance > 140f) actions.expandNotificationShade() },
            )
        }.pointerInput(magicExpanded) {
            if (!magicExpanded) detectTapGestures(onDoubleTap = { lockFromHome() })
        },
    ) {
        val qwertyHome = maxHeight <= maxWidth * 1.55f
        val homeHorizontalPadding = if (qwertyHome) 14.dp else 22.dp
        val headerActionSize = if (qwertyHome) 40.dp else 48.dp
        val headerIconSize = if (qwertyHome) 21.dp else 24.dp
        val focusPanelHeight = (maxWidth * .78f).coerceIn(310.dp, 350.dp)
        Column(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .blur(if (magicExpanded) 10.dp else 0.dp),
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides headerActionSize) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = homeHorizontalPadding)
                        .padding(vertical = if (qwertyHome) 0.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MinkHomeIcon(
                        store = store,
                        isActive = minkStatusActive,
                        onClick = openMinkDay,
                        modifier = Modifier.size(headerActionSize),
                    )
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d")).uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.5.sp,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = openHub, modifier = Modifier.size(headerActionSize)) {
                        BadgedBox(
                            badge = {
                                val count = NotificationHub.conversations().size
                                if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) }
                            },
                        ) { Icon(Icons.Default.Forum, "Conversations", Modifier.size(headerIconSize)) }
                    }
                    IconButton(onClick = openSettings, modifier = Modifier.size(headerActionSize)) {
                        Icon(Icons.Default.Settings, "Settings", Modifier.size(headerIconSize))
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .padding(horizontal = homeHorizontalPadding, vertical = if (qwertyHome) 2.dp else 10.dp),
            ) {
                val focusModifier = if (qwertyHome) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().height(focusPanelHeight).align(Alignment.TopCenter)
                }
                Surface(
                    modifier = focusModifier.widthIn(max = 620.dp).align(if (qwertyHome) Alignment.Center else Alignment.TopCenter),
                    shape = RoundedCornerShape(if (qwertyHome) 26.dp else 34.dp),
                    color = MinkForest,
                    contentColor = LightPaper,
                    shadowElevation = if (isSystemInDarkTheme()) 2.dp else 8.dp,
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(if (qwertyHome) 10.dp else 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (qwertyHome) 8.dp else 12.dp),
                    ) {
                        TodoPager(
                            store,
                            openTodos,
                            todoJumpToken,
                            compact = qwertyHome,
                            embedded = true,
                            modifier = Modifier.weight(2f).fillMaxHeight().onGloballyPositioned { coordinates ->
                                val origin = coordinates.positionInRoot()
                                widgetCenter = origin + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                            },
                        )
                        ShortcutGrid(
                            store = store,
                            actions = actions,
                            openTodos = openTodos,
                            compact = qwertyHome,
                            contentColor = LightPaper,
                            itemContainerColor = LightPaper.copy(alpha = .09f),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) { drawerOpen = true }
                    }
                }
            }
            Spacer(
                Modifier.navigationBarsPadding()
                    .height(if (qwertyHome) 64.dp else 52.dp),
            )
        }
        flyingTodo?.takeIf { flightActive }?.let { text ->
            val progress = flightProgress.value
            val position = Offset(
                x = magicCenter.x + (widgetCenter.x - magicCenter.x) * progress,
                y = magicCenter.y + (widgetCenter.y - magicCenter.y) * progress,
            )
            val alpha = if (progress < .72f) 1f else ((1f - progress) / .28f).coerceIn(0f, 1f)
            Surface(
                modifier = Modifier.offset {
                    IntOffset((position.x - 100).roundToInt(), (position.y - 26).roundToInt())
                }.graphicsLayer { this.alpha = alpha }
                    .zIndex(10f).shadow(8.dp, RoundedCornerShape(18.dp)),
                color = Color(0xFFD6A300),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(Modifier.widthIn(max = 200.dp).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Checklist, null, Modifier.size(18.dp), tint = LightInk)
                    Text(text, Modifier.padding(start = 7.dp), maxLines = 1, color = LightInk, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        MagicBox(
            store = store,
            actions = actions,
            modifier = Modifier.fillMaxSize().zIndex(if (magicExpanded) 20f else 0f),
            collapsedModifier = Modifier.widthIn(max = 620.dp).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .onGloballyPositioned { coordinates ->
                    val origin = coordinates.positionInRoot()
                    magicCenter = origin + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                },
            keyboardInputEnabled = keyboardInputEnabled,
            onTodoAdded = { text ->
                flyingTodo = text
                todoJumpToken++
            },
            onExpandedChange = { magicExpanded = it; onMagicExpandedChange(it) },
        )
    }

    if (showLockDisclosure) {
        LockAccessibilityDisclosureDialog(
            onContinue = {
                showLockDisclosure = false
                lockServiceLauncher.launch(actions.lockAccessibilitySettingsIntent())
            },
            onDismiss = { showLockDisclosure = false },
        )
    }

    if (drawerOpen) {
        ModalBottomSheet(onDismissRequest = { drawerOpen = false }, containerColor = MaterialTheme.colorScheme.background) {
            Text("YOUR DRAWER", Modifier.padding(horizontal = 24.dp), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            if (store.drawerPackages.isEmpty()) {
                Text("Choose up to five apps in Settings.", Modifier.padding(24.dp), color = Muted)
            } else {
                store.drawerPackages.forEach { packageName ->
                    ListItem(
                        headlineContent = { Text(actions.appLabel(packageName)) },
                        leadingContent = { AppIcon(packageName, actions, 38.dp) },
                        modifier = Modifier.clickable { actions.launchPackage(packageName); drawerOpen = false },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
internal fun TodoPager(
    store: LauncherStore,
    openTodos: () -> Unit,
    jumpToken: Int,
    compact: Boolean = false,
    embedded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val pages = maxOf(1, ceil(store.todos.size / TODO_ITEMS_PER_PAGE.toFloat()).toInt())
    val pagerState = rememberPagerState(pageCount = { pages })
    LaunchedEffect(jumpToken, pages) {
        if (jumpToken > 0) {
            val newestUnfinishedPage = store.todos.indexOfLast { !it.completed }
                .coerceAtLeast(0) / TODO_ITEMS_PER_PAGE
            pagerState.animateScrollToPage(newestUnfinishedPage.coerceAtMost(pages - 1))
        }
    }
    val shape = RoundedCornerShape(if (compact) 18.dp else 24.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(if (embedded) MinkForestPanel.copy(alpha = .78f) else MinkForestPanel)
            .then(if (embedded) Modifier else Modifier.border(1.dp, Sage.copy(alpha = .42f), shape))
            .clickable(onClick = openTodos)
            .padding(if (compact) 8.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TO-DO", color = LightPaper, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text("${pagerState.currentPage + 1}/$pages", color = Sage, fontSize = 12.sp)
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
            val pageItems = store.todos
                .drop(page * TODO_ITEMS_PER_PAGE)
                .take(TODO_ITEMS_PER_PAGE)
            if (pageItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text("Tap to add your first item", color = Sage)
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
                    pageItems.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Checkbox(
                                checked = item.completed,
                                onCheckedChange = { store.toggleTodo(item.id) },
                                colors = CheckboxDefaults.colors(checkedColor = Rust, uncheckedColor = Sage, checkmarkColor = LightPaper),
                                modifier = Modifier.size(if (compact) 24.dp else 26.dp),
                            )
                            Text(
                                item.text,
                                color = if (item.completed) Sage else LightPaper,
                                fontSize = if (compact) 13.sp else 15.sp,
                                lineHeight = if (compact) 17.sp else 20.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                                modifier = Modifier.padding(start = 7.dp, top = 2.dp).weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShortcutGrid(
    store: LauncherStore,
    actions: DeviceActions,
    openTodos: () -> Unit,
    compact: Boolean = false,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    itemContainerColor: Color = Color.Transparent,
    modifier: Modifier = Modifier,
    openDrawer: () -> Unit,
) {
    val icons = mapOf(
        Shortcut.NOTE to Icons.Default.EditNote, Shortcut.EVENT to Icons.Default.Event,
        Shortcut.WEATHER to Icons.Default.Cloud, Shortcut.TODO to Icons.Default.CheckCircle,
        Shortcut.CALL to Icons.Default.Call, Shortcut.MESSAGE to Icons.AutoMirrored.Filled.Message,
        Shortcut.FILES to Icons.Default.FolderOpen, Shortcut.DRAWER to Icons.Default.GridView,
    )
    var editing by remember { mutableStateOf(false) }
    val draftOrder = remember { mutableStateListOf<Shortcut>().apply { addAll(store.shortcutOrder) } }
    val gridState = rememberLazyGridState()
    val hapticFeedback = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        draftOrder[to.index] = draftOrder[from.index].also {
            draftOrder[from.index] = draftOrder[to.index]
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun beginEditing() {
        draftOrder.clear()
        draftOrder.addAll(store.shortcutOrder)
        editing = true
    }

    fun cancelEditing() {
        draftOrder.clear()
        draftOrder.addAll(store.shortcutOrder)
        editing = false
    }

    fun finishEditing() {
        store.setShortcutOrder(draftOrder.toList())
        editing = false
    }

    BackHandler(enabled = editing) { cancelEditing() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (editing) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::cancelEditing) { Icon(Icons.Default.Close, "Cancel shortcut reorder", tint = contentColor) }
                IconButton(onClick = ::finishEditing) { Icon(Icons.Default.Check, "Save shortcut order", tint = contentColor) }
            }
        }
        val visibleOrder: List<Shortcut> = if (editing) draftOrder else store.shortcutOrder
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            userScrollEnabled = false,
        ) {
            items(visibleOrder, key = Shortcut::name) { shortcut ->
                ReorderableItem(reorderableState, key = shortcut.name) { isDragging ->
                    val shortcutIndex = Shortcut.entries.indexOf(shortcut)
                    val jiggleAngle = if (editing) {
                        val jiggle = rememberInfiniteTransition(label = "${shortcut.name} jiggle")
                        jiggle.animateFloat(
                            initialValue = if (shortcutIndex % 2 == 0) -1.15f else 1.15f,
                            targetValue = if (shortcutIndex % 2 == 0) 1.15f else -1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = 125 + (shortcutIndex % 3) * 18,
                                    easing = LinearEasing,
                                ),
                                repeatMode = RepeatMode.Reverse,
                            ),
                            label = "${shortcut.name} rotation",
                        ).value
                    } else {
                        0f
                    }
                    val interactionSource = remember { MutableInteractionSource() }
                    val interactionModifier = if (editing) {
                        Modifier.draggableHandle(
                            interactionSource = interactionSource,
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        )
                    } else {
                        Modifier.combinedClickable(
                            onClick = {
                                actions.launchShortcut(shortcut, store.shortcutPackages[shortcut], openTodos, openDrawer)
                            },
                            onLongClick = ::beginEditing,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (compact) 42.dp else 50.dp)
                            .animateItem()
                            .zIndex(if (isDragging) 4f else 0f)
                            .graphicsLayer {
                                rotationZ = if (isDragging) 0f else jiggleAngle
                                if (isDragging) {
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                    shadowElevation = 14.dp.toPx()
                                }
                            }
                            .clip(RoundedCornerShape(if (compact) 14.dp else 18.dp))
                            .background(
                                if (editing) contentColor.copy(alpha = .17f)
                                else itemContainerColor
                            )
                            .then(interactionModifier)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icons.getValue(shortcut),
                            shortcut.label,
                            Modifier.size(if (compact) 24.dp else 28.dp),
                            tint = contentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MagicBox(
    store: LauncherStore,
    actions: DeviceActions,
    modifier: Modifier = Modifier,
    collapsedModifier: Modifier = Modifier,
    keyboardInputEnabled: Boolean = true,
    initiallyExpanded: Boolean = false,
    showSoftwareKeyboardOnStart: Boolean = false,
    onTodoAdded: (String) -> Unit = {},
    onExpandedChange: (Boolean) -> Unit = {},
    onSessionComplete: () -> Unit = {},
) {
    val context = LocalContext.current
    val fileSearchRepository = remember { FileSearchRepository(context.applicationContext) }
    var text by remember { mutableStateOf(TextFieldValue()) }
    var selectedContact by remember { mutableStateOf<ContactResult?>(null) }
    var lockedPrefix by remember { mutableStateOf<Char?>(null) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var hasContacts by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasMediaAccess by remember { mutableStateOf(hasMediaReadAccess(context)) }
    var showFileScopeChoice by remember { mutableStateOf(false) }
    var showAiPicker by remember { mutableStateOf(false) }
    var showAllAiApps by remember { mutableStateOf(false) }
    var pendingAiQuery by remember { mutableStateOf<String?>(null) }
    var callToConfirm by remember { mutableStateOf<ContactResult?>(null) }
    var pendingPermissionCall by remember { mutableStateOf<ContactResult?>(null) }
    var smsToConfirm by remember { mutableStateOf<PendingSms?>(null) }
    var pendingPermissionSms by remember { mutableStateOf<PendingSms?>(null) }
    var pendingAssistantSms by remember { mutableStateOf<PendingSms?>(null) }
    var showSmsSentConfirmation by remember { mutableStateOf(false) }
    var smsSentConfirmationToken by remember { mutableIntStateOf(0) }
    var showSmsAssistantDisclosure by remember { mutableStateOf(false) }
    var fileResults by remember { mutableStateOf<List<FileSearchResult>>(emptyList()) }
    var fileSearchLoading by remember { mutableStateOf(false) }
    val fileSearchRequests = remember { FileSearchRequestTracker() }
    var aiAppsLoaded by remember { mutableStateOf(false) }
    val curatedAiApps by produceState<List<LaunchableApp>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { actions.curatedAiApps() }
        aiAppsLoaded = true
    }
    var allAiAppsLoaded by remember { mutableStateOf(false) }
    val allAiApps by produceState<List<LaunchableApp>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { actions.textShareApps() }
        allAiAppsLoaded = true
    }
    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                hasMediaAccess = hasMediaReadAccess(context)
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasContacts = granted
        if (!granted && isPermanentlyDenied(context, Manifest.permission.READ_CONTACTS)) actions.openAppSettings()
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val contact = pendingPermissionCall
        pendingPermissionCall = null
        if (granted && contact != null) {
            actions.placeCall(contact.phone)
        } else if (!granted && isPermanentlyDenied(context, Manifest.permission.CALL_PHONE)) {
            actions.openAppSettings()
        }
        onSessionComplete()
    }
    fun completeSmsAttempt(draft: PendingSms) {
        fun openComposerFallback(message: String) {
            val opened = actions.chooseMessagingApp(draft.contact, draft.body)
            Toast.makeText(
                context,
                if (opened) message else "Couldn’t send SMS or find a compatible messaging app",
                Toast.LENGTH_LONG,
            ).show()
        }
        when (actions.sendSmsDirect(draft.contact.phone, draft.body)) {
            DirectSmsResult.QUEUED -> {
                showSmsSentConfirmation = true
                smsSentConfirmationToken++
            }
            DirectSmsResult.NO_DEFAULT_SUBSCRIPTION -> {
                openComposerFallback("Choose a SIM in a messaging app to send")
                onSessionComplete()
            }
            DirectSmsResult.NOT_AUTHORIZED, DirectSmsResult.UNSUPPORTED, DirectSmsResult.FAILED -> {
                openComposerFallback("Direct SMS unavailable. Choose a messaging app instead.")
                onSessionComplete()
            }
        }
    }
    LaunchedEffect(smsSentConfirmationToken) {
        if (smsSentConfirmationToken > 0) {
            delay(1_100)
            showSmsSentConfirmation = false
            delay(350)
            onSessionComplete()
        }
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val draft = pendingPermissionSms
        pendingPermissionSms = null
        if (granted && draft != null) {
            completeSmsAttempt(draft)
        } else {
            if (!granted && isPermanentlyDenied(context, Manifest.permission.SEND_SMS)) actions.openAppSettings()
            onSessionComplete()
        }
    }
    val assistantSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val draft = pendingAssistantSms
        pendingAssistantSms = null
        if (draft != null && actions.isAssistantRoleHeld()) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                completeSmsAttempt(draft)
            } else {
                pendingPermissionSms = draft
                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            }
        } else if (draft != null) {
            smsToConfirm = draft
            Toast.makeText(context, "Choose Mink Assistant to enable direct SMS", Toast.LENGTH_LONG).show()
        }
    }
    fun sendDirectOrRequestAccess(draft: PendingSms) {
        when {
            !supportsDirectSms(context) -> completeSmsAttempt(draft)
            !actions.isAssistantRoleHeld() -> {
                pendingAssistantSms = draft
                showSmsAssistantDisclosure = true
            }
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED -> {
                completeSmsAttempt(draft)
            }
            else -> {
                pendingPermissionSms = draft
                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            }
        }
    }

    fun chooseMessagingApp(draft: PendingSms) {
        val opened = actions.chooseMessagingApp(draft.contact, draft.body)
        if (!opened) Toast.makeText(context, "No compatible messaging app found", Toast.LENGTH_LONG).show()
        onSessionComplete()
    }
    val mediaPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasMediaAccess = hasMediaReadAccess(context)
        if (!hasMediaAccess && mediaPermissionPermanentlyDenied(context)) actions.openAppSettings()
        showFileScopeChoice = true
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            store.addSearchFolder(it.toString(), fileSearchRepository.folderLabel(it))
            fileSearchRepository.invalidateFolders()
        }
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focusRequestSerial by remember { mutableIntStateOf(0) }
    var focusRequestShowsKeyboard by remember { mutableStateOf(true) }
    var textFieldPlaced by remember { mutableStateOf(false) }
    val magicResultsScroll = rememberScrollState()
    val prefix = lockedPrefix ?: text.text.firstOrNull()
    val searchTerm = if (lockedPrefix == null) text.text.drop(1).substringBefore(' ').trim() else ""
    val contactResults = remember(prefix, searchTerm, hasContacts) {
        if (prefix in listOf('@', '#') && hasContacts && selectedContact == null) actions.searchContacts(searchTerm).take(5) else emptyList()
    }
    val appResults = remember(prefix, searchTerm) {
        if (prefix == '?' && searchTerm.isNotBlank()) {
            actions.installedApps().filter { it.label.startsWith(searchTerm, true) }.take(5)
        } else emptyList()
    }
    val plainQuery = text.text.trim().takeIf { prefix !in listOf('@', '#', '-', '$', '+', '?') }.orEmpty()
    val indexedFolderUris = store.searchFolders.map { it.uri }
    LaunchedEffect(plainQuery, indexedFolderUris, hasMediaAccess) {
        val request = fileSearchRequests.begin(plainQuery)
        if (plainQuery.length < 2) {
            fileResults = emptyList()
            fileSearchLoading = false
            magicResultsScroll.scrollTo(0)
        } else {
            fileSearchLoading = true
            try {
                delay(180)
                val folders = store.searchFolders.toList()
                val results = withContext(Dispatchers.IO) {
                    fileSearchRepository.search(request.query, folders, hasMediaAccess)
                }
                if (fileSearchRequests.isCurrent(request)) {
                    fileResults = results
                    magicResultsScroll.scrollTo(0)
                }
            } finally {
                if (fileSearchRequests.isCurrent(request)) fileSearchLoading = false
            }
        }
    }
    val actionColor = when (prefix) {
        '@' -> Color(0xFF2563EB)
        '#' -> Color(0xFF198754)
        '-' -> Color(0xFFD6A300)
        '$' -> Color(0xFF7C3AED)
        '+' -> Color(0xFF8B5A2B)
        '?' -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.primary
    }
    val actionIcon = when (prefix) {
        '@' -> Icons.AutoMirrored.Filled.Send
        '#' -> Icons.Default.Phone
        '-' -> Icons.Default.Checklist
        '$' -> Icons.AutoMirrored.Filled.NoteAdd
        '+' -> Icons.Default.Event
        '?' -> Icons.Default.Apps
        else -> Icons.Default.Search
    }
    val actionContentColor = when (prefix) {
        '-' -> LightInk
        null -> MaterialTheme.colorScheme.onPrimary
        else -> Color.White
    }
    fun refocus(showSoftwareKeyboard: Boolean = true) {
        focusRequestShowsKeyboard = showSoftwareKeyboard
        focusRequestSerial += 1
    }

    fun clearCommand() {
        fileSearchRequests.invalidate()
        text = TextFieldValue()
        selectedContact = null
        lockedPrefix = null
        fileResults = emptyList()
        fileSearchLoading = false
    }

    fun dismiss() {
        clearCommand()
        keyboard?.hide()
        expanded = false
        onExpandedChange(false)
        onSessionComplete()
    }

    fun collapseForDialog() {
        clearCommand()
        keyboard?.hide()
        expanded = false
        onExpandedChange(false)
    }

    fun submitAi() {
        val query = plainQuery
        if (query.isBlank()) return
        val preferredPackage = store.preferredAiPackage
        if (!preferredPackage.isNullOrBlank() && actions.shareQueryWithApp(query, preferredPackage)) {
            store.addSearchQuery(query)
            dismiss()
        } else {
            if (!preferredPackage.isNullOrBlank()) store.resetPreferredAiApp()
            pendingAiQuery = query
            showAiPicker = true
            keyboard?.hide()
        }
    }

    fun submit() {
        val payload = if (lockedPrefix != null) text.text.trim() else text.text.drop(1).trim()
        val handled = when (prefix) {
            '-' -> payload.isNotBlank().also { if (it) { store.addTodo(payload); onTodoAdded(payload) } }
            '$' -> payload.isNotBlank().also {
                if (it) actions.createNote(payload, store.shortcutPackages[Shortcut.NOTE])
            }
            '+' -> payload.isNotBlank().also { if (it) actions.createEvent(payload) }
            '@' -> (selectedContact != null && payload.isNotBlank()).also {
                if (it) {
                    val draft = PendingSms(selectedContact!!, payload)
                    collapseForDialog()
                    when (store.messageSendMode) {
                        MessageSendMode.ALWAYS_ASK -> smsToConfirm = draft
                        MessageSendMode.DIRECT_SMS -> sendDirectOrRequestAccess(draft)
                        MessageSendMode.MESSAGING_APP -> chooseMessagingApp(draft)
                    }
                }
            }
            '#' -> false
            '?' -> false
            else -> text.text.isNotBlank() && actions.webSearch(text.text, store.preferredWebPackage).also {
                if (it) store.addSearchQuery(text.text)
            }
        }
        if (handled && prefix != '@') {
            clearCommand()
            dismiss()
        }
    }

    LaunchedEffect(keyboardInputEnabled) {
        if (keyboardInputEnabled) {
            while (!textFieldPlaced) withFrameNanos { }
            withFrameNanos { }
            focusRequester.requestFocus()
            withFrameNanos { }
            if (initiallyExpanded && showSoftwareKeyboardOnStart) keyboard?.show() else keyboard?.hide()
            if (initiallyExpanded) onExpandedChange(true)
        } else {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(focusRequestSerial) {
        if (focusRequestSerial > 0) {
            // TextField focus can request bring-into-view. Wait until its newly docked parent
            // has completed placement before requesting focus or showing the IME.
            while (!textFieldPlaced) withFrameNanos { }
            withFrameNanos { }
            focusRequester.requestFocus()
            if (focusRequestShowsKeyboard) {
                withFrameNanos { }
                keyboard?.show()
            }
        }
    }

    BackHandler(enabled = expanded) { dismiss() }

    Box(modifier) {
        if (expanded) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .48f)).clickable(onClick = { dismiss() }))
        }

        Column(
            modifier = Modifier
                .matchParentSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (expanded) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(magicResultsScroll),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (text.text.isBlank() && lockedPrefix == null && store.searchHistory.isNotEmpty()) {
                            SearchHistoryList(
                                queries = store.searchHistory,
                                onSelect = { query ->
                                    text = TextFieldValue(query, selection = TextRange(query.length))
                                    refocus()
                                },
                                onDelete = store::removeSearchQuery,
                                onClearAll = store::clearSearchHistory,
                            )
                        }
                        if (plainQuery.isNotBlank() && fileSearchLoading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        if (fileResults.isNotEmpty()) {
                            FileResultsGrid(fileResults, fileSearchRepository) { file ->
                                store.addSearchQuery(plainQuery)
                                actions.openFile(file)
                                dismiss()
                            }
                        }
                        if (plainQuery.length >= 2 && !hasMediaAccess) {
                            FilledTonalButton(onClick = { mediaPermissionLauncher.launch(mediaReadPermissions()) }) {
                                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                                Text("Search media filenames", Modifier.padding(start = 8.dp))
                            }
                        }
                        if (plainQuery.isNotBlank() && store.searchFolders.isEmpty()) {
                            Surface(
                                onClick = { showFileScopeChoice = true },
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFD6A300).copy(alpha = .18f),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FolderOff, null, tint = Color(0xFFD6A300))
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text("Document search isn’t set up", fontWeight = FontWeight.SemiBold)
                                        Text("Choose a folder for local document search.", color = Muted, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null)
                                }
                            }
                        }
                        if (prefix !in listOf('@', '#', '-', '$', '+', '?') && text.text.isNotBlank()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchDestinationButton(
                                    label = "Web",
                                    detail = "Search browser",
                                    icon = Icons.Default.Public,
                                    modifier = Modifier.weight(1f),
                                    onClick = { submit() },
                                )
                                SearchDestinationButton(
                                    label = "AI",
                                    detail = store.preferredAiPackage?.let(actions::appLabel) ?: "Choose an app",
                                    icon = Icons.Default.AutoAwesome,
                                    modifier = Modifier.weight(1f),
                                    onClick = { submitAi() },
                                )
                            }
                        }
                        contactResults.forEach { contact ->
                            SuggestionRow("${contact.name}  ·  ${contact.phoneLabel}", Icons.Default.Person) {
                                if (prefix == '#') {
                                    callToConfirm = contact
                                    collapseForDialog()
                                } else {
                                    lockedPrefix = prefix
                                    selectedContact = contact
                                    text = TextFieldValue()
                                    refocus()
                                }
                            }
                        }
                        appResults.forEach { app ->
                            SuggestionRow(
                                text = app.label,
                                leadingContent = { AppIcon(app.packageName, actions, 26.dp) },
                            ) {
                                if (actions.launchPackage(app.packageName)) dismiss()
                            }
                        }
                        if (prefix in listOf('@', '#') && !hasContacts) {
                            FilledTonalButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                                Text("Allow contacts to search people")
                            }
                        }
                    }
                }
            }

            if (expanded && text.text.isBlank() && lockedPrefix == null) {
                MagicBoxLegend(prefix, enabled = true) { key ->
                    clearCommand()
                    text = TextFieldValue(key.toString(), selection = TextRange(1))
                    refocus()
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (expanded) 1f else 0f },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    selectedContact?.let { contact ->
                        CommandChip("${contact.name} · ${contact.phoneLabel}", actionColor, actionContentColor) {
                            clearCommand()
                            refocus()
                        }
                    }
                    TextField(
                        value = text,
                        onValueChange = { value ->
                            text = value
                            if (!expanded && value.text.isNotEmpty()) {
                                expanded = true
                                onExpandedChange(true)
                            }
                            if (lockedPrefix == null && value.text.firstOrNull() != prefix) {
                                selectedContact = null
                            }
                        },
                        placeholder = { Text("@  #  -  $  +  ?", color = Muted) },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester)
                            .onGloballyPositioned { textFieldPlaced = true }
                            .onPreviewKeyEvent { event ->
                                if (prefix == '-' && event.key == Key.Enter) {
                                    if (event.type == KeyEventType.KeyUp) submit()
                                    true
                                } else false
                            },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        minLines = 1,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            showKeyboardOnFocus = false,
                            imeAction = when {
                                prefix == '$' -> ImeAction.Default
                                prefix in listOf('@', '#', '-', '+', '?') -> ImeAction.Send
                                else -> ImeAction.Search
                            },
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { submit() },
                            onSend = { submit() },
                        ),
                    )
                    FilledIconButton(
                        onClick = { submit() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = actionColor,
                            contentColor = actionContentColor,
                        ),
                    ) { Icon(actionIcon, "Run command") }
                }
            }
        }

        if (!expanded && !showSmsSentConfirmation) {
            Row(
                collapsedModifier.align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable {
                        clearCommand()
                        expanded = true
                        onExpandedChange(true)
                        refocus(showSoftwareKeyboard = true)
                    }
                    .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Magic box  @  #  -  $  +  ?", Modifier.weight(1f), color = Muted, fontSize = 14.sp)
                FilledIconButton(
                    onClick = {
                        clearCommand()
                        expanded = true
                        onExpandedChange(true)
                        refocus(showSoftwareKeyboard = true)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.Keyboard, "Open Magic Box")
                }
            }
        }

        AnimatedVisibility(
            visible = showSmsSentConfirmation,
            modifier = Modifier.align(Alignment.Center).zIndex(30f),
            enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = .88f),
            exit = fadeOut(tween(350)) + scaleOut(tween(350), targetScale = .94f),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 16.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(30.dp))
                    Column {
                        Text("Message sent", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Sent as SMS", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f), fontSize = 13.sp)
                    }
                }
            }
        }
    }
    if (showFileScopeChoice) {
        FileSearchScopeDialog(
            onChooseFolder = {
                showFileScopeChoice = false
                folderPicker.launch(null)
            },
            onSkip = { showFileScopeChoice = false },
        )
    }
    if (showSmsAssistantDisclosure) {
        AssistantDisclosureDialog(
            active = false,
            onContinue = {
                showSmsAssistantDisclosure = false
                assistantSettingsLauncher.launch(actions.assistantRoleSelectionIntent())
            },
            onDismiss = {
                showSmsAssistantDisclosure = false
                pendingAssistantSms?.let { draft ->
                    pendingAssistantSms = null
                    smsToConfirm = draft
                } ?: onSessionComplete()
            },
        )
    }
    if (showAiPicker) {
        AppPickerDialog(
            title = "Choose AI app",
            apps = curatedAiApps,
            selected = setOfNotNull(store.preferredAiPackage),
            loading = !aiAppsLoaded,
            emptyMessage = "No curated AI apps were found. Try another compatible app.",
            extraActionLabel = "Other compatible app",
            onExtraAction = { showAiPicker = false; showAllAiApps = true },
            onApp = { app ->
                val query = pendingAiQuery
                if (query != null && actions.shareQueryWithApp(query, app.packageName)) {
                    store.setPreferredAiApp(app.packageName)
                    store.addSearchQuery(query)
                    showAiPicker = false
                    pendingAiQuery = null
                    dismiss()
                }
            },
            onDismiss = {
                showAiPicker = false
                pendingAiQuery = null
                refocus()
            },
        )
    }
    if (showAllAiApps) {
        AppPickerDialog(
            title = "Other compatible apps",
            apps = allAiApps,
            selected = setOfNotNull(store.preferredAiPackage),
            loading = !allAiAppsLoaded,
            emptyMessage = "No other apps currently accept shared text.",
            onApp = { app ->
                val query = pendingAiQuery
                if (query != null && actions.shareQueryWithApp(query, app.packageName)) {
                    store.setPreferredAiApp(app.packageName)
                    store.addSearchQuery(query)
                    showAllAiApps = false
                    pendingAiQuery = null
                    dismiss()
                }
            },
            onDismiss = {
                showAllAiApps = false
                pendingAiQuery = null
                refocus()
            },
        )
    }
    callToConfirm?.let { contact ->
        AlertDialog(
            onDismissRequest = { callToConfirm = null; onSessionComplete() },
            icon = { Icon(Icons.Default.Phone, null, tint = Color(0xFF198754)) },
            title = { Text("Call ${contact.name}?") },
            text = { Text("${contact.phoneLabel}  ·  ${contact.phone}") },
            confirmButton = {
                Button(
                    onClick = {
                        callToConfirm = null
                        if (!supportsDirectCalls(context) || ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            actions.placeCall(contact.phone)
                            onSessionComplete()
                        } else {
                            pendingPermissionCall = contact
                            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754), contentColor = Color.White),
                ) { Text("Call now") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { callToConfirm = null; onSessionComplete() }) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            callToConfirm = null
                            if (!actions.chooseCallingApp(contact.phone)) {
                                Toast.makeText(context, "No compatible calling app found", Toast.LENGTH_LONG).show()
                            }
                            onSessionComplete()
                        },
                    ) { Text("Choose calling app") }
                }
            },
        )
    }
    smsToConfirm?.let { draft ->
        val assistantActive = actions.isAssistantRoleHeld()
        AlertDialog(
            onDismissRequest = { smsToConfirm = null; onSessionComplete() },
            icon = { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF2563EB)) },
            title = { Text("Send message to ${draft.contact.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${draft.contact.phoneLabel}  ·  ${draft.contact.phone}")
                    Text(
                        draft.body,
                        Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                    )
                    Text("This sends through your carrier as SMS, not RCS. Messaging rates may apply, and long messages may use multiple SMS parts.", color = Muted, fontSize = 12.sp)
                    if (!assistantActive) {
                        Text("Choose Mink Assistant first. Google Play restricts direct SMS access to eligible default handlers.", color = Muted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        smsToConfirm = null
                        sendDirectOrRequestAccess(draft)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                ) { Text(if (assistantActive) "Send SMS now" else "Choose assistant") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { smsToConfirm = null; onSessionComplete() }) { Text("Cancel") }
                    TextButton(onClick = { smsToConfirm = null; chooseMessagingApp(draft) }) { Text("Choose messaging app") }
                }
            },
        )
    }
}

private data class PendingSms(val contact: ContactResult, val body: String)

internal enum class FileResultGroup(val label: String) {
    PHOTOS("PHOTOS"), VIDEOS("VIDEOS"), DOCUMENTS("DOCUMENTS"), AUDIO("AUDIO")
}

internal fun fileResultGroup(result: FileSearchResult): FileResultGroup = when {
    result.mimeType.startsWith("image/") -> FileResultGroup.PHOTOS
    result.mimeType.startsWith("video/") -> FileResultGroup.VIDEOS
    result.mimeType.startsWith("audio/") -> FileResultGroup.AUDIO
    else -> FileResultGroup.DOCUMENTS
}

internal fun fileResultIcon(result: FileSearchResult): ImageVector = when {
    result.mimeType.startsWith("image/") -> Icons.Default.Image
    result.mimeType.startsWith("video/") -> Icons.Default.Movie
    result.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    result.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    else -> Icons.Default.InsertDriveFile
}

@Composable
internal fun FileResultsGrid(
    results: List<FileSearchResult>,
    repository: FileSearchRepository,
    onOpen: (FileSearchResult) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
            .clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .96f))
            .padding(horizontal = 9.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        FileResultGroup.entries.forEach { group ->
            val groupedResults = results.filter { fileResultGroup(it) == group }
            if (groupedResults.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header_${group.name}") {
                    Text(
                        group.label,
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(groupedResults, key = { it.uri.toString() }) { file ->
                    FileResultTile(file, repository) { onOpen(file) }
                }
            }
        }
    }
}

@Composable
internal fun FileResultTile(file: FileSearchResult, repository: FileSearchRepository, onClick: () -> Unit) {
    val thumbnail by produceState<android.graphics.Bitmap?>(null, file.uri, file.modifiedAt) {
        value = withContext(Dispatchers.IO) { repository.loadThumbnail(file) }
    }
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                androidx.compose.foundation.Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(fileResultIcon(file), null, Modifier.size(34.dp), tint = Rust)
            }
            if (fileResultGroup(file) == FileResultGroup.VIDEOS) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = .62f)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.padding(5.dp).size(20.dp), tint = Color.White)
                }
            }
        }
        Text(
            file.name,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
internal fun SearchHistoryList(
    queries: List<String>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "RECENT SEARCHES",
                    Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Muted,
                )
                TextButton(onClick = onClearAll) { Text("Clear all") }
            }
            queries.forEach { query ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(query) }
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.History, null, Modifier.size(20.dp), tint = Muted)
                    Text(query, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1)
                    IconButton(onClick = { onDelete(query) }) {
                        Icon(Icons.Default.Close, "Delete $query from search history", tint = Muted)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun MagicBoxLegend(activePrefix: Char?, enabled: Boolean = true, onSelect: (Char) -> Unit) {
    val commands = listOf(
        Triple('@', "Text", Color(0xFF2563EB)),
        Triple('#', "Call", Color(0xFF198754)),
        Triple('-', "To-do", Color(0xFFD6A300)),
        Triple('$', "Note", Color(0xFF7C3AED)),
        Triple('+', "Event", Color(0xFF8B5A2B)),
        Triple('?', "App", Color(0xFFC62828)),
    )
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("HOT KEYS", Modifier.weight(1f), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text("Just type to search", color = Muted, fontSize = 11.sp)
            }
            commands.chunked(3).forEach { rowCommands ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    rowCommands.forEach { (key, label, color) ->
                        val active = activePrefix == key
                        val activeContentColor = if (key == '-') LightInk else Color.White
                        Surface(
                            onClick = { if (enabled) onSelect(key) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) color else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = (if (active) activeContentColor else MaterialTheme.colorScheme.onSurface)
                                .copy(alpha = if (enabled) 1f else .45f),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(key.toString(), fontWeight = FontWeight.Black, color = if (active) activeContentColor else color)
                                Text(label, Modifier.padding(start = 6.dp), fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommandChip(label: String, color: Color, contentColor: Color, onClear: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onClear,
        label = { Text(label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Default.Close, "Clear", Modifier.size(16.dp)) },
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = color, selectedLabelColor = contentColor, selectedTrailingIconColor = contentColor,
        ),
        modifier = Modifier.widthIn(max = 145.dp),
    )
}

@Composable
internal fun SuggestionRow(
    text: String,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) leadingContent() else icon?.let { Icon(it, null, Modifier.size(20.dp)) }
        Text(text, Modifier.padding(start = 10.dp), maxLines = 1, fontSize = 14.sp)
    }
}

@Composable
internal fun SearchDestinationButton(
    label: String,
    detail: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(21.dp))
            Column(Modifier.padding(start = 9.dp)) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(detail, color = Muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
