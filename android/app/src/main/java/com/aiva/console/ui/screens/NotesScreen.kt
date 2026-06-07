package com.aiva.console.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.data.model.NoteItem
import com.aiva.console.ui.AivaIcons
import com.aiva.console.ui.components.HudCard
import com.aiva.console.ui.components.HudChip
import com.aiva.console.ui.components.SectionHeader
import com.aiva.console.ui.components.Tag
import com.aiva.console.ui.components.monoStyle
import com.aiva.console.ui.components.sansStyle
import com.aiva.console.ui.theme.Aiva

@Composable
fun NotesScreen(
    notes: List<NoteItem>,
    onAdd: (String) -> Unit,
    onVoice: () -> Unit,
) {
    val c = Aiva.c
    var input by remember { mutableStateOf("") }

    fun add() {
        val v = input.trim()
        if (v.isEmpty()) return
        onAdd(v)
        input = ""
    }

    Column {
        SectionHeader("Notes", first = true, trailing = { HudChip("${notes.size} SAVED") })

        /* add bar: text + voice */
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(c.panel2, RoundedCornerShape(9.dp))
                    .border(1.dp, c.line, RoundedCornerShape(9.dp))
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) Text("Write a note…", style = sansStyle(12.sp, color = c.inkFaint), maxLines = 1)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = sansStyle(12.sp, color = c.ink),
                    cursorBrush = SolidColor(c.violet),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier
                    .size(width = 44.dp, height = 40.dp)
                    .background(Brush.linearGradient(listOf(c.violet, c.violet.copy(alpha = 0.85f))), RoundedCornerShape(9.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { add() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AivaIcons.Plus, null, Modifier.size(18.dp), tint = Color.White)
            }
            Box(
                Modifier
                    .size(width = 44.dp, height = 40.dp)
                    .background(c.panel2, RoundedCornerShape(9.dp))
                    .border(1.dp, c.mint.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onVoice),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AivaIcons.Mic, null, Modifier.size(17.dp), tint = c.mint)
            }
        }
        Spacer(Modifier.height(11.dp))

        if (notes.isEmpty()) {
            HudCard {
                Text(
                    "NO NOTES YET",
                    Modifier.padding(vertical = 14.dp).align(Alignment.CenterHorizontally),
                    style = monoStyle(11.sp, color = c.inkFaint),
                )
            }
        }
        notes.forEach { note ->
            HudCard(Modifier.padding(bottom = 8.dp), padding = PaddingValues(11.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        if (note.kind == "voice") AivaIcons.Mic else AivaIcons.Note,
                        null,
                        Modifier.size(15.dp).padding(top = 1.dp),
                        tint = if (note.kind == "voice") c.mint else c.violet2,
                    )
                    Column(Modifier.weight(1f)) {
                        if (!note.title.isNullOrBlank()) {
                            Text(note.title, style = sansStyle(12.5.sp, color = c.ink), maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                        }
                        Text(note.body, style = sansStyle(12.sp, color = if (note.title.isNullOrBlank()) c.ink else c.inkDim))
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Tag(note.kind, color = if (note.kind == "voice") c.mint else c.inkDim)
                            if (note.createdAt.isNotBlank()) {
                                Tag(note.createdAt.take(16).replace("T", " · "), color = c.inkFaint, borderColor = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
