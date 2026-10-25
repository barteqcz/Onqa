package com.barteqcz.onqa.ui.settings

import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barteqcz.onqa.R
import com.barteqcz.onqa.ui.main.RadioViewModel
import com.barteqcz.onqa.ui.theme.AnimationSystem
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun MapPickerScreen(
    radioViewModel: RadioViewModel,
    mapViewModel: MapViewModel,
    onBack: () -> Unit,
) {
    val state by mapViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isFlingEnabled = true
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            
            val scale = (density / 1.5f).coerceAtLeast(1.0f)
            setTilesScaleFactor(scale)
            
            controller.setZoom(12.0)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { mapView.onResume() }
            override fun onPause(owner: LifecycleOwner) { mapView.onPause() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val settings by radioViewModel.settings.collectAsStateWithLifecycle()
    val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val pinColor = if (isLightMode) Color.Black.toArgb() else Color(0xFF1A1A1A).toArgb()

    val initialLocation = remember {
        val lat = settings.manualLatitude ?: settings.lastLatitude ?: 52.0
        val lon = settings.manualLongitude ?: settings.lastLongitude ?: 20.0
        GeoPoint(lat, lon)
    }

    LaunchedEffect(initialLocation) {
        mapView.controller.setCenter(initialLocation)
    }

    LaunchedEffect(state.mapCenterTrigger) {
        if (state.mapCenterTrigger > 0L) {
            state.selectedLocation?.let {
                mapView.controller.animateTo(it)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            factory = { ctx ->
                val container = FrameLayout(ctx)
                mapView.apply {
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.performClick()
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                    
                    val marker = Marker(this)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.infoWindow = null
                    
                    val pinDrawable = ContextCompat.getDrawable(context, R.drawable.ic_location_pin)?.apply {
                        setTint(pinColor)
                    }
                    marker.icon = pinDrawable
                    
                    val eventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            mapViewModel.onLocationSelected(p)
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint): Boolean { return false }
                    }
                    
                    overlays.add(MapEventsOverlay(eventsReceiver))
                    overlays.add(marker)
                    
                    if (this.parent != null) {
                        (this.parent as ViewGroup).removeView(this)
                    }
                    container.addView(this)
                }
                container
            },
            update = { _ ->
                val marker = mapView.overlays.filterIsInstance<Marker>().firstOrNull()
                marker?.let { m ->
                    val loc = state.selectedLocation
                    m.isEnabled = loc != null
                    if (loc != null && m.position != loc) {
                        m.position = loc
                        mapView.invalidate()
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            mapView.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
        )

        // Search Bar Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(72.dp))
            
            AnimatedVisibility(
                visible = true,
                enter = expandVertically(AnimationSystem.VividSpringIntSize) + fadeIn(AnimationSystem.vividTween()),
                exit = shrinkVertically(AnimationSystem.VividSpringIntSize) + fadeOut(AnimationSystem.vividTween())
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 4.dp
                ) {
                    Column {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { mapViewModel.onSearchQueryChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.search_location_placeholder)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { mapViewModel.onSearchQueryChanged("") }) {
                                            Icon(Icons.Rounded.Close, contentDescription = null)
                                        }
                                    }
                                    if (state.isGeocoding) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    mapViewModel.searchLocation()
                                }
                            )
                        )

                        AnimatedVisibility(
                            visible = state.searchResults.isNotEmpty(),
                            enter = expandVertically(AnimationSystem.VividSpringIntSize) + fadeIn(),
                            exit = shrinkVertically(AnimationSystem.VividSpringIntSize) + fadeOut()
                        ) {
                            Column {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                ) {
                                    items(state.searchResults) { address ->
                                        val fullAddress = remember(address) {
                                            val parts = mutableListOf<String>()
                                            address.locality?.let { parts.add(it) }
                                            address.adminArea?.let { parts.add(it) }
                                            address.countryName?.let { parts.add(it) }
                                            if (parts.isEmpty()) {
                                                address.getAddressLine(0) ?: ""
                                            } else {
                                                parts.joinToString(", ")
                                            }
                                        }
                                        
                                        ListItem(
                                            headlineContent = { Text(fullAddress) },
                                            modifier = Modifier.clickable {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                mapViewModel.onSearchResultSelected(address)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (state.isGeocoding && state.selectedLocation == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            AnimatedVisibility(
                visible = state.city != null,
                enter = slideInVertically(AnimationSystem.VividSpringIntOffset) { it } + fadeIn(),
                exit = slideOutVertically(AnimationSystem.VividSpringIntOffset) { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                state.city?.let { city ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        )
                    ) {
                        Text(
                            text = city,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            AnimatedVisibility(
                visible = state.error != null,
                enter = slideInVertically(AnimationSystem.VividSpringIntOffset) { it } + fadeIn(),
                exit = slideOutVertically(AnimationSystem.VividSpringIntOffset) { it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                state.error?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Top Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .align(Alignment.TopCenter)
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
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        stringResource(R.string.map_picker_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    AnimatedVisibility(
                        visible = state.selectedLocation != null && !state.isGeocoding,
                        enter = fadeIn() + slideInHorizontally { it / 2 },
                        exit = fadeOut() + slideOutHorizontally { it / 2 }
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                mapViewModel.confirmLocation(radioViewModel, onBack)
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        }
    }
}
