package com.katoaapps.openminilaunch

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap

/** Shared app-icon rendering used by search, setup, the drawer, and Settings. */
@Composable
internal fun AppIcon(packageName: String, actions: DeviceActions?, size: Dp) {
    val context = LocalContext.current
    val bitmap = remember(packageName, actions) {
        val drawable = actions?.appIcon(packageName)
            ?: runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        drawable?.toBitmap(width = 96, height = 96)?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap, null, Modifier.size(size))
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Apps,
                null,
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(size * .55f),
            )
        }
    }
}
