package com.eyeskeychain.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eyeskeychain.app.ble.Ble
import com.eyeskeychain.app.ble.DeviceStatus
import com.eyeskeychain.app.ble.Protocol
import com.eyeskeychain.app.music.BeatSync
import com.eyeskeychain.app.music.CoverArt
import com.eyeskeychain.app.music.MusicListenerService
import com.eyeskeychain.app.music.MusicTracker
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val BG = Color(0xFF0B0C10)
private val CARD = Color(0xFF16181F)
private val BTN = Color(0xFF23262F)
private val MUTED = Color(0xFFA9AEBC)
private val GOOD = Color(0xFF5BE38A)
private val ACCENT = Color(0xFF00E6FF)

private val EMOTIONS = listOf(
    "😊 Mutlu" to 1, "😠 Kızgın" to 2, "😢 Üzgün" to 3, "😮 Şaşkın" to 4,
    "😴 Uykulu" to 5, "😍 Aşık" to 6, "😵 Karışık" to 7, "😉 Göz kırp" to 8,
)

/** Anahtarlıktaki göz hareketleri (eyes_keychain.ino -> enum Idle, 1..15). */
private val EYE_MOVES = listOf(
    "👀 Meraklı" to 1, "😴 Uyuklama" to 2, "😊 Neşe" to 3, "😮 Şaşırma" to 4, "😉 Göz kırp" to 5,
    "🔍 Etrafa bak" to 6, "🙄 Göz devir" to 7, "↕️ Yukarı-aşağı" to 8, "🤔 Düşünme" to 9,
    "😵‍💫 Sersemleme" to 10, "🥱 Esneme" to 11, "🧐 Kısık göz" to 12, "💗 Kalpler" to 13,
    "🥶 Titreme" to 14, "⬆️ Zıplama" to 15,
)
private val LIVELY_CHOICES = listOf("Sakin" to 0, "Normal" to 1, "Hareketli" to 2, "Çok" to 3)

private data class Tab(val label: String, val icon: ImageVector)
private val TABS = listOf(
    Tab("Kontrol", Icons.Filled.Home),
    Tab("Müzik", Icons.Filled.PlayArrow),
    Tab("Tasarla", Icons.Filled.Face),
    Tab("Araçlar", Icons.Filled.Build),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Ble.init(applicationContext)
        AppSettings.init(applicationContext)
        BeatSync.init(applicationContext)
        EyeDesign.init(applicationContext)
        ClockWeather.start(applicationContext)
        DisplayLight.start()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = ACCENT, background = BG, surface = CARD, onPrimary = Color.Black)
            ) { AppScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Uygulama güncellenince bildirim servisi kendiliğinden bağlanmayabiliyor: müzik takibini buradan da başlat
        MusicListenerService.ensureRunning(this)
    }
}

fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

fun hasPermissions(ctx: Context): Boolean =
    requiredPermissions().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

/** Şu an anahtarlıkta görünen göz (bağlı değilse Normal). */
@Composable
private fun currentEye(status: DeviceStatus?): Pair<String, EyeParams> {
    val custom by EyeDesign.params.collectAsState()
    val m = status?.mode ?: 0
    return if (m == MODE_CUSTOM) "Özel" to custom else MODES.getOrElse(m) { MODES[0] }.let { it.name to it.eye }
}

