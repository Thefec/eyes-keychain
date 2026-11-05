import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Circle, Rectangle

RED, BLK, ORG, BLU, GRY = "#d62828", "#1b1b1b", "#f08c00", "#1d70e0", "#6c757d"
SIG = {"SCL": "#7b2cbf", "SDA": "#2a9d8f", "DC": "#d81b60", "RES": "#8d6e63"}
fig = plt.figure(figsize=(17, 15.5))
ax = fig.add_axes([0.0, 0.25, 1.0, 0.75]); ax.set_xlim(0, 23); ax.set_ylim(0.6, 16.6); ax.set_aspect("equal"); ax.axis("off")
tb = fig.add_axes([0.02, 0.0, 0.96, 0.25]); tb.set_xlim(0, 23); tb.set_ylim(0, 6); tb.axis("off")

def box(x0, y0, x1, y1, fc, ec, ls="-", lw=1.8, z=2):
    ax.add_patch(FancyBboxPatch((x0, y0), x1 - x0, y1 - y0, boxstyle="round,pad=0,rounding_size=0.15", fc=fc, ec=ec, lw=lw, ls=ls, zorder=z))
def hole(x, y, fc="white", r=0.15): ax.add_patch(Circle((x, y), r, fc=fc, ec="#333", lw=1.2, zorder=8))
def wire(pts, c, lw=3.2):
    xs, ys = zip(*pts)
    ax.plot(xs, ys, color="white", lw=lw + 4.5, zorder=4, solid_capstyle="butt")
    ax.plot(xs, ys, color=c, lw=lw, zorder=5, solid_capstyle="round", solid_joinstyle="round")
def badge(x, y, n, c, a=None, r=0.27, fs=11):
    a = a or ax
    a.add_patch(Circle((x, y), r, fc=c, ec="white", lw=1.5, zorder=10))
    a.text(x, y, str(n), color="white", ha="center", va="center", fontsize=fs, fontweight="bold", zorder=11)
def two(x, y):   # "bu deliğe 2 şey girer" işareti
    ax.add_patch(Circle((x, y), 0.3, fc="none", ec="#e85d04", lw=2.2, zorder=9, ls=(0, (2, 1.2))))

ax.text(11.5, 16.25, "Düğümsüz bağlantı şeması", ha="center", fontsize=20, fontweight="bold")
ax.text(11.5, 15.8, "Kartlar senin elinde tuttuğun gibi: C3 düğmeli yüzü sana bakıyor, USB yukarıda.  Daire içindeki numara = lehim sırası.", ha="center", fontsize=10.5, color="#444")

# ---------- C3 ----------
box(7.5, 6.3, 11.5, 13.6, "#fde8e8", "#a33")
ax.add_patch(Rectangle((8.8, 13.6), 1.4, 0.45, fc="#cfd4da", ec="#555", lw=1.2, zorder=3)); ax.text(9.5, 13.82, "USB-C", ha="center", va="center", fontsize=8)
for bx, lab in ((8.5, "BOOT"), (10.5, "RST")):
    ax.add_patch(Rectangle((bx - 0.3, 12.7), 0.6, 0.5, fc="#ddd", ec="#777", lw=1, zorder=3)); ax.text(bx, 12.5, lab, ha="center", fontsize=7, zorder=3)
ax.text(9.5, 7.2, "ESP32-C3\nSuper Mini", ha="center", va="center", fontsize=11, fontweight="bold", zorder=3)
R = [("5V", 13.0), ("G", 12.2), ("3.3", 11.4), ("4", 10.6), ("3", 9.8), ("2", 9.0), ("1", 8.2), ("0", 7.4)]
L = [("5", 13.0), ("6", 12.2), ("7", 11.4), ("8", 10.6), ("9", 9.8), ("10", 9.0), ("20", 8.2), ("21", 7.4)]
usedR = {"5V": RED, "G": BLK, "3.3": ORG, "4": SIG["SCL"], "1": BLU}
usedL = {"6": SIG["SDA"], "7": SIG["DC"], "10": SIG["RES"]}
for n, y in R: hole(11.5, y, usedR.get(n, "#eee")); ax.text(11.25, y, n, ha="right", va="center", fontsize=9, fontweight="bold" if n in usedR else "normal", zorder=8)
for n, y in L: hole(7.5, y, usedL.get(n, "#eee")); ax.text(7.75, y, n, ha="left", va="center", fontsize=9, fontweight="bold" if n in usedL else "normal", zorder=8)

# ---------- dirençler (C3 arka yüzünde) ----------
def resistor(x, y0, y1, lab, n, c):
    ax.add_patch(Rectangle((x - 0.17, y0), 0.34, y1 - y0, fc="#fff3d6", ec="#555", lw=1.3, zorder=7, ls="--"))
    ax.text(x - 0.32, (y0 + y1) / 2, lab, ha="right", va="center", fontsize=8, rotation=90, zorder=7)
    badge(x, (y0 + y1) / 2, n, c, r=0.24, fs=9)
