package com.sockjaw.clipsy.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** Which colours the app uses: follow the phone, or always light or dark. */
@Serializable
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** How the Library lays out its snippets. */
@Serializable
enum class LibraryView { LIST, COMPACT, GRID }

/** How long locked snippets stay open after you leave Clipsy. */
@Serializable
enum class RelockAfter(val label: String, val ms: Long) {
    NOW("Right away", 0),
    MINUTE("After 1 minute", 60_000),
    FIVE_MINUTES("After 5 minutes", 300_000),
}

@Serializable
data class AppSettings(
    val onboardingDone: Boolean = false,
    val expandAfter: ExpandAfter = ExpandAfter.SPACE,
    val keywordPrefix: String = "",
    val suggestionPill: Boolean = true,
    val haptics: Boolean = true,
    val excludedApps: Set<String> = emptySet(),
    /** Hash of the clipboard text the user said "Not now" to, so Home doesn't ask again. */
    val dismissedClip: Int = 0,
    val libraryView: LibraryView = LibraryView.LIST,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val relockAfter: RelockAfter = RelockAfter.MINUTE,
    /** Look up a saved link's title, description and image on the web. */
    val linkPreviews: Boolean = true,
    /** The link page has shown, by pulling itself down once, that it can be pulled to preview. */
    val previewNudgeSeen: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("clipsy_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putBoolean("onboardingDone", next.onboardingDone)
            .putString("expandAfter", next.expandAfter.name)
            .putString("keywordPrefix", next.keywordPrefix)
            .putBoolean("suggestionPill", next.suggestionPill)
            .putBoolean("haptics", next.haptics)
            .putStringSet("excludedApps", next.excludedApps)
            .putInt("dismissedClip", next.dismissedClip)
            .putString("libraryView", next.libraryView.name)
            .putString("theme", next.theme.name)
            .putString("relockAfter", next.relockAfter.name)
            .putBoolean("linkPreviews", next.linkPreviews)
            .putBoolean("previewNudgeSeen", next.previewNudgeSeen)
            .apply()
    }

    private fun read() = AppSettings(
        onboardingDone = prefs.getBoolean("onboardingDone", false),
        expandAfter = runCatching { ExpandAfter.valueOf(prefs.getString("expandAfter", null) ?: "") }
            .getOrDefault(ExpandAfter.SPACE),
        keywordPrefix = prefs.getString("keywordPrefix", "") ?: "",
        suggestionPill = prefs.getBoolean("suggestionPill", true),
        haptics = prefs.getBoolean("haptics", true),
        excludedApps = prefs.getStringSet("excludedApps", emptySet())?.toSet() ?: emptySet(),
        dismissedClip = prefs.getInt("dismissedClip", 0),
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
        relockAfter = runCatching { RelockAfter.valueOf(prefs.getString("relockAfter", null) ?: "") }.getOrDefault(RelockAfter.MINUTE),
        linkPreviews = prefs.getBoolean("linkPreviews", true),
        previewNudgeSeen = prefs.getBoolean("previewNudgeSeen", false),
        libraryView = runCatching { LibraryView.valueOf(prefs.getString("libraryView", null) ?: "") }.getOrDefault(LibraryView.LIST),
    )
}
