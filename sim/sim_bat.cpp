#include "full.cpp"   // önce: python3 mkproto.py ../firmware/eyes_keychain/eyes_keychain.ino full.cpp
#include <functional>
// Basit LiPo modeli: 900 mAh, iç direnç 0.15 ohm, yük 80 mA. TP4056: CC 0.5 A (PC USB), CV 4.20, kesme C/10, yeniden şarj < 4.05
const float CAP = 900, R = 0.15f, LOAD = 0.08f;
float soc = 0.4f; bool plugged = false, tpDone = false;
float ocv(float s) { static const float S[] = {0, .05, .12, .2, .3, .4, .5, .65, .8, .9, 1}; static const float V[] = {3.3, 3.5, 3.6, 3.65, 3.7, 3.75, 3.8, 3.9, 4.0, 4.1, 4.2};
  if (s <= 0) return 3.0f + s; for (int i = 1; i < 11; i++) if (s <= S[i]) return V[i-1] + (V[i]-V[i-1]) * (s - S[i-1]) / (S[i]-S[i-1]); return 4.2f; }
float term() {
  float o = ocv(soc);
  if (plugged && !tpDone) { float v = o + 0.5f * R; return v > 4.2f ? 4.2f : v; }
  return o - LOAD * R;
}
void step(float dt) {   // dt saniye
  float iBat = -LOAD;
  if (plugged && !tpDone) { float o = ocv(soc); float i = (o + 0.5f * R <= 4.2f) ? 0.5f : (4.2f - o) / R; if (i < 0.09f) { tpDone = true; } else iBat = i - LOAD; }
  if (plugged && tpDone && term() < 4.05f) tpDone = false;
  soc += iBat * dt / 3600.0f / (CAP / 1000.0f); if (soc > 1) soc = 1;
  g_vbat = term();
}
struct Ev { float t; const char *what; };
int main(int argc, char **argv) {
  g_noise = argc > 1 ? atof(argv[1]) : 0.03f;
  auto run = [](const char *name, float soc0, std::function<void(float, bool &)> plan, float hours) {
    soc = soc0; plugged = false; tpDone = false;
    batPct = -1; onUSB = false; vFilt = -1; vSlow = -1; charging = false; riseCnt = dropCnt = 0; sagCnt = 0; lowSince = 0;
    g_millis = 1000; g_vbat = term();
    printf("\n== %s\n", name);
    bool lastCh = false, lastUsb = false; int lastPct = -2, ups = 0; uint32_t t0 = g_millis;
    try {
      for (float t = 0; t < hours * 3600; t += 2) {
        bool p = plugged; plan(t, p);
        if (p != plugged) { plugged = p; printf("  %6.1f dk  >> fis %s (gercek SOC %%%.0f, V=%.3f)\n", t / 60, p ? "TAKILDI" : "CEKILDI", soc * 100, term()); if (p) tpDone = false; }
        step(2); g_millis = t0 + (uint32_t)(t * 1000);
        readBattery(g_millis);
        if (charging != lastCh) { printf("  %6.1f dk  sarj=%d  gosterilen %%%d  gercek %%%.0f\n", t / 60, charging, batPct, soc * 100); lastCh = charging; }
        if (onUSB != lastUsb) { lastUsb = onUSB; }
        if (!plugged && !charging && lastPct >= 0 && batPct > lastPct) ups++;
        lastPct = batPct;
      }
    } catch (int) { printf("  %6.1f dk  PIL BITTI kapanisi (gercek SOC %%%.0f, V=%.3f)\n", (g_millis - t0) / 60000.0, soc * 100, term()); }
    printf("  son: gosterilen %%%d gercek %%%.0f sarj=%d usb=%d | pilde yukari ziplama: %d\n", batPct, soc * 100, charging, onUSB, ups);
  };
  run("1) Dolu pilden bitene kadar", 0.95f, [](float, bool &p) { p = false; }, 14);
  run("2) %40'ta tak, doldur, 1 saat takili kalsin, cek", 0.4f, [](float t, bool &p) { p = t > 120 && t < 4 * 3600; }, 5);
  run("3) %60'ta tak, 20 dk sonra cek", 0.6f, [](float t, bool &p) { p = t > 120 && t < 120 + 1200; }, 1);
  run("4) Neredeyse doluyken tak (%%92)", 0.92f, [](float t, bool &p) { p = t > 120 && t < 1800; }, 1);
  run("5) Kisa tak-cek (30 sn)", 0.5f, [](float t, bool &p) { p = t > 120 && t < 150; }, 0.2f);
}
