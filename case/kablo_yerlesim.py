import math, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Rectangle, Circle

RED, BLK, ORG, BLU, GRN = "#d62828", "#1b1b1b", "#f08c00", "#1d70e0", "#2b9348"
SIG = {4: "#7b2cbf", 5: "#2a9d8f", 6: "#d81b60", 7: "#8d6e63"}
fig = plt.figure(figsize=(17, 13))
ax = fig.add_axes([0.0, 0.0, 0.58, 0.95]); ax.set_xlim(-24, 24); ax.set_ylim(-33, 37); ax.set_aspect("equal"); ax.axis("off")
tb = fig.add_axes([0.58, 0.0, 0.42, 0.95]); tb.set_xlim(0, 10); tb.set_ylim(0, 16); tb.axis("off")

def rbox(x0, y0, x1, y1, fc, ec="#555", lw=1.6, r=0.6, z=2, ls="-", a=1):
    ax.add_patch(FancyBboxPatch((x0, y0), x1 - x0, y1 - y0, boxstyle=f"round,pad=0,rounding_size={r}", fc=fc, ec=ec, lw=lw, zorder=z, ls=ls, alpha=a))
def badge(x, y, n, c, r=1.05, a=ax, fs=9):
    a.add_patch(Circle((x, y), r, fc=c, ec="white", lw=1.4, zorder=12))
    a.text(x, y, str(n), color="white", ha="center", va="center", fontsize=fs, fontweight="bold", zorder=13)
def pad(x, y, c="white", r=0.55): ax.add_patch(Circle((x, y), r, fc=c, ec="#333", lw=1, zorder=9))

ax.text(0, 36, "Kasa içi kablo yerleşimi", ha="center", fontsize=17, fontweight="bold")
ax.text(0, 34.2, "Ekran tarafından bakış (ekranı ve A çerçevesini çıkarmış gibi). Ölçek gerçek.", ha="center", fontsize=10, color="#555")
# kasa
rbox(-20.5, -29.0, 20.5, 33.0, "#f6d7b8", "#a0662f", 2, r=4.5)
rbox(-18.3, -26.8, 18.3, 26.8, "#fffaf4", "#a0662f", 1.4, r=1)
ax.add_patch(Circle((0, 30.2), 1.8, fc="white", ec="#a0662f", lw=1.2, zorder=3)); ax.text(0, 30.2, "zincir", ha="center", va="center", fontsize=6.5, zorder=4)
# USB delikleri (alt duvar). Ekran tarafından bakınca C3 sağda, TP4056 solda
for cx, lab in ((7.1, "C3 USB-C\n(program)"), (-11.1, "TP4056 USB-C\n(şarj)")):
    ax.add_patch(Rectangle((cx - 4.8, -29.2), 9.6, 2.6, fc="white", ec="#a0662f", lw=1.2, zorder=3))
    ax.text(cx, -31.3, lab, ha="center", va="center", fontsize=8)
# kartlar
rbox(-1.9, -26.4, 16.1, -2.4, "#fde8e8", "#a33", z=3)
ax.text(7.1, -25.3, "ESP32-C3 (düğmeli yüz ekrana bakar)", ha="center", va="center", fontsize=7, zorder=4)
rbox(-18.1, -26.4, -4.1, -6.4, "#efe6fb", "#7b4bb3", z=3)
ax.text(-11.1, -22.0, "TP4056", ha="center", va="center", fontsize=9, zorder=4)
c3L = [("5V", -22.9), ("G", -20.4), ("3.3", -17.8), ("4", -15.3), ("3", -12.8), ("2", -10.2), ("1", -7.7), ("0", -5.1)]
c3R = [("5", -22.9), ("6", -20.4), ("7", -17.8), ("8", -15.3), ("9", -12.8), ("10", -10.2), ("20", -7.7), ("21", -5.1)]
BLT = "#e0a800"   # arka ışık (BLK) kablosu, v12'den beri GPIO 3'e gider
usedL = {"5V": RED, "G": BLK, "3.3": ORG, "4": SIG[4], "3": BLT, "1": BLU}; usedR = {"6": SIG[5], "7": SIG[6], "10": SIG[7]}
for n, y in c3L: pad(-0.7, y, usedL.get(n, "#ddd")); ax.text(0.4, y, n, fontsize=6.5, va="center", zorder=10)
for n, y in c3R: pad(14.9, y, usedR.get(n, "#ddd")); ax.text(13.8, y, n, fontsize=6.5, va="center", ha="right", zorder=10)
TPP = {"+a": -16.0, "B-": -12.5, "OUT-": -9.0, "+b": -5.5}
for k, x in TPP.items():
    pad(x, -7.4, {"+a": RED, "B-": BLK, "OUT-": BLK, "+b": RED}[k]); ax.text(x, -9.0, {"+a": "+", "+b": "+", "B-": "B−", "OUT-": "OUT−"}[k], ha="center", fontsize=6.5, zorder=10)
