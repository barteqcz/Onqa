package com.barteqcz.onqa.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.model.UpdateInfo
import com.barteqcz.onqa.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    viewModel: RadioViewModel,
    onSettingsClick: () -> Unit,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
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
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                
                LocationHeader(viewState.locationInfo)
                
                viewState.updateInfo?.let { updateInfo ->
                    UpdateBanner(
                        updateInfo = updateInfo,
                        accentColor = viewState.settings.accentColor,
                        onDownloadClick = { viewModel.startUpdateDownload(context, it) }
                    )
                }
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
                                val density = LocalDensity.current

                                val favoritesCount = remember(state.stations) { state.stations.count { it.isFavorite } }
                                var lastFavoritesCount by remember { mutableIntStateOf(favoritesCount) }
                                
                                LaunchedEffect(favoritesCount) {
                                    if (favoritesCount > lastFavoritesCount && listState.firstVisibleItemIndex <= 2) {
                                        listState.animateScrollToItem(0)
                                    }
                                    lastFavoritesCount = favoritesCount
                                }

                                var wasMiniPlayerVisible by remember { mutableStateOf(viewState.selectedUrl != null) }
                                LaunchedEffect(viewState.selectedUrl) {
                                    val selectedUrl = viewState.selectedUrl
                                    if (selectedUrl != null) {
                                        val layoutInfo = listState.layoutInfo
                                        val totalItems = layoutInfo.totalItemsCount
                                        val visibleItems = layoutInfo.visibleItemsInfo

                                        if (visibleItems.isNotEmpty()) {
                                            val isLastItemVisible = visibleItems.any { it.index == (totalItems - 1) }
                                            val selectedIndex = state.stations.indexOfFirst { it.streamUrl == selectedUrl }
                                            val isLastItemSelected = selectedIndex == state.stations.size - 1
                                            
                                            if (isLastItemVisible && listState.canScrollBackward) {
                                                if (!wasMiniPlayerVisible) {
                                                    val scrollAmount = with(density) { 100.dp.toPx() }
                                                    listState.animateScrollBy(scrollAmount)
                                                } else if (isLastItemSelected) {
                                                    listState.animateScrollToItem(totalItems - 1)
                                                }
                                            }
                                        }
                                    }
                                    wasMiniPlayerVisible = selectedUrl != null
                                }

                                val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                val showShadow by remember {
                                    derivedStateOf {
                                        viewState.selectedUrl != null
                                    }
                                }

                                LaunchedEffect(listState.canScrollForward, listState.canScrollBackward) {
                                    viewModel.setScrollable(listState.canScrollForward || listState.canScrollBackward)
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
                                                    isPlaying = viewState.selectedUrl != null && (station.streamUrl == viewState.selectedUrl || station.streamUrlHq == viewState.selectedUrl) && viewState.isPlaying && !viewState.isBuffering,
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
                                        val bgColor = MaterialTheme.colorScheme.background
                                        val shadowBrush = remember(bgColor) {
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    bgColor.copy(alpha = 0.4f),
                                                    bgColor.copy(alpha = 0.8f),
                                                    bgColor
                                                )
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                                .background(shadowBrush)
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

@Composable
fun UpdateBanner(updateInfo: UpdateInfo, accentColor: Color, onDownloadClick: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = accentColor.copy(alpha = 0.15f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.update_available_message_simple),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = { onDownloadClick(updateInfo.downloadUrl) },
                colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
            ) {
                Text(
                    text = stringResource(R.string.update_action_download),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
