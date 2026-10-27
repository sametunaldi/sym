@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.symphonylite.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val timeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

@Composable
fun PlayerScreen(vm: AppViewModel, cameraId: String) {
    val api = vm.api
    val info = vm.info
    val camera = vm.cameras.firstOrNull { it.id == cameraId }
    if (api == null || info == null || camera == null) {
        LaunchedEffect(Unit) { vm.screen = Screen.Cameras }
        return
    }

    val context = LocalContext.current
    val zone: ZoneOffset = info.zone
    val s = vm.settings

    // null = canlı yayın, dolu = o andan başlayan geçmiş kayıt (sunucu saat diliminde)
    var startTime by remember { mutableStateOf<ZonedDateTime?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var buffering by remember { mutableStateOf(true) }

    val player = remember {
        val dataSource = OkHttpDataSource.Factory(api.client)
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
        )
        // Canlı izleme için küçük tampon.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 5000, 500, 1000)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, extractors))
            .build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) errorText = null
            }

            override fun onPlayerError(error: PlaybackException) {
                errorText = "Oynatma hatası: ${error.errorCodeName}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Ekran açık kalsın.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Arka plana geçince akışı durdur, dönünce yeniden başlat.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.stop()
                Lifecycle.Event.ON_START -> reloadKey++
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Akışı (yeniden) yükle.
    LaunchedEffect(camera.id, startTime, reloadKey, s.videoWidth, s.videoHeight, s.audio) {
        errorText = null
        val t = startTime
        val url = if (t == null) {
            api.liveUrl(camera, s.videoWidth, s.videoHeight, s.audio)
        } else {
            api.histUrl(camera, t, s.videoWidth, s.videoHeight, s.audio)
        }
        if (url == null) {
            errorText = "Bu kamera için geçmiş kayıt linki yok."
            return@LaunchedEffect
        }
        player.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP2T).build())
        player.prepare()
        player.playWhenReady = true
    }

    fun now() = ZonedDateTime.now(zone)

    /** Mevcut oynatma anına göre saniye kadar ileri/geri atla. */
    fun shift(seconds: Long) {
        val current = startTime?.plus(player.currentPosition, ChronoUnit.MILLIS) ?: now()
        val target = current.plusSeconds(seconds)
        startTime = if (target.isAfter(now().minusSeconds(5))) null else target
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(camera.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { vm.screen = Screen.Cameras }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val exo = player
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = exo
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (buffering && errorText == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp))
                }
            }

            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = startTime?.format(timeFormat)?.let { "Geçmiş kayıt: $it" } ?: "Canlı",
                    style = MaterialTheme.typography.titleSmall,
                )
                errorText?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { reloadKey++ }) { Text("Yeniden dene") }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(onClick = { shift(-60) }, modifier = Modifier.weight(1f)) { Text("-1 dk") }
                    OutlinedButton(
                        onClick = {
                            pickDateTime(context, zone, startTime ?: now()) { picked ->
                                startTime = if (picked.isAfter(now())) null else picked
                            }
                        },
                        modifier = Modifier.weight(2f),
                    ) { Text("Zaman seç") }
                    OutlinedButton(onClick = { shift(60) }, modifier = Modifier.weight(1f)) { Text("+1 dk") }
                }
                Button(
                    onClick = { startTime = null; reloadKey++ },
                    enabled = startTime != null || errorText != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Canlıya dön") }
            }
        }
    }
}

private fun pickDateTime(
    context: Context,
    zone: ZoneOffset,
    initial: ZonedDateTime,
    onPicked: (ZonedDateTime) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, y, m, d ->
            TimePickerDialog(
                context,
                { _, hh, mm -> onPicked(ZonedDateTime.of(y, m + 1, d, hh, mm, 0, 0, zone)) },
                initial.hour, initial.minute, true,
            ).show()
        },
        initial.year, initial.monthValue - 1, initial.dayOfMonth,
    ).show()
}
