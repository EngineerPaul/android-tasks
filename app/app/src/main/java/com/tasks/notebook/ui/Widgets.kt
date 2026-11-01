package com.tasks.notebook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasks.notebook.RatingScale
import com.tasks.notebook.ui.theme.Manrope
import com.tasks.notebook.ui.theme.TasksColors

@Composable
fun AccentButton(text: String, accent: Color, onAccent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(accent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = onAccent, modifier = Modifier.size(18.dp))
        Text(
            text,
            color = onAccent,
            fontFamily = Manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
fun RatingStars(value: Int, accent: Color, muted: Color, onSelect: (Int) -> Unit) {
    Row {
        for (star in RatingScale.MIN..RatingScale.MAX) {
            Icon(
                imageVector = if (star <= value) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (star <= value) accent else muted,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onSelect(star) }
                    .padding(2.dp),
            )
        }
    }
}

@Composable
fun DeleteDot(onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(TasksColors.delete)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null, tint = TasksColors.white, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun CloudCard(
    fill: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(4.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(fill)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        content()
    }
}

@Composable
fun ChecklistMark(accent: Color, plate: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val scale = this.size.minDimension / 72f
        fun box(y: Float, filled: Boolean) {
            val top = y * scale
            val left = 0f
            val side = 16f * scale
            if (filled) {
                drawRoundRect(accent, Offset(left, top), Size(side, side), CornerRadius(4f * scale, 4f * scale))
                drawLine(plate, Offset(4f * scale, 8f * scale), Offset(7f * scale, 12f * scale), 2.5f * scale)
                drawLine(plate, Offset(7f * scale, 12f * scale), Offset(13f * scale, 4f * scale), 2.5f * scale)
            } else {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(left, top),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(4f * scale, 4f * scale),
                    style = Stroke(2f * scale),
                )
            }
        }
        fun bar(y: Float, width: Float, alpha: Float) {
            drawRoundRect(
                accent.copy(alpha = alpha),
                Offset(24f * scale, (y + 5f) * scale),
                Size(width * scale, 6f * scale),
                CornerRadius(3f * scale, 3f * scale),
            )
        }
        box(0f, true)
        box(24f, false)
        box(48f, false)
        bar(0f, 36f, 1f)
        bar(24f, 28f, 0.5f)
        bar(48f, 20f, 0.5f)
    }
}
