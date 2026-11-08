package com.sockjaw.clipsy.ui.lock

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sockjaw.clipsy.data.Snippet
import com.sockjaw.clipsy.data.vault.LockMode
import com.sockjaw.clipsy.data.vault.Vault
import com.sockjaw.clipsy.ui.components.ClipsySheet
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.IconTile
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.SheetTitle
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import com.sockjaw.clipsy.ui.theme.pressable
import compose.icons.TablerIcons
import compose.icons.tablericons.Key
import compose.icons.tablericons.ShieldLock

val LocalUnlocker = staticCompositionLocalOf<Unlocker?> { null }

/**
 * The one place that asks the user to sign in. Anything that needs a locked snippet's text calls
 * [require] (or [reveal]) and gets its action back once the vault is open, however that happened:
 * phone lock, fingerprint or Clipsy PIN. Put [UnlockHost] in the composition to show its sheets.
 */
class Unlocker(
    private val vault: Vault,
    private val auth: Authenticator,
    /** Shows a short message: a toast in the app, a system toast over other apps. */
    private val say: (text: String, error: Boolean) -> Unit,
) {
    sealed interface Sheet {
        data class Pin(val onUnlocked: () -> Unit, val onCancel: () -> Unit, val onFingerprint: (() -> Unit)?) : Sheet
        data class Create(val title: String, val onCreated: (String) -> Unit, val onCancel: () -> Unit) : Sheet
        data class Setup(val onPhoneLock: () -> Unit, val onPin: () -> Unit, val onCancel: () -> Unit) : Sheet
    }

    var sheet by mutableStateOf<Sheet?>(null)
        internal set

    /** Runs [action] with the vault open, signing in first if it isn't. [onCancel] runs if the user backs out or it can't be opened. */
    fun require(reason: String, onCancel: () -> Unit = {}, action: () -> Unit) {
        val st = vault.state.value
        when {
            !st.setUp -> ensureSetUp(onCancel, action)
            vault.isUnlocked -> action()
            st.mode == LockMode.SYSTEM -> {
                if (st.deviceKeyLost) {
                    say("Locked snippets can’t be opened on this phone any more.", true)
                    return onCancel()
                }
                signInWithPhone(reason, onCancel) { if (vault.unlockWithDevice()) action() else { say("Couldn’t unlock. Try again.", true); onCancel() } }
            }
            else -> signInWithPin(reason, onCancel, action)
        }
    }

    /** Opens the vault, then hands over copies of [snippets] with any locked text filled in. Nothing is asked when none is locked. */
    fun reveal(snippets: List<Snippet>, reason: String, onCancel: () -> Unit = {}, use: (List<Snippet>) -> Unit) {
        if (snippets.none { it.locked && it.content.isEmpty() }) return use(snippets)
        require(reason, onCancel) {
            val opened = snippets.mapNotNull { s ->
                if (!s.locked || s.content.isNotEmpty()) s else s.sealedContent?.let(vault::open)?.let(s::revealed)
            }
            if (opened.isEmpty()) { say("Couldn’t open that snippet.", true); onCancel() } else use(opened)
        }
    }

    fun reveal(snippet: Snippet, reason: String, onCancel: () -> Unit = {}, use: (Snippet) -> Unit) =
        reveal(listOf(snippet), reason, onCancel) { use(it.first()) }

    /** First lock ever: pick phone lock or a PIN, set the vault up, then run [then]. */
    fun ensureSetUp(onCancel: () -> Unit = {}, then: () -> Unit) {
        if (vault.state.value.setUp) return then()
        sheet = Sheet.Setup(
            onPhoneLock = {
                if (!vault.deviceIsSecure()) {
                    say("Set a screen lock in your phone’s settings first, or use a Clipsy PIN.", true)
                    return@Setup onCancel()
                }
                signInWithPhone("Set up locked snippets", onCancel) { if (vault.setUpSystem()) then() else { say("Couldn’t set up locking on this phone.", true); onCancel() } }
            },
            onPin = {
                sheet = Sheet.Create("Choose a PIN", onCancel = onCancel, onCreated = { pin ->
                    if (vault.setUpPin(pin)) then() else { say("Couldn’t set up locking on this phone.", true); onCancel() }
                })
            },
            onCancel = onCancel,
        )
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun switchToPin(onDone: () -> Unit = {}) = require("Switch to a Clipsy PIN") {
        sheet = Sheet.Create("Choose a PIN", onCancel = {}, onCreated = { pin ->
            if (vault.setUpPin(pin)) { say("Now using a Clipsy PIN", false); onDone() } else say("Couldn’t switch.", true)
        })
    }

    fun switchToPhoneLock(onDone: () -> Unit = {}) = require("Switch to your phone lock") {
        if (!vault.deviceIsSecure()) return@require say("Set a screen lock in your phone’s settings first.", true)
        signInWithPhone("Switch to your phone lock", {}) {
            if (vault.switchToSystem()) { say("Now using your phone lock", false); onDone() } else say("Couldn’t switch.", true)
        }
    }

    fun changePin(onDone: () -> Unit = {}) = require("Change your PIN") {
        sheet = Sheet.Create("Choose a new PIN", onCancel = {}, onCreated = { pin ->
            if (vault.changePin(pin)) { say("PIN changed", false); onDone() } else say("Couldn’t change the PIN.", true)
        })
    }

    fun enableFingerprint(onDone: () -> Unit = {}) = require("Turn on fingerprint") {
        auth.authenticate("Use fingerprint for locked snippets", null, AuthKind.FINGERPRINT) { r ->
            when (r) {
                AuthResult.SUCCESS -> if (vault.enableBiometricWrap()) onDone() else say("Couldn’t turn on fingerprint.", true)
                AuthResult.UNAVAILABLE -> say("Add a fingerprint in your phone’s settings first.", true)
                else -> {}
            }
        }
    }

    fun disableFingerprint() = vault.disableBiometricWrap()

    fun canUseFingerprint(): Boolean = auth.canUseFingerprint()

    // ── Sign-in steps ─────────────────────────────────────────────────────────

    private fun signInWithPhone(reason: String, onCancel: () -> Unit, onSuccess: () -> Unit) {
        auth.authenticate("Unlock snippets", reason, AuthKind.PHONE_LOCK) { r ->
            when (r) {
                AuthResult.SUCCESS -> onSuccess()
                AuthResult.UNAVAILABLE -> { say("Set a screen lock in your phone’s settings first.", true); onCancel() }
                else -> onCancel()
            }
        }
    }

    private fun signInWithPin(reason: String, onCancel: () -> Unit, action: () -> Unit) {
        val fingerprint = vault.state.value.biometricWrap && auth.canUseFingerprint()
        val showPin = {
            sheet = Sheet.Pin(
                onUnlocked = action,
                onCancel = onCancel,
                onFingerprint = if (fingerprint) ({ tryFingerprint(reason, action) {} }) else null,
            )
        }
        if (fingerprint) tryFingerprint(reason, action) { showPin() } else showPin()
    }

    private fun tryFingerprint(reason: String, action: () -> Unit, fallback: () -> Unit) {
        auth.authenticate("Unlock snippets", reason, AuthKind.FINGERPRINT) { r ->
            when {
                r == AuthResult.SUCCESS && vault.unlockWithDevice() -> { sheet = null; action() }
                else -> fallback()
            }
        }
    }
}

/** Shows whichever sheet the [Unlocker] is asking for. */
@Composable
fun UnlockHost(unlocker: Unlocker, vault: Vault) {
    val vaultState by vault.state.collectAsStateWithLifecycle()
    when (val s = unlocker.sheet) {
        null -> {}
        is Unlocker.Sheet.Pin -> PinUnlockSheet(
            check = vault::unlockWithPin,
            lockedUntil = vaultState.lockedOutUntil,
            onUnlocked = s.onUnlocked,
            onDismiss = { unlocker.sheet = null; if (!vault.isUnlocked) s.onCancel() },
            onFingerprint = s.onFingerprint,
        )
        is Unlocker.Sheet.Create -> {
            var created by remember(s) { mutableStateOf(false) }
            PinCreateSheet(
                title = s.title,
                onCreated = { created = true; s.onCreated(it) },
                onDismiss = { unlocker.sheet = null; if (!created) s.onCancel() },
            )
        }
        is Unlocker.Sheet.Setup -> {
            var picked by remember(s) { mutableStateOf(false) }
            ClipsySheet(onDismiss = { unlocker.sheet = null; if (!picked) s.onCancel() }) { close ->
                SheetTitle("Set up locked snippets", "Locked text is encrypted on this phone and hidden until you sign in. Choose how you’ll sign in.")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    GroupedCard {
                        SetupChoice("Use my phone lock", "Fingerprint, face, or your phone’s PIN. Recommended.", TablerIcons.ShieldLock) { picked = true; close { s.onPhoneLock() } }
                        RowDivider(60.dp)
                        SetupChoice("Use a Clipsy PIN", "A separate 6-digit PIN, with an optional fingerprint.", TablerIcons.Key) { picked = true; close { s.onPin() } }
                    }
                    Text(
                        "With your phone lock, removing the screen lock from your phone makes locked snippets unreadable. A Clipsy PIN can’t be reset if you forget it.",
                        Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        color = ClipsyColors.TextSecondary,
                        fontSize = ClipsyType.Caption,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupChoice(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).pressable(pressedScale = 0.985f, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = ClipsyType.Body, fontWeight = FontWeight.Medium)
            Text(detail, color = ClipsyColors.TextSecondary, fontSize = ClipsyType.Caption)
        }
    }
}
