package com.sockjaw.clipsy.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.sockjaw.clipsy.ui.lock.LocalUnlocker
import com.sockjaw.clipsy.ui.lock.UnlockHost
import com.sockjaw.clipsy.ui.lock.Unlocker
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleOut
import com.sockjaw.clipsy.ui.components.NewSnippetFab
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import android.util.Log
import com.sockjaw.clipsy.AppContainer
import kotlinx.coroutines.CancellationException
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.SnippetType
import com.sockjaw.clipsy.ui.clipboard.ClipboardSheet
import com.sockjaw.clipsy.ui.components.AnimatedTabBar
import com.sockjaw.clipsy.ui.components.Tab
import com.sockjaw.clipsy.ui.components.ToastHost
import com.sockjaw.clipsy.ui.components.ToastKind
import com.sockjaw.clipsy.ui.detail.DetailScreen
import com.sockjaw.clipsy.ui.editor.EditorScreen
import com.sockjaw.clipsy.ui.filesnippet.FileSnippetScreen
import com.sockjaw.clipsy.ui.home.HomeScreen
import com.sockjaw.clipsy.ui.library.LibraryScreen
import com.sockjaw.clipsy.ui.onboarding.OnboardingScreen
import com.sockjaw.clipsy.ui.settings.SettingsScreen
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.Motion

private object Routes {
    const val Onboarding = "onboarding"
    const val Detail = "detail/{id}"
    const val Editor = "editor/{id}?folder={folder}"
    const val File = "file/{id}"
    const val Folder = "folder/{id}"
    fun detail(id: Long) = "detail/$id"
    fun editor(id: Long = 0, folder: Long? = null) = "editor/$id" + (folder?.let { "?folder=$it" } ?: "")
    fun file(id: Long) = "file/$id"
    fun folder(id: Long) = "folder/$id"
}

private val tabRoutes = Tab.entries.map { it.route }.toSet()
private fun NavBackStackEntry.isTab() = destination.route in tabRoutes
private val AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs get() = initialState.isTab() && targetState.isTab()

// Push: the new screen slides a quarter-width in from the right while the old one drifts left.
// Tabs: fade-through, a context switch without a direction.
private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (betweenTabs) fadeIn(Motion.enter(Motion.BASE, delay = 70)) + scaleIn(Motion.enter(Motion.BASE, delay = 70), initialScale = 0.97f)
    else slideInHorizontally(Motion.enter()) { it / 4 } + fadeIn(Motion.enter(Motion.BASE))
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (betweenTabs) fadeOut(Motion.exit(90))
    else slideOutHorizontally(Motion.enter()) { -it / 10 } + fadeOut(Motion.standard(Motion.SLOW))
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (betweenTabs) fadeIn(Motion.enter(Motion.BASE, delay = 70))
    else slideInHorizontally(Motion.enter()) { -it / 10 } + fadeIn(Motion.enter(Motion.BASE))
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (betweenTabs) fadeOut(Motion.exit(90))
    else slideOutHorizontally(Motion.exit(Motion.BASE)) { it / 4 } + fadeOut(Motion.exit(Motion.BASE))
}

private fun NavController.openSnippet(s: Snippet) =
    navigate(if (s.type.isFile) Routes.file(s.id) else Routes.detail(s.id))

