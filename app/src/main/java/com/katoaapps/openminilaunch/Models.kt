package com.katoaapps.openminilaunch

import android.net.Uri

data class TodoItem(
    val id: String,
    val text: String,
    val completed: Boolean = false,
)

data class LaunchableApp(
    val label: String,
    val packageName: String,
)

data class ContactResult(
    val name: String,
    val phone: String,
)

data class SearchFolder(
    val uri: String,
    val label: String,
)

data class FileSearchResult(
    val name: String,
    val uri: Uri,
    val mimeType: String,
    val modifiedAt: Long,
)

enum class Shortcut(val label: String) {
    NOTE("Note"), EVENT("Event"), WEATHER("Weather"), TODO("To-do"),
    CALL("Call"), MESSAGE("Message"), FILES("Files"), DRAWER("Drawer")
}

enum class Screen { HOME, SETTINGS, TODOS }

enum class ThemePreference(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark")
}
