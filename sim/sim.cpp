#include "full.cpp"   // önce: python3 mkproto.py ../firmware/eyes_keychain/eyes_keychain.ino full.cpp
#include <vector>
#include <functional>
extern int g_jpgDecodes;
int fails = 0;
#define CHECK(c, msg) do { if (c) printf("  OK   %s\n", msg); else { printf("  HATA %s\n", msg); fails++; } } while (0)

void send(std::vector<uint8_t> b) { Cmd c; c.len = b.size(); memcpy(c.data, b.data(), b.size()); handleCmd(c); }
// simüle zaman: loop() kendi delay(1)'i ile ilerler; çizim olursa 1 ms ekle
void runFor(uint32_t ms, std::function<void()> each = nullptr) {
  uint32_t end = g_millis + ms;
  while ((int32_t)(g_millis - end) < 0) { uint32_t t = g_millis; loop(); if (each) each(); if (g_millis == t) g_millis++; }
}
void press(uint32_t holdMs) { g_button = 0; runFor(holdMs); g_button = 1; runFor(50); }
void connect() { bleConnected = true; bleLostAt = 0; }
void disconnect() { bleConnected = false; bleLostAt = g_millis ? g_millis : 1; }

void boot() {
  // her senaryo için temiz başlangıç: global durumu sıfırlamak yerine kritik olanları ayarla
  g_millis = 1000; g_vbat = 3.95f; g_button = 1;
  setup();
}

