package com.sockjaw.clipsy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.sockjaw.clipsy.ui.lock.Authenticator
import com.sockjaw.clipsy.ui.lock.UnlockHost
import com.sockjaw.clipsy.ui.lock.Unlocker
import com.sockjaw.clipsy.ui.theme.ClipsyTheme
import com.sockjaw.clipsy.ui.theme.isDark

/**
 * The sign-in step for a locked keyword typed in another app. It sits over that app, asks for the
 * phone lock, fingerprint or PIN, hands the one snippet's text to the typing service and closes.
 * If the vault was closed when this opened, it is closed again straight away, so a pill tap never
 * leaves locked snippets open in Clipsy.
 */
class UnlockActivity : FragmentActivity() {
    private val container get() = (application as ClipsyApp).container
    private val authenticator = Authenticator(this)
    private var requestId = 0L
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestId = intent.getLongExtra(EXTRA_REQUEST, 0)
        val snippetId = intent.getLongExtra(EXTRA_SNIPPET, 0)
        val vault = container.vault
        val unlocker = Unlocker(vault, authenticator) { text, _ -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val dark = settings.theme.isDark()
            ClipsyTheme(dark = dark, fillBackground = false) { UnlockHost(unlocker, vault) }
            LaunchedEffect(Unit) {
                val snippet = container.repository.get(snippetId)
                if (snippet == null || !snippet.locked) return@LaunchedEffect finishWith(null)
                val wasLocked = !vault.isUnlocked
                unlocker.require("Insert “${snippet.name}”", onCancel = { finishWith(null) }) {
                    val text = snippet.sealedContent?.let(vault::open)
                    if (wasLocked) vault.lock()
                    finishWith(text)
                }
            }
        }
    }

    private fun finishWith(text: String?) {
        if (delivered) return
        delivered = true
        container.unlockResults.tryEmit(AppContainer.UnlockResult(requestId, text))
        finish()
    }

    override fun onDestroy() {
        // Closed some other way (the user left): tell the service so it stops waiting.
        if (!isChangingConfigurations) finishWith(null)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SNIPPET = "snippet"
        private const val EXTRA_REQUEST = "request"

        fun intent(context: Context, snippetId: Long, requestId: Long): Intent =
            Intent(context, UnlockActivity::class.java)
                .putExtra(EXTRA_SNIPPET, snippetId)
                .putExtra(EXTRA_REQUEST, requestId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
