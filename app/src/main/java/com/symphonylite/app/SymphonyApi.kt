package com.symphonylite.app

import android.os.Build
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Sunucu kök yolundan ("/") dönen JSON. Tüm diğer yollar buradan öğrenilir. */
data class ServerInfo(
    val apiVersion: Int,
    val serverAgent: String,
    val camerasLink: String,
    /** Sunucunun UTC'den farkı, dakika cinsinden. */
    val utcOffsetMinutes: Int,
) {
    val zone: ZoneOffset get() = ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60)
}

data class Camera(
    val id: String,
    val name: String,
    val thumbnailLink: String,
    val liveLink: String,
    /** {year} {month} {day} {hour} {minute} {second} {ms} yer tutucuları içerir (UTC). */
    val histLink: String,
)

sealed class ConnectResult {
    data class Success(val info: ServerInfo) : ConnectResult()
    data object AuthFailed : ConnectResult()
    data object BadVersion : ConnectResult()
    data object Unreachable : ConnectResult()
}

class SymphonyApi(
    private val cfg: ServerConfig,
    insecureSsl: Boolean,
    timeoutSec: Int,
) {
    @Volatile private var useHttps = true
    @Volatile private var encodeUserName = true

    val client: OkHttpClient = buildClient(insecureSsl, timeoutSec)

    private fun b64(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /**
     * Orijinal uygulamayla aynı: önce kullanıcı adı base64'lenir
     * (base64(base64(user):pass)), olmazsa düz kullanıcı adı denenir.
     */
    private fun authHeader(): String {
        val login = if (encodeUserName) b64(cfg.user) else cfg.user
        return "Basic " + b64("$login:${cfg.password}")
    }

    private val userAgent: String
        get() = "Aimetis Symphony/2.1.2 (${Build.MANUFACTURER} ${Build.MODEL}; " +
            "Android ${Build.VERSION.RELEASE}; TUR_tur) API/2 Mobile/1"

    private fun buildClient(insecure: Boolean, timeoutSec: Int): OkHttpClient {
        val t = timeoutSec.toLong()
        val b = OkHttpClient.Builder()
            .connectTimeout(t, TimeUnit.SECONDS)
            .readTimeout(t, TimeUnit.SECONDS)
            .writeTimeout(t, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", authHeader())
                        .header("User-Agent", userAgent)
                        .build()
                )
            }
        if (insecure) {
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(trustAll), SecureRandom())
            b.sslSocketFactory(ctx.socketFactory, trustAll)
            b.hostnameVerifier { _, _ -> true }
        }
        return b.build()
    }

    private fun baseUrl() = "${if (useHttps) "https" else "http"}://${cfg.host}:${cfg.port}"

    /** Sunucunun verdiği link ya yol ("/cameras/...") ya da tam URL olabilir. */
    fun resolve(link: String): String = when {
        link.startsWith("http://") || link.startsWith("https://") -> link
        link.startsWith("/") -> baseUrl() + link
        else -> "${baseUrl()}/$link"
    }

    private fun withQuery(url: String, query: String) =
        url + (if (url.contains('?')) "&" else "?") + query

    /** Önce HTTPS, bağlantı kurulamazsa HTTP. Blocking — IO thread'inde çağır. */
    fun connect(): ConnectResult {
        for (https in listOf(true, false)) {
            useHttps = https
            var reached = false
            for (encode in listOf(true, false)) {
                encodeUserName = encode
                try {
                    val req = Request.Builder().url(baseUrl() + "/").build()
                    client.newCall(req).execute().use { resp ->
                        when (resp.code) {
                            200 -> {
                                reached = true
                                val info = parseInfo(resp.body?.string().orEmpty())
                                    ?: return ConnectResult.BadVersion
                                if (info.apiVersion < 2) return ConnectResult.BadVersion
                                return ConnectResult.Success(info)
                            }
                            401, 403 -> reached = true
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    break // bu şemada bağlantı kurulamadı, diğer şemayı dene
                }
            }
            if (reached) return ConnectResult.AuthFailed
        }
        return ConnectResult.Unreachable
    }

    private fun parseInfo(body: String): ServerInfo? = try {
        val o = JSONObject(body)
        ServerInfo(
            apiVersion = o.optInt("ApiVersion", 0),
            serverAgent = o.optString("ServerAgent", ""),
            camerasLink = o.optString("CamerasLink", ""),
            utcOffsetMinutes = o.optInt("UtcOffset", 0),
        )
    } catch (e: Exception) {
        null
    }

    private fun getText(link: String): String? = try {
        client.newCall(Request.Builder().url(resolve(link)).build()).execute().use {
            if (it.isSuccessful) it.body?.string() else null
        }
    } catch (e: Exception) {
        null
    }

    fun cameras(info: ServerInfo): List<Camera>? {
        if (info.camerasLink.isBlank()) return null
        val body = getText(info.camerasLink) ?: return null
        return try {
            val arr = JSONArray(body)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val live = o.optString("LiveFootageLink", "")
                if (live.isBlank()) return@mapNotNull null
                Camera(
                    id = o.optString("Id", i.toString()),
                    name = o.optString("Name", "Kamera ${i + 1}"),
                    thumbnailLink = o.optString("ThumbnailLink", ""),
                    liveLink = live,
                    histLink = o.optString("HistFootageLink", ""),
                )
            }.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            null
        }
    }

    fun thumbUrl(cam: Camera, w: Int, h: Int): String? =
        if (cam.thumbnailLink.isBlank()) null
        else withQuery(resolve(cam.thumbnailLink), "width=$w&height=$h")

    /** H.264 / MPEG-TS canlı akış (Content-Type: video/mp2t). */
    fun liveUrl(cam: Camera, w: Int, h: Int, audio: Boolean): String =
        withQuery(resolve(cam.liveLink), "width=$w&height=$h&audio=$audio")

    /** Geçmiş kayıt: seçilen zaman UTC olarak yer tutuculara yazılır. */
    fun histUrl(cam: Camera, time: ZonedDateTime, w: Int, h: Int, audio: Boolean): String? {
        if (cam.histLink.isBlank()) return null
        val utc = time.withZoneSameInstant(ZoneOffset.UTC)
        val link = cam.histLink
            .replace("{year}", utc.year.toString())
            .replace("{month}", utc.monthValue.toString())
            .replace("{day}", utc.dayOfMonth.toString())
            .replace("{hour}", utc.hour.toString())
            .replace("{minute}", utc.minute.toString())
            .replace("{second}", utc.second.toString())
            .replace("{ms}", (utc.nano / 1_000_000).toString())
        return withQuery(resolve(link), "width=$w&height=$h&audio=$audio")
    }
}
