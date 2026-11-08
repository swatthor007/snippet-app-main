package com.sockjaw.clipsy.ui.settings

import com.sockjaw.clipsy.ui.theme.ClipsyType
import compose.icons.TablerIcons
import compose.icons.tablericons.Archive
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Check
import compose.icons.tablericons.CursorText
import compose.icons.tablericons.DeviceMobileVibration
import compose.icons.tablericons.FileExport
import compose.icons.tablericons.FileImport
import compose.icons.tablericons.Hash
import compose.icons.tablericons.Keyboard
import compose.icons.tablericons.Power
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import compose.icons.tablericons.FileText
import com.sockjaw.clipsy.data.CrashLog
import compose.icons.tablericons.Bug
import compose.icons.tablericons.Share
import compose.icons.tablericons.Trash
import compose.icons.tablericons.World
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import com.sockjaw.clipsy.data.ThemeMode
import compose.icons.tablericons.Moon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.data.ExpandAfter
import com.sockjaw.clipsy.ui.components.ActionSheet
import com.sockjaw.clipsy.ui.components.ChoiceRow
import com.sockjaw.clipsy.ui.components.ClipsySheet
import com.sockjaw.clipsy.ui.components.ClipsySwitch
import com.sockjaw.clipsy.ui.components.CrossfadeText
import com.sockjaw.clipsy.ui.components.ExpandingVisibility
import com.sockjaw.clipsy.ui.components.FadeScaleVisibility
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.IconTile
import com.sockjaw.clipsy.ui.components.PillSearchField
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.SectionLabel
import com.sockjaw.clipsy.ui.components.SheetAction
import com.sockjaw.clipsy.ui.components.SheetTitle
import com.sockjaw.clipsy.ui.components.SwitchRow
import com.sockjaw.clipsy.ui.components.TabBarHeight
import com.sockjaw.clipsy.ui.components.ToastKind
import com.sockjaw.clipsy.ui.components.ValueRow
import com.sockjaw.clipsy.ui.components.screenRoot
import com.sockjaw.clipsy.ui.components.TabContentPadding
import com.sockjaw.clipsy.ui.components.TabTitle
import com.sockjaw.clipsy.ui.components.TabTitleGap
import com.sockjaw.clipsy.ui.components.expandDetail
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Motion
import com.sockjaw.clipsy.ui.theme.pressable
import com.sockjaw.clipsy.ui.theme.riseIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AppEntry(val packageName: String, val label: String)

private fun launchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

fun isServiceEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return enabled.split(':').any { it.startsWith("${context.packageName}/") }
}

