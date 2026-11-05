package com.eyeskeychain.app.music

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.eyeskeychain.app.ClockWeather
import com.eyeskeychain.app.DisplayLight
import com.eyeskeychain.app.NotificationReactor
import androidx.core.app.NotificationManagerCompat
import com.eyeskeychain.app.AppSettings
import com.eyeskeychain.app.ble.Ble
import com.eyeskeychain.app.hasPermissions

/**
 * Bildirim erişimi açıldığında sistem bu servisi ayakta tutar.
 * Böylece uygulama kapalıyken de çalan müziği izleyip anahtarlığa gönderebiliriz.
 *
 * Not: Uygulama üstüne güncelleme kurulunca Android bu servisi çoğu zaman yeniden bağlamıyor.
 * O yüzden uygulama açılınca ensureRunning() hem müzik takibini doğrudan başlatır hem de yeniden bağlanma ister.
 */
class MusicListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        connected = true
        AppSettings.init(applicationContext)
        BeatSync.init(applicationContext)
        Ble.init(applicationContext)
        DisplayLight.start()
        if (hasPermissions(applicationContext) && Ble.isBluetoothOn()) Ble.autoConnect()
        MusicTracker.start(applicationContext)
        ClockWeather.start(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) runCatching { NotificationReactor.handle(applicationContext, sbn) }
    }

    override fun onListenerDisconnected() {
        connected = false
        if (isEnabled(applicationContext)) {
            // izin hâlâ açık: sistem bağlantıyı kopardı (güncelleme vb.), geri bağlanmasını iste; müzik takibi sürsün
            rebind(applicationContext)
        } else {
            MusicTracker.stop()
        }
    }

    companion object {
        @Volatile private var connected = false

        fun isEnabled(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        private fun rebind(ctx: Context) {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(ctx.applicationContext, MusicListenerService::class.java))
            }
        }

        /** Uygulama açılınca: izin varsa müzik takibini başlat, servis bağlı değilse yeniden bağlanmasını iste. */
        fun ensureRunning(ctx: Context) {
            if (!isEnabled(ctx)) return
            val app = ctx.applicationContext
            AppSettings.init(app)
            BeatSync.init(app)
            Ble.init(app)
            MusicTracker.start(app)
            if (!connected) rebind(app)
        }
    }
}
