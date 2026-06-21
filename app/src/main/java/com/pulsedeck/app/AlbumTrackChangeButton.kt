package com.pulsedeck.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AlbumTrackChangeButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember(enabled) { MutableInteractionSource() }
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) StreamAccentRed.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, (if (enabled) StreamAccentRed else Color.White).copy(alpha = if (enabled) 0.20f else 0.06f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulseIcon(DeckIcon.Sliders, Color.White.copy(alpha = if (enabled) 0.90f else 0.32f), Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text("Change", color = Color.White.copy(alpha = if (enabled) 0.90f else 0.34f), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}