fun openAccessibilitySettings(context: Context) =
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun openAppInfo(context: Context) =
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unlocker = com.sockjaw.clipsy.ui.lock.LocalUnlocker.current
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    var serviceOn by remember { mutableStateOf(isServiceEnabled(context)) }
    var showExcluded by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showLicences by remember { mutableStateOf(false) }
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    var showCrash by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { serviceOn = isServiceEnabled(context) }

    var includeLocked by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val json = container.repository.exportJson(includeLocked)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            }.onSuccess { container.toaster.show("Backup saved", ToastKind.Success) }
                .onFailure { container.toaster.show("Couldn’t save the backup", ToastKind.Error) }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty() }
                container.repository.importJson(text)
            }.onSuccess { container.toaster.show("Imported $it ${if (it == 1) "snippet" else "snippets"}", ToastKind.Success) }
                .onFailure { container.toaster.show("That file isn’t a Clipsy backup", ToastKind.Error) }
        }
    }

    LazyColumn(
        Modifier.screenRoot(),
        contentPadding = TabContentPadding,
        verticalArrangement = Arrangement.spacedBy(TabTitleGap),
    ) {
        item { TabTitle("Settings", Modifier.riseIn(0)) }
        item { ServiceCard(serviceOn, Modifier.riseIn(1), onToggle = { openAccessibilitySettings(context) }, onAppInfo = { openAppInfo(context) }) }
        item {
            Column(Modifier.riseIn(2), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Appearance")
                GroupedCard {
                    ChoiceRow(
                        "Theme", ThemeMode.entries, settings.theme, { it.label },
                        { v -> container.settings.update { it.copy(theme = v) } },
                        icon = TablerIcons.Moon,
                        sheetSubtitle = "System follows your phone’s light or dark setting.",
                    )
                }
            }
        }
        item {
            Column(Modifier.riseIn(2), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Expansion")
                GroupedCard {
                    ChoiceRow(
                        "Expand after", ExpandAfter.entries, settings.expandAfter, { it.label },
                        { v -> container.settings.update { it.copy(expandAfter = v) } },
                        icon = TablerIcons.Keyboard,
                        sheetSubtitle = "Default for new snippets. Each snippet can override it.",
                        optionDetail = ::expandDetail,
                    )
                    RowDivider(60.dp)
                    ChoiceRow(
                        "Keyword prefix", listOf("", ";", "/", "\\"), settings.keywordPrefix, { if (it.isEmpty()) "None" else it },
                        { v -> container.settings.update { it.copy(keywordPrefix = v) } },
                        icon = TablerIcons.Hash,
                        sheetSubtitle = "A character you type before every keyword, so snippets never fire by accident.",
                        optionDetail = { if (it.isEmpty()) "Type the keyword on its own" else "Type ${it}sig to expand sig" },
                    )
                    RowDivider(60.dp)
                    SwitchRow("Suggestion pill", settings.suggestionPill, { v -> container.settings.update { it.copy(suggestionPill = v) } }, icon = TablerIcons.CursorText)
                    RowDivider(60.dp)
                    SwitchRow("Haptic tap", settings.haptics, { v -> container.settings.update { it.copy(haptics = v) } }, icon = TablerIcons.DeviceMobileVibration)
                }
            }
        }
        item {
            Column(Modifier.riseIn(3), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Privacy")
                GroupedCard {
                    ValueRow("Excluded apps", settings.excludedApps.size.takeIf { it > 0 }?.toString().orEmpty(), icon = TablerIcons.Ban, onClick = { showExcluded = true })
                    RowDivider(60.dp)
                    ValueRow("Backup and export", "", icon = TablerIcons.Archive, onClick = { showBackup = true })
                    RowDivider(60.dp)
                    SwitchRow("Link previews", settings.linkPreviews, { v -> container.settings.update { it.copy(linkPreviews = v) } }, icon = TablerIcons.World)
                }
                Text(
                    "When you save a link, or copy one and Home offers to save it, Clipsy opens it once to read its title and image. Nothing else is sent, and locked snippets are never looked up.",
                    Modifier.padding(horizontal = 4.dp), color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption,
                )
            }
        }
        item { WidgetsSection(container, Modifier.riseIn(3)) }
        item { SecuritySection(container, Modifier.riseIn(3)) }
        item {
            Column(Modifier.riseIn(4), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("About")
                GroupedCard {
                    if (crash != null) {
                        ValueRow("Send crash report", "", icon = TablerIcons.Bug, onClick = { showCrash = true })
                        RowDivider(60.dp)
                    }
                    ValueRow("Open-source licences", "", icon = TablerIcons.FileText, onClick = { showLicences = true })
                }
            }
        }
        // The tab bar floats over the list and sits above the system navigation bar, so clear both.
        item { Spacer(Modifier.navigationBarsPadding().height(TabBarHeight + 24.dp)) }
    }

    if (showExcluded) {
        ExcludedAppsSheet(
            excluded = settings.excludedApps,
            onToggle = { pkg -> container.settings.update { s -> s.copy(excludedApps = if (pkg in s.excludedApps) s.excludedApps - pkg else s.excludedApps + pkg) } },
            onDismiss = { showExcluded = false },
        )
    }

    if (showLicences) LicencesSheet { showLicences = false }

    val report = crash
    if (showCrash && report != null) {
        ActionSheet(
            title = "Crash report",
            message = "Clipsy stopped unexpectedly last time. The report has the error and your phone model, no snippets.",
            actions = listOf(
                SheetAction("Share report", TablerIcons.Share) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Clipsy crash report")
                        .putExtra(Intent.EXTRA_TEXT, report)
                    context.startActivity(Intent.createChooser(send, "Send crash report"))
                },
                SheetAction("Delete report", TablerIcons.Trash, destructive = true) {
                    CrashLog.clear(context)
                    crash = null
                },
            ),
            onDismiss = { showCrash = false },
        )
    }

    if (showBackup) {
        ActionSheet(
            title = "Backup and export",
            message = "Text snippets, folders and settings, as a JSON file. Locked snippets and files you added aren’t included unless you choose to.",
            actions = listOf(
                SheetAction("Export backup", TablerIcons.FileExport) { includeLocked = false; exporter.launch("clipsy-backup.json") },
                // Plain text on purpose: a backup file can't carry the phone's encryption key.
                SheetAction("Export, including locked (as plain text)", TablerIcons.FileExport) {
                    unlocker?.require("Include locked snippets in a backup") { includeLocked = true; exporter.launch("clipsy-backup.json") }
                },
                SheetAction("Import backup", TablerIcons.FileImport) { importer.launch(arrayOf("application/json", "text/plain")) },
            ),
            onDismiss = { showBackup = false },
        )
    }
}