int main() {
  srand(1);
  boot();
  printf("\n[1] Açılış: ışık 0'dan yumuşakça açılmalı\n");
  CHECK(blCur < 1, "ilk anda ışık kapalı");
  runFor(150); float b150 = blCur;
  runFor(1500);
  CHECK(b150 > 20 && b150 < 190, "150 ms'de yarı yolda (titreşimsiz geçiş)");
  CHECK(fabsf(blCur - brightness) < 0.5f, "1.6 sn'de tam parlaklık");

  printf("\n[2] Otomatik kararma (60 sn)\n");
  runFor(59000 - 1650);
  CHECK(!isDimmed, "59. saniyede hâlâ parlak");
  runFor(2500);
  CHECK(isDimmed, "61. saniyede karardı");
  runFor(3000);
  CHECK(fabsf(blCur - brightness * 0.25f) < 1, "kararınca %25'e indi");
  uint8_t m0 = mode;
  press(80);
  CHECK(mode == m0, "kararmışken kısa basış modu DEĞİŞTİRMEDİ (sadece uyandırdı)");
  runFor(400);
  CHECK(!isDimmed && blCur > brightness * 0.9f, "basınca hızla aydınlandı");
  press(80);
  CHECK(mode == (m0 + 1) % MODE_COUNT, "uyanıkken kısa basış mod değiştirdi");

  printf("\n[3] Telefondan gelen şeyler\n");
  runFor(62000); CHECK(isDimmed, "tekrar karardı");
  send({0x05, 0xB0, 0x04, 0x10, 0x00}); runFor(100);
  CHECK(isDimmed, "ritim verisi ekranı aydınlatmadı");
  send({0x45, 10, 0, 200, 0, 1}); runFor(100);
  CHECK(isDimmed, "ilerleme verisi aydınlatmadı");
  send({0x07, 'A', 'y', 's', 'e'}); runFor(100);
  CHECK(!isDimmed, "bildirim aydınlattı");
  send({0x08, 40}); runFor(700);
  printf("       blCur=%.2f dim=%d asleep=%d bat=%d usb=%d\n", blCur, isDimmed, isAsleep, batPct, onUSB);
  CHECK(brightness == 40 && fabsf(blCur - 40) < 1, "parlaklık 40'a kaydı");
  send({0x08, 0}); CHECK(brightness == 4, "parlaklık 0 gelirse en az 4 (ekran hiç sönmez)");
  send({0x08, 200}); runFor(1600);
  CHECK(saveAt == 0, "ayar 1.5 sn sonra flash'a yazıldı (kaydırırken her adımda değil)");

  printf("\n[4] USB'de kararmaz\n");
  onUSB = true; charging = true; lastActivity = g_millis - 200000; runFor(100);
  CHECK(!isDimmed, "şarjdayken kararmadı");
  charging = false; g_vbat = 3.95f; vFilt = 3.95f; onUSB = false; runFor(2500);
  CHECK(!onUSB && isDimmed, "fiş çekilince karardı");

  printf("\n[5] Gece uykusu 23:00-07:00\n");
  noteActivity();
  // 0x0E: 60 sn, %25, gece açık, 23-7, ışık 6
  send({0x0E, 60, 0, 25, 1, 23, 7, 6});
  // saat 22:59:50 (UTC+3)
  uint32_t ep = 1762300000; ep -= ep % 86400; ep += 19 * 3600 + 59 * 60 + 50;   // UTC 19:59:50 = yerel 22:59:50
  send({0x0B, (uint8_t)ep, (uint8_t)(ep >> 8), (uint8_t)(ep >> 16), (uint8_t)(ep >> 24), 180, 0});
  runFor(5000); CHECK(!isAsleep, "22:59:55 uyanık");
  runFor(10000); CHECK(isAsleep, "23:00:05 uyudu");
  runFor(4000);
  CHECK(sleepK > 0.95f, "gözler kapandı");
  CHECK(blCur <= 6.5f, "ışık gece seviyesine indi");
  send({0x07, 'G', 'e', 'c', 'e'}); runFor(500);
  CHECK(isAsleep, "gece bildirimi uyandırmadı");
  uint8_t m1 = mode; press(80); runFor(300);
  CHECK(!isAsleep && mode == m1, "gece basış uyandırdı, mod değişmedi");
  runFor(15000); press(80); runFor(10000);
  CHECK(!isAsleep, "ikinci basış süreyi uzattı (25 sn sonra hâlâ uyanık)");
  runFor(12000); CHECK(isAsleep, "20 sn dokunulmayınca tekrar uyudu");
  // sabah 07:00
  timeLocal += 8 * 3600; runFor(2000);
  CHECK(!isAsleep, "07:00'de uyandı");
  send({0x0E, 60, 0, 25, 0, 23, 7, 6});

  printf("\n[6] Müzik / otomatik Rock / pomodoro\n");
  setMode(0, false); connect();
  send({0x09, 1, 1}); runFor(3000); CHECK(mode == 0, "müzik başladı, 3. sn hâlâ Normal");
  runFor(1500); CHECK(mode == 5, "4 sn sonra Rock");
  send({0x0A, 25, 5}); runFor(100); CHECK(mode == 4, "pomodoro: Odak");
  runFor(10000); CHECK(mode == 4, "müzik çalarken pomodoro Odak'ı BOZULMADI");
  send({0x0A, 0}); runFor(200); CHECK(mode == 5, "pomodoro durunca müzik sürüyor: Rock'a döndü");
  send({0x09, 0, 1}); runFor(200); CHECK(mode == 0, "müzik durdu: Normal'e döndü");
  send({0x09, 1, 1}); runFor(5000); CHECK(mode == 5, "yine Rock");
  send({0x09, 1, 0}); runFor(200); CHECK(mode == 0, "otomatik Rock kapatılınca Normal'e döndü");
  setMode(5, true); send({0x09, 0, 1}); runFor(200); CHECK(mode == 5, "elle seçilen Rock, müzik durunca korunur");
  setMode(0, false);

  printf("\n[7] Telefon kaybolursa (APK güncellemesi / uzaklaşma)\n");
  send({0x09, 1, 1}); send({0x43, 1});
  send({0x03, 'S', 'o', 'z'});
  runFor(6000); disconnect();
  runFor(10000); CHECK(musicPlaying, "10 sn kopukluk: henüz dokunulmadı (kısa kopmada titremesin)");
  runFor(11000);
  CHECK(!musicPlaying && !coverWant && !songText.active && mode == 0, "20 sn sonra müzik, kapak, söz temizlendi, mod geri döndü");
  connect();

  printf("\n[8] Kapak: JPEG sadece bir kez açılmalı\n");
  { std::vector<uint8_t> j(3000, 0x11); j[0] = 0xFF; j[1] = 0xD8;
    send({0x40, (uint8_t)(j.size() & 0xFF), (uint8_t)(j.size() >> 8)});
    for (size_t o = 0; o < j.size(); o += 180) { std::vector<uint8_t> p{0x41}; p.insert(p.end(), j.begin() + o, j.begin() + std::min(j.size(), o + 180)); send(p); }
    send({0x42}); }
  g_jpgDecodes = 0; coverUserHide = false;
  send({0x43, 8}); runFor(2500);
  CHECK(coverT >= 1, "kapak açıldı");
  CHECK(g_jpgDecodes == 1, "açılış animasyonunda JPEG 1 kez açıldı (eskiden her karede)");
  printf("       (açılma sayısı: %d)\n", g_jpgDecodes);
  runFor(8000); CHECK(coverT == 0, "8 sn sonra gözlere döndü");

  printf("\n[8b] Tam ekran kapak (240x240, her şey gizli)\n");
  { // gerçekçi başlık: FFD8, FFE0 APP0 (16 bayt), FFC0 SOF0 240x240
    std::vector<uint8_t> j = {0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
                              0xFF, 0xC0, 0x00, 0x11, 0x08, 0x00, 0xF0, 0x00, 0xF0, 0x03};
    j.resize(20000, 0x22);
    int w = 0, h = 0; CHECK(jpgSize(j.data(), j.size(), w, h) && w == 240 && h == 240, "JPEG başlığından 240x240 okundu");
    send({0x46, 1});
    send({0x40, (uint8_t)(j.size() & 0xFF), (uint8_t)(j.size() >> 8)});
    for (size_t o = 0; o < j.size(); o += 180) { std::vector<uint8_t> p{0x41}; p.insert(p.end(), j.begin() + o, j.begin() + std::min(j.size(), o + 180)); send(p); }
    send({0x42});
    CHECK(coverLen == 20000, "20 KB kapak kabul edildi");
  }
  noteActivity(); g_jpgDecodes = 0; coverUserHide = false;
  send({0x43, 1}); runFor(2500);
  CHECK(coverT >= 1 && coverOnTop && coverFullDrawn, "tam ekran kapak açıldı, durum çubuğunun yeri kapakta");
  CHECK(g_jpgDecodes == 2, "JPEG sadece 2 kez açıldı (alt tampon + üst şerit)");
  statusDirty = true; g_pushes = 0; runFor(5000);
  CHECK(g_pushes == 0, "durağan kapak: 5 sn boyunca ekrana hiç basılmadı (pil)");
  CHECK(statusDirty, "pil değişse de durum çubuğu kapağın üstüne çizilmedi");
  send({0x07, 'S', 'e', 'l', 'a', 'm'}); runFor(1500);
  CHECK(coverT < 0.45f && !coverOnTop && !statusDirty, "bildirim: gözlere döndü, durum çubuğu geri geldi");
  runFor(5000);
  CHECK(coverT >= 1 && coverFullDrawn, "bildirim bitince tam ekran kapak geri geldi");
  press(80); runFor(1500);
  CHECK(coverT == 0 && !coverWant && !coverOnTop, "düğme: kapak kapandı");
  send({0x46, 0});

  printf("\n[9] OTA: araya giren ikinci başlatma\n");
  send({0x20, 0x00, 0x00, 0x10, 0x00}); CHECK(otaActive, "güncelleme başladı");
  send({0x20, 0x00, 0x00, 0x10, 0x00}); CHECK(otaActive && otaState == 1, "ikinci başlat isteği eskisini temizleyip yeniden başladı");
  runFor(9000); CHECK(!otaActive && otaState == 0, "8 sn veri gelmeyince iptal");

  printf("\n[9b] Saat modu aç/kapa ve yeni göz hareketleri\n");
  boot(); runFor(2500);
  send({0x01, 2}); CHECK(mode == 2, "mod 2 seçildi");
  send({0x01, 6}); CHECK(mode == MODE_CLOCK && prevModeBeforeClock == 2, "saat modu açıkken seçilebiliyor");
  send({0x47, 0, 2}); CHECK(!clockOn && mode == 2, "saat kapatılınca önceki moda (2) döndü");
  send({0x01, 6}); CHECK(mode == 2, "kapalıyken saat modu seçilemiyor");
  send({0x36}); CHECK(mode == 2, "kapalıyken '6' komutu da seçemiyor");
  setMode(MODE_CLOCK, true); CHECK(mode == 2, "dahili istek (Rock/pomodoro dönüşü) saat yerine önceki moda düştü");
  send({0x47, 1, 2}); send({0x01, 6}); CHECK(clockOn && mode == MODE_CLOCK, "tekrar açılınca saat modu çalışıyor");
  send({0x47, 0, 2}); runFor(2000); CHECK(mode != MODE_CLOCK, "tekrar kapandı");
  send({0x47, 1, 9}); CHECK(livelyLvl == 3, "canlılık 3'e sınırlandı");
  send({0x47, 1, 2});
  runFor(2000);                                  // bayrak flash'a yazılsın
  {
    int bad = 0;
    for (int id = 1; id < I_COUNT; id++) {
      send({0x48, (uint8_t)id}); bool started = idle == (Idle)id;
      bool ok = started;
      for (int k = 0; k < 400; k++) {            // ~4 sn
        runFor(10);
        if (!(fabsf(lookTgtX) <= 1.2f && fabsf(lookTgtY) <= 1.2f)) { if (ok) printf("    [%d] bakis sinir disi x=%.2f y=%.2f idle=%d t=%u\n", id, lookTgtX, lookTgtY, (int)idle, (unsigned)(g_millis - idleStart)); ok = false; }
        for (int i = 0; i < S_COUNT; i++) if (!std::isfinite(sp[i].x)) ok = false;
        if (!std::isfinite(lookX.x) || !std::isfinite(lookYs.x)) ok = false;
      }
      if (!ok || idle != I_NONE) { printf("    hareket %d sorunlu (basladi=%d idle=%d)\n", id, (int)started, (int)idle); bad++; }
    }
    CHECK(bad == 0, "15 hareketin hepsi başladı, sınırlar içinde kaldı, sayı bitince sona erdi");
  }
  {
    livelyLvl = 0; int extra0 = 0; for (int i = 0; i < 3000; i++) if (pickIdle() > I_LOOKAROUND) extra0++;
    livelyLvl = 3; int extra3 = 0; for (int i = 0; i < 3000; i++) if (pickIdle() > I_LOOKAROUND) extra3++;
    livelyLvl = 2;
    CHECK(extra0 == 0, "canlılık 0 (sakin): yeni hareket seçilmiyor");
    CHECK(extra3 > 300, "canlılık 3: yeni hareketler seçiliyor");
    int perMode = 1;
    for (int m = 0; m < MODE_COUNT; m++) { mode = m; for (int i = 0; i < 500; i++) { Idle x = pickIdle(); if ((int)x < 0 || x >= I_COUNT) perMode = 0; } }
    mode = 0;
    CHECK(perMode, "her modda seçilen hareket geçerli aralıkta");
  }
  {
    int n = 0; uint32_t t0 = g_millis; uint32_t last = 0;
    for (int k = 0; k < 6000; k++) { runFor(10); if (idle != I_NONE && idleStart != last) { last = idleStart; n++; } }
    CHECK(n >= 4, "kendiliğinden de hareket başlatıyor (60 sn'de en az 4 kez)");
    (void)t0;
  }
  send({0x48, I_YAWN}); runFor(50); isAsleep = true; send({0x48, I_ROLL}); CHECK(idle != I_ROLL, "uykudayken hareket başlatılmıyor"); isAsleep = false;

  printf("\n[9c] Final: düğme, kare hızı, millis() başa sarması\n");
  boot(); runFor(3000);
  send({0x47, 0, 2}); send({0x01, 5}); press(80);
  CHECK(mode == MODE_CUSTOM, "saat kapalıyken düğme Rock'tan Özel'e atlıyor (saati atlıyor)");
  press(80); CHECK(mode == 0, "Özel'den Normal'e dönüyor");
  send({0x47, 1, 2});
  {
    send({0x01, 4}); livelyLvl = 0; runFor(3000);
    uint32_t far = g_millis + 60000;
    nextIdle = far; blink.next = far; nextLook = far; idle = I_NONE;
    runFor(3000);                                // yaylar otursun
    int p0 = g_pushes; runFor(10000); int n = g_pushes - p0;
    printf("    durağan 10 sn: %d kare\n", n);
    CHECK(n >= 180 && n <= 230, "durağan ekranda ~20 fps (eskiden 30): daha az SPI/CPU");
    livelyLvl = 2;
    send({0x48, I_BOUNCE}); p0 = g_pushes; runFor(1000); n = g_pushes - p0;
    printf("    hareket sırasında 1 sn: %d kare\n", n);
    CHECK(n >= 50, "hareket sırasında ~60 fps");
    send({0x01, 4});                              // dokunma: ilerideki yanlış tetiklenmeyi önle
  }
  {
    boot(); runFor(3000);
    g_millis = 0xFFFFFFFFu - 8000;               // 49,7 günlük sınırın 8 sn öncesi
    nextBat = g_millis + 2000; nextIdle = g_millis + 4000; blink.next = g_millis + 3000; nextLook = g_millis + 3000;
    runFor(20000);                               // sınırı geç
    bool batOk = (int32_t)(nextBat - g_millis) >= 0 && (int32_t)(nextBat - g_millis) <= 2000;
    bool blinked = false, idled = false; uint32_t s0 = idleStart;
    for (int k = 0; k < 4000; k++) { runFor(10); if (blink.active) blinked = true; if (idleStart != s0) idled = true; }
    CHECK(g_millis < 100000, "zaman başa sardı");
    CHECK(batOk, "başa sardıktan sonra pil ölçümü sürüyor");
    CHECK(blinked, "başa sardıktan sonra göz kırpma sürüyor");
    CHECK(idled, "başa sardıktan sonra hareketler sürüyor");
  }

  printf("\n[10] Rastgele komut bombardımanı (çökme / taşma arama, ASan açık)\n");
  for (int i = 0; i < 200000; i++) {
    Cmd c; c.len = 1 + rand() % 200;
    for (int k = 0; k < c.len; k++) c.data[k] = rand();
    if (c.data[0] == 0x20 || c.data[0] == 0x22) c.data[0] = 0x03;   // gerçek OTA/yeniden başlatma yolunu atla
    if (rand() % 3 == 0) c.data[0] = (uint8_t[]){0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x0E, 0x0D, 0x0A, 0x03, 0x07, 0x47, 0x48}[rand() % 14];
    handleCmd(c);
    if (i % 500 == 0) runFor(20);
  }
  CHECK(true, "200 bin rastgele komut: çökme yok");

  printf("\nSONUÇ: %s (%d hata)\n", fails ? "HATALAR VAR" : "hepsi geçti", fails);
  return fails != 0;
}
