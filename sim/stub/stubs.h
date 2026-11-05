#pragma once
#include <cstdint>
#include <cstddef>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <cmath>
#include <algorithm>
#include <string>
using std::min; using std::max;
#define PROGMEM
#define PI 3.14159265358979f
#define INPUT_PULLUP 5
#define OUTPUT 3
#define LOW 0
#define HIGH 1
#define ADC_11db 3
#ifndef ESP_ARDUINO_VERSION_MAJOR
#define ESP_ARDUINO_VERSION_MAJOR 3
#endif
extern uint32_t g_millis; extern int g_jpgDecodes; extern int g_pushes;
inline uint32_t millis() { return g_millis; }
inline void delay(uint32_t ms) { g_millis += ms; }
inline long random(long a) { return a > 0 ? rand() % a : 0; }
inline long random(long a, long b) { return b > a ? a + rand() % (b - a) : a; }
extern int g_button;
inline int digitalRead(int) { return g_button; }
inline void pinMode(int, int) {}
inline void digitalWrite(int, int) {}
extern float g_vbat; extern float g_noise;
inline uint32_t analogReadMilliVolts(int) { float n = ((rand() % 2001) - 1000) / 1000.0f * g_noise; return (uint32_t)((g_vbat / 2 + n) * 1000); }
inline void analogSetPinAttenuation(int, int) {}
struct SerialC {
  bool quiet = true;
  void begin(int) {}
  int available() { return 0; }
  int read() { return -1; }
  template<class... A> void printf(const char *f, A... a) { if (!quiet) { ::printf("[%7.1fs] ", g_millis / 1000.0); ::printf(f, a...); } }
  void println(const char *s) { if (!quiet) ::printf("[%7.1fs] %s\n", g_millis / 1000.0, s); }
};
extern SerialC Serial;
struct EspC { uint64_t getEfuseMac() { return 0x1234567890ULL; } uint32_t getFreeHeap() { return 100000; } void restart() {} };
extern EspC ESP;
#define MALLOC_CAP_8BIT 4
inline size_t heap_caps_get_largest_free_block(int) { return 90000; }
typedef void *QueueHandle_t;
#define pdTRUE 1
#define pdMS_TO_TICKS(x) (x)
inline QueueHandle_t xQueueCreate(int, int) { return nullptr; }
inline int xQueueSend(QueueHandle_t, const void *, int) { return 1; }
inline int xQueueReceive(QueueHandle_t, void *, int) { return 0; }
extern uint32_t g_duty; extern int g_dutyWrites;
#if ESP_ARDUINO_VERSION_MAJOR >= 3
inline void ledcWrite(uint8_t, uint32_t d) { g_duty = d; g_dutyWrites++; }
inline bool ledcAttach(uint8_t, uint32_t, uint8_t) { return true; }
inline bool ledcDetach(uint8_t) { return true; }
#else
inline void ledcWrite(uint8_t, uint32_t d) { g_duty = d; g_dutyWrites++; }
inline double ledcSetup(uint8_t, double, uint8_t) { return 0; }
inline void ledcAttachPin(uint8_t, uint8_t) {}
inline void ledcDetachPin(uint8_t) {}
#endif
typedef int gpio_num_t;
inline int gpio_hold_en(gpio_num_t) { return 0; }
inline int gpio_hold_dis(gpio_num_t) { return 0; }
inline int esp_sleep_enable_timer_wakeup(uint64_t) { return 0; }
extern int g_lightSleeps;
inline int esp_light_sleep_start() { if (++g_lightSleeps > 2) throw 42; return 0; }
// Preferences
struct Preferences {
  bool begin(const char *, bool) { return true; }
  size_t putUChar(const char *, uint8_t) { return 1; }  uint8_t getUChar(const char *, uint8_t d) { return d; }
  size_t putUShort(const char *, uint16_t) { return 2; } uint16_t getUShort(const char *, uint16_t d) { return d; }
  size_t putBool(const char *, bool) { return 1; }      bool getBool(const char *, bool d) { return d; }
  size_t putBytes(const char *, const void *, size_t n) { return n; } size_t getBytes(const char *, void *, size_t) { return 0; }
};
struct UpdateC { bool begin(size_t) { return true; } size_t write(uint8_t *, size_t n) { return n; } bool end(bool) { return true; } void abort() {} const char *errorString() { return ""; } };
extern UpdateC Update;
// NimBLE
struct NimBLEConnInfo {};
struct NimBLEAttValue { size_t length() { return 0; } const uint8_t *data() { return nullptr; } };
struct NimBLECharacteristic;
struct NimBLECharacteristicCallbacks { virtual ~NimBLECharacteristicCallbacks() {} virtual void onWrite(NimBLECharacteristic *, NimBLEConnInfo &) {} };
struct NimBLECharacteristic { NimBLEAttValue getValue() { return {}; } void setValue(const uint8_t *, size_t) {} void notify() {} void setCallbacks(NimBLECharacteristicCallbacks *) {} };
namespace NIMBLE_PROPERTY { enum { READ = 1, WRITE = 2, WRITE_NR = 4, NOTIFY = 8 }; }
struct NimBLEService { NimBLECharacteristic *createCharacteristic(const char *, int) { static NimBLECharacteristic c; return &c; } bool start() { return true; } };
struct NimBLEServer;
struct NimBLEServerCallbacks { virtual ~NimBLEServerCallbacks() {} virtual void onConnect(NimBLEServer *, NimBLEConnInfo &) {} virtual void onDisconnect(NimBLEServer *, NimBLEConnInfo &, int) {} };
struct NimBLEServer { void setCallbacks(NimBLEServerCallbacks *) {} void advertiseOnDisconnect(bool) {} NimBLEService *createService(const char *) { static NimBLEService s; return &s; } };
struct NimBLEAdvertising { void setName(const char *) {} void addServiceUUID(const char *) {} void enableScanResponse(bool) {} bool start() { return true; } };
struct NimBLEDevice {
  static void init(const char *) {} static void setMTU(int) {} static bool deinit(bool) { return true; }
  static NimBLEServer *createServer() { static NimBLEServer s; return &s; }
  static NimBLEAdvertising *getAdvertising() { static NimBLEAdvertising a; return &a; }
};
// LovyanGFX
#define SPI2_HOST 1
#define SPI_DMA_CH_AUTO 3
namespace lgfx {
  enum class textdatum_t { middle_center, middle_left, middle_right };
  struct IFont {};
  namespace fonts { extern IFont FreeSansBold9pt7b, Font0; }
  struct BusCfg { int spi_host, spi_mode, freq_write, freq_read, pin_sclk, pin_mosi, pin_miso, pin_dc, dma_channel; bool spi_3wire, use_lock; };
  struct Bus_SPI { BusCfg c{}; BusCfg config() { return c; } void config(const BusCfg &x) { c = x; } };
  struct PanelCfg { int pin_cs, pin_rst, pin_busy, panel_width, panel_height, memory_width, memory_height, offset_x, offset_y, offset_rotation; bool readable, invert, rgb_order, dlen_16bit, bus_shared; };
  struct Panel_ST7789 { PanelCfg c{}; PanelCfg config() { return c; } void config(const PanelCfg &x) { c = x; } void setBus(Bus_SPI *) {} };
  struct Gfx {
#define M(n) template<class... A> void n(A...) {}
    M(fillScreen) M(fillCircle) M(fillRect) M(fillTriangle) M(setTextColor) M(setTextSize) M(fillRoundRect)
    M(fillSmoothRoundRect) M(fillEllipse) M(fillArc) M(drawLine) M(setClipRect) M(clearClipRect)
    M(drawRoundRect) M(startWrite) M(endWrite) M(setRotation) M(init) M(sleep) M(loadFont)
#undef M
    void setTextDatum(textdatum_t) {}
    void setFont(const IFont *) {}
    template<class... A> int drawString(const char *, A...) { return 0; }
    int textWidth(const char *s) { return (int)strlen(s) * 9; }
    uint16_t color565(uint8_t r, uint8_t g, uint8_t b) { return (uint16_t)(((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3)); }
    template<class... A> bool drawJpg(const uint8_t *, uint32_t, A...) { g_jpgDecodes++; return true; }
    template<class... A> void pushSprite(A...) { g_pushes++; }
    int getColorDepth() { return 16; }
    void setColorDepth(int) {}
    void *createSprite(int, int) { return this; }
  };
  struct LGFX_Device : Gfx { void setPanel(Panel_ST7789 *) {} };
}
struct LGFX_Sprite : lgfx::Gfx { LGFX_Sprite(lgfx::LGFX_Device *) {} };