@Composable
fun AppScreen() {
    val ctx = LocalContext.current
    val state by Ble.state.collectAsState()
    val status by Ble.status.collectAsState()

    var granted by remember { mutableStateOf(hasPermissions(ctx)) }
    var btOn by remember { mutableStateOf(granted && Ble.isBluetoothOn()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res.values.all { it }
        btOn = granted && Ble.isBluetoothOn()
    }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        btOn = Ble.isBluetoothOn()
        if (btOn) Ble.autoConnect()
    }
    LaunchedEffect(granted, btOn) { if (granted && btOn) Ble.autoConnect() }

    val accent = currentEye(status).second.color

    Scaffold(
        containerColor = BG,
        bottomBar = {
            NavigationBar(containerColor = CARD, tonalElevation = 0.dp) {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = accent,
                            indicatorColor = accent,
                            unselectedIconColor = MUTED,
                            unselectedTextColor = MUTED,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TopBar(state, status)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!granted) {
                    Section("Başlamadan önce") {
                        Text("Anahtarlığı bulmak için Bluetooth izni gerekiyor.", color = Color.White)
                        Button(onClick = { permLauncher.launch(requiredPermissions()) }, modifier = Modifier.fillMaxWidth()) { Text("İzin ver") }
                    }
                } else if (!btOn) {
                    Section("Bluetooth kapalı") {
                        Button(
                            onClick = { btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Bluetooth'u aç") }
                    }
                } else if (state != Ble.State.CONNECTED && tab != 0) {
                    NotConnectedHint { tab = 0 }
                }
                when (tab) {
                    0 -> ControlTab(state, status, granted && btOn)
                    1 -> MusicTab()
                    2 -> DesignerTab(state)
                    else -> ToolsTab(status)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TopBar(state: Ble.State, status: DeviceStatus?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Göz", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.weight(1f))
        val (txt, col) = when (state) {
            Ble.State.CONNECTED -> (if (status != null) batteryText(status) else "Bağlı") to GOOD
            Ble.State.SCANNING -> "Aranıyor" to MUTED
            Ble.State.CONNECTING -> "Bağlanıyor" to MUTED
            Ble.State.DISCONNECTED -> "Bağlı değil" to Color(0xFFFF8A8A)
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(CARD).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(col))
            Spacer(Modifier.width(8.dp))
            Text(txt, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun batteryText(s: DeviceStatus) = if (s.usb) "⚡ %${s.battery}" else "🔋 %${s.battery}"

@Composable
private fun NotConnectedHint(goControl: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A1E22)).clickable { goControl() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Anahtarlık bağlı değil, ayarlar gitmez.", color = Color(0xFFFFB4B4), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("Bağlan ›", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ======================= KONTROL =======================
@Composable
private fun ControlTab(state: Ble.State, status: DeviceStatus?, ready: Boolean) {
    val name by Ble.deviceName.collectAsState()
    val message by Ble.message.collectAsState()
    val (modeName, eye) = currentEye(status)

    Card(colors = CardDefaults.cardColors(containerColor = CARD), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            EyesCanvas(eye, Modifier.clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.height(8.dp))
            Text(modeName, color = eye.color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    if (ready) ConnectionCard(state, name, status, message)

    Section("Mod") {
        val clockOn by AppSettings.clockOn.collectAsState()
        val items = (MODES.mapIndexed { i, m -> Triple(i, m.name, m.color) } + Triple(MODE_CUSTOM, "Özel", EyeDesign.params.value.color))
            .filter { clockOn || it.first != 6 }
        GridRows(items, 4) { (i, label, color) ->
            val selected = status?.mode == i
            Button(
                onClick = { Ble.send(Protocol.mode(i)) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) color else BTN,
                    contentColor = if (selected) Color.Black else color,
                ),
            ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1) }
        }
    }

    Section("Duygu (3 saniye)") {
        GridRows(EMOTIONS, 2) { (label, id) ->
            OutlinedButton(
                onClick = { Ble.send(Protocol.emotion(id, 3)) },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(label, color = Color.White, maxLines = 1) }
        }
    }

    MovesSection(status)

    Section("Ekran yönü") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("0°", "90°", "180°", "270°").forEachIndexed { i, label ->
                val selected = status?.rotation == i
                Button(
                    onClick = { Ble.send(Protocol.rotation(i)) },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) Color.White else BTN,
                        contentColor = if (selected) Color.Black else Color.White,
                    ),
                ) { Text(label) }
            }
        }
    }

    LightSection(status)
}

// ======================= GÖZ HAREKETLERİ =======================
@Composable
private fun MovesSection(status: DeviceStatus?) {
    val lively by AppSettings.lively.collectAsState()
    val fw = status?.fwVersion ?: 0
    Section("Göz hareketleri") {
        if (status != null && fw < DisplayLight.MOVES_FW) {
            Text(
                "Yeni hareketler için anahtarlığa yazılım v${DisplayLight.MOVES_FW} yüklenmeli (şu an v$fw).",
                color = Color(0xFFFFD43B), fontSize = 12.sp,
            )
        }
        Text("Ne kadar sık hareket etsin", color = Color.White, fontSize = 14.sp)
        ChoiceRow(LIVELY_CHOICES, lively) { AppSettings.setLively(it); DisplayLight.sendPersonality() }
        Text("Boştayken gözler kendiliğinden bunları yapar. Aşağıdan istediğini hemen oynatabilirsin.", color = MUTED, fontSize = 12.sp)
        GridRows(EYE_MOVES, 3) { (label, id) ->
            OutlinedButton(
                onClick = { Ble.send(Protocol.playMove(id)) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1) }
        }
    }
}

// ======================= EKRAN IŞIĞI =======================
private val DIM_CHOICES = listOf("Kapalı" to 0, "30 sn" to 30, "1 dk" to 60, "2 dk" to 120, "5 dk" to 300)
private val NIGHT_LIGHT_CHOICES = listOf("Kapalı" to 0, "Çok kısık" to 6, "Kısık" to 20)

@Composable
private fun LightSection(status: DeviceStatus?) {
    val bri by AppSettings.brightness.collectAsState()
    val dimAfter by AppSettings.dimAfter.collectAsState()
    val dimPct by AppSettings.dimPct.collectAsState()
    val nightOn by AppSettings.nightOn.collectAsState()
    val nightFrom by AppSettings.nightFrom.collectAsState()
    val nightTo by AppSettings.nightTo.collectAsState()
    val nightLevel by AppSettings.nightLevel.collectAsState()
    val fw = status?.fwVersion ?: 0

    Section("Ekran ışığı") {
        if (status != null && fw < DisplayLight.MIN_FW) {
            Text(
                "Parlaklık ayarı için anahtarlığa yazılım v${DisplayLight.MIN_FW} yüklenmeli (şu an v$fw).",
                color = Color(0xFFFFD43B), fontSize = 12.sp,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Parlaklık", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("%" + (bri * 100f / 255f).roundToInt().coerceIn(1, 100), color = MUTED, fontSize = 13.sp)
        }
        Slider(
            value = bri.toFloat(),
            onValueChange = { DisplayLight.setBrightness(it.roundToInt().coerceIn(10, 255)) },
            valueRange = 10f..255f,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFFFE066), inactiveTrackColor = BTN),
        )

        Text("Dokunulmazsa karart", color = Color.White, fontSize = 14.sp)
        ChoiceRow(DIM_CHOICES, dimAfter) { AppSettings.setDimAfter(it); DisplayLight.sendSettings() }
        if (dimAfter > 0) {
            Stepper("Kararınca kalan ışık", dimPct, 5..60, 5, "%") { AppSettings.setDimPct(it); DisplayLight.sendSettings() }
        }

        SettingRow("Gece uykusu", nightOn) { AppSettings.setNightOn(it); DisplayLight.sendSettings() }
        if (nightOn) {
            HourStepper("Uyusun", nightFrom) { AppSettings.setNightFrom(it); DisplayLight.sendSettings() }
            HourStepper("Uyansın", nightTo) { AppSettings.setNightTo(it); DisplayLight.sendSettings() }
            Text("Gece ışığı", color = Color.White, fontSize = 14.sp)
            ChoiceRow(NIGHT_LIGHT_CHOICES, nightLevel) { AppSettings.setNightLevel(it); DisplayLight.sendSettings() }
            Text(
                "Gece gözler kapanır, z z z çıkar. Düğmeye basınca 20 saniye uyanır. Saati telefondan öğrenir; " +
                    "açıldıktan sonra telefona hiç bağlanmadıysa uyumaz.",
                color = MUTED, fontSize = 12.sp,
            )
        }
        val now = when {
            status == null || fw < DisplayLight.MIN_FW -> ""
            status?.asleep == true -> "Şu an gece uykusunda"
            status?.dimmed == true -> "Şu an kararmış"
            else -> ""
        }
        if (now.isNotEmpty()) Text(now, color = Color(0xFFFFE066), fontSize = 12.sp)
        Text(
            "Kararmışken düğmeye ilk basış sadece ekranı açar. Şarjdayken ve pomodoro sırasında kararmaz. " +
                "Bildirim gelince ve şarkı değişince aydınlanır.",
            color = MUTED, fontSize = 12.sp,
        )
    }
}

@Composable
private fun ChoiceRow(items: List<Pair<String, Int>>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, v) ->
            val sel = v == selected
            Button(
                onClick = { onPick(v) },
                modifier = Modifier.weight(1f).height(42.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sel) Color(0xFFFFE066) else BTN,
                    contentColor = if (sel) Color.Black else Color.White,
                ),
            ) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

/** Saat seçici: 23'ten sonra 0'a, 0'dan önce 23'e döner. */
@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((hour + 23) % 24) }) { Text("−", color = Color.White) }
        Text("%02d:00".format(hour), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = { onChange((hour + 1) % 24) }) { Text("+", color = Color.White) }
    }
}

