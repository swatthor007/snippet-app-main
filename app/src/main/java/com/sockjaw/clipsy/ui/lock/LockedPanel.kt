package com.sockjaw.clipsy.ui.lock

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.activity.compose.LocalActivity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.sockjaw.clipsy.ui.components.IconTile
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.pressable
import compose.icons.TablerIcons
import compose.icons.tablericons.Lock

/** Stands in for a locked snippet's text: three blurred-looking bars, a lock, and one button. */
@Composable
fun LockedPanel(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.92f, 0.74f, 0.52f).forEach { w ->
                Row(Modifier.fillMaxWidth(w).height(10.dp).clip(CircleShape).background(ClipsyColors.Track)) {}
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(TablerIcons.Lock, size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text("Locked", style = MaterialTheme.typography.titleMedium)
                Text("Sign in to read or edit this text.", color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption)
            }
            Text(
                "Unlock",
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ClipsyColors.Accent)
                    .pressable(role = Role.Button, onClickLabel = "Unlock this snippet", onClick = onUnlock)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                color = ClipsyColors.OnAccent,
                fontSize = ClipsyType.Label,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Keeps screenshots and the recent-apps preview blank while [active] (locked text is on screen). */
@Composable
fun SecureWindow(active: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(active) {
        if (active) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (active) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
