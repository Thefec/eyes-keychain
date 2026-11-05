#include "stubs.h"
uint32_t g_millis = 0; int g_button = 1; float g_vbat = 3.9f, g_noise = 0.02f;
SerialC Serial; EspC ESP; UpdateC Update; uint32_t g_duty = 0; int g_dutyWrites = 0; int g_lightSleeps = 0;
namespace lgfx { namespace fonts { IFont FreeSansBold9pt7b, Font0; } }
int g_jpgDecodes = 0;
int g_pushes = 0;
