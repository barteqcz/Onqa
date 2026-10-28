/**
 * Main settings screen covering appearance, location source, language, backup, and update options.
 */
package com.barteqcz.onqa.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.barteqcz.onqa.data.model.FavoritesImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.barteqcz.onqa.data.model.FavoritesBackup
import kotlinx.serialization.json.Json
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.model.LocationSource
import com.barteqcz.onqa.ui.main.RadioViewModel
import com.barteqcz.onqa.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: RadioViewModel,
    onBack: () -> Unit,
    onNavigateToMapPicker: () -> Unit,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val settings = viewState.settings
    val selectedUrl = viewState.selectedUrl

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImportResult by remember { mutableStateOf<FavoritesImportResult.Success?>(null) }

    val exportedToastTpl = stringResource(R.string.favorites_exported_toast)
    val importedToastTpl = stringResource(R.string.favorites_imported_toast)
    val errorToastText = stringResource(R.string.favorites_import_error)
    val invalidFormatToastText = stringResource(R.string.favorites_import_invalid_format)
    val emptyFileToastText = stringResource(R.string.favorites_import_empty_file)

    val focusManager = LocalFocusManager.current

    val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    val scrollState = rememberScrollState()

    val showShadow = selectedUrl != null

    var showLocationValidationError by remember { mutableStateOf(false) }
    val isManualWithoutLocation = (settings.locationSource == LocationSource.MANUAL) && (settings.manualLatitude == null)

    BackHandler(enabled = isManualWithoutLocation) {
        showLocationValidationError = true
    }

    LaunchedEffect(scrollState.canScrollForward, scrollState.canScrollBackward) {
        viewModel.setScrollable(scrollState.canScrollForward || scrollState.canScrollBackward)
    }

    val accentColors = listOf(
        OnqaGreen,
        OnqaPurple,
        OnqaCyan,
        OnqaOrange,
        OnqaBlue,
    )

    val displayAccentColors = accentColors.map { color ->
        val target = if (isLightMode) color.applyLightVariant() else color
        animateColorAsState(target, AnimationSystem.vividTween(500), label = "paletteColor").value
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    focusManager.clearFocus()
                }
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(80.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                SettingCategory(title = stringResource(R.string.category_regional))

                LanguageSettings(
                    currentLanguage = settings.language,
                    onLanguageSelect = { viewModel.updateLanguage(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                DistanceUnitSettings(
                    currentUnit = settings.distanceUnit,
                    onUnitSelect = { viewModel.updateDistanceUnit(it) }
                )

                Spacer(modifier = Modifier.height(48.dp))

                SettingCategory(title = stringResource(R.string.category_location))
                
                Text(stringResource(R.string.location_source_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.location_source_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(16.dp))

                LocationSourceSwitcher(
                    currentSource = settings.locationSource,
                    onSourceSelect = { viewModel.updateLocationSource(it) }
                )

                AnimatedVisibility(
                    visible = settings.locationSource == LocationSource.MANUAL,
                    enter = expandVertically(AnimationSystem.VividSpringIntSize) + fadeIn(AnimationSystem.vividTween()),
                    exit = shrinkVertically(AnimationSystem.VividSpringIntSize) + fadeOut(AnimationSystem.vividTween())
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Surface(
                            onClick = onNavigateToMapPicker,
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Map,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    val buttonText = if (settings.manualLatitude != null) {
                                        stringResource(R.string.pick_location_button_picked)
                                    } else {
                                        stringResource(R.string.pick_location_button)
                                    }
                                    Text(
                                        text = buttonText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    settings.manualCity?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.graphicsLayer { rotationZ = 180f }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                SettingCategory(title = stringResource(R.string.category_ui_elements))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.show_location_header_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.show_location_header_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = settings.showLocationHeader,
                        onCheckedChange = { viewModel.updateShowLocationHeader(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                SettingCategory(title = stringResource(R.string.category_audio))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.hq_stream_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.hq_stream_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = settings.useHqStream,
                        onCheckedChange = { viewModel.updateUseHqStream(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                SettingCategory(title = "DATABASE")

                Text(stringResource(R.string.catalog_update_freq_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.catalog_update_freq_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(16.dp))

                CatalogFrequencySwitcherCard(
                    currentFrequency = settings.catalogUpdateFrequency,
                    onFrequencySelect = { viewModel.updateCatalogFrequency(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                CatalogManualUpdateCard(
                    lastUpdateTimestamp = settings.lastCatalogUpdate,
                    onUpdateClick = { onDone -> viewModel.updateCatalogDatabaseManually(onDone) }
                )

                Spacer(modifier = Modifier.height(48.dp))

                SettingCategory(title = stringResource(R.string.category_data))

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    uri?.let {
                        try {
                            val favorites = viewModel.getFavoriteStations()
                            val backup = FavoritesBackup(favoriteStations = favorites.toList())
                            val jsonString = Json.encodeToString(FavoritesBackup.serializer(), backup)
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(jsonString.toByteArray())
                            }
                            Toast.makeText(context, exportedToastTpl.format(favorites.size), Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(context, errorToastText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        scope.launch {
                            try {
                                val text = withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                }
                                if (text.isNullOrBlank()) {
                                    Toast.makeText(context, emptyFileToastText, Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                when (val result = viewModel.validateFavoritesImport(text)) {
                                    is FavoritesImportResult.Success -> {
                                        pendingImportResult = result
                                    }
                                    is FavoritesImportResult.Error.EmptyFile -> {
                                        Toast.makeText(context, emptyFileToastText, Toast.LENGTH_SHORT).show()
                                    }
                                    is FavoritesImportResult.Error.InvalidFormat -> {
                                        Toast.makeText(context, invalidFormatToastText, Toast.LENGTH_SHORT).show()
                                    }
                                    is FavoritesImportResult.Error.FileReadError -> {
                                        Toast.makeText(context, errorToastText, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, errorToastText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                Surface(
                    onClick = { exportLauncher.launch("onqa_favorites.json") },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.export_favorites_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.export_favorites_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.import_favorites_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.import_favorites_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
                
                SettingCategory(title = stringResource(R.string.category_appearance))

                Text(stringResource(R.string.theme_mode_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.theme_mode_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(16.dp))

                ThemeSwitcher(
                    currentMode = settings.themeMode,
                    onModeSelect = { viewModel.updateThemeMode(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.amoled_mode_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.amoled_mode_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = settings.isAmoledModeEnabled,
                        onCheckedChange = { viewModel.updateAmoledMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.material_you_title), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.material_you_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = settings.isMaterialYouEnabled,
                            onCheckedChange = { viewModel.updateMaterialYou(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = !settings.isMaterialYouEnabled,
                        enter = expandVertically(AnimationSystem.VividSpringIntSize, expandFrom = Alignment.Top) + fadeIn(animationSpec = AnimationSystem.vividTween(300)),
                        exit = shrinkVertically(AnimationSystem.VividSpringIntSize, shrinkTowards = Alignment.Top) + fadeOut(animationSpec = AnimationSystem.vividTween(300))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                displayAccentColors.forEachIndexed { index, color ->
                                    val baseColor = accentColors[index]
                                    val isSelected = (!settings.isMaterialYouEnabled) && 
                                                     (settings.accentColor.toArgb() == baseColor.toArgb())
                                    
                                    val scale by animateFloatAsState(if (isSelected) 1.15f else 1f, label = "scale")
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .graphicsLayer { 
                                                scaleX = scale
                                                scaleY = scale 
                                            }
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable { viewModel.updateAccentColor(baseColor) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null, 
                                                tint = if (isLightMode) Color.White else Color.Black.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(if (selectedUrl != null) 140.dp else 24.dp))
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
                    .height(160.dp)
                    .background(shadowBrush)
            )
        }

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
                            onClick = {
                                if (isManualWithoutLocation) {
                                    showLocationValidationError = true
                                } else {
                                    onBack()
                                }
                            },
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
                        stringResource(R.string.settings_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    if (showLocationValidationError) {
        AlertDialog(
            onDismissRequest = { showLocationValidationError = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            confirmButton = {
                Button(
                    onClick = { showLocationValidationError = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.alright))
                }
            },
            title = {
                Text(
                    stringResource(R.string.location_validation_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall
                    )
            },
            text = {
                Text(
                    stringResource(R.string.location_validation_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(28.dp)
                )
        )
    }

    pendingImportResult?.let { result ->
        FavoritesImportDialog(
            importResult = result,
            onDismiss = { pendingImportResult = null },
            onConfirm = { stations, replace ->
                viewModel.importFavorites(stations, replace = replace)
                Toast.makeText(context, importedToastTpl.format(stations.size), Toast.LENGTH_SHORT).show()
                pendingImportResult = null
            }
        )
    }
}

@Composable
private fun FavoritesImportDialog(
    importResult: FavoritesImportResult.Success,
    onDismiss: () -> Unit,
    onConfirm: (stations: Set<String>, replace: Boolean) -> Unit,
) {
    var replaceMode by remember { mutableStateOf(false) }
    var includeUnknown by remember { mutableStateOf(true) }

    val hasUnknown = importResult.unknownStations.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val stationsToImport = if (hasUnknown && !includeUnknown) {
                        importResult.validStations
                    } else {
                        importResult.rawStations
                    }
                    onConfirm(stationsToImport, replaceMode)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.favorites_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.favorites_import_dialog_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.favorites_import_dialog_total, importResult.totalCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = OnqaGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.favorites_import_dialog_valid, importResult.validStations.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (hasUnknown) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.favorites_import_dialog_unknown, importResult.unknownStations.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (hasUnknown) {
                    Text(
                        text = stringResource(R.string.favorites_import_dialog_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeUnknown = !includeUnknown },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = includeUnknown,
                            onCheckedChange = { includeUnknown = it }
                        )
                        Text(
                            text = stringResource(R.string.favorites_import_include_unknown),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replaceMode = false },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = !replaceMode,
                            onClick = { replaceMode = false }
                        )
                        Text(
                            text = stringResource(R.string.favorites_import_mode_merge),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replaceMode = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = replaceMode,
                            onClick = { replaceMode = true }
                        )
                        Text(
                            text = stringResource(R.string.favorites_import_mode_replace),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(28.dp)
            )
    )
}
