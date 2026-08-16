package com.katoaapps.openminilaunch

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val MINK_WIDGET_HOST_ID = 0x4D494E4B

private enum class WidgetBindingStage { IDLE, BOUND, CONFIGURING }

private data class WidgetAppGroup(
    val packageName: String,
    val appName: String,
    val providers: List<AppWidgetProviderInfo>,
)

private data class WidgetSizeRange(
    val minColumns: Int,
    val maxColumns: Int,
    val minRows: Int,
    val maxRows: Int,
    val preferred: WidgetGridSize,
) {
    val isResizable: Boolean
        get() = minColumns != maxColumns || minRows != maxRows
}

@Composable
internal fun WidgetPage(store: LauncherStore, actions: DeviceActions, goHome: () -> Unit) {
    val context = LocalContext.current
    val activity = context as MainActivity
    val manager = remember { AppWidgetManager.getInstance(context) }
    val host = remember { AppWidgetHost(context, MINK_WIDGET_HOST_ID) }
    var showPicker by remember { mutableStateOf(false) }
    var sizingProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
    var pendingId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var pendingSize by remember { mutableStateOf<WidgetGridSize?>(null) }
    var bindingStage by remember { mutableStateOf(WidgetBindingStage.IDLE) }

    fun abandonPendingWidget() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(pendingId)
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingSize = null
        bindingStage = WidgetBindingStage.IDLE
    }

    fun finishPendingWidget() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val size = pendingSize ?: manager.getAppWidgetInfo(pendingId)?.let {
                widgetSizeRange(it, context.resources.displayMetrics.density).preferred
            }
            if (size != null) store.addWidget(pendingId, size)
        }
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingSize = null
        bindingStage = WidgetBindingStage.IDLE
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) bindingStage = WidgetBindingStage.BOUND else abandonPendingWidget()
    }

    fun beginWidgetBinding(info: AppWidgetProviderInfo, size: WidgetGridSize) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingSize = size
        val cellWidthDp = ((context.resources.configuration.screenWidthDp - 40).coerceAtLeast(280) / 4f)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, (cellWidthDp * size.columns).roundToInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, (cellWidthDp * size.columns).roundToInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (cellWidthDp * size.rows).roundToInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, (cellWidthDp * size.rows).roundToInt())
        }
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, options)) {
            bindingStage = WidgetBindingStage.BOUND
        } else {
            bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options),
            )
        }
    }

    LaunchedEffect(bindingStage, pendingId) {
        if (bindingStage != WidgetBindingStage.BOUND || pendingId == AppWidgetManager.INVALID_APPWIDGET_ID) return@LaunchedEffect
        val info = manager.getAppWidgetInfo(pendingId)
        if (info == null) {
            abandonPendingWidget()
        } else if (info.configure != null) {
            bindingStage = WidgetBindingStage.CONFIGURING
            val launched = activity.configureAppWidget(host, pendingId) { configured ->
                if (configured) finishPendingWidget() else abandonPendingWidget()
            }
            if (!launched) {
                abandonPendingWidget()
                Toast.makeText(context, "This widget couldn't be configured", Toast.LENGTH_SHORT).show()
            }
        } else {
            finishPendingWidget()
        }
    }

    DisposableEffect(activity, host) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> runCatching { host.startListening() }
                Lifecycle.Event.ON_STOP -> host.stopListening()
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) runCatching { host.startListening() }
        onDispose {
            activity.lifecycle.removeObserver(observer)
            host.stopListening()
        }
    }

    LaunchedEffect(Unit) {
        store.widgetIds.toList().forEach { id ->
            val info = manager.getAppWidgetInfo(id)
            if (info == null) {
                store.removeWidget(id)
            } else if (store.widgetSizes[id] == null) {
                store.setWidgetSize(id, widgetSizeRange(info, context.resources.displayMetrics.density).preferred)
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clickable(onClick = goHome)) {
                Text("Widgets", fontSize = 26.sp, fontWeight = FontWeight.Black)
                if (store.widgetIds.isNotEmpty()) {
                    Text("${store.widgetIds.size} of 4", color = Muted, fontSize = 12.sp)
                }
            }
            FilledTonalIconButton(
                onClick = {
                    if (store.widgetIds.size >= 4) Toast.makeText(context, "Maximum of 4 widgets", Toast.LENGTH_SHORT).show()
                    else showPicker = true
                },
            ) { Icon(Icons.Default.Add, "Add widget") }
        }

        if (store.widgetIds.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Widgets, null, Modifier.size(58.dp), tint = Sage)
                Text("Your widget page", fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp))
                Text("Add up to four Android widgets. Their apps control the content they display.", color = Muted, modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = { showPicker = true }) { Icon(Icons.Default.Add, null); Text("Add widget", Modifier.padding(start = 8.dp)) }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(store.widgetIds, key = { _, id -> id }) { index, id ->
                    val info = manager.getAppWidgetInfo(id)
                    if (info != null) {
                        WidgetPanel(
                            host = host,
                            id = id,
                            info = info,
                            gridSize = store.widgetSizes[id]
                                ?: widgetSizeRange(info, context.resources.displayMetrics.density).preferred,
                            canMoveUp = index > 0,
                            canMoveDown = index < store.widgetIds.lastIndex,
                            onMoveUp = { store.moveWidget(id, -1) },
                            onMoveDown = { store.moveWidget(id, 1) },
                            onRemove = { store.removeWidget(id); host.deleteAppWidgetId(id) },
                            onResize = { store.setWidgetSize(id, it) },
                        )
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    if (showPicker) {
        WidgetProviderDialog(
            providers = manager.installedProviders
                .filter { it.widgetCategory == 0 || it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
                .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() },
            actions = actions,
            onSelect = { info ->
                showPicker = false
                val range = widgetSizeRange(info, context.resources.displayMetrics.density)
                if (range.isResizable) sizingProvider = info else beginWidgetBinding(info, range.preferred)
            },
            onDismiss = { showPicker = false },
        )
    }
    sizingProvider?.let { info ->
        val preferred = widgetSizeRange(info, context.resources.displayMetrics.density).preferred
        WidgetSizeDialog(
            info = info,
            current = preferred,
            confirmLabel = "Add widget",
            onConfirm = { size ->
                sizingProvider = null
                beginWidgetBinding(info, size)
            },
            onDismiss = { sizingProvider = null },
        )
    }
}

@Composable
private fun WidgetPanel(
    host: AppWidgetHost,
    id: Int,
    info: AppWidgetProviderInfo,
    gridSize: WidgetGridSize,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onResize: (WidgetGridSize) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizeRange = remember(info.provider, info.minWidth, info.minHeight) {
        widgetSizeRange(info, context.resources.displayMetrics.density)
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var showResize by remember { mutableStateOf(false) }
    var measuredSize by remember(id) { mutableStateOf(IntSize.Zero) }
    val lastReportedSize = remember(id) { intArrayOf(0, 0) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                info.loadLabel(context.packageManager),
                Modifier.weight(1f),
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .5.sp,
                maxLines = 1,
            )
            Box {
                IconButton(onClick = { menuExpanded = true }, Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, "Widget options", Modifier.size(19.dp), tint = Muted)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (sizeRange.isResizable) {
                        DropdownMenuItem(
                            text = { Text("Resize · ${gridSize.label}") },
                            leadingIcon = { Icon(Icons.Default.Widgets, null) },
                            onClick = { menuExpanded = false; showResize = true },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, null) },
                        enabled = canMoveUp,
                        onClick = { menuExpanded = false; onMoveUp() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, null) },
                        enabled = canMoveDown,
                        onClick = { menuExpanded = false; onMoveDown() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onRemove() },
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cellWidth = maxWidth / 4
            val panelWidth = (cellWidth * gridSize.columns)
                .coerceAtLeast(96.dp)
                .coerceAtMost(maxWidth)
            val panelHeight = (cellWidth * gridSize.rows)
                .coerceAtLeast(72.dp)
                .coerceAtMost(420.dp)

            AndroidView(
                factory = { host.createView(it, id, info).apply { setAppWidget(id, info) } },
                update = { view ->
                    if (measuredSize != IntSize.Zero &&
                        (lastReportedSize[0] != measuredSize.width || lastReportedSize[1] != measuredSize.height)
                    ) {
                        reportWidgetSize(view, measuredSize, density.density)
                        lastReportedSize[0] = measuredSize.width
                        lastReportedSize[1] = measuredSize.height
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(panelWidth / maxWidth)
                    .height(panelHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .onSizeChanged { measuredSize = it },
            )
        }
    }
    if (showResize) {
        WidgetSizeDialog(
            info = info,
            current = gridSize,
            confirmLabel = "Apply",
            onConfirm = { showResize = false; onResize(it) },
            onDismiss = { showResize = false },
        )
    }
}

@Composable
private fun WidgetSizeDialog(
    info: AppWidgetProviderInfo,
    current: WidgetGridSize,
    confirmLabel: String,
    onConfirm: (WidgetGridSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val range = remember(info.provider, info.minWidth, info.minHeight) {
        widgetSizeRange(info, context.resources.displayMetrics.density)
    }
    var selected by remember(info.provider, current) {
        mutableStateOf(
            WidgetGridSize(
                current.columns.coerceIn(range.minColumns, range.maxColumns),
                current.rows.coerceIn(range.minRows, range.maxRows),
            ),
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Widget size", fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(info.loadLabel(context.packageManager), color = Muted, fontSize = 13.sp)
                Text(selected.label, fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("WIDTH", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (range.minColumns..range.maxColumns).forEach { columns ->
                        FilterChip(
                            selected = selected.columns == columns,
                            onClick = { selected = selected.copy(columns = columns) },
                            label = { Text("$columns") },
                        )
                    }
                }
                Text("HEIGHT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (range.minRows..range.maxRows).forEach { rows ->
                        FilterChip(
                            selected = selected.rows == rows,
                            onClick = { selected = selected.copy(rows = rows) },
                            label = { Text("$rows") },
                        )
                    }
                }
                Text(
                    "MinkLauncher Open uses a four-column widget grid. The widget app chooses the layout it displays inside this space.",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onConfirm(selected) }, modifier = Modifier.padding(start = 8.dp)) { Text(confirmLabel) }
                }
            }
        }
    }
}

private fun widgetSizeRange(info: AppWidgetProviderInfo, density: Float): WidgetSizeRange {
    fun cellsForPixels(pixels: Int, fallback: Int): Int =
        if (pixels > 0) estimateWidgetCells(pixels / density) else fallback

    val defaultColumns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0) {
        info.targetCellWidth
    } else {
        cellsForPixels(info.minWidth, 2)
    }.coerceIn(1, 4)
    val defaultRows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
        info.targetCellHeight
    } else {
        cellsForPixels(info.minHeight, 2)
    }.coerceIn(1, 5)
    val horizontallyResizable = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
    val verticallyResizable = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
    val minColumns = (if (horizontallyResizable) cellsForPixels(info.minResizeWidth, defaultColumns) else defaultColumns)
        .coerceIn(1, 4)
    val minRows = (if (verticallyResizable) cellsForPixels(info.minResizeHeight, defaultRows) else defaultRows)
        .coerceIn(1, 5)
    val declaredMaxColumns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cellsForPixels(info.maxResizeWidth, 4) else 4
    val declaredMaxRows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cellsForPixels(info.maxResizeHeight, 5) else 5
    val maxColumns = (if (horizontallyResizable) declaredMaxColumns else defaultColumns).coerceIn(minColumns, 4)
    val maxRows = (if (verticallyResizable) declaredMaxRows else defaultRows).coerceIn(minRows, 5)
    return WidgetSizeRange(
        minColumns = minColumns,
        maxColumns = maxColumns,
        minRows = minRows,
        maxRows = maxRows,
        preferred = WidgetGridSize(defaultColumns.coerceIn(minColumns, maxColumns), defaultRows.coerceIn(minRows, maxRows)),
    )
}

