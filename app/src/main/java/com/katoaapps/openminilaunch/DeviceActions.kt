package com.katoaapps.openminilaunch

import android.content.Context
import android.content.Intent
import android.app.SearchManager
import android.graphics.drawable.Drawable
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.widget.Toast

class DeviceActions(private val context: Context) {
    @Volatile private var appsCache: List<LaunchableApp>? = null
    private val labelCache = mutableMapOf<String, String>()

    fun installedApps(): List<LaunchableApp> {
        appsCache?.let { return it }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { LaunchableApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
            .also { apps ->
                synchronized(labelCache) { apps.forEach { labelCache[it.packageName] = it.label } }
                appsCache = apps
            }
    }

    fun appLabel(packageName: String): String {
        synchronized(labelCache) { labelCache[packageName]?.let { return it } }
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault("Not installed").also { synchronized(labelCache) { labelCache[packageName] = it } }
    }

    fun appIcon(packageName: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    fun launchPackage(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName)?.let(::start) ?: false

    fun launchShortcut(shortcut: Shortcut, assignedPackage: String?, openTodos: () -> Unit, openDrawer: () -> Unit) {
        if (!assignedPackage.isNullOrBlank() && shortcut !in listOf(Shortcut.TODO, Shortcut.DRAWER)) {
            launchPackage(assignedPackage)
            return
        }
        when (shortcut) {
            Shortcut.NOTE -> createNote("")
            Shortcut.EVENT -> start(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI))
            Shortcut.WEATHER -> Toast.makeText(
                context,
                "Choose a Weather app in Settings",
                Toast.LENGTH_SHORT,
            ).show()
            Shortcut.TODO -> openTodos()
            Shortcut.CALL -> start(Intent(Intent.ACTION_DIAL))
            Shortcut.MESSAGE -> launchDefaultMessagesApp()
            Shortcut.FILES -> openFilesApp()
            Shortcut.DRAWER -> openDrawer()
        }
    }

    fun searchContacts(query: String): List<ContactResult> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<ContactResult>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf("$query%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(projection[0])
            val phoneIndex = cursor.getColumnIndexOrThrow(projection[1])
            while (cursor.moveToNext() && results.size < 8) {
                results += ContactResult(cursor.getString(nameIndex), cursor.getString(phoneIndex))
            }
        }
        return results.distinctBy { PhoneNumberUtils.normalizeNumber(it.phone) }
    }

    private fun launchDefaultMessagesApp() {
        val packageName = Telephony.Sms.getDefaultSmsPackage(context)
        if (!packageName.isNullOrBlank()) launchPackage(packageName)
        else start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))
    }

    fun composeMessage(phone: String, body: String, preferredPackage: String?): Boolean {
        fun messageIntent(packageName: String? = null) = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("smsto:${Uri.encode(phone)}"),
        ).putExtra("sms_body", body).apply { if (!packageName.isNullOrBlank()) setPackage(packageName) }

        if (!preferredPackage.isNullOrBlank()) {
            messageIntent(preferredPackage).takeIf(::canResolve)?.let { return start(it) }
        }
        Telephony.Sms.getDefaultSmsPackage(context)?.let { defaultPackage ->
            messageIntent(defaultPackage).takeIf(::canResolve)?.let { return start(it) }
        }
        return start(messageIntent(), chooser = true)
    }

    fun placeCall(phone: String): Boolean {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ||
            runCatching { PhoneNumberUtils.isEmergencyNumber(phone) }.getOrDefault(false)
        ) {
            return dial(phone)
        }
        val direct = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(phone)}"))
        return if (canResolve(direct) && start(direct)) true else dial(phone)
    }

    fun dial(phone: String): Boolean = start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))

    fun webSearchApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "minklauncher")
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { LaunchableApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun shareText(text: String) = start(
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), chooser = true
    )

    fun createNote(text: String, preferredPackage: String? = null): Boolean {
        val clean = text.trim()
        fun modern(packageName: String? = null) = Intent(Intent.ACTION_CREATE_NOTE).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, clean)
            .apply { if (!packageName.isNullOrBlank()) setPackage(packageName) }
        fun shared(packageName: String) = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, clean)
            .setPackage(packageName)

        if (!preferredPackage.isNullOrBlank()) {
            modern(preferredPackage).takeIf(::canResolve)?.let { return start(it) }
            shared(preferredPackage).takeIf(::canResolve)?.let { return start(it) }
        }
        modern().takeIf(::canResolve)?.let { return start(it) }
        return start(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, clean), chooser = true)
    }

    fun createEvent(description: String) = start(
        Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
    )

    fun emailSupport() = start(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:contact@katoaapps.com")))

    fun webSearch(query: String, preferredPackage: String? = null): Boolean {
        val clean = query.trim()
        if (clean.isEmpty()) return false
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, clean)
            .apply { if (!preferredPackage.isNullOrBlank()) setPackage(preferredPackage) }
        return canResolve(search) && start(search)
    }

    private fun openFilesApp(): Boolean {
        val files = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_FILES)
        if (canResolve(files)) return start(files)
        return start(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        )
    }

    fun openFile(result: FileSearchResult) = start(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(result.uri, result.mimeType.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        chooser = true,
    )

    fun openAppSettings() = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    fun expandNotificationShade() {
        runCatching {
            val statusBar = context.getSystemService("statusbar")
            statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
        }
    }

    private fun canResolve(intent: Intent): Boolean = intent.resolveActivity(context.packageManager) != null

    private fun start(intent: Intent, chooser: Boolean = false): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(if (chooser) Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
            true
        }.getOrDefault(false)
    }

}
