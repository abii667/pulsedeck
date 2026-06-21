package com.pulsedeck.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsedeck.app.navigation.Screen
import com.pulsedeck.app.navigation.isLibraryNavScreen

@Composable
private fun NavDockGlass(
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    radius: Dp = 22.dp,
    horizontalPadding: Dp = 6.dp,
    containerAlpha: Float = 0.96f,
    borderAlpha: Float = 0.08f,
    content: @Composable () -> Unit,
) {
    val navShape = RoundedCornerShape(radius)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(navShape)
            .background(Color.Black.copy(alpha = containerAlpha.coerceIn(0f, 1f)))
            .border(
                1.dp,
                Color.White.copy(alpha = borderAlpha.coerceIn(0f, 1f)),
                navShape,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun MiniNavGlass(
    modifier: Modifier = Modifier,
    containerAlpha: Float = 0.96f,
    borderAlpha: Float = 0.08f,
    content: @Composable () -> Unit,
) {
    NavDockGlass(
        modifier = modifier,
        containerAlpha = containerAlpha,
        borderAlpha = borderAlpha,
        content = content,
    )
}

@Composable
internal fun MiniTrackStrip(
    track: Track,
    modifier: Modifier = Modifier,
    primaryTextColor: Color = Color.White,
    secondaryTextColor: Color = Color.White,
) {
    Row(modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Art(
            track.album,
            Modifier.size(38.dp),
            9.dp,
            useCase = ArtworkUseCase.SongListThumbnail,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = primaryTextColor, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = secondaryTextColor, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun BottomNav(
    screen: Screen,
    onScreen: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 58.dp,
    iconWidth: Dp = 70.dp,
    iconHeight: Dp = 54.dp,
    iconSize: Dp = 34.dp,
    plainSelection: Boolean = false,
) {
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        NavIcon(DeckIcon.Grid, isLibraryNavScreen(screen), iconWidth, iconHeight, iconSize, plainSelection) { onScreen(Screen.Library) }
        NavIcon(DeckIcon.Bars, screen == Screen.Audio, iconWidth, iconHeight, iconSize, plainSelection) { onScreen(Screen.Audio) }
        NavIcon(DeckIcon.Search, screen == Screen.Search, iconWidth, iconHeight, iconSize, plainSelection) { onScreen(Screen.Search) }
        NavIcon(DeckIcon.More, screen == Screen.Settings, iconWidth, iconHeight, iconSize, plainSelection) { onScreen(Screen.Settings) }
    }
}

@Composable
internal fun PlayerBottomDock(screen: Screen, onScreen: (Screen) -> Unit, modifier: Modifier = Modifier) {
    MiniNavGlass(modifier = modifier) {
        BottomNav(screen, onScreen, height = 48.dp, iconWidth = 54.dp, iconHeight = 48.dp, iconSize = 25.dp, plainSelection = true)
    }
}

@Composable
private fun NavIcon(icon: DeckIcon, selected: Boolean, width: Dp = 70.dp, height: Dp = 54.dp, iconSize: Dp = 34.dp, plainSelection: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected && !plainSelection) {
                    Brush.linearGradient(listOf(StreamAccentRed.copy(alpha = 0.24f), StreamDeepRed.copy(alpha = 0.18f)))
                } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .border(1.dp, if (selected && !plainSelection) StreamAccentRed.copy(alpha = 0.28f) else Color.Transparent, RoundedCornerShape(24.dp))
            .pressScaleEffect(interactionSource)
            .semantics {
                role = Role.Tab
                contentDescription = icon.accessibilityLabel
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected && !plainSelection) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(StreamAccentRed.copy(alpha = 0.22f), size.minDimension * 0.36f, Offset(size.width * 0.50f, size.height * 0.50f))
            }
        }
        PulseIcon(
            icon,
            if (selected) StreamTextPrimary else Color.White.copy(0.58f),
            Modifier.size(if (selected && plainSelection) iconSize + 3.dp else iconSize),
        )
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IconPill(
    icon: DeckIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    contentDescription: String = icon.accessibilityLabel,
    onLongClick: (() -> Unit)? = null,
    height: Dp = 50.dp,
    iconSize: Dp = 28.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(height)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    if (active) {
                        listOf(Color.White.copy(0.32f), Blue.copy(0.30f), Color.Black.copy(0.30f))
                    } else {
                        listOf(Color.White.copy(0.16f), Color.Black.copy(0.20f))
                    },
                ),
            )
            .border(1.dp, Color.White.copy(if (active) 0.24f else 0.00f), RoundedCornerShape(28.dp))
            .pressScaleEffect(interactionSource)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                stateDescription = if (active) "On" else "Off"
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center
    ) {
        PulseIcon(icon, if (active) Color.White else Color.White.copy(0.88f), Modifier.size(iconSize))
    }
}

@Composable
internal fun IconCircle(icon: DeckIcon, onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val hitSize = if (size < 48.dp) 48.dp else size
    Box(
        modifier
            .size(hitSize)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(0.15f), Color.Black.copy(0.18f))))
            .pressScaleEffect(interactionSource)
            .semantics {
                role = Role.Button
                contentDescription = icon.accessibilityLabel
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        PulseIcon(icon, Color.White, Modifier.size(size * 0.52f))
    }
}

@Composable
internal fun IconTransport(icon: DeckIcon, onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier, transparent: Boolean = false) {
    val interactionSource = remember { MutableInteractionSource() }
    val hitSize = if (size < 48.dp) 48.dp else size
    Box(
        modifier
            .size(hitSize)
            .clip(CircleShape)
            .background(if (transparent) Color.Transparent else Color.Black.copy(0.92f))
            .pressScaleEffect(interactionSource)
            .semantics {
                role = Role.Button
                contentDescription = icon.accessibilityLabel
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        PulseIcon(icon, Color.White, Modifier.size(size * 0.52f))
    }
}

@Composable
internal fun IconTap(icon: DeckIcon, onClick: () -> Unit, size: Dp = 48.dp) {
    val interactionSource = remember { MutableInteractionSource() }
    val hitSize = if (size < 48.dp) 48.dp else size
    Box(
        Modifier
            .size(hitSize)
            .clip(CircleShape)
            .pressScaleEffect(interactionSource)
            .semantics {
                role = Role.Button
                contentDescription = icon.accessibilityLabel
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        PulseIcon(icon, Color.White, Modifier.size(size * 0.54f))
    }
}

@Composable
private fun IconSquare(icon: DeckIcon, onClick: () -> Unit = {}) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(width = 70.dp, height = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(0.05f))
            .pressScaleEffect(interactionSource)
            .semantics {
                role = Role.Button
                contentDescription = icon.accessibilityLabel
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        PulseIcon(icon, Color.White.copy(0.34f), Modifier.size(26.dp))
    }
}

@Composable
internal fun PulseIcon(icon: DeckIcon, color: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(icon.drawableRes),
        contentDescription = null,
        tint = color,
        modifier = modifier,
    )
}