@Composable
private fun ConnectionCard(state: Ble.State, name: String?, status: DeviceStatus?, message: String) {
    Section("Anahtarlık") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name ?: "Henüz bağlanmadı", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                val stateText = when (state) {
                    Ble.State.DISCONNECTED -> "Bağlı değil"
                    Ble.State.SCANNING -> "Aranıyor..."
                    Ble.State.CONNECTING -> "Bağlanıyor..."
                    Ble.State.CONNECTED -> "Bağlı"
                }
                Text(stateText, color = if (state == Ble.State.CONNECTED) GOOD else MUTED, fontSize = 14.sp)
                if (message.isNotEmpty() && message != stateText) Text(message, color = MUTED, fontSize = 12.sp)
            }
            if (state == Ble.State.CONNECTED && status != null) BatteryLabel(status)
        }
        when (state) {
            Ble.State.DISCONNECTED -> Button(onClick = { Ble.connectNow() }, modifier = Modifier.fillMaxWidth()) { Text("Bağlan") }
            Ble.State.SCANNING, Ble.State.CONNECTING -> OutlinedButton(onClick = { Ble.disconnect() }, modifier = Modifier.fillMaxWidth()) { Text("İptal") }
            Ble.State.CONNECTED -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Ble.disconnect() }, modifier = Modifier.weight(1f)) { Text("Bağlantıyı kes") }
                TextButton(onClick = { Ble.forget() }) { Text("Unut", color = MUTED) }
            }
        }
    }
}

