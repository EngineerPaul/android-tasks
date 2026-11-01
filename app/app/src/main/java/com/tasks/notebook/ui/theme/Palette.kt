package com.tasks.notebook.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class TasksPalette(
    val id: String,
    val background: Color,
    val cloud: Color,
    val cloudBorder: Color,
    val accent: Color,
    val onAccent: Color,
    val newRow: Color,
)

object TasksColors {
    val delete = Color(0xFFE5484D)
    val error = Color(0xFFFF8D8D)
    val dueFill = Color(0xFF4A2A24)
    val link = Color(0xFF7EB6FF)
    val text = Color(0xFFF6F3EC)
    val secondary = Color(0xFFB7B1A6)
    val muted = Color(0xFF8E887F)
    val undoBottom = Color(0xFF161B22)
    val undoTop = Color(0xFF3E4654)
    val white = Color(0xFFFFFFFF)
}

object Palettes {
    val amber = TasksPalette(
        id = "amber",
        background = Color(0xFF120C06),
        cloud = Color(0xFF101010),
        cloudBorder = Color(0xFF2A2623),
        accent = Color(0xFFD87404),
        onAccent = Color(0xFF1A1408),
        newRow = Color(0xFF2A2623),
    )
    val teal = TasksPalette(
        id = "teal",
        background = Color(0xFF031217),
        cloud = Color(0xFF111A21),
        cloudBorder = Color(0xFF1E333C),
        accent = Color(0xFF086C86),
        onAccent = Color(0xFFF4FBFC),
        newRow = Color(0xFF1A3038),
    )
    val violet = TasksPalette(
        id = "violet",
        background = Color(0xFF140E1A),
        cloud = Color(0xFF1E1625),
        cloudBorder = Color(0xFF322838),
        accent = Color(0xFF754DC8),
        onAccent = Color(0xFF160E22),
        newRow = Color(0xFF32243E),
    )
    val coral = TasksPalette(
        id = "coral",
        background = Color(0xFF18100D),
        cloud = Color(0xFF2C1E15),
        cloudBorder = Color(0xFF3E2C22),
        accent = Color(0xFFE55E40),
        onAccent = Color(0xFF1A0E0A),
        newRow = Color(0xFF3E2A20),
    )
    val all = listOf(amber, teal, violet, coral)

    fun byId(id: String): TasksPalette = all.firstOrNull { it.id == id } ?: amber
}

val LocalPalette = staticCompositionLocalOf { Palettes.amber }
