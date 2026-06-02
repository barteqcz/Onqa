package com.barteqcz.onqa.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.model.RadioStation
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
        targetValue = if (isScrollable) 16.dp else 12.dp,
        animationSpec = tween(durationMillis = 500),
        label = "miniPlayerElevation"
    )

    Surface(
        modifier = Modifier
            .padding(12.dp)
            .navigationBarsPadding()
            .drawBehind {
                val shadowColor = Color.Black.copy(alpha = 0.2f)
                val blurRadius = (elevation.toPx() * 1.5f).coerceAtLeast(1f)
                drawIntoCanvas { canvas ->
                    val paint = Paint()
                    val frameworkPaint = paint.nativePaint
                    frameworkPaint.color = android.graphics.Color.TRANSPARENT
                    frameworkPaint.setShadowLayer(
                        blurRadius,
                        0f,
                        0f,
                        shadowColor.toArgb()
                    )
                    canvas.drawRoundRect(
                        0f,
                        0f,
                        size.width,
                        size.height,
                        28.dp.toPx(),
                        28.dp.toPx(),
                        paint
                    )
                }
            }
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .fillMaxWidth()
            .height(88.dp)
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { }
            .pointerInput(stations.size) {
                if (stations.size > 1) {
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
                }
            },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            borderColor
        ),
        tonalElevation = if (isScrollable) 8.dp else 4.dp,
        shadowElevation = 0.dp
    ) {
        AnimatedContent(
            targetState = station to showHqIcon,
            transitionSpec = {
                val (initialStation, initialHq) = initialState
                val (targetStation, targetHq) = targetState

                if (initialStation.name == targetStation.name && initialStation.network == targetStation.network) {
                    if (initialHq != targetHq) {
                        (fadeIn(tween(400)) togetherWith fadeOut(tween(400)))
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                } else {
                    val initialIndex = stations.indexOfFirst { it.name == initialStation.name && it.network == initialStation.network }
                    val targetIndex = stations.indexOfFirst { it.name == targetStation.name && it.network == targetStation.network }

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
                }
            },
            label = "stationChange"
        ) { (targetStation, targetShowHqIcon) ->
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val scale by animateFloatAsState(if (isPlaying) 1.05f else 1f, label = "logoScale")
                var isImageLoaded by remember(targetStation.logo) { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isImageLoaded) {
                        Icon(
                            imageVector = Icons.Rounded.CellTower,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    AsyncImage(
                        model = targetStation.logo,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onSuccess = { isImageLoaded = true },
                        onError = { isImageLoaded = false }
                    )
                }

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
                        
                        if (targetShowHqIcon) {
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
