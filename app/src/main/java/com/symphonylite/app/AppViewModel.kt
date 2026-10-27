package com.symphonylite.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Login : Screen
    data object Cameras : Screen
    data object Settings : Screen
    data class Player(val cameraId: String) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)

    var screen by mutableStateOf<Screen>(Screen.Login)
    var settings by mutableStateOf(prefs.loadSettings())
        private set

    var api: SymphonyApi? = null
        private set
    var info by mutableStateOf<ServerInfo?>(null)
        private set
    var cameras by mutableStateOf<List<Camera>>(emptyList())
        private set
    var imageLoader: ImageLoader? = null
        private set

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var refreshTick by mutableIntStateOf(0)
        private set

    init {
        // Kayıtlı sunucu varsa otomatik bağlan.
        prefs.loadServer()?.let { connect(it) }
    }

    fun connect(cfg: ServerConfig) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            val s = settings
            val newApi = SymphonyApi(cfg, s.insecureSsl, s.timeoutSec)
            val result = withContext(Dispatchers.IO) { newApi.connect() }
            when (result) {
                is ConnectResult.Success -> {
                    val list = withContext(Dispatchers.IO) { newApi.cameras(result.info) }
                    if (list == null) {
                        error = "Kamera listesi alınamadı."
                    } else {
                        api = newApi
                        info = result.info
                        cameras = list
                        imageLoader = ImageLoader.Builder(getApplication<Application>())
                            .okHttpClient(newApi.client)
                            .crossfade(true)
                            .build()
                        prefs.saveServer(cfg)
                        refreshTick++
                        screen = Screen.Cameras
                    }
                }
                ConnectResult.AuthFailed -> error = "Kullanıcı adı veya parola hatalı."
                ConnectResult.BadVersion -> error = "Sunucu sürümü desteklenmiyor (API 2 veya üstü gerekli)."
                ConnectResult.Unreachable -> error = "Sunucuya ulaşılamadı. Adres ve portu kontrol edin."
            }
            loading = false
        }
    }

    fun refreshCameras() {
        val a = api ?: return
        val i = info ?: return
        if (loading) return
        loading = true
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { a.cameras(i) }
            if (list != null) {
                cameras = list
                refreshTick++
            }
            loading = false
        }
    }

    fun updateSettings(s: AppSettings) {
        settings = s
        prefs.saveSettings(s)
    }

    fun thumbUrl(cam: Camera): String? = api?.thumbUrl(cam, 320, 240)

    fun back() {
        screen = when (screen) {
            is Screen.Player -> Screen.Cameras
            Screen.Settings -> if (info != null) Screen.Cameras else Screen.Login
            else -> screen
        }
    }

    fun openSettings() { screen = Screen.Settings }

    fun logout() {
        api = null
        info = null
        cameras = emptyList()
        error = null
        screen = Screen.Login
    }
}