resistor(10.55, 9.3, 11.9, "100K", 8, RED)
ax.plot([11.5, 10.55, 10.55], [13.0, 13.0, 11.9], color="#666", lw=1.6, zorder=6, ls="--")
ax.plot([10.55, 10.55, 11.5], [9.3, 8.2, 8.2], color="#666", lw=1.6, zorder=6, ls="--")
resistor(9.85, 8.9, 11.5, "100K", 9, BLK)
ax.plot([11.5, 9.85, 9.85], [12.2, 12.2, 11.5], color="#666", lw=1.6, zorder=6, ls="--")
ax.plot([9.85, 9.85, 10.55], [8.9, 8.2, 8.2], color="#666", lw=1.6, zorder=6, ls="--")
ax.text(9.5, 6.55, "dirençler kartın ARKA yüzünde (kesikli)", ha="center", fontsize=7.5, color="#7a5c00", zorder=8)

# ---------- TP4056 ----------
box(14.2, 6.8, 18.8, 11.0, "#efe6fb", "#7b4bb3")
ax.text(16.5, 8.2, "TP4056", ha="center", va="center", fontsize=12, fontweight="bold")
ax.add_patch(Rectangle((15.8, 6.35), 1.4, 0.45, fc="#cfd4da", ec="#555", lw=1.2, zorder=3)); ax.text(16.5, 6.57, "USB-C", ha="center", va="center", fontsize=8)
TP = {"+": 14.7, "+ (isimsiz)": 15.85, "B−": 17.15, "OUT−": 18.3}
for k, x in TP.items():
    ax.add_patch(Rectangle((x - 0.28, 10.55), 0.56, 0.4, fc="#e9c46a", ec="#555", lw=1, zorder=8))
    ax.text(x, 10.25, k, ha="center", va="center", fontsize=7.5, fontweight="bold", zorder=8)
ax.text(16.5, 9.6, "(ped adları kartının\nüzerindeki yazıya göre)", ha="center", fontsize=7, color="#555")

# ---------- anahtar ----------
box(14.0, 13.0, 17.2, 13.8, "#d9d9d9", "#444")
ax.text(15.6, 13.4, "ANAHTAR", ha="center", va="center", fontsize=9, fontweight="bold", zorder=3)
SW = {"B": 14.5, "ORTA": 15.6, "A (boş)": 16.7}
for k, x in SW.items():
    ax.plot([x, x], [13.0, 12.55], color="#444", lw=2.5, zorder=3); hole(x, 12.5, "white", 0.13)
    ax.text(x, 12.15, k, ha="center", fontsize=7.5, zorder=8)

# ---------- pil ----------
box(19.8, 7.0, 22.4, 11.0, "#eef1f6", "#555")
ax.text(21.1, 9.0, "PİL\n3,7 V", ha="center", va="center", fontsize=11, fontweight="bold")
ax.text(21.1, 6.6, "sarı ince kablo: BAĞLAMA", ha="center", fontsize=7.5, color="#777", style="italic")

# ---------- ekran ----------
box(5.0, 1.0, 14.0, 3.6, "#e6f6f3", "#2a9d8f")
ax.text(9.5, 1.6, "EKRAN (arkası, pinler yukarıda)", ha="center", fontsize=10, fontweight="bold")
D = {"GND": 5.8, "VCC": 7.0, "SCL": 8.2, "SDA": 9.4, "RES": 10.6, "DC": 11.8, "BLK": 13.0}
for k, x in D.items():
    hole(x, 3.6, "white"); ax.text(x, 3.15, k, ha="center", fontsize=8.5, fontweight="bold", zorder=8)

