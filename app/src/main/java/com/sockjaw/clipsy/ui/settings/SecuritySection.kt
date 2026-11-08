package com.sockjaw.clipsy.ui.settings

import com.sockjaw.clipsy.ui.theme.ClipsyType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sockjaw.clipsy.AppContainer
import com.sockjaw.clipsy.data.RelockAfter
import com.sockjaw.clipsy.data.vault.LockMode
import com.sockjaw.clipsy.ui.components.ChoiceRow
import com.sockjaw.clipsy.ui.components.GroupedCard
import com.sockjaw.clipsy.ui.components.RowDivider
import com.sockjaw.clipsy.ui.components.SectionLabel
import com.sockjaw.clipsy.ui.components.SwitchRow
import com.sockjaw.clipsy.ui.components.ValueRow
import com.sockjaw.clipsy.ui.lock.LocalUnlocker
import com.sockjaw.clipsy.ui.theme.ClipsyColors
import compose.icons.TablerIcons
import compose.icons.tablericons.Fingerprint
import compose.icons.tablericons.Key
import compose.icons.tablericons.Lock
import compose.icons.tablericons.ShieldLock

/** How locked snippets are opened, and how long they stay open. */
@Composable
fun SecuritySection(container: AppContainer, modifier: Modifier = Modifier) {
    val unlocker = LocalUnlocker.current
    val vault by container.vault.state.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val lockedCount by container.repository.snippets.collectAsStateWithLifecycle(emptyList())

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Security")
        GroupedCard {
            if (!vault.setUp) {
                ValueRow("Locked snippets", "Not set up", icon = TablerIcons.Lock, onClick = { unlocker?.ensureSetUp {} })
            } else {
                ChoiceRow(
                    "Unlock with", LockMode.entries, vault.mode, { it.label },
                    { picked ->
                        if (picked != vault.mode) {
                            if (picked == LockMode.PIN) unlocker?.switchToPin() else unlocker?.switchToPhoneLock()
                        }
                    },
                    icon = TablerIcons.ShieldLock,
                    sheetSubtitle = "Phone lock uses your fingerprint, face or phone PIN. A Clipsy PIN is separate from your phone.",
                )
                if (vault.mode == LockMode.PIN) {
                    RowDivider(60.dp)
                    ValueRow("Change PIN", "", icon = TablerIcons.Key, onClick = { unlocker?.changePin() })
                    if (unlocker?.canUseFingerprint() == true) {
                        RowDivider(60.dp)
                        SwitchRow(
                            "Use fingerprint", vault.biometricWrap,
                            { on -> if (on) unlocker.enableFingerprint() else unlocker.disableFingerprint() },
                            icon = TablerIcons.Fingerprint,
                        )
                    }
                }
                RowDivider(60.dp)
                ChoiceRow(
                    "Lock again", RelockAfter.entries, settings.relockAfter, { it.label },
                    { v -> container.settings.update { it.copy(relockAfter = v) } },
                    icon = TablerIcons.Lock,
                    sheetSubtitle = "How long locked snippets stay open after you leave Clipsy. They always lock when the screen turns off.",
                )
            }
        }
        Text(
            if (!vault.setUp) "Lock any text snippet from its screen. Locked text is encrypted on this phone."
            else if (vault.deviceKeyLost) "Your phone’s screen lock was removed, so locked snippets can’t be opened. Set a screen lock again, or delete them."
            else "${lockedCount.count { it.locked }} locked. Locked text is encrypted and left out of backups and widgets.",
            Modifier.padding(horizontal = 4.dp),
            color = if (vault.deviceKeyLost) ClipsyColors.Danger else ClipsyColors.TextSecondary,
            fontSize = ClipsyType.Caption,
        )
    }
}
