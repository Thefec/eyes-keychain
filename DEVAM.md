# Göz anahtarlık — devam notu (5 Kasım 2026, FİNAL: firmware v15 / uygulama 1.6)

Animasyonlu robot gözlü anahtarlık. Android uygulamasıyla Bluetooth'tan konuşuyor.
Projenin tamamı bu repoda. Yeni bir Claude oturumuna repoyu (ya da zip'ini) verip bu dosyayı okut.

## Donanım
- ESP32-C3 Super Mini
- 1.3" 240x240 ST7789 ekran, CS pini yok. Pinler: SCL=4, SDA=6, DC=7, RES=10
- BLK (arka ışık) → **GPIO 3** (v12'den beri, PWM ile parlaklık). Eskiden 3V3 düğümüne bağlıydı; kullanıcı kabloyu taşıyacak. BLK hâlâ 3V3'teyse kodda `PIN_BL -1`
- 900 mAh LiPo + TP4056 Type-C şarj kartı + kaydırmalı anahtar
- Pil ölçümü: 2x100K bölücü → GPIO1
- Düğme: BOOT (GPIO9). Kısa basış = mod değiştir (saat modu kapalıysa onu atlar; kapak açıksa kapağı kapatır; ekran kararmış/uyuyorsa sadece uyandırır), uzun basış = ekranı döndür
- Kasa: `case/` klasörü (v5, trimesh + manifold3d ile Python'da üretildi)

## Anahtarlık yazılımı — `firmware/eyes_keychain/eyes_keychain.ino`
- Tek dosya, ~3100 satır, FW_VERSION 15
- Kütüphaneler: LovyanGFX, NimBLE-Arduino 2.x (Update.h ve Preferences ESP32 çekirdeğinde var)
- Arduino ayarları:
  - Partition Scheme: **Default 4MB with spiffs** (kablosuz güncelleme için şart)
  - USB CDC On Boot: Enabled
- Tipler, Arduino'nun otomatik prototip sorunu yüzünden dosyanın en başında tanımlı. Ayrı .h dosyası kullanma.
- Türkçe VLW yazı tipi (Inter 19 px, 174 harf) dosyanın içinde gömülü: `FONT_TR`. Üretici: `firmware/font/make_vlw.py`
- Çizim: 240x216 sprite (16 bit) + 24 px durum çubuğu (doğrudan LCD'ye çizilir)
- Modlar 0-7: Normal, Relax, Hareketli, Üzgün, Odak, Rock, Saat, Özel
- Seri Monitör test komutları:
  - 0-7: mod
  - r: döndür
  - k: kapak testi
  - e: duygu
  - t: yazı
  - n: bildirim
  - p: müzik
  - b: ritim
  - o: pomodoro
  - s: saat
  - l: parlaklık (255 / 30 / 120 arasında döner)
  - d: kararma testi
  - g: gece uykusu testi (aç/kapat)
  - i: sıradaki göz hareketini oynat
  - c: saat modunu aç/kapat

### Bluetooth komutları (ilk bayt)
**DİKKAT:** 0x30-0x39 ('0'-'9') metin mod komutlarına ayrılmış. Yeni komut oraya konmaz; bu bir kez hataya yol açtı.

| Komut | Anlamı |
|---|---|
| 01 m | Mod |
| 02 r | Döndür |
| 03 metin | Alttaki yazı |
| 04 | Alttaki yazıyı sil |
| 05 bpm×10(LE16) ofs(LE16) | Ritim |
| 06 e sn | Duygu |
| 07 metin | Bildirim |
| 08 b | Parlaklık 4-255 (yumuşak geçiş, gama 2.2) |
| 09 çalıyor otoRock | Müzik durumu |
| 0A çalışma mola / 0A 0 | Pomodoro başlat / durdur |
| 0B epoch(LE32) tz(LE16) | Saat |
| 0C sıcaklık kod | Hava durumu |
| 0D +12 bayt | Özel göz: w h r gap tired angry sad happy bottom R G B |
| 0E kararmaSn(LE16) kararma% geceAçık geceBaş geceBit geceIşık | Ekran ışığı ayarları (v12) |
| 20 boyut(LE32) / 21 veri / 22 bitir / 23 iptal | Kablosuz güncelleme (8 sn veri gelmezse anahtarlık kendisi iptal eder) |
| 40 boyut(LE16) / 41 veri / 42 bitir | Albüm kapağı: 150x150 (tam ekranda 240x240) JPEG, en fazla 24 KB. Boyut 0 = sil. Aynı kapak tekrar gelirse yok sayılır |
| 43 s | Kapak göster: 0 gizle, 1 süresiz, N saniye |
| 44 ad 0x1F sanatçı | Kapak ekranındaki yazılar |
| 45 konum(LE16) süre(LE16) çalıyor | İlerleme çubuğu (saniye) |
| 46 f | Kapak düzeni: 0 normal (150 px), 1 tam ekran (240x240, v13) |
| 47 b l | Saat modu aç/kapa (b bit0) + göz hareketi canlılığı l=0 sakin..3 çok (v14). Kapalıyken mod 6 seçilemez; saatteyken kapanırsa önceki moda döner. Flash'a saveLater ile yazılır |
| 48 id | Göz hareketini hemen oynat (v14). id = enum Idle: 1 meraklı, 2 uyuklama, 3 neşe, 4 şaşırma, 5 göz kırp, 6 etrafa bak, 7 göz devir, 8 yukarı-aşağı, 9 düşünme, 10 sersem, 11 esneme, 12 kısık göz, 13 kalp, 14 titreme, 15 zıplama |

- Durum karakteristiği 8 bayt: mod, yön, pil%, bayraklar (1 = USB/şarj, 2 = çalıyor), FW sürümü, OTA durumu (0 boşta, 1 yazılıyor, 2 hata), parlaklık, ışık bayrakları (1 kararmış, 2 gece uykusu)
- UUID'ler: servis `7a1e0001-5d3c-4b8e-9a53-2f6c9d1e0001`, komut `...0002`, durum `...0003`

### Kapak ekranı nasıl çalışıyor
- JPEG sıkıştırılmış halde RAM'de tutulur, sprite'a **bir kez** açılır.
- Animasyon: gözler ortada çizgi olur, sonra ekrana sadece ortadan büyüyen bir şerit basılır (perde efekti; LCD'de kırpma alanı + pushSprite).
- Her karede JPEG'i esneterek çizmek çok yavaştı ve anahtarlığı kilitliyordu. Tekrar yapma.
- Müzik başlayınca Rock moduna 4 sn gecikmeyle geçer. Kapak açıkken beklenir.

### Göz hareketleri ve saat aç/kapa (v14)
- Boş davranışlar (Idle) 6'dan 15'e çıktı. Eski 6'nın ağırlığı ModePreset.idleW, yeni 9'unki IDLE_X[mod][9] tablosunda. Canlılık 0 iken yeni hareketler seçilmez; aralık 7-16 sn × {2.0, 1.0, 0.6, 0.35}. Varsayılan canlılık 2.
- startIdle() hem rastgele seçimde hem 0x48'de kullanılır. Bakış/şekil/renk/zıplama kodu updateBehavior, composeTargets ve bob hesabında.
- Saat modu (clockOn) kapalıyken setMode(6) saatten önceki moda (yoksa NORMAL'e) düşer (rock'tan dönüş, pomodoro dönüşü vb. için). Uygulamada Saat bölümünde "Saat modu açık" anahtarı, Kontrol sekmesinde "Göz hareketleri" bölümü (canlılık + 15 oynat düğmesi).
- Seri test: i = sıradaki hareketi oynat, c = saat modunu aç/kapa.
- sim.cpp [9b] bu özellikleri sınar (15 kontrol).

### Final düzeltmeleri ve optimizasyon (v15 / 1.6)
- Saat kapalıyken düğmeyle mod değiştirmek Rock'tan Normal'e atlıyordu (Özel atlanıyordu). Artık sadece saati atlıyor.
- Saat kapatılınca kayıtlı mod da düzeltiliyor (yeniden açılışta saatte başlamıyor).
- millis() 49,7 günde bir başa sarınca pil ölçümü, göz kırpma, hareketler, gece uykusu takılabiliyordu. Bütün "zamanı geldi mi" kontrolleri `reached(now, t)` ile (0 = hemen).
- Durağan ekranda kare hızı 30 → 20 fps (SPI + CPU ~%33 az). Hareket başlayınca hemen 60 fps; gecikme en fazla 50 ms. Hareket (idle) sürerken her zaman 60 fps.
- Uygulama: bazı telefonlarda BLE yazma onayı gelmeyince komut kuyruğu kalıcı takılıyordu. 2 sn bekçi eklendi (OTA yolu hariç; onun kendi zaman aşımı var).
- Test: sim.cpp 77 kontrol (düğme, kare hızı, zaman başa sarması dahil), -Wall -Wextra ile çekirdek 2 ve 3'te uyarısız, sim_bat pil senaryoları çalışıyor.
- R8 küçültme bilerek kapalı: cihazda denenmeden açılırsa Bluetooth/bildirim tarafında sessizce bozulabilir.

### Tam ekran kapak (v13)
- Uygulamada kapak modu 3 = "Tam ekran": müzik çalarken hep, 240x240 kapak, durum çubuğu / şarkı adı / ilerleme gizli.
- Uygulama 240x240 JPEG'i ~20 KB'a sıkıştırır (en fazla 22 KB), önce `46 01` gönderir. Mod değişince kapak kaydedilen 240'lık kareden yeniden üretilir.
- Firmware: JPEG başlığından boyutu okur (`jpgSize`), küçük gelirse ortalar. Alt 216 satır ana tampona, üst 24 satır `sprTop` (240x24, 11.5 KB) tampona BİR KEZ açılır (2 çözme).
- Perde ortadan (ekran y=120) boydan boya açılır; gözler ekranın ortasında 236 px'lik çizgiye birleşir.
- Tam açıkken ekrana hiç basılmaz, döngü 10 fps (pil). Durum çubuğu `coverOnTop` iken çizilmez, kapak kapanınca geri gelir.
- Bildirim gelince gözler döner, bitince kapak geri gelir. Düğme kapatır (yeni şarkıya kadar). Gece uykusunda gösterilmez. Otomatik kararma uygulanır (yeni şarkı aydınlatır).
- RAM yetmezse: yeni kapak için eski kapak bırakılır; üst şerit tamponu açılamazsa üst 24 satır en sonda doğrudan LCD'ye çizilir.

### Ekran ışığı (v12)
- LEDC PWM 20 kHz, 10 bit. Çekirdek 2.x ve 3.x ikisi de destekleniyor (`ESP_ARDUINO_VERSION_MAJOR`).
- Açılışta ışık 0'dan doğar (açılış çöpü görünmez). Hedefe kayar: kaydırıcı/uyanma hızlı (0.12 sn), otomatik kararma yavaş (0.6 sn).
- Otomatik kararma: varsayılan 60 sn, %25. USB'deyken ve pomodoro sırasında kararmaz. Pil < %15'te ışık ×0.6.
- Aydınlatan şeyler: düğme, mod/duygu/bildirim/parlaklık/tasarım komutları, kapak göster, yeni şarkı, müzik başlaması, pomodoro geçişi. Ritim, saat, ilerleme, söz satırı aydınlatmaz.
- Gece uykusu: saat telefondan gelmişse, aralıkta gözler kapanır, z z z, ışık gece seviyesine iner, 10 fps. Düğme 20 sn uyandırır (her basış uzatır). Gece bildirimleri uyandırmaz, kapak gösterilmez.
- Ayarlar Preferences'a 1.5 sn gecikmeyle ve sadece değiştiyse yazılır (kaydırıcı ve canlı göz tasarımı flash'ı yıpratmasın).
- Pil bitince: ışık kapanır, pin kilitlenir (gpio_hold), hafif uykuda bekler (derin uyku pinleri bırakıp ışığı açık bırakabiliyordu).

### Telefon kaybolursa (v12)
- 20 sn bağlantı yoksa: müzik çalmıyor sayılır (otomatik Rock'tan çıkılır), kapak kapanır, söz silinir. Uygulama güncellenince/kapanınca ekranda takılı kalıyordu.
- Otomatik Rock artık takip ediliyor (`rockAuto`): sadece kendiliğinden geçildiyse geri dönülür. Pomodoro sırasında Rock'a geçilmez; pomodoro bitince müzik sürüyorsa geçer.

### Pil
- Pilden çalışırken: voltaj tablosu, yüzde sadece azalır.
- Şarj, TP4056'dan voltajın birden ≥0.07 V sıçramasından anlaşılır; şarj sırasındaki yüzde süreye göre tahmin edilir (45 sn'de %1).
- v12: şarj bitişi iki kuralla: 4 sn ani düşüş YA DA 1 dk boyunca şarjdaki tepe voltajın 35 mV altında kalma. Dolu pilde fiş çekilince eskiden hiç anlaşılmıyordu (anahtarlık saatlerce "şarjda" sanıp kararma ve pil koruması kapalı kalıyordu), simülasyonda artık ~5 dk.
- Kullanıcıda şarj yüzdesinin düzgün arttığı doğrulandı (v11).
- Kesin çözüm hâlâ donanım: USB 5V'u bölücüyle bir GPIO'ya okumak.

## Android uygulaması — `app/EyesKeychainApp`
- Paket com.eyeskeychain.app, sürüm 1.6 (versionCode 16)
- Kotlin + Jetpack Compose (Material3), AGP 8.7.3, Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 26
- Sekmeler: Kontrol / Müzik / Tasarla / Araçlar
- Dosyalar:
  - `ble/Ble.kt`: GATT, sıralı yazma kuyruğu, sendBulk, OTA yazma yolu
  - `ble/Protocol.kt`
  - `MainActivity.kt`: tüm ekranlar
  - `EyesView.kt`: canlı göz önizlemesi
  - `EyeDesign.kt`: tasarlayıcı + mod şekilleri
  - `Ota.kt`
  - `music/MusicTracker.kt`, `music/CoverArt.kt`, `music/Lyrics.kt` (LRCLIB), `music/BeatSync.kt` (Deezer BPM + ritme dokunma)
  - `ClockWeather.kt` (Open-Meteo), `NotificationReactor.kt`, `DisplayText.kt` (başka alfabeleri Latin harfe çevirir)
  - `DisplayLight.kt` (v1.3): parlaklık/kararma/gece ayarları, her bağlanışta gönderilir. Kontrol sekmesinde "Ekran ışığı" bölümü
- v1.3 düzeltmeleri:
  - Uygulama üstüne güncelleme kurulunca bildirim servisi yeniden bağlanmıyordu, müzik takibi duruyordu ("Spotify modları sapıtıyor"). Artık `onResume`'da `MusicListenerService.ensureRunning()` takibi doğrudan başlatıyor + `requestRebind`.
  - Bağlantı 2 doğrudan denemeden sonra pes ediyordu; artık arka planda bekliyor (`connectGatt(autoConnect=true)`), anahtarlık yakına gelince kendisi bağlanıyor. Bekleyen bağlantıda "İptal" takılmıyor.
- Derleme: Android SDK 35 + Gradle 8.x, `gradle assembleDebug`. Maven 429 hatası verirse tekrar dene.
- **Derleme: `bash build_apk.sh`** → `Goz.apk`. Java + /opt/gradle hazırsa SDK'yı dl.google.com'dan kendisi kurar (~5 dk), Maven 429'da bekleyip tekrar dener. Bulut oturumunda çalıştı (5 Kasım).
- **İmza:** İmza anahtarı (`app/EyesKeychainApp/app/goz.keystore`) güvenlik için repoda YOK. Anahtar dosyası + `GOZ_KEYSTORE_PASSWORD` ortam değişkeni varsa APK onunla imzalanır; yoksa normal debug anahtarıyla. Anahtar değişirse telefonda eski uygulamayı bir kez kaldırmak gerekir; sahibi anahtar dosyasını yedeklemeli.

## Simülasyon (bulut oturumunda)
- `sim/` klasörü: sahte kütüphane başlıklarıyla .ino'nun tamamı bilgisayarda derlenip çalıştırılıyor.
  - `mkproto.py`: Arduino gibi fonksiyon prototiplerini üretir → `full.cpp`
  - `sim.cpp`: setup()/loop() üzerinde 77 kontrol (ışık, kararma, gece, müzik/Rock/pomodoro, telefon kaybı, kapak, OTA, 200 bin rastgele komut + ASan)
  - `sim_bat.cpp`: LiPo + TP4056 modeliyle pil senaryoları
  - Çalıştırma: `python3 mkproto.py ../firmware/eyes_keychain/eyes_keychain.ino full.cpp && g++ -std=gnu++17 -fsanitize=address,undefined -Wno-narrowing -Wno-nonnull -Istub -include stub/stubs.h sim.cpp stub/globals.cpp -o sim && ASAN_OPTIONS=detect_leaks=0 ./sim`

## Kullanıcıda çalıştığı doğrulananlar
- Ekran ve gözler, modlar, uygulama kurulumu ve bağlantı, şarkı adı ve sözler
- Albüm kapağı ekranı (v10'dan sonra düzeldi)

## Doğrulananlar (v11)
- Şarj yüzdesi düzgün artıyor, bar yeni yerinde.

## Denenmesi gerekenler (v15 / uygulama 1.6)
- Kullanıcı v14'ü yükledi (flash %61, RAM %8, yükleme doğrulandı).
- Saat modunu kapat/aç; Saat'teyken kapatınca önceki moda dönüyor mu.
- 15 hareketin her biri doğru görünüyor mu (özellikle göz devirme dairesi, esneme, sersemleme). Tuhaf olanı söyle, süre/şiddet ayarlanır.
- Canlılık Sakin/Normal/Hareketli/Çok farkı.

## Önceki sürümde denenmesi gerekenler (v13 / uygulama 1.4)
- Tam ekran kapak: açılış perdesi akıcı mı, kapak gelmesi kaç sn sürüyor, RAM yetiyor mu (Seri Monitör'de "Tam ekran kapak: 240x240 tamam")
- BLK kablosu GPIO 3'e taşındıktan sonra parlaklık kaydırıcısı, kararma, gece uykusu
- Uygulamayı üstüne kurunca müzik takibi kendiliğinden geri geliyor mu?
- Dolu pilde fiş çekilince birkaç dk içinde "Sarj bitti" çıkıyor mu?
- Kablosuz güncelleme hâlâ hiç denenmedi (v12 → v12 bin ile denenebilir).

## Fikir listesi (sıradakiler)
- Albüm rengine göre göz rengi
- Anahtarlığı/telefonu bulma, unutma uyarısı
- Sanal evcil hayvan ruh hali
- Google Maps yön okları
- Birden çok özel tasarım kaydetme ve paylaşma
