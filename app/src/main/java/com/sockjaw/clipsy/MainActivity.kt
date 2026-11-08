package com.sockjaw.clipsy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.sockjaw.clipsy.ui.lock.Authenticator
import com.sockjaw.clipsy.ui.lock.Unlocker
import com.sockjaw.clipsy.ui.components.ToastKind
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.content.IntentCompat
import android.graphics.Color
import com.sockjaw.clipsy.service.SharedText
import com.sockjaw.clipsy.ui.navigation.ClipsyNavHost
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sockjaw.clipsy.ui.theme.ClipsyTheme
import com.sockjaw.clipsy.ui.theme.isDark
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.remember

// A FragmentActivity because the system sign-in prompt (BiometricPrompt) needs one.
class MainActivity : FragmentActivity() {
    private val container get() = (application as ClipsyApp).container
    private val authenticator = Authenticator(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            val themes = remember { container.settings.settings.map { it.theme }.distinctUntilChanged() }
            val mode by themes.collectAsStateWithLifecycle(remember { container.settings.settings.value.theme })
            val dark = mode.isDark()
            // Bar icons follow the chosen theme, which may differ from the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            val unlocker = remember {
                Unlocker(container.vault, authenticator) { text, error ->
                    container.toaster.show(text, if (error) ToastKind.Error else ToastKind.Success)
                }
            }
            ClipsyTheme(dark = dark) { ClipsyNavHost(container, unlocker) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        // IntentCompat avoids the typed getParcelableExtra, which returns null on Android 13
        // in some cases. Some apps only attach the file as ClipData, so check there too.
        val uri: Uri? = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (uri != null) {
            container.incomingFile.value = uri
            return
        }
        // Plain text or a link shared from another app becomes a new snippet.
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        container.incomingText.value = SharedText(text.trim(), intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() })
    }
}
