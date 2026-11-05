package com.eyeskeychain.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Kullanıcı ayarları (telefonda saklanır). */
object AppSettings {
    private lateinit var prefs: SharedPreferences

    private val _showTitle = MutableStateFlow(true)
    val showTitle: StateFlow<Boolean> = _showTitle
    private val _showLyrics = MutableStateFlow(true)
    val showLyrics: StateFlow<Boolean> = _showLyrics
    private val _autoRock = MutableStateFlow(true)
    val autoRock: StateFlow<Boolean> = _autoRock
    private val _latinize = MutableStateFlow(true)
    val latinize: StateFlow<Boolean> = _latinize
    private val _notify = MutableStateFlow(true)
    val notify: StateFlow<Boolean> = _notify
    private val _notifyContent = MutableStateFlow(false)
    val notifyContent: StateFlow<Boolean> = _notifyContent
    private val _notifyAllApps = MutableStateFlow(false)
    val notifyAllApps: StateFlow<Boolean> = _notifyAllApps
    private val _workMin = MutableStateFlow(25)
    val workMin: StateFlow<Int> = _workMin
    private val _breakMin = MutableStateFlow(5)
    val breakMin: StateFlow<Int> = _breakMin

    /** Albüm kapağı: 0 kapalı, 1 şarkı değişince birkaç saniye, 2 müzik çalarken hep, 3 tam ekran (çalarken hep, her şey gizli). */
    private val _coverMode = MutableStateFlow(1)
    val coverMode: StateFlow<Int> = _coverMode
    private val _coverSecs = MutableStateFlow(10)
    val coverSecs: StateFlow<Int> = _coverSecs

    // Ekran ışığı (anahtarlıkta BLK -> GPIO 3 bağlı olmalı)
    private val _brightness = MutableStateFlow(200)
    val brightness: StateFlow<Int> = _brightness
    /** Dokunulmazsa kaç saniye sonra kararır, 0 = hiç. */
    private val _dimAfter = MutableStateFlow(60)
    val dimAfter: StateFlow<Int> = _dimAfter
    /** Kararınca parlaklığın yüzde kaçı kalır. */
    private val _dimPct = MutableStateFlow(25)
    val dimPct: StateFlow<Int> = _dimPct
    private val _nightOn = MutableStateFlow(false)
    val nightOn: StateFlow<Boolean> = _nightOn
    private val _nightFrom = MutableStateFlow(23)
    val nightFrom: StateFlow<Int> = _nightFrom
    private val _nightTo = MutableStateFlow(7)
    val nightTo: StateFlow<Int> = _nightTo
    /** Gece ışığı 0-255 (0 = tamamen kapalı). */
    private val _nightLevel = MutableStateFlow(6)
    val nightLevel: StateFlow<Int> = _nightLevel

    /** Saat modu kullanılabilir mi (kapalıysa anahtarlıkta da seçilemez). */
    private val _clockOn = MutableStateFlow(true)
    val clockOn: StateFlow<Boolean> = _clockOn
    /** Göz hareketlerinin sıklığı: 0 sakin, 1 normal, 2 hareketli, 3 çok hareketli. */
    private val _lively = MutableStateFlow(2)
    val lively: StateFlow<Int> = _lively

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _showTitle.value = prefs.getBoolean("showTitle", true)
        _showLyrics.value = prefs.getBoolean("showLyrics", true)
        _autoRock.value = prefs.getBoolean("autoRock", true)
        _latinize.value = prefs.getBoolean("latinize", true)
        _notify.value = prefs.getBoolean("notify", true)
        _notifyContent.value = prefs.getBoolean("notifyContent", false)
        _notifyAllApps.value = prefs.getBoolean("notifyAllApps", false)
        _workMin.value = prefs.getInt("workMin", 25)
        _breakMin.value = prefs.getInt("breakMin", 5)
        _coverMode.value = prefs.getInt("coverMode", 1)
        _coverSecs.value = prefs.getInt("coverSecs", 10)
        _brightness.value = prefs.getInt("brightness", 200)
        _dimAfter.value = prefs.getInt("dimAfter", 60)
        _dimPct.value = prefs.getInt("dimPct", 25)
        _nightOn.value = prefs.getBoolean("nightOn", false)
        _nightFrom.value = prefs.getInt("nightFrom", 23)
        _nightTo.value = prefs.getInt("nightTo", 7)
        _nightLevel.value = prefs.getInt("nightLevel", 6)
        _clockOn.value = prefs.getBoolean("clockOn", true)
        _lively.value = prefs.getInt("lively", 2).coerceIn(0, 3)
    }

    fun setShowTitle(v: Boolean) { _showTitle.value = v; prefs.edit().putBoolean("showTitle", v).apply() }
    fun setShowLyrics(v: Boolean) { _showLyrics.value = v; prefs.edit().putBoolean("showLyrics", v).apply() }
    fun setAutoRock(v: Boolean) { _autoRock.value = v; prefs.edit().putBoolean("autoRock", v).apply() }
    fun setLatinize(v: Boolean) { _latinize.value = v; prefs.edit().putBoolean("latinize", v).apply() }
    fun setNotify(v: Boolean) { _notify.value = v; prefs.edit().putBoolean("notify", v).apply() }
    fun setNotifyContent(v: Boolean) { _notifyContent.value = v; prefs.edit().putBoolean("notifyContent", v).apply() }
    fun setNotifyAllApps(v: Boolean) { _notifyAllApps.value = v; prefs.edit().putBoolean("notifyAllApps", v).apply() }
    fun setWorkMin(v: Int) { _workMin.value = v; prefs.edit().putInt("workMin", v).apply() }
    fun setBreakMin(v: Int) { _breakMin.value = v; prefs.edit().putInt("breakMin", v).apply() }
    fun setCoverMode(v: Int) { _coverMode.value = v; prefs.edit().putInt("coverMode", v).apply() }
    fun setCoverSecs(v: Int) { _coverSecs.value = v; prefs.edit().putInt("coverSecs", v).apply() }
    fun setBrightness(v: Int) { _brightness.value = v; prefs.edit().putInt("brightness", v).apply() }
    fun setDimAfter(v: Int) { _dimAfter.value = v; prefs.edit().putInt("dimAfter", v).apply() }
    fun setDimPct(v: Int) { _dimPct.value = v; prefs.edit().putInt("dimPct", v).apply() }
    fun setNightOn(v: Boolean) { _nightOn.value = v; prefs.edit().putBoolean("nightOn", v).apply() }
    fun setNightFrom(v: Int) { _nightFrom.value = v; prefs.edit().putInt("nightFrom", v).apply() }
    fun setNightTo(v: Int) { _nightTo.value = v; prefs.edit().putInt("nightTo", v).apply() }
    fun setClockOn(v: Boolean) { _clockOn.value = v; prefs.edit().putBoolean("clockOn", v).apply() }
    fun setLively(v: Int) { _lively.value = v; prefs.edit().putInt("lively", v).apply() }
    fun setNightLevel(v: Int) { _nightLevel.value = v; prefs.edit().putInt("nightLevel", v).apply() }
}
