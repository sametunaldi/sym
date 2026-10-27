@file:OptIn(ExperimentalMaterial3Api::class)

package com.symphonylite.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest

// ---------------------------------------------------------------- Giriş

@Composable
fun LoginScreen(vm: AppViewModel) {
    val saved = remember { vm.prefs.loadServer() }
    var host by remember { mutableStateOf(saved?.host ?: "") }
    var port by remember { mutableStateOf((saved?.port ?: 8433).toString()) }
    var user by remember { mutableStateOf(saved?.user ?: "") }
    var pass by remember { mutableStateOf(saved?.password ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Symphony Lite") },
                actions = {
                    IconButton(onClick = { vm.openSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                },
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sunucuya bağlan", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = host, onValueChange = { host = it.trim() },
                label = { Text("Sunucu adresi") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("Port") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("Kullanıcı adı") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it },
                label = { Text("Parola") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    vm.connect(ServerConfig(host, port.toIntOrNull() ?: 8433, user, pass))
                },
                enabled = host.isNotBlank() && !vm.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Bağlan")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Kameralar

@Composable
fun CamerasScreen(vm: AppViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kameralar") },
                actions = {
                    IconButton(onClick = { vm.refreshCameras() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                    }
                    IconButton(onClick = { vm.openSettings() }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
                    }
                },
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (vm.cameras.isEmpty() && !vm.loading) {
                Text("Kamera bulunamadı.", Modifier.align(Alignment.Center))
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(vm.cameras, key = { it.id }) { cam -> CameraCard(vm, cam) }
            }
            if (vm.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun CameraCard(vm: AppViewModel, cam: Camera) {
    val ctx = LocalContext.current
    Card(onClick = { vm.screen = Screen.Player(cam.id) }) {
        Column {
            val url = vm.thumbUrl(cam)
            if (url != null) {
                val request = ImageRequest.Builder(ctx)
                    .data(url)
                    .memoryCacheKey("${cam.id}#${vm.refreshTick}")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .crossfade(true)
                    .build()
                AsyncImage(
                    model = request,
                    imageLoader = vm.imageLoader ?: ctx.imageLoader,
                    contentDescription = cam.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                )
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            }
            Text(
                cam.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Ayarlar

private val qualityPresets = listOf(
    "320x240" to (320 to 240),
    "480x360 (varsayılan)" to (480 to 360),
    "640x480" to (640 to 480),
    "1280x720" to (1280 to 720),
)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Video kalitesi", style = MaterialTheme.typography.titleMedium)
            qualityPresets.forEach { (label, size) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = s.videoWidth == size.first && s.videoHeight == size.second,
                        onClick = {
                            vm.updateSettings(s.copy(videoWidth = size.first, videoHeight = size.second))
                        },
                    )
                    Text(label)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ses", Modifier.weight(1f))
                Switch(checked = s.audio, onCheckedChange = { vm.updateSettings(s.copy(audio = it)) })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Self-signed sertifikaları kabul et")
                    Text(
                        "Değişiklik bir sonraki bağlanışta geçerli olur.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = s.insecureSsl,
                    onCheckedChange = { vm.updateSettings(s.copy(insecureSsl = it)) },
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                Text("Sunucuyu değiştir")
            }
        }
    }
}
