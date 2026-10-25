package com.barteqcz.onqa.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Straighten
import com.barteqcz.onqa.data.model.CatalogUpdateFrequency
import com.barteqcz.onqa.data.model.DistanceUnit
import androidx.compose.ui.text.style.TextOverflow
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.model.AppLanguage
import com.barteqcz.onqa.data.model.LocationSource
import com.barteqcz.onqa.data.model.ThemeMode

@Composable
internal fun LanguageSettings(
    currentLanguage: AppLanguage,
    onLanguageSelect: (AppLanguage) -> Unit,
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
                onCheckedChange = { checked ->
                    focusManager.clearFocus()
                    if (checked) {
                        onLanguageSelect(AppLanguage.SYSTEM)
                    } else {
                        val sysCode = Locale.getDefault().language
                        val matchedLang = AppLanguage.entries.find { lang ->
                            lang != AppLanguage.SYSTEM && lang.code.equals(sysCode, ignoreCase = true)
                        } ?: AppLanguage.ENGLISH
                        onLanguageSelect(matchedLang)
                    }
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
internal fun LanguageDropdown(
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
        val availableLanguages = remember {
            AppLanguage.entries.filter { it != AppLanguage.SYSTEM }
        }

        Dialog(
            onDismissRequest = { showDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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
                        items(availableLanguages, key = { it.code }) { language ->
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
internal fun DistanceUnitSettings(
    currentUnit: DistanceUnit,
    onUnitSelect: (DistanceUnit) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.distance_unit_title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.distance_unit_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        DistanceUnitSwitcher(
            currentUnit = currentUnit,
            onUnitSelect = onUnitSelect
        )
    }
}

@Composable
internal fun DistanceUnitSwitcher(
    currentUnit: DistanceUnit,
    onUnitSelect: (DistanceUnit) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DistanceUnit.entries.forEach { unit ->
            DistanceUnitOption(
                unit = unit,
                isSelected = currentUnit == unit,
                onClick = { onUnitSelect(unit) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun DistanceUnitOption(
    unit: DistanceUnit,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = Icons.Rounded.Straighten
    val label = when (unit) {
        DistanceUnit.KILOMETERS -> stringResource(R.string.distance_unit_kilometers)
        DistanceUnit.MILES -> stringResource(R.string.distance_unit_miles)
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
internal fun LocationSourceSwitcher(
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
internal fun LocationSourceOption(
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
internal fun ThemeSwitcher(
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
internal fun ThemeOption(
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
internal fun SelectableOption(
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
internal fun SettingCategory(title: String) {
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
internal fun CatalogFrequencySwitcherCard(
    currentFrequency: CatalogUpdateFrequency,
    onFrequencySelect: (CatalogUpdateFrequency) -> Unit
) {
    val options = listOf(
        CatalogUpdateFrequency.MANUAL,
        CatalogUpdateFrequency.DAILY,
        CatalogUpdateFrequency.WEEKLY,
        CatalogUpdateFrequency.MONTHLY
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { freq ->
                val isSelected = currentFrequency == freq
                val label = stringResource(freq.labelRes)

                val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f
                val activeColor = MaterialTheme.colorScheme.primary

                val containerColor = if (isSelected) activeColor else Color.Transparent
                val contentColor = if (isSelected) {
                    if (isLightMode) Color.White else Color.Black
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    onClick = { onFrequencySelect(freq) },
                    shape = RoundedCornerShape(12.dp),
                    color = containerColor,
                    contentColor = contentColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

internal enum class UpdateState {
    IDLE, UPDATING, SUCCESS, ERROR
}

@Composable
internal fun CatalogManualUpdateCard(
    lastUpdateTimestamp: Long,
    onUpdateClick: ((Boolean) -> Unit) -> Unit
) {
    var state by remember { mutableStateOf(UpdateState.IDLE) }

    LaunchedEffect(state) {
        if (state == UpdateState.SUCCESS) {
            delay(2500.milliseconds)
            state = UpdateState.IDLE
        }
    }

    val context = LocalContext.current
    val lastUpdateText = if (lastUpdateTimestamp > 0) {
        val dateFormat = remember { android.text.format.DateFormat.getDateFormat(context) }
        val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
        val dateStr = dateFormat.format(Date(lastUpdateTimestamp))
        val timeStr = timeFormat.format(Date(lastUpdateTimestamp))
        stringResource(R.string.catalog_last_updated, "$dateStr $timeStr")
    } else {
        stringResource(R.string.catalog_manual_update_desc)
    }

    val isError = state == UpdateState.ERROR
    val isUpdating = state == UpdateState.UPDATING

    val warningYellow = Color(0xFFFFC107)
    val cardColor = if (isError) warningYellow.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    val cardBorder = if (isError) BorderStroke(1.5.dp, warningYellow.copy(alpha = 0.5f)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

    Surface(
        onClick = {
            if (isUpdating) return@Surface
            state = UpdateState.UPDATING
            onUpdateClick { success ->
                state = if (success) UpdateState.SUCCESS else UpdateState.ERROR
            }
        },
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        border = cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "updateStateIcon"
            ) { currentState ->
                when (currentState) {
                    UpdateState.UPDATING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp
                        )
                    }
                    UpdateState.SUCCESS -> {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    UpdateState.ERROR -> {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = warningYellow,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    UpdateState.IDLE -> {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.catalog_manual_update_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = lastUpdateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