@Composable
private fun BatteryLabel(s: DeviceStatus) {
    val (text, color) = when {
        s.usb -> "⚡ %${s.battery}" to Color(0xFFFFD43B)
        s.battery > 40 -> "%${s.battery}" to GOOD
        s.battery > 15 -> "%${s.battery}" to Color(0xFFFFD43B)
        else -> "%${s.battery}" to Color(0xFFFF6B6B)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text("Pil", color = MUTED, fontSize = 12.sp)
        Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

// ======================= MÜZİK =======================
@Composable
private fun MusicTab() {
    MusicSection()
    CoverSection()
    Section("Ritim") { BeatBlock() }
}

@Composable
private fun CoverSection() {
    val cover by CoverArt.preview.collectAsState()
    val mode by AppSettings.coverMode.collectAsState()
    val secs by AppSettings.coverSecs.collectAsState()
    Section("Albüm kapağı") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).background(BTN),
                contentAlignment = Alignment.Center,
            ) {
                val b = cover
                if (b != null) Image(b.asImageBitmap(), contentDescription = "Albüm kapağı", modifier = Modifier.fillMaxSize())
                else Text("♪", color = MUTED, fontSize = 28.sp)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                if (cover != null) "Gözler kapanıp çizgi olur, çizgi açılıp kapağa dönüşür. Altta şarkı adı ve ilerleme çubuğu görünür."
                else "Çalan şarkının kapağı burada görünür ve anahtarlığa gider.",
                color = MUTED, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
        }
        GridRows(listOf("Kapalı", "Şarkı değişince", "Çalarken hep", "Tam ekran").withIndex().toList(), 2) { (i, label) ->
            val sel = mode == i
            Button(
                onClick = { AppSettings.setCoverMode(i); CoverArt.onModeChanged() },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sel) Color(0xFF1ED760) else BTN,
                    contentColor = if (sel) Color.Black else Color.White,
                ),
            ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
        if (mode == CoverArt.MODE_FULL) {
            Text(
                "Müzik çalarken kapak bütün ekranı kaplar; pil, şarkı adı ve ilerleme çubuğu gizlenir. " +
                    "Kapak biraz daha büyük olduğu için anahtarlığa gelmesi 2-3 saniye sürer.",
                color = MUTED, fontSize = 12.sp,
            )
            val fw = Ble.status.collectAsState().value?.fwVersion ?: 0
            if (fw in 1 until 13) Text("Tam ekran için anahtarlığa yazılım v13 yüklenmeli (şu an v$fw).", color = Color(0xFFFFD43B), fontSize = 12.sp)
        }
        if (mode == 1) Stepper("Ekranda kalma", secs, 3..60, 1, "sn") { AppSettings.setCoverSecs(it) }
        if (mode < 2) {
            OutlinedButton(onClick = { CoverArt.showNow() }, modifier = Modifier.fillMaxWidth()) {
                Text("Şimdi göster", color = Color.White)
            }
        }
        Text("Kapak açıkken anahtarlığın düğmesine kısa basınca gözlere döner. Bildirim gelince de gözler döner.", color = MUTED, fontSize = 12.sp)
    }
}

