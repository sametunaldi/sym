package com.symphonylite.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App(vm: AppViewModel = viewModel()) {
    val screen = vm.screen
    BackHandler(enabled = screen is Screen.Player || screen is Screen.Settings) { vm.back() }

    when (screen) {
        Screen.Login -> LoginScreen(vm)
        Screen.Cameras -> CamerasScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        is Screen.Player -> PlayerScreen(vm, screen.cameraId)
    }
}
