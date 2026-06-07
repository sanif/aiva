package com.aiva.console.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiva.console.data.model.TaskItem
import com.aiva.console.ui.AivaIcons
import com.aiva.console.ui.components.HudCard
import com.aiva.console.ui.components.PriBar
import com.aiva.console.ui.components.Tag
import com.aiva.console.ui.components.TickBox
import com.aiva.console.ui.components.monoStyle
import com.aiva.console.ui.components.priColor
import com.aiva.console.ui.components.sansStyle
import com.aiva.console.ui.theme.Aiva

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(
    tasks: List<TaskItem>,
    onToggle: (TaskItem) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (TaskItem) -> Unit = {},
) {
    val c = Aiva.c
    var tab by rememberSaveable { mutableStateOf("today") }
    var projectFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }

    val projects = tasks.mapNotNull { it.project }.distinct().sorted()
    val list = tasks.filter {
        val statusOk = when (tab) {
            "today" -> it.status == "today"
            "upcoming" -> it.status == "upcoming"
            else -> it.status == "done"
        }
        statusOk && (projectFilter == null || it.project == projectFilter)
    }

    fun add() {
        val v = input.trim()
        if (v.isEmpty()) return
        onAdd(v)
        input = ""
    }

    @Composable
    fun ProjectChip(label: String, on: Boolean, onClick: () -> Unit) {
        Box(
            Modifier
                .background(if (on) c.violet.copy(alpha = 0.16f) else c.panel2, RoundedCornerShape(12.dp))
                .border(1.dp, if (on) c.violet else c.line, RoundedCornerShape(12.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(label.uppercase(), style = monoStyle(8.5.sp, tracking = 0.6.sp, color = if (on) c.ink else c.inkFaint), maxLines = 1)
        }
    }

    Column {
        /* tabs */
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.panel2, RoundedCornerShape(9.dp))
                .border(1.dp, c.line, RoundedCornerShape(9.dp))
                .padding(3.dp),
        ) {
            listOf("today" to "Today", "upcoming" to "Upcoming", "done" to "Completed").forEach { (id, label) ->
                val on = tab == id
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (on) c.violet.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { tab = id }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label.uppercase(),
                        style = monoStyle(9.5.sp, tracking = 0.95.sp, color = if (on) c.ink else c.inkFaint),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(11.dp))

        /* project filter chips */
        if (projects.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProjectChip("ALL", projectFilter == null) { projectFilter = null }
                projects.forEach { p ->
                    ProjectChip("#$p", projectFilter == p) {
                        projectFilter = if (projectFilter == p) null else p
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        /* add bar (Today tab) */
        if (tab == "today") {
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
                    if (input.isEmpty()) Text("Add a task… (#project @tag !high)", style = sansStyle(11.5.sp, color = c.inkFaint), maxLines = 1)
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
            }
            Spacer(Modifier.height(11.dp))
        }

        /* task cards */
        if (list.isEmpty()) {
            HudCard {
                Text(
                    "NO TASKS",
                    Modifier.padding(vertical = 14.dp).align(Alignment.CenterHorizontally),
                    style = monoStyle(11.sp, color = c.inkFaint),
                )
            }
        }
        list.forEach { task ->
            val done = task.status == "done"
            // long-press a card to delete the task
            HudCard(
                Modifier
                    .padding(bottom = 8.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = { onDelete(task) },
                    ),
                padding = androidx.compose.foundation.layout.PaddingValues(11.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TickBox(done, { onToggle(task) }, Modifier.padding(top = 1.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            PriBar(task.priority, 13.dp)
                            Text(
                                task.title,
                                style = sansStyle(12.5.sp, color = if (done) c.inkFaint else c.ink).copy(
                                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                                ),
                            )
                        }
                        if (!task.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                task.notes,
                                style = sansStyle(10.5.sp, color = c.inkFaint),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (task.project != null) {
                                Tag("#${task.project}", color = c.violet2, borderColor = c.violet.copy(alpha = 0.3f))
                            }
                            task.tags?.split(",")?.take(2)?.forEach { tg ->
                                if (tg.isNotBlank()) Tag("@$tg", color = c.blue, borderColor = null)
                            }
                            Tag(
                                task.category,
                                color = when (task.category) {
                                    "work" -> c.blue
                                    "personal" -> c.mint
                                    else -> c.violet2
                                },
                                borderColor = when (task.category) {
                                    "work" -> c.blue.copy(alpha = 0.3f)
                                    "personal" -> c.mint.copy(alpha = 0.3f)
                                    else -> c.violet.copy(alpha = 0.3f)
                                },
                            )
                            Tag(task.priority, color = priColor(task.priority), borderColor = null)
                            if (task.due != null) Tag(task.due, color = c.inkFaint, borderColor = null)
                        }
                    }
                }
            }
        }
    }
}
