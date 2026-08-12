package com.katoaapps.openminilaunch

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LauncherStore(context: Context) {
    private val prefs = context.getSharedPreferences("mini_launch", Context.MODE_PRIVATE)
    val todos = mutableStateListOf<TodoItem>()
    val shortcutPackages = mutableStateMapOf<Shortcut, String>()
    val drawerPackages = mutableStateListOf<String>()
    val searchFolders = mutableStateListOf<SearchFolder>()
    val searchHistory = mutableStateListOf<String>()
    var onboardingComplete by mutableStateOf(prefs.getBoolean("onboarding_complete", false))
        private set
    var themePreference by mutableStateOf(
        runCatching { ThemePreference.valueOf(prefs.getString("theme_preference", "SYSTEM") ?: "SYSTEM") }.getOrDefault(ThemePreference.SYSTEM)
    )
        private set
    var preferredWebPackage by mutableStateOf(prefs.getString("preferred_web_package", null))
        private set

    init {
        prefs.edit()
            .remove("weather_zip")
            .remove("temperature_unit")
            .remove("weather_temperature_f")
            .remove("weather_summary")
            .remove("weather_fetched_at")
            .apply()
        load()
    }

    private fun load() {
        runCatching {
            val array = JSONArray(prefs.getString("todos", "[]") ?: "[]")
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                todos += TodoItem(item.getString("id"), item.getString("text"), item.optBoolean("completed"))
            }
            val shortcuts = JSONObject(prefs.getString("shortcuts", "{}") ?: "{}")
            Shortcut.entries.forEach { shortcut ->
                shortcuts.optString(shortcut.name).takeIf(String::isNotBlank)?.let { shortcutPackages[shortcut] = it }
            }
            val drawer = JSONArray(prefs.getString("drawer", "[]") ?: "[]")
            repeat(drawer.length()) { drawerPackages += drawer.getString(it) }
            val folders = JSONArray(prefs.getString("search_folders", "[]") ?: "[]")
            repeat(folders.length()) { index ->
                val folder = folders.getJSONObject(index)
                searchFolders += SearchFolder(folder.getString("uri"), folder.getString("label"))
            }
            val history = JSONArray(prefs.getString("search_history", "[]") ?: "[]")
            repeat(minOf(history.length(), MAX_SEARCH_HISTORY)) { index ->
                history.optString(index).trim().takeIf(String::isNotEmpty)?.let(searchHistory::add)
            }
        }
    }

    fun addTodo(text: String) {
        val clean = text.trim()
        if (clean.isNotEmpty()) {
            todos += TodoItem(UUID.randomUUID().toString(), clean)
            saveTodos()
        }
    }

    fun toggleTodo(id: String) = updateTodo(id) { it.copy(completed = !it.completed) }
    fun renameTodo(id: String, text: String) = updateTodo(id) { it.copy(text = text.trim()) }

    private fun updateTodo(id: String, transform: (TodoItem) -> TodoItem) {
        val index = todos.indexOfFirst { it.id == id }
        if (index >= 0) {
            todos[index] = transform(todos[index])
            saveTodos()
        }
    }

    fun deleteTodo(id: String) {
        todos.removeAll { it.id == id }
        saveTodos()
    }

    fun moveTodo(index: Int, direction: Int) {
        val target = index + direction
        if (index in todos.indices && target in todos.indices) {
            val item = todos.removeAt(index)
            todos.add(target, item)
            saveTodos()
        }
    }

    fun assignShortcut(shortcut: Shortcut, packageName: String) {
        shortcutPackages[shortcut] = packageName
        saveSettings()
    }

    fun resetShortcut(shortcut: Shortcut) {
        shortcutPackages.remove(shortcut)
        saveSettings()
    }

    fun toggleDrawerApp(packageName: String) {
        if (packageName in drawerPackages) drawerPackages.remove(packageName)
        else if (drawerPackages.size < 5) drawerPackages += packageName
        saveSettings()
    }

    fun completeOnboarding() {
        onboardingComplete = true
        prefs.edit().putBoolean("onboarding_complete", true).apply()
    }

    fun hasSeenUpdate(updateId: String): Boolean = updateId in (prefs.getStringSet("seen_updates", emptySet()) ?: emptySet())

    fun markUpdateSeen(updateId: String) {
        val seen = (prefs.getStringSet("seen_updates", emptySet()) ?: emptySet()).toMutableSet()
        seen += updateId
        prefs.edit().putStringSet("seen_updates", seen).apply()
    }

    fun setTheme(preference: ThemePreference) {
        themePreference = preference
        prefs.edit().putString("theme_preference", preference.name).apply()
    }

    fun setPreferredWebApp(packageName: String) {
        preferredWebPackage = packageName
        prefs.edit().putString("preferred_web_package", packageName).apply()
    }

    fun resetPreferredWebApp() {
        preferredWebPackage = null
        prefs.edit().remove("preferred_web_package").apply()
    }

    fun addSearchFolder(uri: String, label: String) {
        if (searchFolders.none { it.uri == uri }) {
            searchFolders += SearchFolder(uri, label)
            saveSearchFolders()
        }
    }

    fun removeSearchFolder(uri: String) {
        searchFolders.removeAll { it.uri == uri }
        saveSearchFolders()
    }

    fun addSearchQuery(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        searchHistory.removeAll { it.equals(clean, ignoreCase = true) }
        searchHistory.add(0, clean)
        while (searchHistory.size > MAX_SEARCH_HISTORY) searchHistory.removeAt(searchHistory.lastIndex)
        saveSearchHistory()
    }

    fun removeSearchQuery(query: String) {
        searchHistory.removeAll { it == query }
        saveSearchHistory()
    }

    fun clearSearchHistory() {
        searchHistory.clear()
        saveSearchHistory()
    }

    private fun saveTodos() {
        val value = JSONArray().apply {
            todos.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("completed", it.completed)) }
        }
        prefs.edit().putString("todos", value.toString()).apply()
    }

    private fun saveSettings() {
        val shortcuts = JSONObject().apply { shortcutPackages.forEach { (key, value) -> put(key.name, value) } }
        val drawer = JSONArray().apply { drawerPackages.forEach(::put) }
        prefs.edit().putString("shortcuts", shortcuts.toString()).putString("drawer", drawer.toString()).apply()
    }

    private fun saveSearchFolders() {
        val folders = JSONArray().apply {
            searchFolders.forEach { put(JSONObject().put("uri", it.uri).put("label", it.label)) }
        }
        prefs.edit().putString("search_folders", folders.toString()).apply()
    }

    private fun saveSearchHistory() {
        val history = JSONArray().apply { searchHistory.forEach(::put) }
        prefs.edit().putString("search_history", history.toString()).apply()
    }

    private companion object {
        const val MAX_SEARCH_HISTORY = 5
    }
}
