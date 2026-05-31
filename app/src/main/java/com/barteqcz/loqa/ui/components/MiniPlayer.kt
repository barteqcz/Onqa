package com.barteqcz.loqa.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.barteqcz.loqa.R
import com.barteqcz.loqa.data.model.RadioStation
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    station: RadioStation,
    stations: List<RadioStation>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    metadata: String?,
    showHqIcon: Boolean,
    isScrollable: Boolean = false,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "swipeOffset"
    )
    
    val interactionSource = remember { MutableInteractionSource() }

    val borderColor by animateColorAsState(
        targetValue = (if (station.isFavorite) Color(0xFFE57373) else MaterialTheme.colorScheme.primary)
            .copy(alpha = if (isPlaying || isBuffering) 0.5f else 0f),
        animationSpec = tween(durationMillis = 500),
        label = "miniPlayerBorder"
    )

    val elevation by animateDpAsState(
        targetValue = if (isScrollable) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 500),
        label = "miniPlayerElevation"
    )

    Surface(
        modifier = Modifier
            .padding(12.dp)
            .navigationBarsPadding()
            .fillMaxWidth()
            .height(88.dp)
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 120) {
                            onPrevious()
                        } else if (offsetX < -120) {
                            onNext()
                        }
                        offsetX = 0f
                    },
                ) { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount
                }
            },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            borderColor
        ),
        tonalElevation = if (isScrollable) 8.dp else 0.dp,
        shadowElevation = elevation
    ) {
        AnimatedContent(
            targetState = station.streamUrl,
            transitionSpec = {
                val initialIndex = stations.indexOfFirst { it.streamUrl == initialState || it.streamUrlHq == initialState }
                val targetIndex = stations.indexOfFirst { it.streamUrl == targetState || it.streamUrlHq == targetState }
                
                val isNext = if ((initialIndex != -1) && (targetIndex != -1)) {
                    if (stations.size > 2) {
                        when (initialIndex) {
                            stations.lastIndex -> targetIndex == 0
                            0 -> targetIndex != stations.lastIndex
                            else -> targetIndex > initialIndex
                        }
                    } else {
                        targetIndex > initialIndex
                    }
                } else {
                    offsetX < 0
                }

                if (isNext) {
                    (slideInHorizontally(tween(400)) { it } + fadeIn(tween(400)))
                        .togetherWith(slideOutHorizontally(tween(400)) { -it } + fadeOut(tween(400)))
                } else {
                    (slideInHorizontally(tween(400)) { -it } + fadeIn(tween(400)))
                        .togetherWith(slideOutHorizontally(tween(400)) { it } + fadeOut(tween(400)))
                }
            },
            label = "stationChange"
        ) { targetUrl ->
            val targetStation = stations.find { it.streamUrl == targetUrl || it.streamUrlHq == targetUrl } ?: station
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val scale by animateFloatAsState(if (isPlaying) 1.05f else 1f, label = "logoScale")
                AsyncImage(
                    model = targetStation.logo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val infoColor by animateColorAsState(
                        targetValue = if (targetStation.isFavorite) Color(0xFFE57373) else MaterialTheme.colorScheme.primary,
                        label = "miniPlayerIconColor"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = targetStation.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false).basicMarquee()
                        )
                        
                        if (showHqIcon) {
                            HqIcon(tint = infoColor)
                        }
                    }
                    
                    AnimatedContent(
                        targetState = metadata,
                        transitionSpec = {
                            (fadeIn(tween(600)) + slideInVertically { it / 2 })
                                .togetherWith(fadeOut(tween(600)) + slideOutVertically { -it / 2 })
                        },
                        label = "metadataTransition"
                    ) { text ->
                        if (!text.isNullOrBlank()) {
                            Text(
                                text = text,
                                color = infoColor,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        } else if (isBuffering) {
                            Text(
                                text = stringResource(R.string.buffering),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(52.dp)
                ) {
                    AnimatedContent(
                        targetState = isBuffering to isPlaying,
                        transitionSpec = {
                            fadeIn(tween(200)).togetherWith(fadeOut(tween(200)))
                        },
                        label = "playPauseIcon"
                    ) { (buffering, playing) ->
                        if (buffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 3.dp,
                            )
                        } else {
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
