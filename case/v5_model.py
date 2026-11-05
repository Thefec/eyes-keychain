#!/usr/bin/env python3
"""Göz Anahtarlık v3: dikey kasa, ekran ortada, iki USB-C alt kenarda yan yana, anahtar sağ yanda ortada,
anahtarlık kanalı kasanın içinden (dışa çıkıntı yok). 4 parça: front, ayirici_A (ekran tarafı), ayirici_B (pil tarafı), back."""
import os, sys
import numpy as np, trimesh
from shapely.geometry import box
from shapely.ops import unary_union

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
os.makedirs(OUT, exist_ok=True)

# ====================== ÖLÇÜLER ======================
BAT_W, BAT_L, BAT_T = 30.0, 50.0, 6.3
BAT_MARGIN = 1.0
DISP_PCB_W, DISP_PCB_L, DISP_PCB_T = 27.8, 39.3, 2.0
DISP_GLASS_W, DISP_GLASS_L, DISP_GLASS_T = 26.0, 28.0, 1.0
CHIP_H = 1.0
C3_W, C3_L, C3_T = 18.0, 24.0, 4.0
TP_W, TP_L, TP_H = 14.0, 20.0, 3.4
SW_BODY_L, SW_BODY_D, SW_BODY_H = 10.0, 5.0, 5.0   # anahtar gövdesi (boy, duvardan içe derinlik, yükseklik) - bol pay
SW_WIN_L, SW_WIN_H = 8.0, 4.4                       # tırnak penceresi: 3 mm tırnak + 3 mm kayma + 2 mm pay
CLR, WALL, FW, BP = 0.4, 2.2, 1.6, 1.8
R_OUT, R_IN = 4.5, 1.0
CH = 0.8
TONGUE_H = 2.0
USB_W, USB_H = 9.6, 4.2
USB_REC_W, USB_REC_H, USB_REC_D = 12.6, 7.0, 1.4      # dış girinti: fiş kafası yuvaya otursun
KEY_D = 3.6                                            # anahtarlık kanalı çapı

GUT = 2.0                                              # C3'ün iki yanında kablo kanalı (pedlerden yana çıkan kablolar için)
IW = 0.2 + GUT + C3_W + GUT + 0.2 + TP_W + 0.2         # 37.6
WALL_TOP = KEY_D + 2 * 1.3                            # üst duvar: anahtarlık kanalı + 1.3 mm et
IL = BAT_L + 2 * BAT_MARGIN + 1.6                      # 53.6
OW, OL = IW + 2 * WALL, IL + WALL + WALL_TOP
OC_Y = (WALL_TOP - WALL) / 2                            # dış gövde merkezi (iç boşluğa göre)
POCKET_W, POCKET_L = DISP_PCB_W + CLR, DISP_PCB_L + CLR
POCKET_D = DISP_GLASS_T + DISP_PCB_T + 0.15
ZS = FW + POCKET_D
DIV_T = 1.0
EXTRA = 3.0                                       # ön parçanın ek derinliği (kart katmanı 7.5 mm)
Z_A0 = ZS - 0.15 + CHIP_H + 0.35
Z_A1 = Z_A0 + DIV_T
Z_B0 = Z_A1 + C3_T + 0.5
Z_B1 = Z_B0 + DIV_T
ZB = Z_B1 + 0.1 + BAT_T + 0.3
ZT = ZB + BP
Z_MID = (Z_A1 + Z_B0) / 2

WIN_CY = OC_Y                    # ekran kasanın tam ortasında
POCKET_CY = WIN_CY + 0.8
WINDOW = 24.0
pcb_top, pcb_bot = POCKET_CY + DISP_PCB_L / 2, POCKET_CY - DISP_PCB_L / 2
# kartlar alt kenarda yan yana, USB'ler aşağı bakıyor
BRD_Y0 = -IL / 2 + 0.4
C3_X0 = -IW / 2 + 0.2 + GUT;  C3_X1 = C3_X0 + C3_W
TP_X0 = C3_X1 + GUT + 0.2;   TP_X1 = TP_X0 + TP_W
C3_CX, TP_CX = (C3_X0 + C3_X1) / 2, (TP_X0 + TP_X1) / 2
SW_Y = OC_Y                      # anahtar: sağ yanın tam ortası
SW_ZC = ZS + TONGUE_H + 2.15     # anahtar merkezi (arka parça koordinatı): sağlam duvar bölgesinde
SW_Z0 = SW_ZC - SW_WIN_H / 2
USB_Z0, USB_Z1 = Z_B0 - USB_H, Z_B0
KEY_Y = IL / 2 + WALL_TOP / 2    # kanal üst duvarın ortasından
KEY_Z = ZT - 4.5

