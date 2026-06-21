package com.pulsedeck.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class PulseInfoPillStyle(
    val accent: Color,
) {
    Neutral(Color.White),
    Info(Blue),
    Success(Green),
    Warning(Color(0xFFFFB156)),
    Blocked(Color(0xFFFF5F6E)),
}

@Composable
internal fun PulseInfoPill(
    text: String,
    modifier: Modifier = Modifier,
    style: PulseInfoPillStyle = PulseInfoPillStyle.Neutral,
    icon: DeckIcon? = null,
) {
    val accent = style.accent
    Row(
        modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.22f), CircleShape)
            .heightIn(min = 28.dp)
            .padding(horizontal = if (icon == null) 11.dp else 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            PulseIcon(it, accent.copy(alpha = 0.92f), Modifier.size(14.dp))
            Box(Modifier.size(7.dp))
        }
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
