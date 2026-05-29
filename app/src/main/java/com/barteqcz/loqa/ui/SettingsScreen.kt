package com.barteqcz.loqa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.barteqcz.loqa.R
import com.barteqcz.loqa.ui.theme.DarkBackground
import com.barteqcz.loqa.ui.theme.LoqaGreen
import com.barteqcz.loqa.ui.theme.TextGrey

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

    val accentColors = listOf(
        LoqaGreen,
        Color(0xFFD0BCFF),
        Color(0xFF03DAC5),
        Color(0xFFFFB74D),
        Color(0xFF64B5F6),
    )

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), color = Color.White, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
                modifier = Modifier
                    .background(DarkBackground)
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
                        Text(stringResource(R.string.hq_stream_title), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.hq_stream_desc), color = TextGrey, style = MaterialTheme.typography.bodySmall)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.material_you_title), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.material_you_desc), color = TextGrey, style = MaterialTheme.typography.bodySmall)
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
                            label = { Text(stringResource(R.string.custom_hex_label), color = TextGrey) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            prefix = { Text(stringResource(R.string.hex_prefix), color = Color.White) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.DarkGray
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
