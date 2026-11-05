package com.eyeskeychain.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Anahtarlıkla Bluetooth bağlantısı. Uygulama boyunca tek örnek.
 * Tüm GATT işlemleri ana iş parçacığında, sırayla (kuyrukla) yapılır.
 */
@SuppressLint("MissingPermission")
object Ble {
    enum class State { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state
    private val _status = MutableStateFlow<DeviceStatus?>(null)
    val status: StateFlow<DeviceStatus?> = _status
    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())

    private var gatt: BluetoothGatt? = null
    private var cmdChar: BluetoothGattCharacteristic? = null
    private var statusChar: BluetoothGattCharacteristic? = null
    private val queue = ArrayDeque<ByteArray>()
    private var writing = false
    private var writeSeq = 0
    private var userDisconnect = false
    private var scanning = false
    private var retryCount = 0
    private val readyListeners = mutableListOf<() -> Unit>()

    // Kablosuz güncelleme: normal komutlar durur, parçalar tek tek ve onaylı gider (hiçbiri düşmez)
    private var otaMode = false
    private var otaDone: ((Boolean) -> Unit)? = null

    /** Her bağlantı kurulduğunda çağrılır (saat, hava, müzik durumu tekrar gönderilir). */
    fun addReadyListener(l: () -> Unit) { if (l !in readyListeners) readyListeners.add(l) }

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("ble", Context.MODE_PRIVATE)
    }

    private val adapter: BluetoothAdapter?
        get() = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    /** Daha önce bağlanılan anahtarlığa doğrudan bağlan, yoksa tara. */
    fun autoConnect() {
        if (_state.value != State.DISCONNECTED) return
        val a = adapter ?: return
        if (!a.isEnabled) { _message.value = "Bluetooth kapalı"; return }
        val addr = prefs.getString("addr", null)
        if (addr != null && BluetoothAdapter.checkBluetoothAddress(addr)) {
            // önce 2 kez doğrudan dene; olmazsa arka planda bekle: anahtarlık yakına gelip açılınca Android kendisi bağlar
            connect(a.getRemoteDevice(addr), background = retryCount >= 2)
        } else {
            startScan()
        }
    }

    /** "Bağlan" düğmesi: baştan, önce doğrudan bağlanmayı dene. */
    fun connectNow() {
        retryCount = 0
        autoConnect()
    }

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run { _message.value = "Bluetooth kapalı"; return }
        if (scanning) return
        _state.value = State.SCANNING
        _message.value = "Anahtarlık aranıyor..."
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(Protocol.SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanning = true
        scanner.startScan(filters, settings, scanCallback)
        main.postDelayed({
            if (scanning) {
                stopScan()
                if (_state.value == State.SCANNING) {
                    _state.value = State.DISCONNECTED
                    _message.value = "Bulunamadı. Anahtarlık açık ve yakında mı?"
                }
            }
        }, 12_000)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) { }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning) return
            stopScan()
            retryCount = 0
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _state.value = State.DISCONNECTED
            _message.value = "Tarama hatası ($errorCode)"
        }
    }

    private fun connect(device: BluetoothDevice, background: Boolean = false) {
        userDisconnect = false
        _state.value = State.CONNECTING
        _message.value = if (background) "Anahtarlık bekleniyor, yakına gelince kendiliğinden bağlanır" else "Bağlanıyor..."
        gatt?.close()
        gatt = device.connectGatt(app, background, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        userDisconnect = true
        stopScan()
        val g = gatt
        when {
            g == null -> _state.value = State.DISCONNECTED
            _state.value == State.CONNECTED -> g.disconnect()
            else -> {
                // henüz bağlanmamış (bekleyen) bağlantı: Android geri bildirim vermeyebilir, kendimiz kapatalım
                runCatching { g.disconnect() }
                cleanup(g)
                _message.value = "Bağlantı kesildi"
            }
        }
    }

    /** Kayıtlı anahtarlığı unut (başka bir anahtarlığa bağlanmak için). */
    fun forget() {
        prefs.edit().remove("addr").apply()
        disconnect()
    }

    fun send(data: ByteArray) {
        if (otaMode && !com.eyeskeychain.app.Ota.busy) otaMode = false   // güncelleme bittiyse takılı kalmasın
        if (_state.value != State.CONNECTED || otaMode) return
        if (queue.size > 400) queue.removeFirst()    // takılırsa kuyruk şişmesin (tam ekran kapak ~125 parça, ona dokunmasın)
        queue.addLast(data)
        pump()
    }

    /** Çok parçalı veri (albüm kapağı): hepsi sıraya girer, bitene kadar bağlantı hızlı modda. */
    fun sendBulk(items: List<ByteArray>) {
        if (otaMode && !com.eyeskeychain.app.Ota.busy) otaMode = false
        if (_state.value != State.CONNECTED || otaMode) return
        queue.addAll(items)
        runCatching { gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
        main.removeCallbacks(unboost)
        main.postDelayed(unboost, 300)
        pump()
    }

    private val unboost: Runnable = object : Runnable {
        override fun run() {
            if (otaMode) return
            if (queue.isNotEmpty() || writing) { main.postDelayed(this, 300); return }
            runCatching { gatt?.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED) }
        }
    }

    /** Güncelleme modunu aç/kapat. Açıkken bağlantı hızlı moda alınır, bekleyen komutlar silinir. */
    fun setOtaMode(on: Boolean) {
        otaMode = on
        if (on) queue.clear()
        runCatching {
            gatt?.requestConnectionPriority(
                if (on) BluetoothGatt.CONNECTION_PRIORITY_HIGH else BluetoothGatt.CONNECTION_PRIORITY_BALANCED
            )
        }
        if (!on) otaDone = null
    }

    /** Güncelleme için tek bir onaylı yazma. done(true) = anahtarlık aldı. */
    @Suppress("DEPRECATION")
    fun otaWrite(data: ByteArray, attempt: Int = 0, done: (Boolean) -> Unit) {
        if (_state.value != State.CONNECTED || attempt > 150) { done(false); return }   // ~3 sn denedi, olmadı
        if (writing) { main.postDelayed({ otaWrite(data, attempt + 1, done) }, 15); return }
        val g = gatt ?: run { done(false); return }
        val c = cmdChar ?: run { done(false); return }
        writing = true
        otaDone = done
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = data
            g.writeCharacteristic(c)
        }
        if (!ok) {
            // Android meşgul olabilir, kısa bekleyip tekrar dene
            writing = false
            otaDone = null
            main.postDelayed({ otaWrite(data, attempt + 1, done) }, 30)
        }
    }

    // ---------------- GATT ----------------
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    _deviceName.value = g.device.name ?: "Göz"
                    prefs.edit().putString("addr", g.device.address).apply()
                    retryCount = 0
                    g.requestMtu(185)
                } else {
                    cleanup(g)
                    if (userDisconnect) {
                        _message.value = "Bağlantı kesildi"
                    } else {
                        retryCount++
                        _message.value = "Bağlantı koptu, tekrar deneniyor..."
                        main.postDelayed({
                            if (_state.value == State.DISCONNECTED && !userDisconnect) autoConnect()
                        }, 2500)
                    }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            main.post { g.discoverServices() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                val svc = g.getService(Protocol.SERVICE)
                cmdChar = svc?.getCharacteristic(Protocol.CMD)
                statusChar = svc?.getCharacteristic(Protocol.STATUS)
                if (cmdChar == null) {
                    _message.value = "Bu cihazda Göz servisi yok"
                    g.disconnect()
                    return@post
                }
                val sc = statusChar
                if (sc == null) markReady() else enableNotify(g, sc)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post {
                val sc = statusChar
                if (sc == null || !g.readCharacteristic(sc)) markReady()
            }
        }

        // Android 13+
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            main.post { parseStatus(value); markReady() }
        }

        // Android 12 ve altı
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val v = c.value ?: ByteArray(0)
            main.post { parseStatus(v); markReady() }
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == Protocol.STATUS) main.post { parseStatus(value) }
        }

        // Android 12 ve altı
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val v = c.value ?: return
            if (c.uuid == Protocol.STATUS) main.post { parseStatus(v) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post {
                writing = false
                val cb = otaDone
                if (cb != null) { otaDone = null; cb(status == BluetoothGatt.GATT_SUCCESS) } else pump()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(Protocol.CCCD)
        if (d == null) { markReady(); return }
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(d)
        }
        if (!ok) markReady()
    }

    private fun markReady() {
        if (_state.value == State.CONNECTED) return
        _state.value = State.CONNECTED
        _message.value = "Bağlandı"
        pump()
        readyListeners.toList().forEach { runCatching { it() } }
    }

    @Suppress("DEPRECATION")
    private fun pump() {
        if (writing || _state.value != State.CONNECTED) return
        val g = gatt ?: return
        val c = cmdChar ?: return
        val data = queue.removeFirstOrNull() ?: return
        writing = true
        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = data
            g.writeCharacteristic(c)
        }
        if (!ok) {
            writing = false
            queue.addFirst(data)
            main.postDelayed({ pump() }, 60)
        } else {
            // Bazı telefonlarda yazma onayı hiç gelmiyor: 2 sn bekle, gelmezse kuyruğu kendimiz yürüt (yoksa her şey takılır)
            val seq = ++writeSeq
            main.postDelayed({ if (writing && writeSeq == seq && otaDone == null) { writing = false; pump() } }, 2000)
        }
    }

    private fun parseStatus(v: ByteArray) {
        if (v.size < 4) return
        _status.value = DeviceStatus(
            mode = v[0].toInt() and 0xFF,
            rotation = v[1].toInt() and 3,
            battery = v[2].toInt() and 0xFF,
            usb = (v[3].toInt() and 1) != 0,
            playing = (v[3].toInt() and 2) != 0,
            fwVersion = if (v.size >= 5) v[4].toInt() and 0xFF else 0,
            otaState = if (v.size >= 6) v[5].toInt() and 0xFF else 0,
            brightness = if (v.size >= 7) v[6].toInt() and 0xFF else -1,
            dimmed = v.size >= 8 && (v[7].toInt() and 1) != 0,
            asleep = v.size >= 8 && (v[7].toInt() and 2) != 0,
        )
    }

    private fun cleanup(g: BluetoothGatt) {
        try { g.close() } catch (_: Exception) { }
        if (gatt === g) gatt = null
        cmdChar = null
        statusChar = null
        queue.clear()
        writing = false
        otaMode = false                              // bağlantı koptuysa güncelleme de bitmiştir
        otaDone?.let { cb -> otaDone = null; cb(false) }
        _state.value = State.DISCONNECTED
    }
}
