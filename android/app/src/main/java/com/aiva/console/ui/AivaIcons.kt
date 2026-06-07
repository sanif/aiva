package com.aiva.console.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Aiva thin-line HUD icon set — transcribed from design/prototype/icons.jsx
 * (24x24 viewBox, 1.6 stroke, round caps/joins). Tint via Icon(tint=...).
 */
object AivaIcons {

    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.stroke(d: String, w: Float = 1.6f) {
        addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = w,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun ImageVector.Builder.fill(d: String) {
        addPath(pathData = addPathNodes(d), fill = SolidColor(Color.White))
    }

    /** circle as path data */
    private fun c(cx: Float, cy: Float, r: Float) =
        "M${cx - r} $cy a$r $r 0 1 0 ${r * 2} 0 a$r $r 0 1 0 ${-r * 2} 0 z"

    /** rounded rect as path data */
    private fun rr(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} $y h${w - 2 * r} a$r $r 0 0 1 $r $r v${h - 2 * r} a$r $r 0 0 1 ${-r} $r " +
            "h${-(w - 2 * r)} a$r $r 0 0 1 ${-r} ${-r} v${-(h - 2 * r)} a$r $r 0 0 1 $r ${-r} z"

    val Home = build("home") {
        stroke("M3 10.2 L12 3 L21 10.2"); stroke("M5 9.5 V20 H19 V9.5"); stroke("M9.5 20 V15 H14.5 V20")
    }
    val Monitor = build("monitor") {
        stroke(rr(3f, 4f, 18f, 13f, 1.5f)); stroke("M7 13 L10 9.5 L12.4 11.7 L16.5 8")
        stroke("M9 21 H15"); stroke("M12 17 V21")
    }
    val Tasks = build("tasks") {
        stroke("M9 5 H20"); stroke("M9 12 H20"); stroke("M9 19 H20")
        stroke("M3.5 5 L4.5 6 L6.1 4"); stroke("M3.5 12 L4.5 13 L6.1 11"); stroke(c(4.5f, 19f, 1.3f))
    }
    val Chat = build("chat") {
        stroke("M4 5 H20 V16 H8 L4 19.5 Z")
        fill(c(9f, 10.5f, 0.9f)); fill(c(12.5f, 10.5f, 0.9f)); fill(c(16f, 10.5f, 0.9f))
    }
    val Grid = build("grid") {
        stroke(rr(3.5f, 3.5f, 7f, 7f, 1.4f)); stroke(rr(13.5f, 3.5f, 7f, 7f, 1.4f))
        stroke(rr(3.5f, 13.5f, 7f, 7f, 1.4f)); stroke(rr(13.5f, 13.5f, 7f, 7f, 1.4f))
    }
    val Gear = build("gear") {
        stroke(c(12f, 12f, 3f))
        // proper cog outline (Feather "settings") — not a sun!
        stroke(
            "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06" +
                "a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09" +
                "A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83" +
                "l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09" +
                "A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0" +
                "l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09" +
                "a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83" +
                "l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09" +
                "a1.65 1.65 0 0 0-1.51 1z"
        )
    }
    val Moon = build("moon") { stroke("M20 14.5 A8 8 0 0 1 9.5 4 A8 8 0 1 0 20 14.5 Z") }
    val Bolt = build("bolt") { stroke("M13 2 L4 14 H10 L9 22 L18 10 H12 Z") }
    val Cpu = build("cpu") {
        stroke(rr(6f, 6f, 12f, 12f, 2f)); stroke(rr(9.5f, 9.5f, 5f, 5f, 1f))
        stroke("M9 2 V5 M15 2 V5 M9 19 V22 M15 19 V22 M2 9 H5 M2 15 H5 M19 9 H22 M19 15 H22")
    }
    val Ram = build("ram") {
        stroke(rr(2.5f, 7f, 19f, 9f, 1.5f))
        stroke("M6 16 V18.5 M10 16 V18.5 M14 16 V18.5 M18 16 V18.5")
        stroke("M6.5 10.5 H8.5 M11 10.5 H13 M15.5 10.5 H17.5")
    }
    val Disk = build("disk") {
        stroke(c(12f, 12f, 9f)); stroke(c(12f, 12f, 2.4f)); stroke("M12 3 V7 M19 14 L15.5 13")
    }
    val Thermo = build("thermo") {
        stroke("M10 13.5 V5 a2 2 0 0 1 4 0 V13.5 a4 4 0 1 1 -4 0 Z"); fill(c(12f, 16.5f, 1.6f))
    }
    val Docker = build("docker") {
        stroke("M3 10 h3 v3 h-3 z"); stroke("M7 10 h3 v3 h-3 z"); stroke("M11 10 h3 v3 h-3 z")
        stroke("M7 6 h3 v3 h-3 z"); stroke("M11 6 h3 v3 h-3 z")
        stroke("M3 13 H17 a4 4 0 0 0 4 -3.5 c-1.5 -0.8 -2.6 0 -2.8 0.4")
    }
    val Bell = build("bell") {
        stroke("M6 9 a6 6 0 0 1 12 0 c0 5 2 6 2 6 H4 s2 -1 2 -6 z"); stroke("M10 19 a2 2 0 0 0 4 0")
    }
    val Send = build("send") { stroke("M4 12 L20 4 L14 20 L10.5 13.5 Z"); stroke("M10.5 13.5 L20 4") }
    val Mic = build("mic") {
        stroke(rr(9f, 3f, 6f, 11f, 3f)); stroke("M5.5 11 a6.5 6.5 0 0 0 13 0"); stroke("M12 17.5 V21 M9 21 H15")
    }
    val Plus = build("plus") { stroke("M12 5 V19 M5 12 H19") }
    val Check = build("check") { stroke("M5 12.5 L10 17 L19 6.5") }
    val Focus = build("focus") {
        stroke(c(12f, 12f, 3f)); stroke("M3 7 V4 H6 M21 7 V4 H18 M3 17 V20 H6 M21 17 V20 H18")
    }
    val Note = build("note") {
        stroke("M5 3 H15 L19 7 V21 H5 Z"); stroke("M14 3 V8 H19"); stroke("M8 13 H15 M8 17 H13")
    }
    val Url = build("url") {
        stroke(c(12f, 12f, 9f)); stroke("M3 12 H21"); stroke("M12 3 a14 14 0 0 1 0 18 a14 14 0 0 1 0 -18 z")
    }
    val Restart = build("restart") { stroke("M20 12 a8 8 0 1 1 -2.3 -5.6"); stroke("M20 4 V8 H16") }
    val Lock = build("lock") { stroke(rr(5f, 10f, 14f, 10f, 2f)); stroke("M8 10 V7 a4 4 0 0 1 8 0 V10") }
    val Script = build("script") { stroke("M7 8 L4 12 L7 16 M17 8 L20 12 L17 16 M14 5 L10 19") }
    val Wifi = build("wifi") {
        stroke("M2.5 8.5 a14 14 0 0 1 19 0 M5.5 12 a9 9 0 0 1 13 0 M8.5 15.5 a4.5 4.5 0 0 1 7 0")
        fill(c(12f, 19f, 1.1f))
    }
    val Battery = build("battery") {
        stroke(rr(2.5f, 8f, 16f, 9f, 2f)); stroke("M21 11 V14"); fill(rr(4.5f, 10f, 9f, 5f, 1f))
    }
    val ChevR = build("chevR") { stroke("M9 6 L15 12 L9 18") }
    val Pulse = build("pulse") { stroke("M2 12 H6 L8 6 L12 20 L14 12 H22") }
    val Shield = build("shield") {
        stroke("M12 3 L5 6 V11 c0 4 3 7 7 9 c4 -2 7 -5 7 -9 V6 Z"); stroke("M9 12 L11 14 L15 10")
    }
}
