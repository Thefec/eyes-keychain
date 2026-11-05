package com.eyeskeychain.app

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import com.eyeskeychain.app.ble.Ble
import com.eyeskeychain.app.ble.Protocol

/**
 * Mesaj ya da arama bildirimi gelince anahtarlıkta "şaşırma + kimden" gösterir.
 * Varsayılan: sadece mesajlaşma uygulamaları ve aramalar, mesaj içeriği GİZLİ.
 */
object NotificationReactor {
    private val MESSAGING = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thunderdog.challegram",
        "com.instagram.android", "com.discord", "com.facebook.orca", "com.snapchat.android",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.twitter.android", "com.viber.voip", "com.skype.raider", "com.microsoft.teams",
        "com.google.android.gm", "com.bip.im", "tr.com.turkcell.bip",
    )
    private var lastKey = ""
    private var lastTime = 0L

    fun handle(ctx: Context, sbn: StatusBarNotification) {
        if (!AppSettings.notify.value) return
        if (sbn.packageName == ctx.packageName) return
        val n = sbn.notification ?: return
        if (sbn.isOngoing || (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val cat = n.category
        if (cat in setOf(Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS,
                Notification.CATEGORY_SERVICE, Notification.CATEGORY_SYSTEM)) return
        val isCall = cat == Notification.CATEGORY_CALL
        val isMsg = cat == Notification.CATEGORY_MESSAGE || sbn.packageName in MESSAGING
        if (!isCall && !isMsg && !AppSettings.notifyAllApps.value) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val app = runCatching {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault("Bildirim")

        // aynı bildirim tekrar tekrar güncellenirse (yazıyor..., sayaç) spam olmasın
        val key = "${sbn.packageName}|$title|$text"
        val now = SystemClock.elapsedRealtime()
        if (key == lastKey && now - lastTime < 10_000) return
        if (now - lastTime < 2500) return
        lastKey = key
        lastTime = now

        val who = title.ifBlank { app }
        val msg = when {
            isCall -> "Arıyor: $who"
            AppSettings.notifyContent.value && text.isNotBlank() -> "$who: $text"
            title.isNotBlank() && title != app -> "$who ($app)"
            else -> app
        }
        val shown = DisplayText.prepare(msg) ?: DisplayText.prepare(app) ?: "Yeni bildirim"
        Ble.send(Protocol.notification(shown))
    }
}
