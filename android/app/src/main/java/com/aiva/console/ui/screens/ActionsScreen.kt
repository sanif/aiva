package com.aiva.console.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.ui.AivaIcons
import com.aiva.console.ui.Screen
import com.aiva.console.ui.components.HudCard
import com.aiva.console.ui.components.HudChip
import com.aiva.console.ui.components.SectionHeader
import com.aiva.console.ui.components.monoStyle
import com.aiva.console.ui.theme.Aiva
import com.aiva.console.ui.theme.AivaPalette

/** The 9 allowlisted quick actions — ids match backend actions.yaml / nav intents. */
data class QuickActionDef(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val tint: (AivaPalette) -> Color,
    val nav: Screen? = null,
)

val QUICK_ACTIONS = listOf(
    QuickActionDef("focus", "Start Focus Mode", AivaIcons.Focus, { it.violet }),
    QuickActionDef("note", "Add Note", AivaIcons.Note, { it.mint }, nav = Screen.Notes),
    QuickActionDef("voice", "Voice Note", AivaIcons.Mic, { it.blue }),
    QuickActionDef("chat", "Open Chat", AivaIcons.Chat, { it.violet2 }, nav = Screen.Chat),
    QuickActionDef("monitor", "Show Monitoring", AivaIcons.Pulse, { it.mint2 }, nav = Screen.Monitor),
    QuickActionDef("restart_backend", "Restart Backend", AivaIcons.Restart, { it.warn }),
    QuickActionDef("open_dashboard", "Local Dashboard", AivaIcons.Url, { it.blue }),
    QuickActionDef("backup_status", "Run Script", AivaIcons.Script, { it.violet2 }),
    QuickActionDef("lock_display", "Lock / Dim", AivaIcons.Lock, { it.inkDim }),
)

@Composable
fun ActionsScreen(onFire: (QuickActionDef) -> Unit) {
    val c = Aiva.c

    Column {
        SectionHeader(
            "Quick actions", first = true,
            trailing = { HudChip("ALLOWLIST", icon = AivaIcons.Shield) },
        )

        QUICK_ACTIONS.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowItems.forEach { action ->
                    ActionCard(action, Modifier.weight(1f)) { onFire(action) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
        }

        Spacer(Modifier.height(2.dp))
        HudCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(AivaIcons.Shield, null, Modifier.size(16.dp), tint = c.mint)
                Text(
                    "Only predefined, allowlisted actions run. No arbitrary shell access from the phone.",
                    style = monoStyle(9.5.sp, FontWeight.Medium, 0.29.sp, c.inkFaint),
                )
            }
        }
    }
}

@Composable
private fun ActionCard(action: QuickActionDef, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Aiva.c
    val tint = action.tint(c)
    HudCard(
        modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        padding = PaddingValues(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.drawBehind {
                    drawCircle(
                        Brush.radialGradient(listOf(tint.copy(alpha = 0.35f), Color.Transparent)),
                        radius = size.minDimension,
                    )
                },
            ) {
                Icon(action.icon, null, Modifier.size(22.dp), tint = tint)
            }
            Text(
                action.label.uppercase(),
                style = monoStyle(9.5.sp, tracking = 0.6.sp, color = c.inkDim),
            )
        }
    }
}
