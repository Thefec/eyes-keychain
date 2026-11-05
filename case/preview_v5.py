import numpy as np, trimesh, matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
src = open("/home/claude/eyes_keychain_case/render_preview.py").read()
exec(src[src.index("def rasterize"):src.index("views = [")])
P = dict(trimesh.load("_assembly.glb", force="scene").geometry)
CF, CB, CBB = (0.30, 0.79, 0.94), (0.96, 0.64, 0.38), (0.78, 0.55, 0.85)
views = [("Sağ yan: büyük pencere + parmak girintisi", [(P["front"], CF), (P["back"], CB)], 75, 10),
         ("Arka parça içi: anahtar yuvası (sağda)", [(P["back"], CB), (P["c_anahtar gövdesi"], (0.3, 0.3, 0.3))], 205, -50),
         ("Ayırıcı B: direnç (sol) + düğüm (orta) olukları", [(P["ayirici_B"], CBB)], 200, -45),
         ("B + kartlar", [(P["ayirici_B"], CBB), (P["c_ESP32-C3"], (0.9, 0.22, 0.27)), (P["c_TP4056"], (0.62, 0.31, 0.87))], 200, -45)]
fig = plt.figure(figsize=(16, 6), dpi=100)
for i, (t, ms, az, el) in enumerate(views):
    ax = fig.add_subplot(1, 4, i + 1); ax.imshow(rasterize(ms, az, el, size=480)); ax.set_title(t, fontsize=9.5); ax.axis("off")
plt.tight_layout(); plt.savefig("preview_v5.png", facecolor="white")
