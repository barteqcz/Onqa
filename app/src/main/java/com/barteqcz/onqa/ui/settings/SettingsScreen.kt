package com.barteqcz.onqa.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.barteqcz.onqa.R
import com.barteqcz.onqa.data.model.ThemeMode
import com.barteqcz.onqa.ui.main.RadioViewModel
import com.barteqcz.onqa.ui.theme.OnqaGreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: RadioViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val settings = viewState.settings
    val selectedUrl = viewState.selectedUrl

    var customHex by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val showShadow by remember {
        derivedStateOf {
            (selectedUrl != null) && scrollState.canScrollForward
        }
    }

    LaunchedEffect(scrollState.canScrollForward, scrollState.canScrollBackward) {
        viewModel.setScrollable(scrollState.canScrollForward || scrollState.canScrollBackward)
    }

    val accentColors = listOf(
        OnqaGreen,
        Color(0xFFD0BCFF),
        Color(0xFF03DAC5),
        Color(0xFFFFB74D),
        Color(0xFF64B5F6),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding(),
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        top = padding.calculateTopPadding() + 16.dp,
                        bottom = if (selectedUrl != null) 120.dp + padding.calculateBottomPadding() else 16.dp + padding.calculateBottomPadding(),
                        start = 24.dp,
                        end = 24.dp
                    )
                    .animateContentSize(),
            ) {
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
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
                
                SettingCategory(title = stringResource(R.string.category_appearance))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                            onClick = { viewModel.updateThemeMode(mode) },
                            selected = settings.themeMode == mode,
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                                        ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                                        ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
                                    }
                                )
                                    },
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    activeContentColor = MaterialTheme.colorScheme.primary,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurface
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

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
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }

                AnimatedVisibility(
                    visible = !settings.isMaterialYouEnabled,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(animationSpec = tween(300))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            accentColors.forEach { color ->
                                val isSelected = (!settings.isMaterialYouEnabled) && 
                                                 (settings.accentColor.toArgb() == color.toArgb())
                                
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
                                        .clickable { viewModel.updateAccentColor(color) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check, 
                                            contentDescription = null, 
                                            tint = Color.Black.copy(alpha = 0.7f), 
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = customHex,
                            onValueChange = { 
                                customHex = it
                                if (it.length == 6) {
                                    try {
                                        val color = Color("#$it".toColorInt())
                                        viewModel.updateAccentColor(color)
                                    } catch (_: Exception) {}
                                }
                            },
                            label = { Text(stringResource(R.string.custom_hex_label), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            prefix = { Text(stringResource(R.string.hex_prefix), color = MaterialTheme.colorScheme.onSurface) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
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