# ---------- KABLOLAR ----------
# 1-4 sinyaller
wire([(D["SCL"], 3.6), (D["SCL"], 4.4), (12.9, 4.4), (12.9, 10.6), (11.5, 10.6)], SIG["SCL"]); badge(12.9, 7.6, 1, SIG["SCL"])
wire([(D["SDA"], 3.6), (D["SDA"], 5.6), (6.3, 5.6), (6.3, 12.2), (7.5, 12.2)], SIG["SDA"]); badge(6.3, 10.0, 2, SIG["SDA"])
wire([(D["DC"], 3.6), (D["DC"], 5.2), (5.9, 5.2), (5.9, 11.4), (7.5, 11.4)], SIG["DC"]); badge(5.9, 8.4, 3, SIG["DC"])
wire([(D["RES"], 3.6), (D["RES"], 4.8), (6.7, 4.8), (6.7, 9.0), (7.5, 9.0)], SIG["RES"]); badge(6.7, 6.9, 4, SIG["RES"])
# 5 VCC -> 3.3
wire([(D["VCC"], 3.6), (D["VCC"], 4.1), (13.3, 4.1), (13.3, 11.4), (11.5, 11.4)], ORG); badge(13.3, 9.0, 5, ORG)
# 6 ekran GND -> TP OUT-
wire([(D["GND"], 3.6), (D["GND"], 0.95)], BLK)
wire([(D["GND"], 0.95), (19.3, 0.95), (19.3, 11.6), (TP["OUT−"], 11.6), (TP["OUT−"], 10.95)], BLK); badge(16.0, 0.95, 6, BLK)
# 7 C3 G -> TP OUT-
wire([(11.5, 12.2), (13.7, 12.2), (13.7, 11.95), (TP["OUT−"] - 0.15, 11.95), (TP["OUT−"] - 0.15, 10.95)], BLK); badge(16.0, 11.95, 7, BLK)
# 10 C3 5V -> anahtar B
wire([(11.5, 13.0), (12.4, 13.0), (12.4, 14.4), (13.6, 14.4), (13.6, 12.5), (SW["B"], 12.5)], RED); badge(12.4, 14.0, 10, RED)
# 11 TP + (isimsiz) -> anahtar ORTA
wire([(TP["+ (isimsiz)"], 10.95), (TP["+ (isimsiz)"], 11.3), (15.6, 11.3), (15.6, 12.5)], RED); badge(15.15, 11.3, 11, RED)
# 12 pil - -> B-, 13 pil + -> +
wire([(19.8, 9.6), (19.6, 9.6), (19.6, 11.3), (TP["B−"], 11.3), (TP["B−"], 10.95)], BLK); badge(18.6, 11.3, 12, BLK)
wire([(19.8, 10.6), (20.1, 10.6), (20.1, 14.9), (14.0, 14.9), (13.85, 14.9), (13.85, 11.6), (TP["+"], 11.6), (TP["+"], 10.95)], RED); badge(17.5, 14.9, 13, RED)
ax.text(19.55, 9.6, "−", fontsize=12, ha="right", va="center", fontweight="bold"); ax.text(19.55, 10.6, "+", fontsize=12, ha="right", va="center", color=RED, fontweight="bold")
# BLK
ax.annotate("BLK: önce BOŞ bırak.\nArka ışık yanmazsa\nVCC'den köprü çek", (D["BLK"], 3.75), (14.6, 4.6), fontsize=8.5, color="#555",
            arrowprops=dict(arrowstyle="->", color="#999", lw=1))
# "2 şey girer" işaretleri
for (x, y) in ((11.5, 13.0), (11.5, 12.2), (11.5, 8.2)): two(x, y)
two(TP["OUT−"], 10.75)

# ---------- liste ----------
tb.text(0.2, 5.6, "Lehim sırası", fontsize=14, fontweight="bold")
rows1 = [(1, SIG["SCL"], "Ekran SCL  →  C3 4"), (2, SIG["SDA"], "Ekran SDA  →  C3 6"), (3, SIG["DC"], "Ekran DC  →  C3 7"),
         (4, SIG["RES"], "Ekran RES  →  C3 10"), (5, ORG, "Ekran VCC  →  C3 3.3"), (6, BLK, "Ekran GND  →  TP4056 OUT−"), (7, BLK, "C3 G  →  TP4056 OUT−")]
rows2 = [(8, RED, "100K: bir bacak C3 5V, diğeri C3 1 (arka yüzde)"), (9, BLK, "100K: bir bacak C3 1, diğeri C3 G (arka yüzde)"),
         (10, RED, "C3 5V  →  Anahtar B"), (11, RED, "TP4056 \"+\" (isimsiz)  →  Anahtar ORTA"),
         (12, BLK, "Pil siyah  →  TP4056 B−   (EN SON)"), (13, RED, "Pil kırmızı  →  TP4056 \"+\"   (EN SON)")]
for i, (n, c, t) in enumerate(rows1):
    badge(0.55, 4.9 - i * 0.62, n, c, a=tb, r=0.22, fs=9); tb.text(1.0, 4.9 - i * 0.62, t, va="center", fontsize=11)
for i, (n, c, t) in enumerate(rows2):
    badge(7.6, 4.9 - i * 0.62, n, c, a=tb, r=0.22, fs=9); tb.text(8.05, 4.9 - i * 0.62, t, va="center", fontsize=11)
tb.add_patch(Circle((16.0, 4.9), 0.25, fc="none", ec="#e85d04", lw=2.2, ls=(0, (2, 1.2))))
notes = ["= bu deliğe/pede 2 şey girer. İkisini birlikte sok,", "   tek seferde lehimle.",
         "• Hiçbir yerde 3'lü düğüm yok.", "• Kabloyu deliğin içinden geçir,", "   arka taraftan lehimle.",
         "• İki \"+\" ped aynı hat; isimler kartına", "   göre değişebilir, yazıya bak.",
         "• Pil bağlarken anahtar KAPALI olsun."]
for i, t in enumerate(notes): tb.text(16.4, 4.9 - i * 0.6, t, va="center", fontsize=10.5)
plt.savefig("dugumsuz_sema.png", dpi=100, facecolor="white", bbox_inches="tight")
print("ok")