# kablo kanalları
for x0, x1 in ((-4.1, -1.9), (16.1, 18.3)):
    ax.add_patch(Rectangle((x0, -26.4), x1 - x0, 24, fc="#ffe9a8", ec="none", alpha=0.7, zorder=2))
ax.text(-3.0, -27.6, "kanal", ha="center", fontsize=6.5, color="#a07a00"); ax.text(17.2, -27.6, "kanal", ha="center", fontsize=6.5, color="#a07a00")
# B çerçevesindeki yataklar
rbox(3.0, 0.5, 15.4, 11.5, "#eadcf2", "#8a5aa6", z=2, r=0.4)
rbox(-15.4, 9.0, 4.0, 20.0, "#eadcf2", "#8a5aa6", z=2, r=0.4)
ax.text(9.2, 12.4, "DİRENÇ YATAĞI", ha="center", fontsize=7.5, color="#6a3d86", fontweight="bold")
ax.text(-5.7, 20.8, "DÜĞÜM YATAĞI", ha="center", fontsize=7.5, color="#6a3d86", fontweight="bold")
for x in (6.8, 11.6): ax.add_patch(Rectangle((x - 1.4, 0.5), 2.8, 11, fc="#d9c3e6", ec="#8a5aa6", lw=0.8, zorder=3))
NODES = {"5V": (-11.6, RED), "GND": (-5.8, BLK), "3V3": (0.0, ORG)}
for k, (x, c) in NODES.items():
    ax.add_patch(Rectangle((x - 2.3, 9.0), 4.6, 11, fc="#d9c3e6", ec="#8a5aa6", lw=0.8, zorder=3))
    rbox(x - 1.6, 12.0, x + 1.6, 17.5, c, c, z=5, r=0.8); ax.text(x, 14.8, k, color="white", ha="center", va="center", fontsize=7, fontweight="bold", rotation=90, zorder=6)
# dirençler
for x, lab in ((6.8, "100K\n(üst)"), (11.6, "100K\n(alt)")):
    ax.add_patch(Rectangle((x - 1.1, 3.5), 2.2, 5.5, fc="#fff3d6", ec="#555", lw=1, zorder=6))
    ax.plot([x, x], [1.0, 3.5], color="#777", lw=1.2, zorder=6); ax.plot([x, x], [9.0, 11.0], color="#777", lw=1.2, zorder=6)
    ax.text(x, 6.25, lab, ha="center", va="center", fontsize=5.5, zorder=7, rotation=90)
ax.plot([6.8, 11.6], [1.0, 1.0], color=BLU, lw=2.5, zorder=7); pad(9.2, 1.0, BLU, 0.45); ax.text(9.2, -0.5, "orta nokta", ha="center", fontsize=6.5, color=BLU)
# anahtar (ekran tarafından bakınca SOLDA)
rbox(-18.3, -3.0, -13.3, 7.0, "#c8c8c8", "#444", z=4, r=0.3)
ax.text(-15.8, 2.0, "ANAHTAR", rotation=90, ha="center", va="center", fontsize=7, zorder=5)
for (y, lab) in ((-0.5, "A"), (2.0, "O"), (4.5, "B")):
    ax.plot([-13.3, -12.3], [y, y], color="#444", lw=2, zorder=5); pad(-12.0, y, "white", 0.45); ax.text(-10.9, y, lab, fontsize=6.5, va="center", zorder=10)