// ======================= TASARLA =======================
@Composable
private fun DesignerTab(state: Ble.State) {
    val p by EyeDesign.params.collectAsState()
    val live by EyeDesign.live.collectAsState()
    val col = p.color

    Card(colors = CardDefaults.cardColors(containerColor = CARD), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EyesCanvas(p, Modifier.clip(RoundedCornerShape(16.dp)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Canlı gönder", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (live) "Kaydırdıkça anahtarlıkta da değişir" else "Bitince \"Gönder\"e bas",
                        color = MUTED, fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = live,
                    onCheckedChange = { EyeDesign.setLive(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = col),
                )
            }
            Button(
                onClick = { EyeDesign.sendNow() },
                enabled = state == Ble.State.CONNECTED,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = col, contentColor = Color.Black),
            ) { Text("Anahtarlığa gönder (Özel mod)", fontWeight = FontWeight.Bold) }
        }
    }

    Section("Hazır şekiller") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (SHAPE_PRESETS + MODES.take(6).map { it.name to it.eye }).forEach { (name, shape) ->
                Chip(name) { EyeDesign.update(shape.copy(rgb = p.rgb)) }
            }
        }
    }

    Section("Renk") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            COLOR_PRESETS.forEach { c ->
                val selected = c == p.rgb
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(Color(0xFF000000.toInt() or c))
                        .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape)
                        .clickable { EyeDesign.update(p.copy(rgb = c)) }
                )
            }
        }
        ColorSlider("Kırmızı", (p.rgb shr 16) and 0xFF, Color(0xFFFF5050)) { EyeDesign.update(p.copy(rgb = (p.rgb and 0x00FFFF) or (it shl 16))) }
        ColorSlider("Yeşil", (p.rgb shr 8) and 0xFF, Color(0xFF50FF70)) { EyeDesign.update(p.copy(rgb = (p.rgb and 0xFF00FF) or (it shl 8))) }
        ColorSlider("Mavi", p.rgb and 0xFF, Color(0xFF5090FF)) { EyeDesign.update(p.copy(rgb = (p.rgb and 0xFFFF00) or it)) }
    }

    Section("Boyut") {
        ParamSlider("Genişlik", p.w, 20..110, col) { EyeDesign.update(p.copy(w = it)) }
        ParamSlider("Yükseklik", p.h, 20..150, col) { EyeDesign.update(p.copy(h = it)) }
        ParamSlider("Köşe yuvarlaklığı", p.r, 2..60, col) { EyeDesign.update(p.copy(r = it)) }
        ParamSlider("Gözler arası", p.gap, 0..80, col) { EyeDesign.update(p.copy(gap = it)) }
    }

    Section("İfade (göz kapakları)") {
        ParamSlider("Uykulu (üst kapak)", p.tired, 0..100, col, "%") { EyeDesign.update(p.copy(tired = it)) }
        ParamSlider("Kızgın (içten eğik)", p.angry, 0..100, col, "%") { EyeDesign.update(p.copy(angry = it)) }
        ParamSlider("Üzgün (dıştan eğik)", p.sad, 0..100, col, "%") { EyeDesign.update(p.copy(sad = it)) }
        ParamSlider("Mutlu (alttan yay ^ ^)", p.happy, 0..60, col, "%") { EyeDesign.update(p.copy(happy = it)) }
        ParamSlider("Alt kapak", p.bottom, 0..100, col, "%") { EyeDesign.update(p.copy(bottom = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { EyeDesign.update(EyeParams(rgb = p.rgb)) }) { Text("Şekli sıfırla", color = MUTED) }
        }
    }
    Text(
        "Gönderince anahtarlık \"Özel\" moda geçer ve bu şekli kapatıp açsan da hatırlar. " +
            "Göz kırpma, etrafa bakma ve duygular bu şekille çalışmaya devam eder.",
        color = MUTED, fontSize = 12.sp,
    )
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(BTN).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) { Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ParamSlider(label: String, value: Int, range: IntRange, color: Color, unit: String = "", onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (unit == "%") "%$value" else "$value", color = MUTED, fontSize = 13.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = BTN),
        )
    }
}

