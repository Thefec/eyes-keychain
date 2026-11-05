"""TTF -> VLW (LovyanGFX/TFT_eSPI yumuşak yazı tipi) + C dizisi."""
import struct, sys
from PIL import Image, ImageDraw, ImageFont
MAIN = "/usr/share/fonts/opentype/inter/Inter-SemiBold.otf"
FALLBACK = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SIZE = int(sys.argv[1]) if len(sys.argv) > 1 else 19
chars = [chr(c) for c in range(32, 127)]
chars += list("ÇçĞğİıÖöŞşÜü")
chars += [chr(c) for c in range(0xC0, 0x100) if chr(c) not in "×÷"]
chars += list("‘’“”–—…•♪°·")
chars = sorted(set(chars), key=ord)
fm, ff = ImageFont.truetype(MAIN, SIZE), ImageFont.truetype(FALLBACK, SIZE)
asc, desc = fm.getmetrics()
from fontTools.ttLib import TTFont
CMAP = TTFont(MAIN).getBestCmap()
glyphs = []
for ch in chars:
    f = fm
    if ch != " " and ord(ch) not in CMAP:
        f = ff
    adv = int(round(f.getlength(ch)))
    pad = SIZE
    W = H = SIZE * 3
    im = Image.new("L", (W, H), 0)
    base = pad + asc
    ImageDraw.Draw(im).text((pad, base), ch, font=f, fill=255, anchor="ls")
    bb = im.getbbox()
    if ch == " " or bb is None:
        glyphs.append((ord(ch), 0, 0, adv, 0, 0, b""))
        continue
    x0, y0, x1, y1 = bb
    crop = im.crop(bb)
    glyphs.append((ord(ch), y1 - y0, x1 - x0, adv, base - y0, x0 - pad, crop.tobytes()))
out = struct.pack(">6i", len(glyphs), 11, SIZE, 0, asc, desc)
for u, h, w, adv, dy, dx, _ in glyphs:
    out += struct.pack(">7i", u, h, w, adv, dy, dx, 0)
for g in glyphs:
    out += g[6]
open(f"tr{SIZE}.vlw", "wb").write(out)
# C dizisi
lines = []
for i in range(0, len(out), 24):
    lines.append("  " + ",".join(f"0x{b:02X}" for b in out[i:i + 24]) + ",")
open(f"tr{SIZE}.h", "w").write(f"// Inter SemiBold {SIZE}px, Türkçe + Latin-1, {len(glyphs)} karakter, {len(out)} bayt\n"
                                 f"static const uint8_t FONT_TR[] PROGMEM = {{\n" + "\n".join(lines) + "\n};\n")
print(len(glyphs), "glif,", len(out), "bayt, ascent", asc, "descent", desc)
# önizleme: VLW'yi geri okuyup bir satır yaz
def render(text, data):
    n, ver, size, _, a, d = struct.unpack(">6i", data[:24])
    met, off = {}, 24 + n * 28
    for k in range(n):
        u, h, w, adv, dy, dx, _ = struct.unpack(">7i", data[24 + k * 28: 52 + k * 28])
        met[u] = (h, w, adv, dy, dx, off); off += h * w
    img = Image.new("L", (sum(met.get(ord(c), (0, 0, size // 3, 0, 0, 0))[2] for c in text) + 10, size + 10), 0)
    x, base = 5, 5 + a
    for c in text:
        h, w, adv, dy, dx, o = met.get(ord(c), (0, 0, size // 3, 0, 0, 0))
        if h and w:
            g = Image.frombytes("L", (w, h), data[o:o + h * w])
            img.paste(g, (x + dx, base - dy), g)
        x += adv
    return img
render("Şebnem Ferah - Sil Baştan ♪ İĞÜÇÖ ığüçö", out).resize((None, None) if False else render("Şebnem Ferah - Sil Baştan ♪ İĞÜÇÖ ığüçö", out).size).save("preview.png")