# ====================== YARDIMCI ======================
def rrect(w, h, r, cx=0.0, cy=0.0):
    return box(cx - w / 2 + r, cy - h / 2 + r, cx + w / 2 - r, cy + h / 2 - r).buffer(r, resolution=24)
def union(ms):
    ms = list(ms); return ms[0] if len(ms) == 1 else trimesh.boolean.union(ms, engine="manifold")
def diff(a, *bs):
    for b in bs: a = trimesh.boolean.difference([a, b], engine="manifold")
    return a
def prism(poly, z0, z1):
    gs = list(poly.geoms) if hasattr(poly, "geoms") else [poly]; ms = []
    for g in gs:
        m = trimesh.creation.extrude_polygon(g, z1 - z0); m.apply_translation([0, 0, z0]); ms.append(m)
    return union(ms)
def cube(x0, x1, y0, y1, z0, z1):
    x0, x1 = sorted((x0, x1)); y0, y1 = sorted((y0, y1)); z0, z1 = sorted((z0, z1))
    m = trimesh.creation.box(extents=[x1 - x0, y1 - y0, z1 - z0]); m.apply_translation([(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2]); return m
def cyl(r, p0, p1):
    p0, p1 = np.array(p0, float), np.array(p1, float)
    return trimesh.creation.cylinder(radius=r, segment=[p0, p1], sections=48)
def for_print(m):
    m = m.copy(); m.apply_transform(trimesh.transformations.rotation_matrix(np.pi, [1, 0, 0])); m.apply_translation([0, 0, -m.bounds[0][2]]); return m
def chamfered(poly, z0, z1, ch, n=4, at="bottom"):
    dz = ch / n; parts = []
    for i in range(n):
        ins = ch * (1 - (i + 0.5) / n)
        a, b = (z0 + i * dz, z0 + (i + 1) * dz) if at == "bottom" else (z1 - (i + 1) * dz, z1 - i * dz)
        parts.append(prism(poly.buffer(-ins), a, b))
    parts.append(prism(poly, z0 + ch, z1) if at == "bottom" else prism(poly, z0, z1 - ch))
    return union(parts)

OUTER = rrect(OW, OL, R_OUT, 0, OC_Y); CAV = rrect(IW, IL, R_IN)
CAVX = rrect(IW + 2.2, IL + 2.2, R_IN + 1.1); TOUT = rrect(IW + 1.8, IL + 1.8, R_IN + 0.9)
BATP = rrect(BAT_W + 2 * BAT_MARGIN, BAT_L + 2 * BAT_MARGIN, 1.5)

# ====================== KESİCİLER ======================
YB = -IL / 2 - WALL
usb_cuts = []
for cx in (C3_CX, TP_CX):
    usb_cuts.append(cube(cx - USB_W / 2, cx + USB_W / 2, YB - 1, -IL / 2 + 1.6, USB_Z0, USB_Z1))
    zc = (USB_Z0 + USB_Z1) / 2
    usb_cuts.append(cube(cx - USB_REC_W / 2, cx + USB_REC_W / 2, YB - 1, YB + USB_REC_D,
                         max(ZS + TONGUE_H + 0.15, zc - USB_REC_H / 2), zc + USB_REC_H / 2))
sw_cut = cube(IW / 2 - 1.0, OW / 2 + 1, SW_Y - SW_WIN_L / 2, SW_Y + SW_WIN_L / 2, SW_Z0, SW_Z0 + SW_WIN_H)
# anahtarlık kanalı: üst yüzden girer, arka yüzden çıkar (L şeklinde, yuvarlatılmış dönüş)
r = KEY_D / 2
key_cut = union([cyl(r, (0, KEY_Y, KEY_Z), (0, IL / 2 + WALL_TOP + OC_Y * 0 + 2, KEY_Z)),
                 cyl(r, (0, KEY_Y, KEY_Z), (0, KEY_Y, ZT + 2)),
                 trimesh.creation.icosphere(subdivisions=3, radius=r).apply_translation([0, KEY_Y, KEY_Z])])

# ====================== ÖN KABUK ======================
front = chamfered(OUTER, 0, ZS, CH, at="bottom")
front = diff(front, prism(rrect(POCKET_W, POCKET_L, 0.2, 0, POCKET_CY), FW, ZS + 0.1),
             prism(rrect(WINDOW, WINDOW, 1.0, 0, WIN_CY), -0.1, FW + 0.1))
front_base = front

# ====================== ARKA KABUK ======================
back = chamfered(OUTER, ZS, ZT, CH, at="top")
back = diff(back, prism(CAV, ZS - 0.1, ZB), prism(CAVX, ZS - 0.1, ZS + TONGUE_H))
back = union([back, prism(CAV.difference(BATP), Z_B1 + 0.1, ZB + 0.05)])       # pil yuvası eteği
cheeks = []
# Anahtar yuvası: iki uç duvarı + gövdeyi arkadan köşelerinden tutan tırnaklar; ortası açık (bacaklar ve kablolar için)
L2 = SW_BODY_L / 2 + 0.3
x_back = IW / 2 - (SW_BODY_D + 0.4)
zc0, zc1 = Z_A1 + 0.05 - EXTRA, Z_B0 - 0.05
z_wall = ZS + TONGUE_H + 0.05                     # bunun altında duvar oyuğu (ön parçanın dili) var
for sy in (-1, 1):
    y_in, y_out = SW_Y + sy * L2, SW_Y + sy * (L2 + 1.2)
    cheeks.append(cube(x_back - 1.2, IW / 2, y_in, y_out, zc0, zc1))                 # uç duvarı
    cheeks.append(cube(x_back - 1.2, IW / 2 + 0.3, y_in, y_out, z_wall, zc1))        # duvara gömülen kısım
    cheeks.append(cube(x_back - 1.2, x_back, SW_Y + sy * (L2 - 1.5), y_in, zc0, zc1)) # arka tırnak
back = union([back] + cheeks)
finger = cube(OW / 2 - 0.9, OW / 2 + 1, SW_Y - 5.5, SW_Y + 5.5, max(z_wall + 0.05, SW_ZC - 2.45), SW_ZC + 2.45)   # dış parmak girintisi
back = diff(back, *usb_cuts, sw_cut, key_cut, finger)

# ====================== AYIRICI PLAKALAR ======================
DIVP = rrect(IW - 0.4, IL - 0.4, max(R_IN - 0.2, 0.3))
RING_W = 3.0                                                # çerçeve kenar genişliği
RING = DIVP.difference(rrect(IW - 0.4 - 2 * RING_W, IL - 0.4 - 2 * RING_W, 0.5))
# A: ekran tarafı ÇERÇEVE. Dış halka + ekran kartını sol/sağ/üst kenarından tutan U tutucu (alt kenar pinler için açık)
FR_W = 2.0
z_fr0 = ZS - 0.15 + 0.05                                   # ekran kartı arkasına 0.05 mm kala
xi, xo = DISP_PCB_W / 2 - FR_W, IW / 2 - 0.2 - RING_W + 0.3   # tutucu bandı: ekran kenarından halkaya kadar
U2d = unary_union([box(-xo, pcb_bot + 0.2, -xi, pcb_top + 1.5), box(xi, pcb_bot + 0.2, xo, pcb_top + 1.5),
                   box(-xo, pcb_top - FR_W, xo, IL / 2 - 0.2 - RING_W + 0.3)])
divA = prism(unary_union([RING, U2d]).intersection(DIVP), Z_A0, Z_A1)
fr = []
for sgn in (-1, 1):
    fr.append(cube(sgn * xi, sgn * (DISP_PCB_W / 2 - 0.2), pcb_bot + 0.2, pcb_top - 0.2, z_fr0, Z_A0 + 0.1))
fr.append(cube(-(DISP_PCB_W / 2 - 0.2), DISP_PCB_W / 2 - 0.2, pcb_top - FR_W, pcb_top - 0.2, z_fr0, Z_A0 + 0.1))
divA = union([divA] + fr)
# B: pil tarafı ÇERÇEVE. Dış halka + kartların oturduğu 2 çapraz çubuk + aralık sütunları + kart durdurucuları
RIB_H, RIB_T = 2.5, 1.0
full = (Z_A1 + 0.05 - EXTRA, Z_B0 + 0.1); rib = (Z_B0 - RIB_H, Z_B0 + 0.1)
c3_end, tp_end = BRD_Y0 + C3_L, BRD_Y0 + TP_L
xin = IW / 2 - 0.2 - RING_W + 0.3
bars = unary_union([box(-xin, BRD_Y0 + 3.0, xin, BRD_Y0 + 6.5),                     # alt çubuk (USB tarafı)
                    box(-xin, tp_end - 3.0, xin, c3_end + 0.25 + RIB_T)])           # üst çubuk (kart uçları + durdurucular)
divB = prism(unary_union([RING, bars]).intersection(DIVP), Z_B0, Z_B1)
add = []
for sx in (-1, 1):                                                                           # üst köşe sütunları
    add.append(prism(box(min(sx * (IW / 2 - 3.2), sx * (IW / 2 - 0.2)), IL / 2 - 3.2, max(sx * (IW / 2 - 3.2), sx * (IW / 2 - 0.2)), IL / 2 - 0.2).intersection(DIVP), *full))
    add.append(cube(sx * (IW / 2 - 3.2), sx * (IW / 2 - 0.2), 12.0, 15.0, *full))               # yan sütunlar
for (x0, x1) in ((C3_X0, C3_X0 + 4.0), (C3_X1 - 4.0, C3_X1)):                                # C3 iç uç durdurucuları
    add.append(cube(x0, x1, c3_end + 0.25, c3_end + 0.25 + RIB_T, *rib))
for (x0, x1) in ((TP_X0, TP_X0 + 4.0), (TP_X1 - 4.0, TP_X1)):                                # TP iç uç durdurucuları
    add.append(cube(x0, x1, tp_end + 0.25, tp_end + 0.25 + RIB_T, *rib))
divB = union([divB] + add)
# Direnç yatağı (sol üst) ve düğüm yatağı (orta üst): kart tarafına doğru 2.6 mm çıkıntılı tabla + oluklar
TZ0 = Z_B0 - 2.6
res_tray = cube(-(IW / 2 - 0.2 - RING_W + 0.3), -3.0, 0.5, 11.5, TZ0, Z_B1)
spl_tray = cube(-4.0, IW / 2 - 0.2 - RING_W + 0.3, 9.0, 20.0, TZ0, Z_B1)
divB = union([divB, res_tray, spl_tray])
RES_X = (-11.6, -6.8); SPL_X = (0.0, 5.8, 11.6)
grooves = [cube(x - 1.4, x + 1.4, 0.4, 11.6, TZ0 - 0.1, TZ0 + 1.4) for x in RES_X] + \
          [cube(x - 2.3, x + 2.3, 8.9, 20.1, TZ0 - 0.1, TZ0 + 2.0) for x in SPL_X]
divB = diff(divB, *grooves)

# ====================== ARA HALKA (ön ile arka arasına) ======================
# Alt kısım: ön parçanın 2 mm dilini içine alır (CAVX oyuğu). Üst kısım: arka parçanın oyuğuna giren kendi dili.
TONGUE_REC = TONGUE_H + 0.05
spacer = prism(OUTER.difference(CAVX), ZS, ZS + TONGUE_REC)                       # dış duvar (dil yuvası)
spacer = union([spacer, prism(OUTER.difference(CAV), ZS + TONGUE_REC, ZS + EXTRA)])   # bağlantı flanşı
spacer = union([spacer, prism(TOUT.difference(CAV), ZS + EXTRA - 0.05, ZS + EXTRA + TONGUE_H)])  # arka parçaya giren dil
def _sh(m):
    m = m.copy(); m.apply_translation([0, 0, EXTRA]); return m
# Ön parça EXTRA kadar derinleşir (halka ön parçaya katıldı), dili arka parçaya girer
front = union([front_base, prism(OUTER.difference(CAV), ZS - 0.01, ZS + EXTRA),
               diff(prism(TOUT.difference(CAV), ZS + EXTRA - 0.05, ZS + EXTRA + TONGUE_H), *[_sh(c) for c in usb_cuts], _sh(sw_cut))])

# ====================== UYUM KONTROLÜ ======================
glass_cy = POCKET_CY + DISP_PCB_L / 2 - 4.7 - DISP_GLASS_L / 2
SWZ = SW_Z0 + SW_WIN_H / 2
comps = {
    "ekran camı": cube(-DISP_GLASS_W / 2, DISP_GLASS_W / 2, glass_cy - DISP_GLASS_L / 2, glass_cy + DISP_GLASS_L / 2, FW, FW + DISP_GLASS_T),
    "ekran kartı": cube(-DISP_PCB_W / 2, DISP_PCB_W / 2, pcb_bot, pcb_top, FW + DISP_GLASS_T, FW + DISP_GLASS_T + DISP_PCB_T),
    "ekran çipi(1mm)": cube(-DISP_PCB_W / 2 + 2.5, DISP_PCB_W / 2 - 2.5, pcb_bot + 3, pcb_top - 3, ZS - 0.15, ZS - 0.15 + CHIP_H),
    "ESP32-C3": cube(C3_X0, C3_X1, BRD_Y0, BRD_Y0 + C3_L, Z_B0 - C3_T, Z_B0),
    "TP4056": cube(TP_X0, TP_X1, BRD_Y0, BRD_Y0 + TP_L, Z_B0 - TP_H, Z_B0),
    "pil": cube(-BAT_W / 2, BAT_W / 2, -BAT_L / 2, BAT_L / 2, ZB - BAT_T, ZB),
    "anahtar gövdesi": cube(IW / 2 - SW_BODY_D, IW / 2, SW_Y - SW_BODY_L / 2, SW_Y + SW_BODY_L / 2, SWZ - SW_BODY_H / 2, SWZ + SW_BODY_H / 2),
}
for _k in ("ESP32-C3", "TP4056", "pil", "anahtar gövdesi"): comps[_k] = _sh(comps[_k])
parts = {"front": front, "back": _sh(back), "ayirici_A": divA, "ayirici_B": _sh(divB)}
L = [f"Dış ölçü: {OW:.1f} x {OL:.1f} x {ZT + EXTRA:.1f} mm; kart katmanı {Z_B0 - Z_A1 + EXTRA:.1f} mm",
     f"Pil yuvası {BAT_W+2*BAT_MARGIN:.0f} x {BAT_L+2*BAT_MARGIN:.0f}; ekran arkası boşluk {Z_A0-(ZS-0.15):.2f} mm; kart katmanı {Z_B0-Z_A1:.1f} mm",
     f"USB merkezleri x: C3 {C3_CX:.1f}, TP {TP_CX:.1f} (alt kenar); anahtar y={SW_Y:.1f} (dış merkez); kanal Ø{KEY_D}"]
for n, m in parts.items():
    L.append(f"{n}: watertight={m.is_watertight} hacim={m.volume:.0f} mm3 sınırlar={np.round(m.extents,1)}")
L.append("--- Çarpışma (mm3, 0 olmalı) ---"); ok = True
for pn, pm in parts.items():
    for cn, cm in comps.items():
        i = trimesh.boolean.intersection([pm, cm], engine="manifold")
        v = 0.0 if (i is None or len(i.vertices) == 0) else abs(i.volume)
        ok &= v <= 0.5
        if v > 0.01: L.append(f"{pn} x {cn}: {v:.2f}")
names = list(parts)
for a in range(len(names)):
    for b in range(a + 1, len(names)):
        i = trimesh.boolean.intersection([parts[names[a]], parts[names[b]]], engine="manifold")
        v = 0.0 if (i is None or len(i.vertices) == 0) else abs(i.volume); ok &= v <= 0.5
        if v > 0.01: L.append(f"{names[a]} x {names[b]}: {v:.2f}")
L.append("SONUÇ: " + ("çarpışma yok" if ok else "ÇARPIŞMA VAR"))
rep = "\n".join(L); print(rep); open(os.path.join(OUT, "fit_report.txt"), "w").write(rep + "\n")

front.export(os.path.join(OUT, "front.stl")); for_print(back).export(os.path.join(OUT, "back.stl"))
divA.export(os.path.join(OUT, "ayirici_A.stl")); for_print(divB).export(os.path.join(OUT, "ayirici_B.stl"))
trimesh.Scene({**parts, **{"c_" + k: v for k, v in comps.items()}}).export(os.path.join(OUT, "_assembly.glb"))
