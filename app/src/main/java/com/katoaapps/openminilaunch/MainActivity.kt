@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

private val LightInk = Color(0xFF1E2A24)
private val LightPaper = Color(0xFFF4F1E8)
private val Sage = Color(0xFFB8C7B0)
private val Rust = Color(0xFFB85C3C)
private val Muted = Color(0xFF6B746D)
private const val FEATURE_UPDATE_ID = "search_destinations_0_5_0"

private fun isPermanentlyDenied(context: android.content.Context, permission: String): Boolean =
    context is Activity &&
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
        !ActivityCompat.shouldShowRequestPermissionRationale(context, permission)

private fun mediaReadPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaReadAccess(context: android.content.Context): Boolean = mediaReadPermissions().any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun mediaPermissionPermanentlyDenied(context: android.content.Context): Boolean =
    !hasMediaReadAccess(context) && mediaReadPermissions().all { isPermanentlyDenied(context, it) }

private fun supportsDirectCalls(context: android.content.Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

class MainActivity : ComponentActivity() {
    private val homeRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private var homeRequestToken by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val store = LauncherStore(this)
        val actions = DeviceActions(this)
        Thread({ actions.installedApps() }, "minilaunch-app-index").start()
        setContent { MiniLaunchApp(store, actions, ::requestHomeRole, homeRequestToken) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeRequestToken++
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(RoleManager::class.java)
            if (manager?.isRoleAvailable(RoleManager.ROLE_HOME) == true && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
                homeRoleLauncher.launch(manager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            }
        } else {
            homeRoleLauncher.launch(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }
}

@Composable
private fun MiniLaunchApp(
    store: LauncherStore,
    actions: DeviceActions,
    requestHomeRole: () -> Unit,
    homeRequestToken: Int,
) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var showTutorial by rememberSaveable { mutableStateOf(!store.onboardingComplete) }
    var showUpdateNotice by rememberSaveable {
        mutableStateOf(store.onboardingComplete && !store.hasSeenUpdate(FEATURE_UPDATE_ID))
    }
    var tutorialRun by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(homeRequestToken) {
        if (homeRequestToken > 0) screen = Screen.HOME
    }
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (store.themePreference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val fallbackColors = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFC8D8C1), onPrimary = Color(0xFF152018),
            background = Color(0xFF101512), surface = Color(0xFF171D19),
            surfaceContainerLow = Color(0xFF1C2420), onSurface = Color(0xFFF1F3EE), secondary = Rust,
        )
    } else {
        lightColorScheme(
            primary = LightInk, onPrimary = LightPaper, background = LightPaper,
            surface = LightPaper, surfaceContainerLow = Color.White, onSurface = LightInk, secondary = Rust,
        )
    }
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else fallbackColors
    val view = LocalView.current
    val onboardingCallPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && isPermanentlyDenied(context, Manifest.permission.CALL_PHONE)) actions.openAppSettings()
        else requestHomeRole()
    }
    val onboardingPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && isPermanentlyDenied(context, Manifest.permission.READ_CONTACTS)) {
            actions.openAppSettings()
        } else if (!supportsDirectCalls(context) || ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            requestHomeRole()
        } else {
            onboardingCallPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }
    SideEffect {
        val window = (context as Activity).window
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
    ) {
        BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
        val imeVisible = WindowInsets.isImeVisible
        BackHandler(enabled = screen == Screen.HOME && !imeVisible) { }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    store = store,
                    actions = actions,
                    openSettings = { screen = Screen.SETTINGS },
                    openTodos = { screen = Screen.TODOS },
                    keyboardInputEnabled = !showTutorial && !showUpdateNotice,
                )
                Screen.SETTINGS -> SettingsScreen(
                    store,
                    actions,
                    requestHomeRole,
                    onRepeatTutorial = { tutorialRun++; showTutorial = true },
                ) { screen = Screen.HOME }
                Screen.TODOS -> TodosScreen(store) { screen = Screen.HOME }
            }
        }
        if (showTutorial) {
            key(tutorialRun) {
                OnboardingDialog(
                    store = store,
                    onFinish = {
                        store.completeOnboarding()
                        store.markUpdateSeen(FEATURE_UPDATE_ID)
                        showTutorial = false
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                            if (!supportsDirectCalls(context) || ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                requestHomeRole()
                            } else {
                                onboardingCallPermission.launch(Manifest.permission.CALL_PHONE)
                            }
                        } else {
                            onboardingPermission.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                )
            }
        }
        if (showUpdateNotice && !showTutorial) {
            FeatureUpdateDialog(
                onOpenSettings = {
                    store.markUpdateSeen(FEATURE_UPDATE_ID)
                    showUpdateNotice = false
                    screen = Screen.SETTINGS
                },
                onReviewTutorial = {
                    store.markUpdateSeen(FEATURE_UPDATE_ID)
                    showUpdateNotice = false
                    tutorialRun++
                    showTutorial = true
                },
                onNotNow = {
                    store.markUpdateSeen(FEATURE_UPDATE_ID)
                    showUpdateNotice = false
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    store: LauncherStore,
    actions: DeviceActions,
    openSettings: () -> Unit,
    openTodos: () -> Unit,
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
    val flightPosition = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
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
            flightPosition.snapTo(magicCenter)
            flightActive = true
            flightPosition.animateTo(widgetCenter, tween(650, easing = FastOutSlowInEasing))
            flightActive = false
            flyingTodo = null
        }
    }

    Box(
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
        Column(
            Modifier.fillMaxSize().blur(if (magicExpanded) 10.dp else 0.dp)
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d")).uppercase(),
                    letterSpacing = 1.5.sp,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, "Settings") }
            }

            TodoPager(
                store,
                openTodos,
                todoJumpToken,
                Modifier.onGloballyPositioned { coordinates ->
                    val origin = coordinates.positionInRoot()
                    widgetCenter = origin + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                },
            )
            ShortcutGrid(store, actions, openTodos) { drawerOpen = true }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(52.dp))
        }
        flyingTodo?.takeIf { flightActive }?.let { text ->
            Surface(
                modifier = Modifier.offset {
                    IntOffset((flightPosition.value.x - 100).roundToInt(), (flightPosition.value.y - 26).roundToInt())
                }.zIndex(10f).shadow(8.dp, RoundedCornerShape(18.dp)),
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
            collapsedModifier = Modifier.fillMaxWidth().navigationBarsPadding()
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
            onExpandedChange = { magicExpanded = it },
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
private fun TodoPager(store: LauncherStore, openTodos: () -> Unit, jumpToken: Int, modifier: Modifier = Modifier) {
    val pages = maxOf(1, ceil(store.todos.size / 2f).toInt())
    val pagerState = rememberPagerState(pageCount = { pages })
    LaunchedEffect(jumpToken, pages) {
        if (jumpToken > 0) pagerState.animateScrollToPage(pages - 1)
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(LightInk).clickable(onClick = openTodos).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("TO-DO", color = LightPaper, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text("${pagerState.currentPage + 1}/$pages", color = Sage, fontSize = 12.sp)
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(58.dp)) { page ->
            val pageItems = store.todos.drop(page * 2).take(2)
            if (pageItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text("Tap to add your first item", color = Sage)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    pageItems.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.completed,
                                onCheckedChange = { store.toggleTodo(item.id) },
                                colors = CheckboxDefaults.colors(checkedColor = Rust, uncheckedColor = Sage, checkmarkColor = LightPaper),
                                modifier = Modifier.size(26.dp),
                            )
                            Text(
                                item.text,
                                color = if (item.completed) Sage else LightPaper,
                                maxLines = 1,
                                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutGrid(store: LauncherStore, actions: DeviceActions, openTodos: () -> Unit, openDrawer: () -> Unit) {
    val icons = mapOf(
        Shortcut.NOTE to Icons.Default.EditNote, Shortcut.EVENT to Icons.Default.Event,
        Shortcut.WEATHER to Icons.Default.Cloud, Shortcut.TODO to Icons.Default.CheckCircle,
        Shortcut.CALL to Icons.Default.Call, Shortcut.MESSAGE to Icons.AutoMirrored.Filled.Message,
        Shortcut.FILES to Icons.Default.FolderOpen, Shortcut.DRAWER to Icons.Default.GridView,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Shortcut.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { shortcut ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable {
                            actions.launchShortcut(shortcut, store.shortcutPackages[shortcut], openTodos, openDrawer)
                        }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icons.getValue(shortcut), shortcut.label, tint = MaterialTheme.colorScheme.onSurface)
                        Text(shortcut.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MagicBox(
    store: LauncherStore,
    actions: DeviceActions,
    modifier: Modifier = Modifier,
    collapsedModifier: Modifier = Modifier,
    keyboardInputEnabled: Boolean = true,
    onTodoAdded: (String) -> Unit = {},
    onExpandedChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val fileSearchRepository = remember { FileSearchRepository(context.applicationContext) }
    var text by remember { mutableStateOf(TextFieldValue()) }
    var selectedContact by remember { mutableStateOf<ContactResult?>(null) }
    var selectedApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var lockedPrefix by remember { mutableStateOf<Char?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var hasContacts by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasMediaAccess by remember { mutableStateOf(hasMediaReadAccess(context)) }
    var showFileScopeChoice by remember { mutableStateOf(false) }
    var callToConfirm by remember { mutableStateOf<ContactResult?>(null) }
    var pendingPermissionCall by remember { mutableStateOf<ContactResult?>(null) }
    var fileResults by remember { mutableStateOf<List<FileSearchResult>>(emptyList()) }
    var fileSearchLoading by remember { mutableStateOf(false) }
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
    val keyboard = LocalSoftwareKeyboardController.current
    val prefix = lockedPrefix ?: text.text.firstOrNull()
    val searchTerm = if (lockedPrefix == null) text.text.drop(1).substringBefore(' ').trim() else ""
    val contactResults = remember(prefix, searchTerm, hasContacts) {
        if (prefix in listOf('@', '#') && hasContacts && selectedContact == null) actions.searchContacts(searchTerm).take(5) else emptyList()
    }
    val appResults = remember(prefix, searchTerm) {
        if (prefix == '?' && searchTerm.isNotBlank() && selectedApp == null) {
            actions.installedApps().filter { it.label.startsWith(searchTerm, true) }.take(5)
        } else emptyList()
    }
    val plainQuery = text.text.trim().takeIf { prefix !in listOf('@', '#', '-', '$', '+', '?') }.orEmpty()
    val indexedFolderUris = store.searchFolders.map { it.uri }
    LaunchedEffect(plainQuery, indexedFolderUris, hasMediaAccess) {
        if (plainQuery.length < 2) {
            fileResults = emptyList()
            fileSearchLoading = false
        } else {
            fileSearchLoading = true
            delay(180)
            fileResults = withContext(Dispatchers.IO) {
                fileSearchRepository.search(plainQuery, store.searchFolders.toList(), hasMediaAccess)
            }
            fileSearchLoading = false
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
        '?' -> Icons.Default.Warning
        else -> Icons.Default.Search
    }
    val actionContentColor = when (prefix) {
        '-' -> LightInk
        null -> MaterialTheme.colorScheme.onPrimary
        else -> Color.White
    }
    val inputLocked = selectedApp != null || (prefix == '#' && selectedContact != null)

    fun refocus(showSoftwareKeyboard: Boolean = true) {
        focusRequester.requestFocus()
        if (showSoftwareKeyboard) keyboard?.show()
    }

    fun clearCommand() {
        text = TextFieldValue()
        selectedContact = null
        selectedApp = null
        lockedPrefix = null
        fileResults = emptyList()
    }

    fun dismiss() {
        clearCommand()
        keyboard?.hide()
        expanded = false
        onExpandedChange(false)
    }

    fun submit() {
        val payload = if (lockedPrefix != null) text.text.trim() else text.text.drop(1).trim()
        val handled = when (prefix) {
            '-' -> payload.isNotBlank().also { if (it) { store.addTodo(payload); onTodoAdded(payload) } }
            '$' -> payload.isNotBlank().also {
                if (it) actions.createNote(payload, store.shortcutPackages[Shortcut.NOTE])
            }
            '+' -> payload.isNotBlank().also { if (it) actions.createEvent(payload) }
            '@' -> (selectedContact != null).also {
                if (it) actions.composeMessage(
                    selectedContact!!.phone,
                    payload,
                    store.shortcutPackages[Shortcut.MESSAGE],
                )
            }
            '#' -> (selectedContact != null).also { if (it) callToConfirm = selectedContact }
            '?' -> (selectedApp != null).also { if (it) actions.launchPackage(selectedApp!!.packageName) }
            else -> text.text.isNotBlank() && actions.webSearch(text.text, store.preferredWebPackage).also {
                if (it) store.addSearchQuery(text.text)
            }
        }
        if (handled) {
            clearCommand()
            dismiss()
        }
    }

    LaunchedEffect(keyboardInputEnabled) {
        if (keyboardInputEnabled) {
            focusRequester.requestFocus()
            keyboard?.hide()
        }
    }

    BackHandler(enabled = expanded) { dismiss() }

    Box(modifier) {
        if (expanded) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .48f)).clickable(onClick = { dismiss() }))
        } else {
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

        Column(
            modifier = if (expanded) {
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .statusBarsPadding().imePadding().padding(horizontal = 18.dp, vertical = 18.dp)
            } else {
                Modifier.size(1.dp).align(Alignment.BottomCenter).graphicsLayer { alpha = 0f }
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            selectedContact?.let { contact -> CommandChip(contact.name, actionColor, actionContentColor) { clearCommand(); refocus() } }
                            selectedApp?.let { app -> CommandChip(app.label, actionColor, actionContentColor) { clearCommand(); refocus() } }
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
                                        selectedApp = null
                                    }
                                },
                                placeholder = { Text("@  #  -  $  +  ?", color = Muted) },
                                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                ),
                                minLines = 1,
                                maxLines = 5,
                                readOnly = inputLocked,
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
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = actionColor, contentColor = actionContentColor),
                            ) { Icon(actionIcon, "Run command") }
                        }
                    }
            if (expanded) {
                    MagicBoxLegend(prefix, enabled = !inputLocked) { key ->
                        clearCommand()
                        text = TextFieldValue(key.toString(), selection = TextRange(1))
                        refocus()
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
                                    Text("Choose a folder to search documents.", color = Muted, fontSize = 12.sp)
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
                        }
                    }
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
                    contactResults.forEach { contact ->
                        SuggestionRow("${contact.name}  ·  ${contact.phone}", Icons.Default.Person) {
                            lockedPrefix = prefix
                            selectedContact = contact
                            text = TextFieldValue()
                            if (prefix == '#') keyboard?.hide() else refocus()
                        }
                    }
                    appResults.forEach { app ->
                        SuggestionRow(app.label, Icons.Default.Apps) {
                            lockedPrefix = '?'
                            selectedApp = app
                            text = TextFieldValue()
                            keyboard?.hide()
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
    if (showFileScopeChoice) {
        FileSearchScopeDialog(
            onChooseFolder = {
                showFileScopeChoice = false
                folderPicker.launch(null)
            },
            onSkip = { showFileScopeChoice = false },
        )
    }
    callToConfirm?.let { contact ->
        AlertDialog(
            onDismissRequest = { callToConfirm = null },
            icon = { Icon(Icons.Default.Phone, null, tint = Color(0xFF198754)) },
            title = { Text("Call ${contact.name}?") },
            text = { Text(contact.phone) },
            confirmButton = {
                Button(
                    onClick = {
                        callToConfirm = null
                        if (!supportsDirectCalls(context) || ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            actions.placeCall(contact.phone)
                        } else {
                            pendingPermissionCall = contact
                            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF198754), contentColor = Color.White),
                ) { Text("Call") }
            },
            dismissButton = { TextButton(onClick = { callToConfirm = null }) { Text("Cancel") } },
        )
    }
}

private enum class FileResultGroup(val label: String) {
    PHOTOS("PHOTOS"), VIDEOS("VIDEOS"), DOCUMENTS("DOCUMENTS"), AUDIO("AUDIO")
}

private fun fileResultGroup(result: FileSearchResult): FileResultGroup = when {
    result.mimeType.startsWith("image/") -> FileResultGroup.PHOTOS
    result.mimeType.startsWith("video/") -> FileResultGroup.VIDEOS
    result.mimeType.startsWith("audio/") -> FileResultGroup.AUDIO
    else -> FileResultGroup.DOCUMENTS
}

private fun fileResultIcon(result: FileSearchResult): ImageVector = when {
    result.mimeType.startsWith("image/") -> Icons.Default.Image
    result.mimeType.startsWith("video/") -> Icons.Default.Movie
    result.mimeType.startsWith("audio/") -> Icons.Default.AudioFile
    result.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    else -> Icons.Default.InsertDriveFile
}

@Composable
private fun FileResultsGrid(
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
private fun FileResultTile(file: FileSearchResult, repository: FileSearchRepository, onClick: () -> Unit) {
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
private fun SearchHistoryList(
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
private fun MagicBoxLegend(activePrefix: Char?, enabled: Boolean = true, onSelect: (Char) -> Unit) {
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
private fun CommandChip(label: String, color: Color, contentColor: Color, onClear: () -> Unit) {
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
private fun SuggestionRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Text(text, Modifier.padding(start = 10.dp), maxLines = 1, fontSize = 14.sp)
    }
}

@Composable
private fun SearchDestinationButton(
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

@Composable
private fun FeatureUpdateDialog(
    onOpenSettings: () -> Unit,
    onReviewTutorial: () -> Unit,
    onNotNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        icon = { Icon(Icons.Default.AutoAwesome, null, tint = Rust) },
        title = { Text("What’s new in 0.5.0") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Sharper search and shortcuts", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                UpdatePoint(Icons.Default.Public, "System-owned web search", "Web searches are delegated to Android’s selected search handler.")
                UpdatePoint(Icons.Default.NoteAdd, "Better notes", "The Note shortcut and $ command now prefer Android’s dedicated create-note action.")
                UpdatePoint(Icons.Default.FolderOpen, "Files up front", "Files replaces Camera in the shortcut grid. Weather opens an app you explicitly configure.")
                TextButton(onClick = onReviewTutorial, contentPadding = PaddingValues(0.dp)) {
                    Text("Review the updated tutorial")
                }
            }
        },
        confirmButton = { Button(onClick = onOpenSettings) { Text("Choose apps") } },
        dismissButton = { TextButton(onClick = onNotNow) { Text("Not now") } },
    )
}

@Composable
private fun FileSearchScopeDialog(
    onChooseFolder: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        icon = { Icon(Icons.Default.FolderOpen, null, tint = Rust) },
        title = { Text("Where should MinkLauncher search?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Choose a document folder to search. You can add or remove folders later in Settings.")
                Surface(
                    onClick = onChooseFolder,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Box(Modifier.padding(14.dp)) {
                        UpdatePoint(
                            Icons.Default.Folder,
                            "Choose a folder",
                            "Recommended. MinkLauncher searches only folders you approve through Android.",
                        )
                    }
                }
                Text(
                    "Search queries and filenames stay on your device and are never sent to us.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onSkip) { Text("Skip for now") } },
    )
}

@Composable
private fun UpdatePoint(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(22.dp), tint = Rust)
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun OnboardingDialog(store: LauncherStore, onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(
        "A quieter home screen",
        "Meet the Magic Box",
        "To-dos, kept close",
        "Search locally or beyond",
        "Permissions, on your terms",
    )
    val icons = listOf(Icons.Default.Keyboard, Icons.Default.AutoAwesome, Icons.Default.Checklist, Icons.Default.ManageSearch, Icons.Default.Security)
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            Modifier.fillMaxWidth(.92f).fillMaxHeight(.82f),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().padding(26.dp)) {
                Icon(icons[page], null, Modifier.size(46.dp), tint = Rust)
                Text(titles[page], fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp))
                Spacer(Modifier.height(18.dp))
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    when (page) {
                        0 -> Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("MinkLauncher is a minimal keyboard launcher designed around fast, keyboard-based input.", fontSize = 18.sp)
                            OnboardingPoint(Icons.Default.FilterAlt, "Less visual noise", "One home page, eight shortcuts, and only five drawer apps.")
                            OnboardingPoint(Icons.Default.Keyboard, "Just start typing", "On a physical-keyboard phone, press any text key from home. The Magic Box appears with that first character already entered.")
                            OnboardingPoint(Icons.Default.Search, "Everything is still reachable", "Use ? to find any installed app.")
                        }
                        1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("The Magic Box is ready from the moment home opens. No tap is required on a physical keyboard.", color = Muted, fontSize = 12.sp)
                            listOf(
                                "@" to "Text a contact",
                                "#" to "Call a contact",
                                "-" to "Create a to-do",
                                "$" to "Send text to a notes app",
                                "+" to "Create a calendar event",
                                "?" to "Find and launch an app",
                            ).forEach { (key, description) -> MagicKeyRow(key, description) }
                            Text("Messages open in your configured SMS/RCS app for review. Calls require confirmation in MinkLauncher before they are placed.", color = Muted, fontSize = 12.sp)
                        }
                        2 -> Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("Type - followed by your task to send it straight to the home widget.", fontSize = 18.sp)
                            OnboardingPoint(Icons.Default.Swipe, "Swipe the widget", "Each page shows two items at a time.")
                            OnboardingPoint(Icons.Default.TouchApp, "Tap the widget", "Open the full list to check, edit, delete, add, or rearrange items.")
                            OnboardingPoint(Icons.Default.CheckCircle, "Keep the context", "New Magic Box tasks animate into the newest to-do page.")
                        }
                        3 -> Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("Type normally in the Magic Box to find local files or delegate a web query to Android’s selected search handler.", fontSize = 18.sp)
                            OnboardingPoint(Icons.Default.History, "Recent searches", "Your last five plain-text searches stay on this device. Reuse one, delete one, or clear them all from the empty Magic Box.")
                            OnboardingPoint(Icons.Default.PhotoLibrary, "Media filenames", "Optional access finds photos, videos, and audio through Android’s media index.")
                            OnboardingPoint(Icons.Default.FolderOpen, "Choose your scope", "Select only the document folders you want MinkLauncher to search.")
                            OnboardingPoint(Icons.Default.Security, "Never sent to us", "MinkLauncher does not receive your queries, filenames, or selected folder locations.")
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("MinkLauncher asks only for access tied to features you use.", fontSize = 18.sp)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Contacts, null, tint = Rust)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text("Contacts", fontWeight = FontWeight.Bold)
                                    Text("Only read when you search for a person.", color = Muted, fontSize = 13.sp)
                                }
                            }
                            OnboardingPoint(Icons.Default.Phone, "Direct calls", "Call access is used only after you choose a contact and approve the confirmation dialog.")
                            OnboardingPoint(Icons.Default.PhotoLibrary, "Media", "Optional access searches photo, video, and audio filenames locally.")
                            OnboardingPoint(Icons.Default.Lock, "Double Tap to Lock Screen", "Optional accessibility access performs only Android's Lock screen action after you double-tap empty Home space. It does not inspect screen content.")
                            Text("For documents, Android lets you choose specific folders. MinkLauncher Open does not request All files access.", color = Muted, fontSize = 13.sp)
                            Text("Android will show Contacts and Call prompts next. Optional media and Double Tap to Lock Screen access are enabled later from Settings.", color = Muted)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(titles.size) { index ->
                            Box(Modifier.size(if (index == page) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (index == page) Rust else Sage))
                        }
                    }
                    if (page > 0) TextButton(onClick = { page-- }) { Text("Back") }
                    Button(onClick = { if (page < titles.lastIndex) page++ else onFinish() }) {
                        Text(if (page < titles.lastIndex) "Next" else "Finish setup")
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPoint(icon: ImageVector, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = Rust, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MagicKeyRow(key: String, description: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(LightInk), contentAlignment = Alignment.Center) {
            Text(key, color = LightPaper, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Text(description, Modifier.padding(start = 12.dp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsScreen(
    store: LauncherStore,
    actions: DeviceActions,
    requestHomeRole: () -> Unit,
    onRepeatTutorial: () -> Unit,
    goBack: () -> Unit,
) {
    val context = LocalContext.current
    val fileSearchRepository = remember { FileSearchRepository(context.applicationContext) }
    val apps by produceState<List<LaunchableApp>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { actions.installedApps() }
    }
    var webAppsLoaded by remember { mutableStateOf(false) }
    val webApps by produceState<List<LaunchableApp>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { actions.webSearchApps() }
        webAppsLoaded = true
    }
    var contactsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var mediaGranted by remember { mutableStateOf(hasMediaReadAccess(context)) }
    var callsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
    }
    var lockServiceEnabled by remember { mutableStateOf(actions.isLockServiceEnabled()) }
    var showLockDisclosure by remember { mutableStateOf(false) }
    val directCallsSupported = remember(context) { supportsDirectCalls(context) }
    var showFileScopeChoice by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val lifecycle = (context as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                mediaGranted = hasMediaReadAccess(context)
                callsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                lockServiceEnabled = actions.isLockServiceEnabled()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsGranted = granted
        if (!granted && isPermanentlyDenied(context, Manifest.permission.READ_CONTACTS)) actions.openAppSettings()
    }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        callsGranted = granted
        if (!granted && isPermanentlyDenied(context, Manifest.permission.CALL_PHONE)) actions.openAppSettings()
    }
    val mediaPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        mediaGranted = hasMediaReadAccess(context)
        if (!mediaGranted && mediaPermissionPermanentlyDenied(context)) actions.openAppSettings()
        showFileScopeChoice = true
    }
    val lockServiceSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        lockServiceEnabled = actions.isLockServiceEnabled()
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            store.addSearchFolder(it.toString(), fileSearchRepository.folderLabel(it))
            fileSearchRepository.invalidateFolders()
        }
    }
    var pickingShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var pickingDrawer by remember { mutableStateOf(false) }
    var pickingWeb by remember { mutableStateOf(false) }
    LaunchedEffect(webAppsLoaded, webApps, store.preferredWebPackage) {
        if (webAppsLoaded && store.preferredWebPackage != null && webApps.none { it.packageName == store.preferredWebPackage }) {
            store.resetPreferredWebApp()
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageHeader("Settings", goBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel("LAUNCHER")
            SettingsRow("Default home app", "Choose MinkLauncher as your launcher", Icons.Default.Home) { requestHomeRole() }
            HorizontalDivider(color = Sage)
            SectionLabel("APPEARANCE")
            ThemeChooser(store.themePreference, store::setTheme)
            HorizontalDivider(color = Sage)
            SectionLabel("SHORTCUT APPS")
            Text("Choose the app each shortcut opens. To-do and Drawer stay built in.", color = Muted, fontSize = 13.sp)
            Shortcut.entries.filterNot { it in listOf(Shortcut.TODO, Shortcut.DRAWER) }.forEach { shortcut ->
                SettingsRow(
                    shortcut.label,
                    store.shortcutPackages[shortcut]?.let(actions::appLabel) ?: "System default",
                    Icons.Default.ChevronRight,
                ) { pickingShortcut = shortcut }
            }
            HorizontalDivider(color = Sage)
            SectionLabel("APP DRAWER")
            SettingsRow("Selected apps", "${store.drawerPackages.size} of 5", Icons.Default.Apps) { pickingDrawer = true }
            Text("Use ? in the Magic Box to find any other installed app.", color = Muted, fontSize = 13.sp)
            HorizontalDivider(color = Sage)
            SectionLabel("SEARCH")
            SettingsRow(
                "Web app",
                store.preferredWebPackage?.let(actions::appLabel) ?: "System search handler",
                Icons.Default.Public,
            ) { pickingWeb = true }
            Text(
                "Web queries are delegated to Android’s selected search handler. MinkLauncher Open does not choose or embed a provider.",
                color = Muted,
                fontSize = 13.sp,
            )
            HorizontalDivider(color = Sage)
            SectionLabel("PERMISSIONS")
            PermissionCard(
                title = "Contacts",
                description = "Used only for @ messages and # calls.",
                granted = contactsGranted,
                icon = Icons.Default.Contacts,
                onGrant = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                onManage = { actions.openAppSettings() },
            )
            if (directCallsSupported) {
                PermissionCard(
                    title = "Direct calls",
                    description = "Used only after you confirm a # call in MinkLauncher.",
                    granted = callsGranted,
                    icon = Icons.Default.Phone,
                    onGrant = { callPermission.launch(Manifest.permission.CALL_PHONE) },
                    onManage = { actions.openAppSettings() },
                )
            } else {
                SettingsRow("Direct calls", "Not supported on this device; # uses the dialer", Icons.Default.Phone, enabled = false) { }
            }
            PermissionCard(
                title = "Photos, videos & audio",
                description = "Searches media filenames locally. MinkLauncher never uploads your library.",
                granted = mediaGranted,
                icon = Icons.Default.PhotoLibrary,
                onGrant = { mediaPermission.launch(mediaReadPermissions()) },
                onManage = { actions.openAppSettings() },
            )
            if (actions.supportsLockScreenAction()) {
                PermissionCard(
                    title = "Double Tap to Lock Screen",
                    description = "Uses Android's Lock screen accessibility action only after you double-tap empty Home space.",
                    granted = lockServiceEnabled,
                    icon = Icons.Default.Lock,
                    onGrant = { showLockDisclosure = true },
                    onManage = { actions.openLockAccessibilitySettings() },
                )
            } else {
                SettingsRow("Double Tap to Lock Screen", "Requires Android 9 or newer", Icons.Default.Lock, enabled = false) { }
            }
            HorizontalDivider(color = Sage)
            SectionLabel("FILE SEARCH")
            Text(
                "Choose specific document folders. Only filenames are indexed, and everything remains on this device.",
                color = Muted,
                fontSize = 13.sp,
            )
            OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CreateNewFolder, null)
                Text("Add search folder", Modifier.padding(start = 8.dp))
            }
            if (store.searchFolders.isEmpty()) {
                Text("No document folders selected.", color = Muted, fontSize = 12.sp)
            } else {
                store.searchFolders.forEach { folder ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Folder, null, tint = Rust)
                        Text(folder.label, Modifier.weight(1f).padding(horizontal = 10.dp), maxLines = 1)
                        IconButton(onClick = {
                            runCatching {
                                context.contentResolver.releasePersistableUriPermission(
                                    android.net.Uri.parse(folder.uri),
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            }
                            store.removeSearchFolder(folder.uri)
                            fileSearchRepository.invalidateFolders()
                        }) { Icon(Icons.Default.Close, "Remove ${folder.label}") }
                    }
                }
            }
            HorizontalDivider(color = Sage)
            SectionLabel("INFO")
            SettingsRow("Email us", "contact@katoaapps.com", Icons.Default.Email) { actions.emailSupport() }
            SettingsRow("Repeat tutorial", "Review setup, Magic Box, to-dos, and permissions", Icons.Default.School) { onRepeatTutorial() }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text("Version", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(BuildConfig.VERSION_NAME, color = Muted)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLockDisclosure) {
        LockAccessibilityDisclosureDialog(
            onContinue = {
                showLockDisclosure = false
                lockServiceSettings.launch(actions.lockAccessibilitySettingsIntent())
            },
            onDismiss = { showLockDisclosure = false },
        )
    }

    pickingShortcut?.let { shortcut ->
        AppPickerDialog(
            title = "Choose ${shortcut.label} app",
            apps = apps,
            selected = setOfNotNull(store.shortcutPackages[shortcut]),
            onApp = { store.assignShortcut(shortcut, it.packageName); pickingShortcut = null },
            onReset = { store.resetShortcut(shortcut); pickingShortcut = null },
            onDismiss = { pickingShortcut = null },
        )
    }
    if (pickingDrawer) {
        AppPickerDialog(
            title = "Drawer apps · ${store.drawerPackages.size}/5",
            apps = apps,
            selected = store.drawerPackages.toSet(),
            onApp = { app ->
                val addingFifth = app.packageName !in store.drawerPackages && store.drawerPackages.size == 4
                store.toggleDrawerApp(app.packageName)
                if (addingFifth) Toast.makeText(context, "5 apps selected", Toast.LENGTH_SHORT).show()
            },
            onSelectionLimit = {
                Toast.makeText(context, "Maximum of 5 apps selected", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { pickingDrawer = false },
            multiSelect = true,
        )
    }
    if (pickingWeb) {
        AppPickerDialog(
            title = "Choose web search app",
            apps = webApps,
            selected = setOfNotNull(store.preferredWebPackage),
            loading = !webAppsLoaded,
            emptyMessage = "No web search apps found.",
            onApp = { store.setPreferredWebApp(it.packageName); pickingWeb = false },
            onReset = { store.resetPreferredWebApp(); pickingWeb = false },
            resetLabel = "Use system search handler",
            onDismiss = { pickingWeb = false },
        )
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
}

@Composable
private fun AppPickerDialog(
    title: String,
    apps: List<LaunchableApp>,
    selected: Set<String>,
    onApp: (LaunchableApp) -> Unit,
    onReset: (() -> Unit)? = null,
    resetLabel: String = "Reset to system default",
    onDismiss: () -> Unit,
    multiSelect: Boolean = false,
    loading: Boolean = true,
    emptyMessage: String = "No apps found.",
    onSelectionLimit: () -> Unit = {},
    extraActionLabel: String? = null,
    onExtraAction: () -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val letters = remember { ('A'..'Z').toList() }
    var railHeight by remember { mutableIntStateOf(1) }
    var railLetterIndex by remember { mutableIntStateOf(0) }
    var railDragging by remember { mutableStateOf(false) }
    LaunchedEffect(railLetterIndex, apps) {
        if (apps.isNotEmpty()) {
            val letter = letters[railLetterIndex]
            val index = apps.indexOfFirst { (it.label.firstOrNull()?.uppercaseChar() ?: 'Z') >= letter }
                .let { if (it < 0) apps.lastIndex else it }
            if (index >= 0) gridState.scrollToItem(index)
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.78f), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                onReset?.let {
                    TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
                        Text(resetLabel, Modifier.padding(start = 6.dp))
                    }
                }
                extraActionLabel?.let { label ->
                    TextButton(onClick = onExtraAction, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Icon(Icons.Default.MoreHoriz, null, Modifier.size(18.dp))
                        Text(label, Modifier.padding(start = 6.dp))
                    }
                }
                if (multiSelect && selected.isNotEmpty()) {
                    Text(
                        "SELECTED · TAP TO REMOVE",
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        apps.filter { it.packageName in selected }.take(5).forEach { app ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable { onApp(app) }.padding(horizontal = 3.dp, vertical = 7.dp),
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
                                Text(app.label, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        repeat((5 - selected.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                    }
                    HorizontalDivider(Modifier.padding(top = 10.dp), color = Sage)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (apps.isEmpty() && loading) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurface)
                    } else if (apps.isEmpty()) {
                        Text(emptyMessage, Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center, color = Muted)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp, end = 34.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(apps, key = { it.packageName }) { app ->
                                val isSelected = app.packageName in selected
                                Column(
                                    Modifier.padding(5.dp).clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) Sage else MaterialTheme.colorScheme.surfaceContainerLow)
                                        .clickable {
                                            if (multiSelect && !isSelected && selected.size >= 5) onSelectionLimit()
                                            else onApp(app)
                                        }
                                        .padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AppIcon(app.packageName, actions = null, size = 42.dp)
                                    Text(app.label, textAlign = TextAlign.Center, fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(top = 7.dp))
                                }
                            }
                        }
                        Column(
                            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp)
                                .onSizeChanged { railHeight = it.height }
                                .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background.copy(alpha = .94f))
                                .pointerInput(apps, railHeight) {
                                    fun selectAt(y: Float) {
                                        railLetterIndex = ((y / railHeight) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                                    }
                                    detectVerticalDragGestures(
                                        onDragStart = { railDragging = true; selectAt(it.y) },
                                        onVerticalDrag = { change, _ -> selectAt(change.position.y) },
                                        onDragEnd = { railDragging = false },
                                        onDragCancel = { railDragging = false },
                                    )
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            letters.forEach { letter ->
                                val isFocused = letters.indexOf(letter) == railLetterIndex
                                val scale by animateFloatAsState(
                                    targetValue = if (isFocused) 1.7f else .9f,
                                    animationSpec = spring(dampingRatio = .7f, stiffness = 500f),
                                    label = "rail-letter-scale",
                                )
                                Text(
                                    letter.toString(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFocused) MaterialTheme.colorScheme.onSurface else Rust.copy(alpha = .72f),
                                    modifier = Modifier.clickable {
                                        railLetterIndex = letters.indexOf(letter)
                                    }.graphicsLayer { scaleX = scale; scaleY = scale }
                                        .padding(horizontal = 6.dp),
                                )
                            }
                        }
                        if (railDragging) {
                            Surface(
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 38.dp),
                                shape = CircleShape,
                                color = Rust,
                                shadowElevation = 8.dp,
                            ) {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    Text(letters[railLetterIndex].toString(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String, actions: DeviceActions?, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        val drawable = actions?.appIcon(packageName) ?: runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        drawable?.toBitmap(width = 96, height = 96)?.asImageBitmap()
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(bitmap, null, Modifier.size(size))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Apps, null, tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(size * .55f))
        }
    }
}

@Composable
private fun ThemeChooser(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Light Mode", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ThemePreference.entries.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option.label) },
                    leadingIcon = if (selected == option) ({ Icon(Icons.Default.Check, null, Modifier.size(15.dp)) }) else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    icon: ImageVector,
    onGrant: () -> Unit,
    onManage: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, color = Muted, fontSize = 12.sp)
            }
            if (granted) Icon(Icons.Default.CheckCircle, "Granted", tint = Color(0xFF198754))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onManage) { Text("Manage") }
            if (!granted) FilledTonalButton(onClick = onGrant) { Text("Allow") }
        }
    }
}

@Composable
private fun LockAccessibilityDisclosureDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = Rust) },
        title = { Text("Enable Double Tap to Lock Screen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("To behave like the power button and retain normal fingerprint and face unlock eligibility, MinkLauncher Open uses Android's accessibility Lock screen action.")
                Text("The service runs only when you double-tap empty Home space. It does not observe accessibility events, read screen content, perform gestures, or collect data.")
                Text("In Accessibility settings, enable “MinkLauncher Open - Double Tap to Lock Screen.” You can disable it at any time.")
            }
        },
        confirmButton = { Button(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun TodosScreen(store: LauncherStore, goBack: () -> Unit) {
    var newText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var deleting by remember { mutableStateOf<TodoItem?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageHeader("To-do", goBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it },
                placeholder = { Text("Add a to-do") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { store.addTodo(newText); newText = "" }),
            )
            IconButton(onClick = { store.addTodo(newText); newText = "" }, enabled = newText.isNotBlank()) {
                Icon(Icons.Default.AddCircle, "Add", tint = Rust, modifier = Modifier.size(32.dp))
            }
        }
        if (store.todos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet.", color = Muted)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                itemsIndexed(store.todos, key = { _, item -> item.id }) { index, item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = item.completed, onCheckedChange = { store.toggleTodo(item.id) })
                        Text(
                            item.text,
                            Modifier.weight(1f).clickable { editing = item },
                            textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                            color = if (item.completed) Muted else MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { store.moveTodo(index, -1) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { store.moveTodo(index, 1) }, enabled = index < store.todos.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { deleting = item }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Rust) }
                    }
                }
            }
        }
    }
    editing?.let { item ->
        var editText by remember(item.id) { mutableStateOf(item.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit to-do") },
            text = { OutlinedTextField(editText, { editText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (editText.isNotBlank()) store.renameTodo(item.id, editText); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = Rust) },
            title = { Text("Delete this to-do?") },
            text = { Text("“${item.text}” will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { store.deleteTodo(item.id); deleting = null }) {
                    Text("Delete", color = Rust)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PageHeader(title: String, goBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = Rust, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(icon, null, tint = Muted)
    }
}