private fun NavController.switchTab(tab: Tab) {
    // A tab already in the back stack (Home always is, under whatever a link or widget opened) is
    // reached by popping back to it, saving what's above for later. The usual "pop up to Home and
    // restore state" call silently does nothing when the stack was built from a link.
    if (runCatching { getBackStackEntry(tab.route) }.isSuccess) {
        popBackStack(tab.route, inclusive = false, saveState = true)
        return
    }
    navigate(tab.route) {
        popUpTo(Tab.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Opens a clipsy:// link (from a widget) on top of whatever is showing, so Home and Back still work
 * afterwards. NavController.handleDeepLink would clear the stack and leave only the linked screen.
 */
private fun NavController.openLink(uri: android.net.Uri) {
    val id = uri.lastPathSegment?.toLongOrNull()
    when (uri.host) {
        "library" -> switchTab(Tab.Library)
        "snippet" -> if (id != null) navigate(Routes.detail(id))
        "file" -> if (id != null) navigate(Routes.file(id))
        "folder" -> if (id != null) navigate(Routes.folder(id))
        "new" -> navigate(Routes.editor(folder = uri.getQueryParameter("folder")?.toLongOrNull()))
    }
}

@Composable
fun ClipsyNavHost(container: AppContainer, unlocker: Unlocker) {
    val linkImages by container.links.images.collectAsStateWithLifecycle(emptyMap())
    CompositionLocalProvider(LocalUnlocker provides unlocker, com.sockjaw.clipsy.ui.components.LocalLinkImages provides linkImages) {
        ClipsyNavHostContent(container)
    }
    UnlockHost(unlocker, container.vault)
}

@Composable
private fun ClipsyNavHostContent(container: AppContainer) {
    val nav = rememberNavController()
    // A link opened while the app is already running (a widget tap, say) arrives as a new intent.
    val activity = androidx.activity.compose.LocalActivity.current as? androidx.activity.ComponentActivity
    androidx.compose.runtime.DisposableEffect(nav, activity) {
        val listener = androidx.core.util.Consumer<android.content.Intent> {
            val uri = it.data
            if (it.action == android.content.Intent.ACTION_VIEW && uri?.scheme == "clipsy") nav.openLink(uri) else nav.handleDeepLink(it)
        }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val currentTab = Tab.entries.firstOrNull { it.route == backStack?.destination?.route }
    val start = remember { if (container.settings.settings.value.onboardingDone) Tab.Home.route else Routes.Onboarding }
    var fabExpanded by remember { mutableStateOf(true) }
    val selecting by container.librarySelecting.collectAsStateWithLifecycle()
    val inFolder = backStack?.destination?.route == Routes.Folder
    val searching by container.searching.collectAsStateWithLifecycle()
    // Searching takes the screen over: New snippet would sit on top of the results.
    val showFab = (currentTab == Tab.Home || currentTab == Tab.Library || inFolder) && !selecting && !searching

    // A file shared into Clipsy becomes a file snippet right away.
    val incoming by container.incomingFile.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        val uri = incoming ?: return@LaunchedEffect
        // Clear the pending file only once the copy is done: clearing it first changes this
        // effect's key, which cancels the import halfway through.
        val result = runCatching { container.repository.importFile(uri) }
        container.incomingFile.value = null
        result
            .onSuccess { nav.navigate(Routes.file(it)); container.toaster.show("File added", ToastKind.Success) }
            .onFailure {
                if (it is CancellationException) throw it
                Log.w("Clipsy", "Shared file import failed: $uri", it)
                container.toaster.show("Couldn’t read that file", ToastKind.Error)
            }
    }

    // Text or a link shared from another app opens the new-snippet sheet wherever the app is.
    val sharedText by container.incomingText.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(ClipsyColors.Background)) {
        NavHost(
            nav,
            startDestination = start,
            modifier = Modifier.fillMaxSize(),
            enterTransition = enter,
            exitTransition = exit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            composable(Routes.Onboarding) {
                OnboardingScreen(container, onDone = { nav.navigate(Tab.Home.route) { popUpTo(Routes.Onboarding) { inclusive = true } } })
            }
            composable(Tab.Home.route) {
                HomeScreen(
                    container,
                    onOpen = { nav.openSnippet(it) },
                    onOpenFile = { nav.navigate(Routes.file(it)) },
                    onOpenLibrary = { nav.switchTab(Tab.Library) },
                    onScrolledToTop = { fabExpanded = it },
                )
            }
            composable(Tab.Library.route, deepLinks = listOf(navDeepLink { uriPattern = "clipsy://library" })) {
                LibraryScreen(
                    container, folderId = null,
                    onOpen = { nav.openSnippet(it) },
                    onOpenFolder = { nav.navigate(Routes.folder(it)) },
                    onBack = {},
                    onScrolledToTop = { fabExpanded = it },
                )
            }
            composable(
                Routes.Folder, listOf(navArgument("id") { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = "clipsy://folder/{id}" }),
            ) {
                val id = it.arguments!!.getLong("id")
                LibraryScreen(
                    container, folderId = id,
                    onOpen = { s -> nav.openSnippet(s) },
                    onOpenFolder = {},
                    onBack = { nav.popBackStack() },
                    onScrolledToTop = { fabExpanded = it },
                )
            }
            composable(Tab.Settings.route) { SettingsScreen(container) }
            composable(
                Routes.Detail, listOf(navArgument("id") { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = "clipsy://snippet/{id}" }),
            ) {
                val id = it.arguments!!.getLong("id")
                DetailScreen(
                    container, id, onBack = { nav.popBackStack() },
                    // A link that was saved as media becomes a file snippet: show it on its own screen.
                    onConverted = { fileId -> nav.navigate(Routes.file(fileId)) { popUpTo(Routes.Detail) { inclusive = true } } },
                )
            }
            composable(
                Routes.File, listOf(navArgument("id") { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = "clipsy://file/{id}" }),
            ) {
                val id = it.arguments!!.getLong("id")
                FileSnippetScreen(container, id, onBack = { nav.popBackStack() })
            }
            composable(
                Routes.Editor,
                listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("folder") { type = NavType.LongType; defaultValue = -1L },
                ),
                deepLinks = listOf(navDeepLink { uriPattern = "clipsy://new?folder={folder}" }, navDeepLink { uriPattern = "clipsy://new" }),
                // The editor is a modal task: it rises from the bottom and drops away.
                enterTransition = { slideInVertically(Motion.enter()) { it / 5 } + fadeIn(Motion.enter(Motion.BASE)) },
                exitTransition = { fadeOut(Motion.exit(Motion.QUICK)) },
                popExitTransition = { slideOutVertically(Motion.exit(Motion.BASE)) { it / 5 } + fadeOut(Motion.exit(Motion.BASE)) },
            ) {
                val id = it.arguments!!.getLong("id")
                EditorScreen(
                    container, id,
                    initialFolder = it.arguments!!.getLong("folder").takeIf { f -> f >= 0 },
                    onCancel = { nav.popBackStack() },
                    onSaved = { saved ->
                        // Back to wherever the editor was opened from; the new row animates into that list.
                        nav.popBackStack()
                        if (id == 0L) {
                            container.toaster.show("Snippet saved", ToastKind.Success, actionLabel = "View") { nav.navigate(Routes.detail(saved)) }
                        } else {
                            container.toaster.show("Changes saved", ToastKind.Success)
                        }
                    },
                    onFileImported = { fileId -> nav.navigate(Routes.file(fileId)) { popUpTo(Routes.Editor) { inclusive = true } } },
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            // Above the toast, so a toast pushes it up instead of covering it.
            AnimatedVisibility(
                visible = showFab,
                modifier = Modifier.align(Alignment.End).padding(end = 20.dp, bottom = 16.dp),
                enter = scaleIn(Motion.pop(), initialScale = 0.6f) + fadeIn(Motion.enter(Motion.BASE)),
                exit = scaleOut(Motion.exit(), targetScale = 0.6f) + fadeOut(Motion.exit()),
            ) {
                NewSnippetFab(fabExpanded, onClick = { nav.navigate(Routes.editor(folder = if (inFolder) backStack?.arguments?.getLong("id") else null)) })
            }
            ToastHost(
                container.toaster,
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .then(if (currentTab == null) Modifier.navigationBarsPadding() else Modifier)
                    .padding(bottom = 12.dp),
            )
            AnimatedTabBar(currentTab.takeIf { !selecting }, onSelect = { nav.switchTab(it) })
        }
    }

    sharedText?.let { shared ->
        ClipboardSheet(
            container,
            shared.text,
            onDismiss = { container.incomingText.value = null },
            onSaved = { name -> container.toaster.show("Saved “$name”", ToastKind.Success) },
            title = "Shared from another app",
            suggestedName = shared.subject,
        )
    }
}
