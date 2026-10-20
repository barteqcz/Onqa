package com.barteqcz.onqa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.model.DistanceUnit
import com.barteqcz.onqa.data.model.RadioStation
import kotlin.math.roundToInt

@Composable
fun FavoriteHeart(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = fadeOut(animationSpec = snap())
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            modifier = modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationCard(
    station: RadioStation,
    isActive: Boolean,
    isPlaying: Boolean,
    showHqIcon: Boolean,
    modifier: Modifier = Modifier,
    distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (station.isFavorite) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        animationSpec = tween(durationMillis = 300),
        label = "borderColor"
    )

    val activeOverlayColor by animateColorAsState(
        targetValue = when {
            isActive && station.isFavorite -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 300),
        label = "activeOverlayColor"
    )

    val cardShape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            borderColor
        ),
        tonalElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(activeOverlayColor)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isImageLoaded by remember(station.logo) { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isImageLoaded) {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(station.logo)
                            .crossfade(true)
                            .build(),
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
                        targetValue = if (station.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        label = "stationIconColor",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = station.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    animationMode = MarqueeAnimationMode.Immediately,
                                    initialDelayMillis = 1000
                                ),
                        )
                        
                        if (showHqIcon) {
                            HqIcon(tint = infoColor)
                        }
                    }
                    if ((station.transmitterName != null) || (station.distance != null)) {
                        val infoText = buildString {
                            station.transmitterName?.let { append(it) }
                            station.distance?.let { distKm ->
                                if (isNotEmpty()) append(" ")
                                if (distanceUnit == DistanceUnit.MILES) {
                                    val distMiles = (distKm * 0.621371).roundToInt()
                                    if (distMiles == 0) {
                                        append(stringResource(R.string.less_than_one_mi_with_dot))
                                    } else {
                                        append(stringResource(R.string.distance_mi_with_dot, distMiles))
                                    }
                                } else {
                                    val dist = distKm.roundToInt()
                                    if (dist == 0) {
                                        append(stringResource(R.string.less_than_one_km_with_dot))
                                    } else {
                                        append(stringResource(R.string.distance_with_dot, dist))
                                    }
                                }
                            }
                        }
                        
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = infoText,
                                color = infoColor,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.padding(start = 8.dp).width(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying) {
                        EqualizerAnimation(color = if (station.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    } else {
                        FavoriteHeart(visible = station.isFavorite)
                    }
                }
            }
        }
    }
}
