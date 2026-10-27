package com.symphonylite.app

import android.content.Context

data class ServerConfig(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
)

data class AppSettings(
    val videoWidth: Int = 480,
    val videoHeight: Int = 360,
    val audio: Boolean = true,
    /** Eski uygulama gibi self-signed sertifikaları kabul eder. */
    val insecureSsl: Boolean = true,
    val timeoutSec: Int = 15,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("symphony_lite", Context.MODE_PRIVATE)

    fun loadServer(): ServerConfig? {
        val host = sp.getString("host", "") ?: ""
        if (host.isBlank()) return null
        return ServerConfig(
            host = host,
            port = sp.getInt("port", 8433),
            user = sp.getString("user", "") ?: "",
            password = sp.getString("pass", "") ?: "",
        )
    }

    fun saveServer(c: ServerConfig) {
        sp.edit()
            .putString("host", c.host)
            .putInt("port", c.port)
            .putString("user", c.user)
            .putString("pass", c.password)
            .apply()
    }

    fun loadSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            videoWidth = sp.getInt("vw", d.videoWidth),
            videoHeight = sp.getInt("vh", d.videoHeight),
            audio = sp.getBoolean("audio", d.audio),
            insecureSsl = sp.getBoolean("insecure", d.insecureSsl),
            timeoutSec = sp.getInt("timeout", d.timeoutSec),
        )
    }

    fun saveSettings(s: AppSettings) {
        sp.edit()
            .putInt("vw", s.videoWidth)
            .putInt("vh", s.videoHeight)
            .putBoolean("audio", s.audio)
            .putBoolean("insecure", s.insecureSsl)
            .putInt("timeout", s.timeoutSec)
            .apply()
    }
}
