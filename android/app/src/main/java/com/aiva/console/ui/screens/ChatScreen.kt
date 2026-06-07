package com.aiva.console.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.ui.AivaIcons
import com.aiva.console.data.model.ToolInfo
import com.aiva.console.ui.components.StatusDot
import com.aiva.console.ui.components.Waveform
import com.aiva.console.ui.components.monoStyle
import com.aiva.console.ui.components.sansStyle
import com.aiva.console.ui.theme.Aiva
import com.aiva.console.viewmodel.ChatMessage

val QUICK_PROMPTS = listOf(
    "What should I focus on now?",
    "Show system status",
    "Summarize today",
    "Any urgent alerts?",
    "Start focus mode",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    streaming: Boolean,
    onSend: (String) -> Unit,
    onVoice: () -> Unit = {},
    listening: Boolean = false,
    onResolveSuggestion: (ChatMessage, Boolean) -> Unit = { _, _ -> },
    onCopied: () -> Unit = {},
    tools: List<ToolInfo> = emptyList(),
) {
    val c = Aiva.c
    var input by remember { mutableStateOf("") }
    var showTools by remember { mutableStateOf(false) }
    val mic = listening
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    fun submit(text: String = input) {
        val q = text.trim()
        if (q.isEmpty() || streaming) return
        input = ""
        onSend(q)
    }

    Column(Modifier.fillMaxSize()) {
        /* messages */
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 2.dp),
        ) {
            items(messages) { msg ->
                if (msg.suggestion != null) {
                    ApprovalCard(
                        actionId = msg.suggestion,
                        onApprove = { onResolveSuggestion(msg, true) },
                        onDismiss = { onResolveSuggestion(msg, false) },
                    )
                } else {
                    Bubble(
                        msg,
                        cursor = streaming && msg === messages.lastOrNull() && !msg.fromMe,
                        onCopied = onCopied,
                    )
                }
            }
            // typing indicator while Aiva hasn't produced the first token yet
            if (streaming && messages.lastOrNull()?.fromMe == true) {
                item { TypingBubble() }
            }
        }

        /* quick prompts */
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (tools.isNotEmpty()) {
                Box(
                    Modifier
                        .background(if (showTools) c.violet.copy(alpha = 0.16f) else c.panel2, RoundedCornerShape(20.dp))
                        .border(1.dp, if (showTools) c.violet else c.mint.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { showTools = !showTools }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    Text(
                        "TOOLS · ${tools.size}",
                        style = monoStyle(9.sp, tracking = 0.9.sp, color = if (showTools) c.ink else c.mint),
                        maxLines = 1,
                    )
                }
            }
            QUICK_PROMPTS.forEach { p ->
                Box(
                    Modifier
                        .background(c.panel2, RoundedCornerShape(20.dp))
                        .border(1.dp, c.line, RoundedCornerShape(20.dp))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { submit(p) }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    Text(p, style = sansStyle(10.5.sp, color = c.inkDim), maxLines = 1)
                }
            }
        }

        /* allowed tools — everything the agent can do, nothing else */
        if (showTools) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.panel2, RoundedCornerShape(11.dp))
                    .border(1.dp, c.mint.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusDot(c.mint)
                    Text("ALLOWED TOOLS", style = monoStyle(8.5.sp, tracking = 1.2.sp, color = c.mint))
                }
                Spacer(Modifier.height(7.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    tools.forEach { tool ->
                        Box(
                            Modifier
                                .background(c.panel2, RoundedCornerShape(6.dp))
                                .border(1.dp, c.line, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            Text(tool.name, style = monoStyle(8.5.sp, color = c.inkDim), maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "Actions run only after your approval. Nothing outside this list can execute.",
                    style = monoStyle(8.sp, color = c.inkFaint),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        /* composer */
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // mic (voice placeholder — pulses red while "listening")
            val micPulse = rememberInfiniteTransition(label = "mic")
            val micGlow by micPulse.animateFloat(
                0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "micGlow",
            )
            Box(
                Modifier
                    .size(42.dp)
                    .background(c.panel2, CircleShape)
                    .border(
                        1.dp,
                        if (mic) c.err.copy(alpha = 0.5f + 0.3f * micGlow) else c.line,
                        CircleShape,
                    )
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onVoice),
                contentAlignment = Alignment.Center,
            ) {
                if (mic) Waveform(bars = 5, color = c.err, height = 14.dp)
                else Icon(AivaIcons.Mic, null, Modifier.size(18.dp), tint = c.inkDim)
            }

            // input field
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .background(c.panel2, RoundedCornerShape(21.dp))
                    .border(1.dp, c.line, RoundedCornerShape(21.dp))
                    .padding(horizontal = 15.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) {
                    Text("Message Aiva…", style = sansStyle(12.5.sp, color = c.inkFaint), maxLines = 1)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = sansStyle(12.5.sp, color = c.ink),
                    cursorBrush = SolidColor(c.violet),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // send
            Box(
                Modifier
                    .size(42.dp)
                    .background(Brush.linearGradient(listOf(c.violet, c.violet.copy(alpha = 0.85f))), CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { submit() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AivaIcons.Send, null, Modifier.size(17.dp), tint = Color.White)
            }
        }
    }
}

/** Animated three-dot "Aiva is typing…" bubble. */
@Composable
private fun TypingBubble() {
    val c = Aiva.c
    val t = rememberInfiniteTransition(label = "typing")
    val phase by t.animateFloat(
        0f, 3f,
        infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)),
        label = "dots",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            Modifier
                .background(c.panel2, RoundedCornerShape(13.dp, 13.dp, 13.dp, 4.dp))
                .border(1.dp, c.line, RoundedCornerShape(13.dp, 13.dp, 13.dp, 4.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                val on = phase.toInt() % 3 == i
                Box(
                    Modifier
                        .size(if (on) 6.dp else 5.dp)
                        .background(c.violet2.copy(alpha = if (on) 0.95f else 0.35f), CircleShape),
                )
            }
        }
    }
}

/** Aiva proposes an allowlisted action — runs only when approved. */
@Composable
private fun ApprovalCard(actionId: String, onApprove: () -> Unit, onDismiss: () -> Unit) {
    val c = Aiva.c
    val label = QUICK_ACTIONS.find { it.id == actionId }?.label ?: actionId
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .background(c.panel2, RoundedCornerShape(13.dp))
                .border(1.dp, c.warn.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
                .padding(11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(c.warn)
                Text("PERMISSION REQUIRED", style = monoStyle(8.sp, tracking = 1.2.sp, color = c.warn))
            }
            Spacer(Modifier.height(6.dp))
            Text("Aiva wants to run: $label", style = sansStyle(12.sp, color = c.ink))
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .background(Brush.linearGradient(listOf(c.mint, c.mint.copy(alpha = 0.8f))), RoundedCornerShape(8.dp))
                        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onApprove)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("APPROVE", style = monoStyle(9.sp, tracking = 1.sp, color = Color(0xFF06060C)))
                }
                Box(
                    Modifier
                        .background(c.panel2, RoundedCornerShape(8.dp))
                        .border(1.dp, c.line, RoundedCornerShape(8.dp))
                        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("DISMISS", style = monoStyle(9.sp, tracking = 1.sp, color = c.inkDim))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(msg: ChatMessage, cursor: Boolean, onCopied: () -> Unit = {}) {
    val c = Aiva.c
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromMe) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                // long-press a bubble to copy its text
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = {
                        if (msg.text.isNotBlank()) {
                            clipboard.setText(AnnotatedString(msg.text))
                            onCopied()
                        }
                    },
                )
                .then(
                    if (msg.fromMe) {
                        Modifier.background(
                            Brush.linearGradient(listOf(c.violet, c.violet.copy(alpha = 0.88f))),
                            RoundedCornerShape(13.dp, 13.dp, 4.dp, 13.dp),
                        )
                    } else {
                        Modifier
                            .background(c.panel2, RoundedCornerShape(13.dp, 13.dp, 13.dp, 4.dp))
                            .border(1.dp, c.line, RoundedCornerShape(13.dp, 13.dp, 13.dp, 4.dp))
                    }
                )
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            if (!msg.fromMe) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    StatusDot(c.ai)
                    Text("AIVA", style = monoStyle(8.sp, tracking = 1.44.sp, color = c.violet2))
                }
                Spacer(Modifier.height(5.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    msg.text,
                    style = sansStyle(12.sp, color = if (msg.fromMe) Color.White else c.ink),
                )
                if (cursor) {
                    val blink = rememberInfiniteTransition(label = "cursor")
                    val a by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "blink")
                    Spacer(Modifier.width(2.dp))
                    Box(
                        Modifier
                            .size(width = 7.dp, height = 13.dp)
                            .background(c.mint.copy(alpha = a)),
                    )
                }
            }
        }
    }
}