@Composable
private fun ColorSlider(label: String, value: Int, color: Color, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MUTED, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = BTN),
        )
    }
}

// ======================= ARAÇLAR =======================
@Composable
private fun ToolsTab(status: DeviceStatus?) {
    PomodoroSection()
    ClockSection()
    NotifySection()
    UpdateSection(status)
    TextTestSection()
}

@Composable
private fun UpdateSection(status: DeviceStatus?) {
    val ctx = LocalContext.current
    val ui by Ota.ui.collectAsState()
    val state by Ble.state.collectAsState()
    val busy = ui.phase in setOf(Ota.Phase.STARTING, Ota.Phase.SENDING, Ota.Phase.FINISHING)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Ota.load(ctx, uri)
    }
    val view = LocalView.current
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }
    val appVer = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val fw = status?.fwVersion ?: 0

    Section("Kablosuz güncelleme") {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Anahtarlık yazılımı", color = MUTED, fontSize = 12.sp)
                Text(
                    when {
                        status == null -> "—"
                        fw == 0 -> "eski (kablosuzsuz)"
                        else -> "v$fw"
                    },
                    color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Uygulama", color = MUTED, fontSize = 12.sp)
                Text("v$appVer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (status != null && fw in 1 until Ota.MIN_FW || status != null && fw == 0) {
            Text(
                "Kablosuz güncelleme için yeni yazılımı bir kez USB ile yüklemen gerek. Sonraki her güncelleme buradan.",
                color = Color(0xFFFFD43B), fontSize = 12.sp,
            )
        }

        if (busy) {
            LinearProgressIndicator(
                progress = { ui.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = ACCENT, trackColor = BTN,
            )
            Text(ui.message, color = Color.White, fontSize = 13.sp)
            Text("Uygulamayı kapatma, telefonu anahtarlığa yakın tut. Ekran açık kalacak.", color = MUTED, fontSize = 12.sp)
            if (ui.phase != Ota.Phase.FINISHING) {
                OutlinedButton(onClick = { Ota.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("İptal", color = Color.White) }
            }
        } else {
            if (ui.fileName.isNotEmpty()) {
                Text("${ui.fileName}  •  ${ui.fileSize / 1024} KB", color = Color.White, fontSize = 13.sp)
            }
            if (ui.message.isNotEmpty() && ui.phase != Ota.Phase.READY) {
                Text(
                    ui.message,
                    color = when (ui.phase) { Ota.Phase.DONE -> GOOD; Ota.Phase.ERROR -> Color(0xFFFF8A8A); else -> MUTED },
                    fontSize = 13.sp,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/octet-stream", "application/macbinary", "*/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text(if (ui.phase == Ota.Phase.READY) "Başka dosya" else "Dosya seç (.bin)", color = Color.White) }
                if (ui.phase == Ota.Phase.READY) {
                    Button(
                        onClick = { Ota.start() },
                        enabled = state == Ble.State.CONNECTED,
                        modifier = Modifier.weight(1f),
                    ) { Text("Güncelle", fontWeight = FontWeight.Bold) }
                }
            }
            Text(
                "Dosyayı hazırlamak: Arduino IDE'de Sketch > Export Compiled Binary. Sketch klasöründe build/... altında " +
                    "\"eyes_keychain.ino.bin\" çıkar (\"merged\" yazanı değil). Onu telefona at (Drive, WhatsApp'ta kendine, kablo) ve burada seç. " +
                    "Yaklaşık 2-4 dakika sürer; yarıda kalırsa anahtarlık eski yazılımla açılır.",
                color = MUTED, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun TextTestSection() {
    var txt by remember { mutableStateOf("") }
    Section("Yazı testi") {
        OutlinedTextField(
            value = txt,
            onValueChange = { txt = it },
            label = { Text("Ekranın altında görünecek yazı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { Ble.send(Protocol.text(DisplayText.prepare(txt) ?: txt)) }, modifier = Modifier.weight(1f)) { Text("Alt yazı") }
            Button(onClick = { Ble.send(Protocol.notification(DisplayText.prepare(txt) ?: txt)) }, modifier = Modifier.weight(1f)) { Text("Bildirim") }
            OutlinedButton(onClick = { Ble.send(Protocol.clearText()) }, modifier = Modifier.weight(1f)) { Text("Sil") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CARD), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(java.util.Locale("tr")), color = MUTED, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable
private fun <T> GridRows(items: List<T>, columns: Int, cell: @Composable RowScope.(T) -> Unit) {
    items.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { cell(it) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MusicSection() {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(MusicListenerService.isEnabled(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            val e = MusicListenerService.isEnabled(ctx)
            if (e && !enabled) MusicListenerService.ensureRunning(ctx)   // izin yeni açıldı
            enabled = e
            delay(1500)
        }
    }
    val track by MusicTracker.track.collectAsState()
    val playing by MusicTracker.playing.collectAsState()
    val lyricsInfo by MusicTracker.lyricsInfo.collectAsState()
    val line by MusicTracker.currentLine.collectAsState()
    val showTitle by AppSettings.showTitle.collectAsState()
    val showLyrics by AppSettings.showLyrics.collectAsState()
    val autoRock by AppSettings.autoRock.collectAsState()
    val latinize by AppSettings.latinize.collectAsState()

    Section("Müzik") {
        if (!enabled) {
            Text(
                "Çalan şarkıyı görebilmem için bildirim erişimi gerekiyor. Açılan listede \"Göz müzik\"i aç.",
                color = Color.White, fontSize = 14.sp,
            )
            Text(
                "\"Kısıtlanmış ayar\" uyarısı çıkarsa: Ayarlar > Uygulamalar > Göz > sağ üstteki ⋮ > " +
                    "\"Kısıtlanmış ayarlara izin ver\", sonra tekrar dene.",
                color = MUTED, fontSize = 12.sp,
            )
            Button(
                onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Bildirim erişimini aç") }
        } else {
            val t = track
            if (t == null) {
                Text("Şu an çalan bir şey yok", color = MUTED, fontSize = 14.sp)
            } else {
                Text(t.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (t.artist.isNotBlank()) Text(t.artist, color = MUTED, fontSize = 14.sp, maxLines = 1)
                Text(if (playing) "▶ Çalıyor" else "⏸ Durdu", color = if (playing) Color(0xFF5BE38A) else MUTED, fontSize = 13.sp)
                if (lyricsInfo.isNotEmpty()) Text(lyricsInfo, color = MUTED, fontSize = 12.sp)
                if (line.isNotBlank()) Text("“$line”", color = Color(0xFFFF8AD8), fontSize = 14.sp, maxLines = 2)
            }
        }
        SettingRow("Şarkı adını göster", showTitle) { AppSettings.setShowTitle(it); MusicTracker.resendAll() }
        SettingRow("Zamanlı sözleri göster", showLyrics) { AppSettings.setShowLyrics(it); MusicTracker.resendAll() }
        SettingRow("Müzik çalınca Rock moduna geç", autoRock) { AppSettings.setAutoRock(it); MusicTracker.resendAll() }
        SettingRow("Farklı alfabeleri Latin harfe çevir (Japonca, Rusça...)", latinize) { AppSettings.setLatinize(it); MusicTracker.resendAll() }
        Text(
            if (latinize) "Örn. Привет → Privet, ありがとう → arigatou. Japonca kanji'ler Çince okunuşla çevrilir, tam doğru olmayabilir."
            else "Kapalıyken farklı alfabedeki sözler yerine şarkı adı (o da uygun değilse ♪) gösterilir.",
            color = MUTED, fontSize = 12.sp,
        )
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF00E6FF)),
        )
    }
}

@Composable
private fun BeatBlock() {
    val bpm by BeatSync.bpm.collectAsState()
    val source by BeatSync.source.collectAsState()
    val taps by BeatSync.tapCount.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ritim (Rock modu)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    (if (bpm > 0f) "${bpm.toInt()} BPM" else "120 BPM (varsayılan)") + if (source.isNotEmpty()) "  •  $source" else "",
                    color = MUTED, fontSize = 12.sp,
                )
            }
            TextButton(onClick = { BeatSync.reset() }) { Text("Sıfırla", color = MUTED) }
        }
        Button(
            onClick = { BeatSync.tap(MusicTracker.positionOrNull()) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3CC8), contentColor = Color.Black),
        ) {
            Text(
                if (taps in 1..3) "Devam et... ($taps/4)" else if (taps >= 4) "Ayarlandı ✓ (dokunmaya devam edebilirsin)" else "Ritme dokun",
                fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
        }
        Text("Şarkı çalarken vuruşlarla birlikte en az 4 kez dokun. Şarkı başına hatırlanır.", color = MUTED, fontSize = 12.sp)
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int, unit: String = "dk", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceIn(range)) }) { Text("−", color = Color.White) }
        Text("$value $unit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = { onChange((value + step).coerceIn(range)) }) { Text("+", color = Color.White) }
    }
}

@Composable
private fun PomodoroSection() {
    val work by AppSettings.workMin.collectAsState()
    val brk by AppSettings.breakMin.collectAsState()
    var running by remember { mutableStateOf(false) }
    Section("Pomodoro") {
        Stepper("Çalışma", work, 5..90, 5) { AppSettings.setWorkMin(it) }
        Stepper("Mola", brk, 1..30, 1) { AppSettings.setBreakMin(it) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { Ble.send(Protocol.pomodoro(work, brk)); running = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFAA28), contentColor = Color.Black),
            ) { Text("Başlat", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = { Ble.send(Protocol.stopPomodoro()); running = false }, modifier = Modifier.weight(1f)) {
                Text("Durdur", color = Color.White)
            }
        }
        Text(
            if (running) "Çalışıyor: gözler Odak modunda, geri sayım anahtarlığın ekranında. Süre bitince mola, sonra yeniden çalışma."
            else "Çalışmada gözler Odak, molada Relax moduna geçer.",
            color = MUTED, fontSize = 12.sp,
        )
    }
}

@Composable
private fun ClockSection() {
    val ctx = LocalContext.current
    val weather by ClockWeather.weather.collectAsState()
    val info by ClockWeather.info.collectAsState()
    var locGranted by remember { mutableStateOf(ClockWeather.hasLocationPermission(ctx)) }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        locGranted = ok
        if (ok) ClockWeather.refreshWeather()
    }
    val clockOn by AppSettings.clockOn.collectAsState()
    Section("Saat ve hava durumu") {
        SettingRow("Saat modu açık", clockOn) { AppSettings.setClockOn(it); DisplayLight.sendPersonality() }
        Text(
            if (clockOn) "Saat modunda (mod listesindeki \"Saat\") gözler küçülür, altta saat ve hava görünür. Kapatırsan Saat modu listeden kalkar; şu an saat gösteriliyorsa önceki moda dönülür."
            else "Saat modu kapalı: mod listesinde görünmez ve anahtarlıkta seçilemez.",
            color = MUTED, fontSize = 12.sp,
        )
        if (clockOn) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { Ble.send(Protocol.mode(6)); ClockWeather.sendTime() }, modifier = Modifier.weight(1f)) { Text("Saat moduna geç") }
            OutlinedButton(onClick = { ClockWeather.sendTime() }, modifier = Modifier.weight(1f)) { Text("Saati gönder", color = Color.White) }
        }
        if (!locGranted) {
            Button(
                onClick = { locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Hava durumu için konum izni ver") }
            Text("Sadece yaklaşık konum kullanılır, ücretsiz Open-Meteo servisine gönderilir.", color = MUTED, fontSize = 12.sp)
        } else {
            val w = weather
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (w != null) "${w.tempC}°C  •  ${w.label}" else "Hava durumu bekleniyor", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { ClockWeather.refreshWeather() }) { Text("Yenile", color = MUTED) }
            }
        }
        if (info.isNotEmpty()) Text(info, color = MUTED, fontSize = 12.sp)
    }
}

@Composable
private fun NotifySection() {
    val on by AppSettings.notify.collectAsState()
    val content by AppSettings.notifyContent.collectAsState()
    val all by AppSettings.notifyAllApps.collectAsState()
    Section("Bildirimler") {
        SettingRow("Mesaj ve aramalarda tepki ver", on) { AppSettings.setNotify(it) }
        SettingRow("Mesaj içeriğini de göster", content) { AppSettings.setNotifyContent(it) }
        SettingRow("Tüm uygulamaların bildirimleri", all) { AppSettings.setNotifyAllApps(it) }
        Text(
            if (content) "Dikkat: mesajın kendisi anahtarlığın ekranında kayar, yanındakiler okuyabilir."
            else "Sadece kimden geldiği görünür (örn. \"Ayşe (WhatsApp)\"). Müzik bölümündeki bildirim erişimi gerekir.",
            color = MUTED, fontSize = 12.sp,
        )
    }
}
