package com.aiva.console.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.ui.AivaIcons
import com.aiva.console.ui.theme.Aiva
import com.aiva.console.ui.theme.Mono
import com.aiva.console.ui.theme.Sans

/* ---------- text style helpers ---------- */

@Composable
fun monoStyle(
    size: TextUnit,
    weight: FontWeight = FontWeight.SemiBold,
    tracking: TextUnit = 0.sp,
    color: Color = Color.Unspecified,
) = TextStyle(fontFamily = Mono, fontSize = size, fontWeight = weight, letterSpacing = tracking, color = color)

@Composable
fun sansStyle(
    size: TextUnit,
    weight: FontWeight = FontWeight.Medium,
    color: Color = Color.Unspecified,
) = TextStyle(fontFamily = Sans, fontSize = size, fontWeight = weight, color = color)

/* ---------- glow halo for cards / accents ---------- */

fun Modifier.hudGlow(color: Color, radius: Dp = 11.dp, alpha: Float = 0.30f): Modifier = drawBehind {
    val r = CornerRadius(radius.toPx())
    for (i in 1..5) {
        drawRoundRect(
            color = color.copy(alpha = alpha * (1f - i / 5.5f) * 0.5f),
            topLeft = Offset(-i * 1.5f, -i * 1.5f),
            size = androidx.compose.ui.geometry.Size(size.width + i * 3f, size.height + i * 3f),
            cornerRadius = r,
            style = Stroke(width = 2.5f),
        )
    }
}

/* ---------- HUD corner brackets (.bracket) ---------- */

fun Modifier.bracket(color: Color): Modifier = drawBehind {
    val s = 9.dp.toPx()
    val inset = 6.dp.toPx()
    val w = 1.5.dp.toPx()
    // top-left
    drawLine(color, Offset(inset, inset), Offset(inset + s, inset), w)
    drawLine(color, Offset(inset, inset), Offset(inset, inset + s), w)
    // bottom-right
    drawLine(color, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - s, size.height - inset), w)
    drawLine(color, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - s), w)
}

/* ---------- card ---------- */

@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    showBracket: Boolean = false,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Aiva.c
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier
            .fillMaxWidth()
            .then(if (glow) Modifier.hudGlow(c.violet) else Modifier)
            .background(c.panel2, shape)
            .border(1.dp, if (glow) c.violet.copy(alpha = 0.28f) else c.line, shape)
            .then(if (showBracket) Modifier.bracket(c.violet.copy(alpha = 0.55f)) else Modifier)
            .padding(padding),
        content = content,
    )
}

/* ---------- eyebrow + section header ---------- */

@Composable
fun Eyebrow(text: String, tickColor: Color = Aiva.c.violet, color: Color = Aiva.c.inkFaint) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(5.dp)
                .hudGlow(tickColor, radius = 1.dp, alpha = 0.8f)
                .background(tickColor, RoundedCornerShape(1.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(text.uppercase(), style = monoStyle(9.sp, tracking = 1.8.sp, color = color), maxLines = 1)
    }
}

@Composable
fun SectionHeader(
    title: String,
    tickColor: Color = Aiva.c.violet,
    first: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = if (first) 2.dp else 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Eyebrow(title, tickColor)
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** Tappable "DETAILS >" style link used in section headers. */
@Composable
fun HeaderLink(text: String, onClick: () -> Unit) {
    val c = Aiva.c
    Row(
        Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = monoStyle(8.5.sp, tracking = 0.85.sp, color = c.inkFaint))
        Icon(AivaIcons.ChevR, null, Modifier.size(11.dp), tint = c.inkFaint)
    }
}

/* ---------- status dot + chip ---------- */

@Composable
fun StatusDot(color: Color, size: Dp = 6.dp) {
    Box(
        Modifier
            .size(size)
            .drawBehind {
                drawCircle(color.copy(alpha = 0.55f), radius = this.size.minDimension)
            }
            .background(color, CircleShape),
    )
}

@Composable
fun statusColor(status: String): Color = when (status) {
    "up" -> Aiva.c.ok
    "warn" -> Aiva.c.warn
    else -> Aiva.c.err
}

@Composable
fun HudChip(
    text: String,
    dotColor: Color? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val c = Aiva.c
    Row(
        modifier
            .background(c.panel2, RoundedCornerShape(7.dp))
            .border(1.dp, c.line, RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dotColor != null) StatusDot(dotColor)
        if (icon != null) Icon(icon, null, Modifier.size(11.dp), tint = c.inkDim)
        Text(text.uppercase(), style = monoStyle(9.5.sp, tracking = 0.76.sp, color = c.inkDim), maxLines = 1)
    }
}

/* ---------- tag (tasks meta) ---------- */

@Composable
fun Tag(text: String, color: Color = Aiva.c.inkDim, borderColor: Color? = Aiva.c.line) {
    Box(
        Modifier
            .then(
                if (borderColor != null) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(5.dp))
                } else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(text.uppercase(), style = monoStyle(8.sp, tracking = 0.8.sp, color = color), maxLines = 1)
    }
}

/* ---------- priority bar + tickbox ---------- */

@Composable
fun priColor(pri: String): Color = when (pri) {
    "high" -> Aiva.c.err
    "med" -> Aiva.c.warn
    else -> Aiva.c.mint2
}

@Composable
fun PriBar(pri: String, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(3.dp)
            .height(height)
            .background(priColor(pri), RoundedCornerShape(2.dp)),
    )
}

@Composable
fun TickBox(done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Aiva.c
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .size(19.dp)
            .then(if (done) Modifier.hudGlow(c.mint, radius = 6.dp, alpha = 0.5f) else Modifier)
            .background(if (done) c.mint else Color.Transparent, shape)
            .border(1.5.dp, if (done) c.mint else c.line, shape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(AivaIcons.Check, null, Modifier.size(13.dp), tint = c.bg)
    }
}

/* ---------- toggle ---------- */

@Composable
fun HudToggle(on: Boolean, onToggle: () -> Unit) {
    val c = Aiva.c
    val bg by animateColorAsState(if (on) c.violet else Color.White.copy(alpha = 0.1f), label = "togglebg")
    val x by animateDpAsState(if (on) 22.dp else 3.dp, label = "togglex")
    Box(
        Modifier
            .size(width = 44.dp, height = 25.dp)
            .then(if (on) Modifier.hudGlow(c.violet, radius = 13.dp, alpha = 0.45f) else Modifier)
            .background(bg, RoundedCornerShape(13.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
    ) {
        Box(
            Modifier
                .offset(x = x, y = 3.dp)
                .size(19.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/* ---------- button ---------- */

@Composable
fun HudButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = Aiva.c
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .then(if (primary) Modifier.hudGlow(c.violet, radius = 10.dp, alpha = 0.4f) else Modifier)
            .then(
                if (primary) {
                    Modifier.background(
                        Brush.linearGradient(listOf(c.violet, c.violet.copy(red = c.violet.red * 0.86f))),
                        shape,
                    )
                } else {
                    Modifier
                        .background(c.panel2, shape)
                        .border(1.dp, c.line, shape)
                }
            )
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading(); Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(15.dp), tint = if (primary) Color.White else c.ink)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text.uppercase(),
            style = monoStyle(11.sp, tracking = 1.32.sp, color = if (primary) Color.White else c.ink),
            maxLines = 1,
        )
    }
}
