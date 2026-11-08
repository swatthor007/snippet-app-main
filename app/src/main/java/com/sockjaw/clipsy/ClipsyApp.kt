package com.sockjaw.clipsy

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import android.net.Uri
import com.sockjaw.clipsy.data.ClipsyDatabase
import com.sockjaw.clipsy.data.CrashLog
import com.sockjaw.clipsy.data.SettingsStore
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.Folder
import com.sockjaw.clipsy.data.SnippetRepository
import com.sockjaw.clipsy.data.LinkPreviews
import com.sockjaw.clipsy.data.vault.Vault
import com.sockjaw.clipsy.service.SharedText
import com.sockjaw.clipsy.ui.components.Toaster
import com.sockjaw.clipsy.ui.components.UNDO_WINDOW_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.sockjaw.clipsy.service.Rendered
import com.sockjaw.clipsy.service.Template
import com.sockjaw.clipsy.service.TemplateContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import com.sockjaw.clipsy.widget.WidgetData
import com.sockjaw.clipsy.widget.refreshWidgets
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(app)
    val vault = Vault(app)
    private val database = ClipsyDatabase.create(app)
    val repository = SnippetRepository(app, database, settings, vault)
    val links = LinkPreviews(app, database, settings, repository)
    val toaster = Toaster()

    /** Every snippet, kept current, for lookups that can't wait on a flow (rendering placeholders). */
    val snippetsNow: StateFlow<List<Snippet>> = repository.snippets.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    /** Last {counter} number handed out per snippet, so two quick uses never get the same number. */
    private val counters = HashMap<Long, Int>()

    /**
     * A text snippet's content with every placeholder filled in, the same for typing its keyword,
     * Copy and Share. [app] is the app being typed in (null when copying from Clipsy). Hands out
     * and records the next {counter} number when the snippet uses one.
     */
    fun render(snippet: Snippet, clipboard: String?, app: String?): Rendered {
        val all = snippetsNow.value
        val stored = all.firstOrNull { it.id == snippet.id }?.counter ?: snippet.counter
        val counter = if (Template.usesCounter(snippet.content)) {
            synchronized(counters) {
                (maxOf(stored, counters[snippet.id] ?: 0) + 1).also { counters[snippet.id] = it }
            }.also { appScope.launch { repository.takeCounter(snippet.id) } }
        } else {
            null
        }
        val byKeyword = all.filter { !it.type.isFile && !it.locked && it.keyword != null }.associateBy { it.keyword!!.lowercase() }
        return Template.render(snippet.content, context = TemplateContext(clipboard, app, counter) { byKeyword[it]?.content })
    }

    fun resetCounter(snippet: Snippet) {
        synchronized(counters) { counters.remove(snippet.id) }
        appScope.launch { repository.resetCounter(snippet.id) }
    }

    /** A file shared into the app via the share sheet, waiting to be turned into a snippet. */
    val incomingFile = MutableStateFlow<Uri?>(null)

    /** What an [com.sockjaw.clipsy.UnlockActivity] found: the text for [requestId], or null when the user backed out. */
    data class UnlockResult(val requestId: Long, val text: String?)

    val unlockResults = MutableSharedFlow<UnlockResult>(extraBufferCapacity = 4)

    /** True while Library is in multi-select mode, so the tab bar and New snippet button step aside. */
    val librarySelecting = MutableStateFlow(false)

    /** True while Home or Library is searching (field focused or a query typed), so New snippet steps aside. */
    val searching = MutableStateFlow(false)

    /** Text shared into the app from another app, waiting for the user to save it. */
    val incomingText = MutableStateFlow<SharedText?>(null)

    init {
        appScope.launch { runCatching { repository.fixFileExtensions() } }
        watchSession(app)
        watchWidgets(app)
    }

    /**
     * Keeps home-screen widgets current: redraws them when snippets or folders change, and when
     * you leave the app (so "Most used" reflects what you just did). Tapping a widget tile bumps
     * a use count, which on its own never redraws - the list shouldn't reshuffle under your finger.
     */
    private fun watchWidgets(app: Application) {
        appScope.launch {
            combine(repository.snippets, repository.folders) { snippets, folders ->
                WidgetData.signature(snippets) * 31 + folders.hashCode()
            }.distinctUntilChanged().debounce(500).collect { refreshWidgets(app) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { appScope.launch { refreshWidgets(app) } }
        })
    }

    private var relockJob: Job? = null

    /**
     * Locked snippets stay open only while you're using Clipsy: leaving it closes them after the
     * chosen delay, and turning the screen off closes them at once.
     */
    private fun watchSession(app: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { relockJob?.cancel() }
            override fun onStop(owner: LifecycleOwner) {
                val wait = settings.settings.value.relockAfter.ms
                if (wait <= 0) return vault.lock()
                relockJob = appScope.launch { delay(wait); vault.lock() }
            }
        })
        val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = vault.lock()
        }
        ContextCompat.registerReceiver(app, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private val pendingDeletes = MutableStateFlow<Map<Long, Job>>(emptyMap())
    private val pendingFolderDeletes = MutableStateFlow<Set<Long>>(emptySet())

    /** Every snippet except ones deleted in the last few seconds and still undoable. */
    val visibleSnippets: Flow<List<Snippet>> =
        combine(repository.snippets, pendingDeletes) { all, pending -> all.filter { it.id !in pending } }

    /** Every folder except one being deleted and still undoable. */
    val visibleFolders: Flow<List<Folder>> =
        combine(repository.folders, pendingFolderDeletes) { all, pending -> all.filter { it.id !in pending } }

    fun deleteWithUndo(snippet: Snippet) = deleteWithUndo(listOf(snippet))

    /**
     * Hides the snippets at once and shows one Undo toast; the rows and their files are only
     * removed once the undo window closes.
     */
    fun deleteWithUndo(snippets: List<Snippet>) {
        // A second call for the same snippet would orphan the first timer, which Undo can't cancel.
        val fresh = snippets.filter { it.id !in pendingDeletes.value }
        if (fresh.isEmpty()) return
        val job = appScope.launch {
            delay(UNDO_WINDOW_MS + 500)
            fresh.forEach { repository.delete(it) }
            pendingDeletes.update { it - fresh.map { s -> s.id }.toSet() }
        }
        pendingDeletes.update { it + fresh.associate { s -> s.id to job } }
        val label = if (fresh.size == 1) "Deleted “${fresh[0].name}”" else "Deleted ${fresh.size} snippets"
        toaster.show(label, actionLabel = "Undo") {
            job.cancel()
            pendingDeletes.update { it - fresh.map { s -> s.id }.toSet() }
        }
    }

    /**
     * Deletes a folder after the undo window. With [withSnippets] its snippets go too, inside the
     * same window; otherwise they return to the library root.
     */
    fun deleteFolderWithUndo(folder: Folder, withSnippets: Boolean) {
        if (folder.id in pendingFolderDeletes.value) return
        appScope.launch {
            val inside = if (withSnippets) repository.folderSnippets(folder.id) else emptyList()
            val snippetJob = inside.filter { it.id !in pendingDeletes.value }
            val job = launch {
                delay(UNDO_WINDOW_MS + 500)
                snippetJob.forEach { repository.delete(it) }
                repository.deleteFolder(folder.id)
                pendingFolderDeletes.update { it - folder.id }
                pendingDeletes.update { it - snippetJob.map { s -> s.id }.toSet() }
            }
            pendingFolderDeletes.update { it + folder.id }
            pendingDeletes.update { it + snippetJob.associate { s -> s.id to job } }
            val label = if (inside.isEmpty()) "Deleted folder “${folder.name}”" else "Deleted “${folder.name}” and ${inside.size} snippets"
            toaster.show(label, actionLabel = "Undo") {
                job.cancel()
                pendingFolderDeletes.update { it - folder.id }
                pendingDeletes.update { it - snippetJob.map { s -> s.id }.toSet() }
            }
        }
    }
}

class ClipsyApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }

    val container by lazy { AppContainer(this) }

    /** Image previews for media snippets; GIFs animate (native decoder on Android 9+). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
}