ax.annotate("pencere + parmak girintisi\n(dış duvarda)", (-19.5, 2.0), (-23.5, 9.5), fontsize=7, ha="left", arrowprops=dict(arrowstyle="->", lw=0.8))
# ekran (önde): kesikli çerçeve + pinler
rbox(-13.9, -16.85, 13.9, 22.45, "none", "#2a9d8f", 1.4, r=0.3, z=8, ls="--")
ax.text(-13.4, 21.3, "ekran kartı (önde, kesikli)", fontsize=7, color="#2a9d8f", zorder=8)
DP = ["GND", "VCC", "SCL", "SDA", "RES", "DC", "BLK"]
dx = {n: -7.62 + 2.54 * i for i, n in enumerate(DP)}
for n in DP: pad(dx[n], -16.85, "#bdebe3", 0.5); ax.text(dx[n], -15.4, n, ha="center", fontsize=5.3, rotation=90, zorder=10, color="#1d6e64")
# pil
ax.text(0, 25.0, "PİL: B çerçevesinin arkasında, bütün alanı kaplar.\nKabloları alttan (TP4056 tarafından) çıksın.", ha="center", fontsize=7.5, color="#666", style="italic")

wires = []   # (no, renk, yol, ek_mm, açıklama)
BPOS = {'A': (-7.3, 7.5), 'B': (-4.2, 7.0), 'C': (-1.1, 6.0)}
def W(no, c, pts, extra, desc): wires.append((no, c, pts, extra, desc))
W("A", RED, [(-0.7, -22.9), (-3.0, -22.9), (-3.0, 7.5), (-11.6, 7.5), (-11.6, 12)], 3, "C3 5V → 5V düğümü")
W("B", BLK, [(-0.7, -20.4), (-2.6, -20.4), (-2.6, 7.0), (-5.8, 7.0), (-5.8, 12)], 3, "C3 G → GND düğümü")
W("C", ORG, [(-0.7, -17.8), (-2.2, -17.8), (-2.2, 6.0), (0.0, 6.0), (0.0, 12)], 3, "C3 3.3 → 3V3 düğümü")
W(1, BLK, [(dx["GND"], -16.85), (dx["GND"], -4.0), (-5.8, -4.0), (-5.8, 12)], 6, "Ekran GND → GND düğümü")
W(2, ORG, [(dx["VCC"], -16.85), (dx["VCC"], -3.0), (0.6, -3.0), (0.6, 12)], 6, "Ekran VCC → 3V3 düğümü")
W(3, BLT, [(dx["BLK"], -16.85), (dx["BLK"], -11.6), (0.6, -11.6), (0.6, -12.8), (-0.7, -12.8)], 6, "Ekran BLK → C3 pin 3 (parlaklık)")
W(4, SIG[4], [(dx["SCL"], -16.85), (-2.54, -15.3), (-0.7, -15.3)], 6, "Ekran SCL → C3 pin 4")
W(5, SIG[5], [(dx["SDA"], -16.85), (dx["SDA"], -19.0), (14.9, -19.0), (14.9, -20.4)], 6, "Ekran SDA → C3 pin 6")
W(6, SIG[6], [(dx["DC"], -16.85), (dx["DC"], -18.2), (14.9, -18.2), (14.9, -17.8)], 6, "Ekran DC → C3 pin 7")
W(7, SIG[7], [(dx["RES"], -16.85), (dx["RES"], -13.0), (12.8, -13.0), (12.8, -10.2), (14.9, -10.2)], 6, "Ekran RES → C3 pin 10")
W(8, RED, [(6.8, 11.0), (6.8, 21.5), (-11.6, 21.5), (-11.6, 17.5)], 3, "Üst 100K → 5V düğümü")
W(9, BLK, [(11.6, 11.0), (11.6, 22.5), (-5.8, 22.5), (-5.8, 17.5)], 3, "Alt 100K → GND düğümü")
W(10, BLU, [(9.2, 1.0), (9.2, -1.0), (-0.7, -1.0), (-0.7, -7.7)], 3, "Orta nokta → C3 pin 1")
W(11, BLK, [(TPP["OUT-"], -7.4), (TPP["OUT-"], 5.0), (-6.6, 5.0), (-6.6, 12)], 3, "TP4056 OUT− → GND düğümü")
W(12, RED, [(TPP["+a"], -7.4), (-16.0, -4.5), (-12.0, -4.5), (-12.0, 2.0)], 3, "TP4056 + → Anahtar ORTA")
W(13, RED, [(-12.0, 4.5), (-12.0, 8.0), (-10.8, 8.0), (-10.8, 12)], 3, "Anahtar B → 5V düğümü")
W(14, BLK, [(-12.5, 0.5), (-12.5, -7.4)], 10, "Pil − → TP4056 B−")
W(15, RED, [(-5.5, 0.5), (-5.5, -7.4)], 10, "Pil + → TP4056 +")

