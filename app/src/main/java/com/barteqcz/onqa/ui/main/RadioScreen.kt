package com.barteqcz.onqa.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barteqcz.onqa.R
import com.barteqcz.onqa.ui.components.*
import com.barteqcz.onqa.ui.theme.AnimationSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    viewModel: RadioViewModel,
    onSettingsClick: () -> Unit,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    var isSearchFocused by remember { mutableStateOf(value = false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(viewState.isSearchActive) {
        if (isSearchFocused) {
            focusManager.clearFocus()
        } else {
            viewModel.setSearchActive(false)
        }
    }

    LaunchedEffect(viewState.isSearchActive) {
        if (viewState.isSearchActive) {
            searchFocusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = viewState.isSearchActive
                ) {
                    focusManager.clearFocus()
                }
        ) mainColumn@ {
            // Content Offset for Top Bar
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(72.dp))
            
            AnimatedVisibility(
                visible = viewState.settings.showLocationHeader && !isLandscape && !viewState.isSearchActive,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LocationHeader(viewState.locationInfo)
            }

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = viewState.uiState,
                    transitionSpec = {
                        fadeIn(AnimationSystem.vividTween()).togetherWith(
                            fadeOut(AnimationSystem.vividTween())
                        )
                    },
                    contentKey = { if (!viewState.isNetworkAvailable) "no_internet" else it::class },
                    label = "uiStateTransition",
                    modifier = Modifier.fillMaxSize()
                ) { state ->
                    if (!viewState.isNetworkAvailable) {
                        StatusContainer(
                            message = stringResource(R.string.error_no_internet),
                            isError = true,
                            modifier = Modifier.fillMaxSize(),
                            onRetry = { viewModel.refresh() },
                        )
                    } else {
                        when (state) {
                            is RadioUiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            is RadioUiState.Success -> {
                                if (state.stations.isEmpty()) {
                                    StatusContainer(
                                        message = stringResource(R.string.no_stations_message),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    val gridState = rememberLazyGridState()
                                    val density = LocalDensity.current

                                    LaunchedEffect(Unit) {
                                        viewModel.events.collect { event ->
                                            when (event) {
                                                is RadioUiEvent.ScrollToTop -> { }
                                            }
                                        }
                                    }

                                    var wasMiniPlayerVisible by remember { mutableStateOf(viewState.selectedUrl != null) }
                                    LaunchedEffect(viewState.selectedUrl) {
                                        val selectedUrl = viewState.selectedUrl
                                        if (selectedUrl != null) {
                                            val layoutInfo = gridState.layoutInfo
                                            val isLastItemVisible = layoutInfo.visibleItemsInfo.any { it.index == (layoutInfo.totalItemsCount - 1) }

                                            if (isLastItemVisible && !wasMiniPlayerVisible) {
                                                val scrollAmount = with(density) { 
                                                    (if (isLandscape) 84.dp else 100.dp).toPx() 
                                                }
                                                gridState.animateScrollBy(scrollAmount)
                                            }
                                        }
                                        wasMiniPlayerVisible = selectedUrl != null
                                    }

                                    val bottomNavPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                    
                                    val showShadow = viewState.selectedUrl != null
                                    
                                    val bottomPadding = remember(viewState.selectedUrl, isLandscape, bottomNavPadding) {
                                        if (viewState.selectedUrl != null) {
                                            (if (isLandscape) 100.dp else 116.dp) + bottomNavPadding
                                        } else {
                                            16.dp + bottomNavPadding
                                        }
                                    }

                                    LaunchedEffect(gridState.canScrollForward, gridState.canScrollBackward) {
                                        viewModel.setScrollable(gridState.canScrollForward || gridState.canScrollBackward)
                                    }

                                    Box(modifier = Modifier.fillMaxSize()) {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(minSize = 340.dp),
                                            state = gridState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(
                                                top = 8.dp,
                                                bottom = bottomPadding,
                                                start = 20.dp,
                                                end = 20.dp
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            item(
                                                key = "scroll_anchor",
                                                span = { GridItemSpan(maxLineSpan) }
                                            ) {
                                                Spacer(modifier = Modifier.height(0.5.dp))
                                            }

                                            items(
                                                items = state.stations,
                                                key = { "${it.streamUrl ?: it.name}|${it.network}" }
                                            ) { station ->
                                                StationCard(
                                                    station = station,
                                                    isActive = station.matchesUrl(viewState.selectedUrl),
                                                    isPlaying = station.matchesUrl(viewState.selectedUrl) && viewState.isPlaying && !viewState.isBuffering,
                                                    showHqIcon = !station.streamUrlHq.isNullOrBlank(),
                                                    modifier = Modifier.animateItem(
                                                        fadeInSpec = AnimationSystem.RelaxedSpring,
                                                        fadeOutSpec = AnimationSystem.RelaxedSpring,
                                                        placementSpec = if (viewState.isSearchActive) null else AnimationSystem.RelaxedSpringIntOffset
                                                    ),
                                                    onClick = {
                                                        focusManager.clearFocus()
                                                        val url = station.streamUrl ?: station.streamUrlHq
                                                        url?.let { viewModel.toggleStation(it) }
                                                    },
                                                    onLongClick = { viewModel.toggleFavorite(station) }
                                                )
                                            }
                                        }

                                        this@mainColumn.AnimatedVisibility(
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
                                                    .height(if (isLandscape) 100.dp else 140.dp)
                                                    .background(shadowBrush)
                                            )
                                        }
                                    }
                                }
                            }
                            is RadioUiState.Error -> {
                                if (state.isServerError) {
                                    ServerNapContainer(
                                        modifier = Modifier.fillMaxSize(),
                                        onRetry = { viewModel.refresh() }
                                    )
                                } else {
                                    StatusContainer(
                                        message = stringResource(R.string.error_no_internet),
                                        isError = true,
                                        modifier = Modifier.fillMaxSize(),
                                        onRetry = { viewModel.refresh() },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = viewState.isSearchActive,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { -it } + fadeOut()
                                )
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { it } + fadeOut()
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = "TopBarSearchTransition",
                    ) { isSearchActive ->
                        if (isSearchActive) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.setSearchActive(false) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(R.string.back),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                TextField(
                                    value = viewState.searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(searchFocusRequester)
                                        .onFocusChanged { isSearchFocused = it.isFocused },
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.search_stations_hint),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                    trailingIcon = {
                                        if (viewState.searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.setSearchQuery("") },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onBackground,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.app_name),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!viewState.isSearchActive) {
                            IconButton(
                                onClick = { viewModel.setSearchActive(true) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        if (!viewState.isSearchActive) {
                            IconButton(
                                onClick = { onSettingsClick() },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = stringResource(R.string.settings_title),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = isLandscape && viewState.settings.showLocationHeader && !viewState.isSearchActive,
                    transitionSpec = {
                        fadeIn().togetherWith(fadeOut())
                    },
                    label = "LandscapeLocationHeader"
                ) { visible ->
                    if (visible) {
                        CompactLocationHeader(viewState.locationInfo)
                    }
                }
            }
        }
    }
}
