package com.barteqcz.onqa.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.model.AppLanguage
import com.barteqcz.onqa.data.model.LocationSource
import com.barteqcz.onqa.data.model.ThemeMode
import com.barteqcz.onqa.ui.main.LanguageDownloadState
import com.barteqcz.onqa.ui.main.RadioViewModel
import com.barteqcz.onqa.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: RadioViewModel,
    onBack: () -> Unit,
    onNavigateToMapPicker: () -> Unit,
) {
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val settings = viewState.settings
    val selectedUrl = viewState.selectedUrl

    val focusManager = LocalFocusManager.current

    val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    val scrollState = rememberScrollState()

    val showShadow = selectedUrl != null

    var showLocationValidationError by remember { mutableStateOf(false) }
    val isManualWithoutLocation = settings.locationSource == LocationSource.MANUAL && settings.manualLatitude == null

    BackHandler(enabled = isManualWithoutLocation) {
        showLocationValidationError = true
    }

    val languageDownloadState = viewState.languageDownloadState

    LanguageDownloadProgressDialog(downloadState = languageDownloadState)
    LanguageDownloadErrorDialog(
        error = languageDownloadState.error,
        onDismiss = { viewModel.dismissLanguageDownloadError() }
    )

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
            // Stable Offset for Top Bar
            Spacer(modifier = Modifier.statusBarsPadding())
            Spacer(modifier = Modifier.height(80.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                SettingCategory(title = stringResource(R.string.category_general))

                LanguageSettings(
                    currentLanguage = settings.language,
                    onLanguageSelect = { viewModel.updateLanguage(it) }
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
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
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
            
            // Bottom Padding for MiniPlayer
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
                        .padding(horizontal = 12.dp)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (isManualWithoutLocation) {
                                showLocationValidationError = true
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

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
                .fillMaxWidth(0.92f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(28.dp)
                )
        )
    }
}

@Composable
private fun LanguageSettings(
    currentLanguage: AppLanguage,
    onLanguageSelect: (AppLanguage) -> Unit
) {
    val isSystem = currentLanguage == AppLanguage.SYSTEM
    val focusManager = LocalFocusManager.current

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.language_system),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.language_system_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = isSystem,
                onCheckedChange = {
                    focusManager.clearFocus()
                    if (it) onLanguageSelect(AppLanguage.SYSTEM)
                    else onLanguageSelect(AppLanguage.ENGLISH)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            )
        }

        AnimatedVisibility(
            visible = !isSystem,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(24.dp))
                LanguageDropdown(
                    currentLanguage = currentLanguage,
                    onLanguageSelect = {
                        focusManager.clearFocus()
                        onLanguageSelect(it)
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageDropdown(
    currentLanguage: AppLanguage,
    onLanguageSelect: (AppLanguage) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    
    val label = stringResource(currentLanguage.labelRes)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Surface(
            onClick = { 
                focusManager.clearFocus()
                showDialog = true 
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
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
                    Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = { showDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.language_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(AppLanguage.entries.filter { it != AppLanguage.SYSTEM }) { language ->
                            val isSelected = currentLanguage == language
                            Surface(
                                onClick = {
                                    showDialog = false
                                    onLanguageSelect(language)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = language.code.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = stringResource(language.labelRes),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextButton(
                        onClick = { showDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.back))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationSourceSwitcher(
    currentSource: LocationSource,
    onSourceSelect: (LocationSource) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LocationSource.entries.forEach { source ->
            LocationSourceOption(
                source = source,
                isSelected = currentSource == source,
                onClick = { onSourceSelect(source) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LocationSourceOption(
    source: LocationSource,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (source) {
        LocationSource.GPS -> Icons.Rounded.LocationOn
        LocationSource.MANUAL -> Icons.Rounded.Map
    }
    val label = when (source) {
        LocationSource.GPS -> stringResource(R.string.location_source_gps)
        LocationSource.MANUAL -> stringResource(R.string.location_source_manual)
    }

    SelectableOption(
        icon = icon,
        label = label,
        isSelected = isSelected,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun ThemeSwitcher(
    currentMode: ThemeMode,
    onModeSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            ThemeOption(
                mode = mode,
                isSelected = currentMode == mode,
                onClick = { onModeSelect(mode) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (mode) {
        ThemeMode.SYSTEM -> Icons.Rounded.Contrast
        ThemeMode.LIGHT -> Icons.Rounded.LightMode
        ThemeMode.DARK -> Icons.Rounded.DarkMode
    }
    val label = when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
        ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
    }

    SelectableOption(
        icon = icon,
        label = label,
        isSelected = isSelected,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun SelectableOption(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val activeColor = MaterialTheme.colorScheme.primary
    
    val containerColor = if (isSelected) {
        activeColor.copy(alpha = if (isLightMode) 0.12f else 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    
    val contentColor = if (isSelected) {
        activeColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) activeColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SettingCategory(title: String) {
    Text(
        text = title.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
private fun LanguageDownloadProgressDialog(
    downloadState: LanguageDownloadState
) {
    if (downloadState.isDownloading) {
        Dialog(
            onDismissRequest = { /* Prevent dismiss during active download */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )

                    Text(
                        text = stringResource(R.string.language_downloading_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    val targetLang = downloadState.targetLanguage
                    val langName = if (targetLang != null) stringResource(targetLang.labelRes) else ""
                    Text(
                        text = stringResource(R.string.language_downloading_desc, langName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (downloadState.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageDownloadErrorDialog(
    error: String?,
    onDismiss: () -> Unit
) {
    if (error != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.language_download_failed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.alright))
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