def plen(pts): return sum(math.dist(pts[i], pts[i + 1]) for i in range(len(pts) - 1))
for no, c, pts, extra, desc in wires:
    xs, ys = zip(*pts)
    ax.plot(xs, ys, color="white", lw=5.2, zorder=10, solid_capstyle="round")
    ax.plot(xs, ys, color=c, lw=2.6, zorder=11, solid_capstyle="round", solid_joinstyle="round")
    # rozet: en uzun segmentin ortası
    seg = max(range(len(pts) - 1), key=lambda i: math.dist(pts[i], pts[i + 1]))
    mx, my = (pts[seg][0] + pts[seg + 1][0]) / 2, (pts[seg][1] + pts[seg + 1][1]) / 2
    if no in BPOS: mx, my = BPOS[no]
    badge(mx, my, no, GRN if isinstance(no, str) else c)
for (x, y) in ((-12.5, 0.5), (-5.5, 0.5)):
    ax.add_patch(Circle((x, y), 0.8, fc="white", ec="#666", lw=1, zorder=12, ls="--"))
ax.text(-9.0, 0.6, "pilden\n(B'nin\narkası)", ha="center", va="center", fontsize=6, color="#555", zorder=12)

# ============ tablo ============
tb.text(0.2, 15.5, "Kesim boyları (yol + 1 cm pay)", fontsize=13, fontweight="bold")
tb.text(0.2, 15.0, "Boyu kablonun soyulmamış kısmı. Fazlası kasada sorun çıkarır.", fontsize=8.5, color="#555")
y = 14.3
for no, c, pts, extra, desc in wires:
    L = plen(pts) + extra + 10
    cm = math.ceil(L / 5) * 0.5
    badge(0.55, y, no, GRN if isinstance(no, str) else c, r=0.22, a=tb, fs=8)
    tb.text(1.0, y, desc, va="center", fontsize=9.5)
    tb.text(9.7, y, f"{cm:.1f} cm", va="center", ha="right", fontsize=9.5, fontweight="bold")
    y -= 0.58
y -= 0.2
notes = ["• Ekran kabloları (1-7) A çerçevesinin alt açıklığından iner, kartların üstünden geçer.",
         "• Düğüm = o hattaki kabloları bur, lehimle, makaron çek, oluğa yatır.",
         "• Dirençleri oluklara yatır. Alt uçları birleşir (orta nokta), oradan 10 GPIO1'e.",
         "• TP4056'daki iki \"+\" ped aynı hat: hangisi yakınsa kullan.",
         "• Ped sıraları kartına göre biraz farklı olabilir; yazılara bak, yolu ona göre kaydır.",
         "• Anahtar ekrana bakarken SOL yanda (modelde \"sağ\" dediğim taraf, arkadan bakınca sağ)."]
for n in notes:
    tb.text(0.2, y, n, fontsize=8.5, va="top", wrap=True); y -= 0.55
plt.savefig("kablo_yerlesim.png", dpi=100, facecolor="white", bbox_inches="tight")
print("ok")
