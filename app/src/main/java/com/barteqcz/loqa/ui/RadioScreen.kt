package com.barteqcz.loqa.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.barteqcz.loqa.R
import com.barteqcz.loqa.data.model.RadioStation
import com.barteqcz.loqa.ui.theme.CardBackground
import com.barteqcz.loqa.ui.theme.DarkBackground
import com.barteqcz.loqa.ui.theme.TextGrey
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    viewModel: RadioViewModel,
    onSettingsClick: () -> Unit,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBackground)
                        .statusBarsPadding()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { }
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.app_name),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                        )
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    
                    LocationHeader(viewState.locationInfo)
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = viewState.uiState,
                    transitionSpec = {
                        fadeIn(tween(500)).togetherWith(fadeOut(tween(500)))
                    },
                    contentKey = { if (!viewState.isNetworkAvailable) "no_internet" else it::class },
                    label = "uiStateTransition",
                    modifier = Modifier.fillMaxSize()
                ) { state ->
                    if (!viewState.isNetworkAvailable) {
                        StatusContainer(
                            message = stringResource(R.string.error_no_internet),
                            isError = true,
                            modifier = Modifier.padding(paddingValues),
                            onRetry = { viewModel.refresh() }
                        )
                    } else {
                        when (state) {
                            is RadioUiState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(paddingValues),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            is RadioUiState.Success -> {
                                if (state.stations.isEmpty()) {
                                    StatusContainer(
                                        message = stringResource(R.string.no_stations_message),
                                        modifier = Modifier.padding(paddingValues)
                                    )
                                } else {
                                    val listState = rememberLazyListState()

                                    val favoritesCount = remember(state.stations) { state.stations.count { it.isFavorite } }
                                    var lastFavoritesCount by remember { mutableIntStateOf(favoritesCount) }
                                    
                                    LaunchedEffect(favoritesCount) {
                                        if (favoritesCount > lastFavoritesCount && listState.firstVisibleItemIndex <= 2) {
                                            listState.animateScrollToItem(0)
                                        }
                                        lastFavoritesCount = favoritesCount
                                    }

                                    LaunchedEffect(viewState.selectedUrl) {
                                        val selectedUrl = viewState.selectedUrl
                                        if (selectedUrl != null) {
                                            val layoutInfo = listState.layoutInfo
                                            val totalItems = layoutInfo.totalItemsCount
                                            val visibleItems = layoutInfo.visibleItemsInfo

                                            val fitsOnScreen = visibleItems.size >= totalItems
                                            
                                            if (!fitsOnScreen) {
                                                val isLastItemVisible = visibleItems.any { it.index == (totalItems - 1) }
                                                val selectedIndex = state.stations.indexOfFirst { it.streamUrl == selectedUrl }
                                                val isLastItemSelected = selectedIndex == state.stations.size - 1
                                                
                                                if (isLastItemVisible && isLastItemSelected && listState.canScrollBackward) {
                                                    listState.animateScrollToItem(totalItems - 1)
                                                }
                                            }
                                        }
                                    }

                                    val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                    val showShadow by remember {
                                        derivedStateOf {
                                            (viewState.selectedUrl != null) && listState.canScrollForward
                                        }
                                    }

                                    Box(modifier = Modifier.fillMaxSize()) {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(
                                                top = paddingValues.calculateTopPadding() + 8.dp,
                                                bottom = if (viewState.selectedUrl != null) 116.dp + bottomNavPadding else 16.dp + bottomNavPadding,
                                                start = 20.dp,
                                                end = 20.dp
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            item(key = "scroll_anchor") {
                                                Spacer(modifier = Modifier.height(0.5.dp))
                                            }

                                            itemsIndexed(
                                                items = state.stations,
                                                key = { _, it -> "${it.name}|${it.network}" }
                                            ) { _, station ->
                                                Box(
                                                    modifier = Modifier
                                                        .animateItem(
                                                            fadeInSpec = tween(durationMillis = 300),
                                                            fadeOutSpec = tween(durationMillis = 300),
                                                            placementSpec = spring(
                                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                                stiffness = Spring.StiffnessMedium
                                                            )
                                                        )
                                                ) {
                                                    StationCard(
                                                        station = station,
                                                        isActive = viewState.selectedUrl != null && (station.streamUrl == viewState.selectedUrl || station.streamUrlHq == viewState.selectedUrl),
                                                        isPlaying = viewState.selectedUrl != null && (station.streamUrl == viewState.selectedUrl || station.streamUrlHq == viewState.selectedUrl) && viewState.isPlaying,
                                                        showHqIcon = !station.streamUrlHq.isNullOrBlank(),
                                                        onClick = {
                                                            val url = station.streamUrl ?: station.streamUrlHq
                                                            url?.let { viewModel.toggleStation(it) }
                                                        },
                                                        onLongClick = { viewModel.toggleFavorite(station) }
                                                    )
                                                }
                                            }
                                        }

                                        AnimatedVisibility(
                                            visible = showShadow,
                                            enter = fadeIn(),
                                            exit = fadeOut(),
                                            modifier = Modifier.align(Alignment.BottomCenter)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(140.dp)
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                DarkBackground.copy(alpha = 0.4f),
                                                                DarkBackground.copy(alpha = 0.8f),
                                                                DarkBackground
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                            is RadioUiState.Error -> {
                                if (state.isServerError) {
                                    ServerNapContainer(
                                        modifier = Modifier.padding(paddingValues),
                                        onRetry = { viewModel.refresh() }
                                    )
                                } else {
                                    StatusContainer(
                                        message = stringResource(R.string.error_no_internet),
                                        isError = true,
                                        modifier = Modifier.padding(paddingValues),
                                        onRetry = { viewModel.refresh() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteHeart(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = fadeOut(animationSpec = snap())
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            modifier = modifier.size(24.dp),
            tint = Color(0xFFE57373),
        )
    }
}

@Composable
fun LocationHeader(info: LocationInfo) {
    if (info.city == null) {
        Spacer(modifier = Modifier.height(0.dp))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.current_location_header),
            color = TextGrey,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        AnimatedContent(
            targetState = info.city,
            transitionSpec = {
                fadeIn(tween(1000)).togetherWith(fadeOut(tween(1000)))
            },
            label = "locationTransition"
        ) { targetCity ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val targetCode = info.countryCode
                val flagEmoji = remember(targetCode) {
                    if (targetCode?.length == 2) {
                        targetCode.uppercase().map { char ->
                            Character.toChars(0x1F1E6 + (char - 'A'))
                        }.joinToString("") { String(it) }
                    } else null
                }

                flagEmoji?.let {
                    Text(
                        text = it,
                        fontSize = 20.sp
                    )
                }
                Text(
                    text = targetCity,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                
                AnimatedVisibility(
                    visible = info.distanceKm != null,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Text(
                        text = if (info.distanceKm == 0) stringResource(R.string.less_than_one_km_with_dot) else stringResource(R.string.distance_with_dot, info.distanceKm ?: 0),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationCard(
    station: RadioStation,
    isActive: Boolean,
    isPlaying: Boolean,
    showHqIcon: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (station.isFavorite) Color(0xFFE57373).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
        animationSpec = tween(durationMillis = 500),
        label = "borderColor"
    )

    val cardShape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
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
        color = if (isActive) Color(0xFF131217) else CardBackground,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            borderColor
        ),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = station.logo,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2C2C2E)),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                val iconColor by animateColorAsState(
                    targetValue = if (station.isFavorite) Color(0xFFE57373) else MaterialTheme.colorScheme.primary,
                    label = "stationIconColor",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = station.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false).basicMarquee(),
                    )
                    
                    if (showHqIcon) {
                        HqIcon(tint = iconColor)
                    }
                }
                if ((station.transmitterName != null) || (station.distance != null)) {
                    val infoText = buildString {
                        station.transmitterName?.let { append(it) }
                        station.distance?.let {
                            val dist = it.roundToInt()
                            if (isNotEmpty()) append(" ")
                            if (dist == 0) {
                                append(stringResource(R.string.less_than_one_km_with_dot))
                            } else {
                                append(stringResource(R.string.distance_with_dot, dist))
                            }
                        }
                    }
                    
                    Text(
                        text = infoText,
                        color = iconColor,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier.padding(start = 8.dp).width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    EqualizerAnimation(color = if (station.isFavorite) Color(0xFFE57373) else MaterialTheme.colorScheme.primary)
                } else {
                    FavoriteHeart(visible = station.isFavorite)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    station: RadioStation,
    stations: List<RadioStation>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    metadata: String?,
    showHqIcon: Boolean,
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
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            borderColor
        ),
        tonalElevation = 12.dp,
        shadowElevation = 20.dp
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
                    val iconColor by animateColorAsState(
                        targetValue = if (targetStation.isFavorite) Color(0xFFE57373) else MaterialTheme.colorScheme.primary,
                        label = "miniPlayerIconColor"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = targetStation.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false).basicMarquee()
                        )
                        
                        if (showHqIcon) {
                            HqIcon(tint = iconColor)
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
                                color = iconColor,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        } else if (isBuffering) {
                            Text(
                                text = stringResource(R.string.buffering),
                                color = TextGrey,
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
                                color = Color.White,
                                strokeWidth = 3.dp,
                            )
                        } else {
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServerNapContainer(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            color = CardBackground.copy(alpha = 0.5f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            CircleShape
                        )
                        .padding(12.dp)
                )

                Text(
                    text = stringResource(R.string.error_server_nap),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                onRetry?.let {
                    Button(
                        onClick = it,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.retry), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusContainer(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            color = CardBackground.copy(alpha = 0.5f),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                else Color.White.copy(alpha = 0.05f),
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isError) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                CircleShape
                            )
                            .padding(12.dp)
                    )
                }

                Text(
                    text = message,
                    color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else TextGrey,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )

                onRetry?.let {
                    Button(
                        onClick = it,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.retry), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun HqIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        color = tint.copy(alpha = 0.1f),
        shape = RoundedCornerShape(3.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            tint.copy(alpha = 0.5f)
        ),
        modifier = modifier.height(16.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "HQ",
                color = tint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 10.sp
            )
        }
    }
}

@Composable
fun EqualizerAnimation(color: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier = Modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val transition = rememberInfiniteTransition(label = "eq")
        val durations = listOf(450, 350, 550, 400, 500)
        
        durations.forEachIndexed { index, duration ->
            val heightScale by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$index",
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightScale)
                    .background(
                        color,
                        RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                    ),
            )
        }
    }
}