private fun estimateWidgetCells(sizeDp: Float): Int = ceil((sizeDp + 30f) / 70f).toInt().coerceAtLeast(1)

@Suppress("DEPRECATION")
private fun reportWidgetSize(view: AppWidgetHostView, size: IntSize, density: Float) {
    val widthDp = size.width / density
    val heightDp = size.height / density
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
    } else {
        view.updateAppWidgetSize(null, widthDp.roundToInt(), heightDp.roundToInt(), widthDp.roundToInt(), heightDp.roundToInt())
    }
}

@Composable
private fun WidgetProviderDialog(
    providers: List<AppWidgetProviderInfo>,
    actions: DeviceActions,
    onSelect: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val groups = remember(providers) {
        providers.groupBy { it.provider.packageName }
            .map { (packageName, appProviders) ->
                WidgetAppGroup(
                    packageName = packageName,
                    appName = actions.appLabel(packageName),
                    providers = appProviders.sortedBy { it.loadLabel(context.packageManager).toString().lowercase() },
                )
            }
            .sortedBy { it.appName.lowercase() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(.86f),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add a widget", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Black)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "header:${group.packageName}") {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(group.packageName, actions, 34.dp)
                                Text(group.appName, Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                        items(
                            items = group.providers.chunked(2),
                            key = { row -> row.joinToString("|") { it.provider.flattenToShortString() } },
                        ) { rowProviders ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowProviders.forEach { info ->
                                    WidgetPreviewCard(info, Modifier.weight(1f)) { onSelect(info) }
                                }
                                if (rowProviders.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (groups.isEmpty()) {
                        item { Text("No widget providers are installed.", color = Muted, modifier = Modifier.padding(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewCard(info: AppWidgetProviderInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.densityDpi
    val sizeRange = remember(info.provider, info.minWidth, info.minHeight) {
        widgetSizeRange(info, context.resources.displayMetrics.density)
    }
    val bitmap = remember(info.provider, info.previewImage, density) {
        val drawable = runCatching { info.loadPreviewImage(context, density) }.getOrNull()
            ?: runCatching { info.loadIcon(context, density) }.getOrNull()
        drawable?.let {
            runCatching {
                val sourceWidth = it.intrinsicWidth.coerceAtLeast(1)
                val sourceHeight = it.intrinsicHeight.coerceAtLeast(1)
                val scale = minOf(360f / sourceWidth, 220f / sourceHeight, 1f)
                it.toBitmap(
                    width = (sourceWidth * scale).roundToInt().coerceAtLeast(1),
                    height = (sourceHeight * scale).roundToInt().coerceAtLeast(1),
                ).asImageBitmap()
            }.getOrNull()
        }
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Box(
                Modifier.fillMaxWidth().height(116.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Icon(Icons.Default.Widgets, null, Modifier.size(42.dp), tint = Muted)
                }
            }
            Text(
                info.loadLabel(context.packageManager),
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
            Text(
                if (sizeRange.isResizable) {
                    "${sizeRange.preferred.label} · resizable"
                } else {
                    sizeRange.preferred.label
                },
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
