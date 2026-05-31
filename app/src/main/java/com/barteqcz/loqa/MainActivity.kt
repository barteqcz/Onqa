package com.barteqcz.loqa

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.barteqcz.loqa.ui.navigation.RadioRoute
import com.barteqcz.loqa.ui.navigation.SettingsRoute
import com.barteqcz.loqa.ui.components.MiniPlayer
import com.barteqcz.loqa.ui.onboarding.BackgroundLocationDisclosure
import com.barteqcz.loqa.ui.onboarding.OnboardingScreen
import com.barteqcz.loqa.ui.main.RadioScreen
import com.barteqcz.loqa.ui.main.RadioUiState
import com.barteqcz.loqa.ui.main.RadioViewModel
import com.barteqcz.loqa.ui.settings.SettingsScreen
import com.barteqcz.loqa.ui.theme.LoqaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: RadioViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val locationGranted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)

        if (locationGranted && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
            showBackgroundLocationDisclosure = true
        } else {
            checkPermissionsAndCompleteOnboarding()
        }
    }

    private var showBackgroundLocationDisclosure by mutableStateOf(value = false)

    private val requestBackgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        checkPermissionsAndCompleteOnboarding()
    }

    private fun checkPermissionsAndCompleteOnboarding() {
        if (hasAllPermissions()) {
            viewModel.completeOnboarding()
        }
    }

    private fun hasAllPermissions(): Boolean {
        val location = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) ||
                (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        return location && background
    }

    private fun launchPermissionRequest() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onResume() {
        super.onResume()
        if (!hasAllPermissions()) {
            viewModel.resetOnboarding()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewState by viewModel.viewState.collectAsStateWithLifecycle()

            LoqaTheme(
                themeMode = viewState.settings.themeMode,
                dynamicColor = viewState.settings.isMaterialYouEnabled,
                accentColor = viewState.settings.accentColor,
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (showBackgroundLocationDisclosure) {
                        BackgroundLocationDisclosure(
                            onConfirm = {
                                showBackgroundLocationDisclosure = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    requestBackgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                }
                            },
                            onDismiss = {
                                showBackgroundLocationDisclosure = false
                                checkPermissionsAndCompleteOnboarding()
                            }
                        )
                    }

                    if (viewState.settings.isInitialValue) {
                        Box(modifier = Modifier.fillMaxSize())
                    } else if (!viewState.settings.isOnboardingCompleted) {
                        OnboardingScreen(onGrantClick = { launchPermissionRequest() })
                    } else {
                        val navController = rememberNavController()
                        Box(modifier = Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = RadioRoute,
                            ) {
                                composable<RadioRoute> {
                                    RadioScreen(
                                        viewModel = viewModel,
                                        onSettingsClick = { navController.navigate(SettingsRoute) }
                                    )
                                }
                                composable<SettingsRoute> {
                                    SettingsScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = viewState.selectedUrl != null,
                                enter = slideInVertically { it } + fadeIn(),
                                exit = slideOutVertically { it } + fadeOut(),
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) {
                                val stations = (viewState.uiState as? RadioUiState.Success)?.stations ?: emptyList()
                                val selectedStation = viewState.selectedUrl?.let { url ->
                                    stations.find { it.streamUrl == url || it.streamUrlHq == url }
                                }
                                val displayStation = selectedStation ?: viewState.currentStation

                                displayStation?.let {
                                    MiniPlayer(
                                        station = it,
                                        stations = stations,
                                        isPlaying = viewState.isPlaying,
                                        isBuffering = viewState.isBuffering,
                                        metadata = viewState.metadata,
                                        showHqIcon = viewState.settings.useHqStream && !it.streamUrlHq.isNullOrBlank(),
                                        isScrollable = viewState.isScrollable,
                                        onToggle = { viewModel.toggleStation(viewState.selectedUrl!!) },
                                        onNext = { viewModel.nextStation() },
                                        onPrevious = { viewModel.previousStation() }
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
