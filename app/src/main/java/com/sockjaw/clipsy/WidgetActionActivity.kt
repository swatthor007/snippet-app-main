package com.sockjaw.clipsy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.sockjaw.clipsy.service.Share
import kotlinx.coroutines.launch

/** Invisible stop for a widget tile set to "Share": opens the share sheet for that snippet and closes. */
class WidgetActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.data?.lastPathSegment?.toLongOrNull()
        lifecycleScope.launch {
            val container = (application as ClipsyApp).container
            val snippet = id?.let { container.repository.get(it) }
            if (snippet != null && !snippet.needsUnlock) {
                Share.chooser(this@WidgetActionActivity, listOf(snippet))?.let { runCatching { startActivity(it) } }
            }
            finish()
            overridePendingTransition(0, 0)
        }
    }
}
