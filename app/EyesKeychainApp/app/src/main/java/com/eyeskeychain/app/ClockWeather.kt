package com.eyeskeychain.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.eyeskeychain.app.ble.Ble
import com.eyeskeychain.app.ble.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * Saat modu için: bağlanınca telefonun saatini gönderir,
 * konum izni varsa Open-Meteo'dan (ücretsiz, anahtarsız) hava durumunu 30 dakikada bir günceller.
 */
object ClockWeather {
    data class Weather(val tempC: Int, val code: Int, val label: String)

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather
    private val _info = MutableStateFlow("")
    val info: StateFlow<String> = _info

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        Ble.addReadyListener { sendTime(); _weather.value?.let { send(it) } }
        main.post(refresher)
    }

    fun hasLocationPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun sendTime() {
        val now = System.currentTimeMillis()
        val tzMin = TimeZone.getDefault().getOffset(now) / 60000
        Ble.send(Protocol.time(now / 1000, tzMin))
    }

    private fun send(w: Weather) = Ble.send(Protocol.weather(w.tempC, w.code))

    private val refresher = object : Runnable {
        override fun run() {
            refreshWeather()
            sendTime()                          // saat kaymasın
            main.postDelayed(this, 30 * 60 * 1000L)
        }
    }

    @Suppress("MissingPermission")
    fun refreshWeather() {
        if (!::app.isInitialized) return
        if (!hasLocationPermission(app)) { _info.value = "Hava durumu için konum izni gerekli"; return }
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc: Location? = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (loc == null) { _info.value = "Konum bulunamadı (bir kez harita uygulaması açmak yardımcı olur)"; return }
        _info.value = "Hava durumu alınıyor..."
        io.execute {
            val w = try { fetch(loc.latitude, loc.longitude) } catch (_: Exception) { null }
            main.post {
                if (w == null) { _info.value = "Hava durumu alınamadı"; return@post }
                _weather.value = w
                _info.value = ""
                send(w)
            }
        }
    }

    private fun fetch(lat: Double, lon: Double): Weather? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code"
            .format(java.util.Locale.US, lat, lon)
        val c = URL(url).openConnection() as HttpURLConnection
        val body = try {
            c.connectTimeout = 8000; c.readTimeout = 8000
            if (c.responseCode != 200) return null
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
        val cur = JSONObject(body).getJSONObject("current")
        val t = Math.round(cur.getDouble("temperature_2m")).toInt()
        val wmo = cur.getInt("weather_code")
        val (code, label) = when (wmo) {
            0, 1 -> 0 to "Açık"
            2, 3 -> 1 to "Bulutlu"
            45, 48 -> 5 to "Sisli"
            in 51..67, in 80..82 -> 2 to "Yağmurlu"
            in 71..77, 85, 86 -> 3 to "Karlı"
            in 95..99 -> 4 to "Fırtına"
            else -> 1 to "Bulutlu"
        }
        return Weather(t, code, label)
    }
}