@Composable
private fun ServiceCard(on: Boolean, modifier: Modifier, onToggle: () -> Unit, onAppInfo: () -> Unit) {
    val tileBg by animateColorAsState(if (on) ClipsyColors.AccentFaint else ClipsyColors.CardAlt, Motion.standard(), label = "svcTile")
    val tileFg by animateColorAsState(if (on) ClipsyColors.Accent else ClipsyColors.TextSecondary, Motion.standard(), label = "svcIcon")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupedCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(TablerIcons.Power, size = 36.dp, tint = tileFg, background = tileBg)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Clipsy service", style = MaterialTheme.typography.titleMedium)
                    CrossfadeText(if (on) "On in every app" else "Off", color = if (on) ClipsyColors.Accent else ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption)
                }
                ClipsySwitch(on, { onToggle() }, "Clipsy service")
            }
        }
        CrossfadeText(
            if (on) "Accessibility access is on. Clipsy only reads text fields to spot your keywords, and never reads password fields."
            else "Turn it on in Accessibility settings so your keywords expand in other apps.",
            color = ClipsyColors.TextSecondary,
            fontSize = ClipsyType.Caption,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        ExpandingVisibility(!on) {
            Text(
                "Greyed out? Open App info, tap ⋮, then Allow restricted settings.",
                color = ClipsyColors.Accent,
                fontSize = ClipsyType.Caption,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onAppInfo).padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ExcludedAppsSheet(excluded: Set<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { launchableApps(context) } }

    ClipsySheet(onDismiss) {
        SheetTitle("Excluded apps", "Clipsy won’t expand anything in these apps.")
        PillSearchField(query, { query = it }, "Search apps", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        val list = apps
        if (list == null) {
            Text("Loading apps…", color = ClipsyColors.TextSecondary, modifier = Modifier.padding(24.dp))
        } else {
            val shown = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(shown, key = { it.packageName }) { app ->
                    val on = app.packageName in excluded
                    val box by animateColorAsState(if (on) ClipsyColors.Accent else ClipsyColors.CardAlt, Motion.standard(), label = "check")
                    Row(
                        Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .pressable(role = Role.Checkbox, pressedScale = 0.985f) { onToggle(app.packageName) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(box), contentAlignment = Alignment.Center) {
                            FadeScaleVisibility(on) { Icon(TablerIcons.Check, null, tint = ClipsyColors.OnAccent, modifier = Modifier.size(16.dp)) }
                        }
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
private fun LicencesSheet(onDismiss: () -> Unit) {
    ClipsySheet(onDismiss) {
        SheetTitle("Open-source licences")
        Column(
            Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Geist and Geist Mono", style = MaterialTheme.typography.titleMedium)
            Text(
                "Copyright (c) Vercel, in collaboration with basement.studio. Licensed under the SIL Open Font License, Version 1.1 (openfontlicense.org).",
                color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
            )
            Text("Tabler Icons", style = MaterialTheme.typography.titleMedium)
            Text(
                "MIT License. Copyright (c) 2020-2024 Paweł Kuna. Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files, to deal in the software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the software, subject to the inclusion of this copyright notice and permission notice in all copies or substantial portions of the software. The software is provided \"as is\", without warranty of any kind.",
                color = ClipsyColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
