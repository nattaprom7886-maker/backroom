from PIL import Image, ImageDraw
import math, random

N = 64          # texture size
P = 32          # one stripe + field period (px)
random.seed(11)

BASE   = (203, 197, 94)
FIELD2 = (198, 192, 88)    # faint tonal shift inside a field
MOTIF  = (181, 175, 76)    # chevrons
MOTIF2 = (188, 182, 82)    # second, lighter chevron
BAND   = (192, 186, 82)    # stripe band
BANDE  = (170, 164, 68)    # band edge lines
DASH   = (216, 210, 118)   # dashes in the band
TICK   = (178, 172, 72)

def clamp(v): return max(0, min(255, int(v)))

def periodic_noise(n, seed, cells=4):
    """tileable low-frequency blotches in -1..1"""
    r = random.Random(seed)
    ph = [(r.random()*6.283, r.random()*6.283, r.randint(1,cells), r.randint(1,cells)) for _ in range(5)]
    out = [[0.0]*n for _ in range(n)]
    for x in range(n):
        for y in range(n):
            v = 0
            for a,b,fx,fy in ph:
                v += math.sin(2*math.pi*fx*x/n + a) * math.sin(2*math.pi*fy*y/n + b)
            out[x][y] = v/5
    return out

def chevron(d, cx, y, w, h, t, col):
    d.polygon([(cx, y), (cx+w, y+h), (cx+w, y+h+t), (cx, y+t), (cx-w, y+h+t), (cx-w, y+h)], fill=col)

def base_wallpaper():
    im = Image.new("RGB", (N, N), BASE)
    d = ImageDraw.Draw(im)
    for col in range(N // P):
        x0 = col * P
        # field tint (slightly different tone, 24 px wide) and stripe band (6 px wide) at the left of every period
        d.rectangle([x0+6, 0, x0+P-1, N-1], fill=FIELD2)
        d.rectangle([x0, 0, x0+5, N-1], fill=BAND)
        d.line([(x0, 0), (x0, N-1)], fill=BANDE)
        d.line([(x0+5, 0), (x0+5, N-1)], fill=BANDE)
        # dashes + diamond ticks inside the band, every 8 px
        for y in range(0, N, 8):
            d.line([(x0+2, y+1), (x0+2, y+4)], fill=DASH)
            d.line([(x0+3, y+1), (x0+3, y+4)], fill=DASH)
            d.point((x0+2, y+6), fill=TICK); d.point((x0+3, y+6), fill=TICK)
        # chevron pairs: alternate columns are offset by half a repeat
        cx = x0 + 6 + (P-6)//2
        off = 0 if col % 2 == 0 else 16
        for k in range(2):
            y = (off + k*32) % N
            for dy in (0, -N):             # wrap so the tile stays seamless
                chevron(d, cx, y+dy+4,  9, 7, 4, MOTIF)
                chevron(d, cx, y+dy+13, 9, 7, 2, MOTIF2)
    return im

def finish(im, seed=3):
    """fabric weave, soft aged blotches, fine grain. all tileable."""
    px = im.load()
    blot = periodic_noise(N, seed)
    r = random.Random(seed*7)
    for x in range(N):
        for y in range(N):
            c = px[x, y]
            weave = -3 if (x + y) % 2 == 0 else 0           # fine cross-hatch
            weave += -2 if x % 4 == 0 else 0                # vertical thread rows
            g = r.randint(-3, 3)
            b = blot[x][y] * 5
            px[x, y] = (clamp(c[0]+weave+g+b), clamp(c[1]+weave+g+b), clamp(c[2]+weave+g+b*0.6))
    return im

def alpha(im, x, y, col, a):
    px = im.load()
    c = px[x, y]
    px[x, y] = tuple(clamp(c[i]*(1-a) + col[i]*a) for i in range(3))

# ---------- damaged variants (restrained; each is one small feature, nothing near the edges) ----------
def stained(im):
    cx, cy = 38, 18
    nz = periodic_noise(N, 21, 6)
    for x in range(N):
        for y in range(N):
            dd = math.hypot((x-cx)/11.0, (y-cy)/8.0) + nz[x][y]*0.35
            if dd < 1.0:
                alpha(im, x, y, (152, 120, 54), 0.30*(1-dd)**0.6)
            elif dd < 1.12:
                alpha(im, x, y, (136, 104, 44), 0.16)      # faint dried tide line
    for y in range(cy, 56):                                  # drip trail fading downward
        a = 0.26*(1 - (y-cy)/38)
        alpha(im, cx-1, y, (152, 120, 54), a); alpha(im, cx, y, (152, 120, 54), a*0.7)
    return im

def moldy(im):
    r = random.Random(9)
    nz = periodic_noise(N, 33, 5)
    for x in range(8, 56):
        for y in range(36, 62):
            dens = (y-36)/26.0 * (0.5 + 0.5*nz[x][y]) * (1 - abs(x-30)/26.0)
            if r.random() < dens*0.28:
                alpha(im, x, y, (46, 54, 32), 0.75)
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    if r.random() < 0.5 and 0 <= x+dx < N and 0 <= y+dy < N:
                        alpha(im, x+dx, y+dy, (80, 88, 48), 0.35)
            elif r.random() < dens*0.10:
                alpha(im, x, y, (96, 104, 56), 0.25)
    return im

def peeling(im):
    px = im.load()
    for x in range(N):
        for y in range(N):
            t = (x-38) + (24-y)          # distance from the diagonal tear line
            if x > 38 and y < 24 and 0 < t < 26:
                if t < 16:
                    v = 222 - int(t*1.2)                         # bare backing paper, darkening toward the tear
                    px[x, y] = (v, v-6, v-34)
                elif t < 19:
                    k = (t-16)/3.0                               # curled strip: light edge fading to shadow
                    px[x, y] = (int(214-40*k), int(208-40*k), int(110-40*k))
                elif t < 22:
                    alpha(im, x, y, (92, 88, 36), 0.55 - (t-19)*0.15)   # shadow cast on the wall
    for y in range(24, 36):                                      # seam lifting below the tear
        alpha(im, 39, y, (120, 114, 48), 0.30*(1-(y-24)/12))
    return im

def scratched(im):
    d = ImageDraw.Draw(im)
    for (x1, y1, x2, y2) in [(14, 12, 30, 40), (18, 12, 34, 40), (22, 14, 36, 38)]:
        d.line([(x1, y1), (x2, y2)], fill=(222, 214, 150))
        d.line([(x1+1, y1), (x2+1, y2)], fill=(168, 150, 62))
    return im

out = {
  "wallpaper": lambda: finish(base_wallpaper()),
  "wallpaper_stained": lambda: stained(finish(base_wallpaper())),
  "wallpaper_moldy": lambda: moldy(finish(base_wallpaper())),
  "wallpaper_peeling": lambda: peeling(finish(base_wallpaper())),
  "wallpaper_scratched": lambda: scratched(finish(base_wallpaper())),
}
import os
os.makedirs("out", exist_ok=True)
for name, fn in out.items():
    fn().save(f"out/{name}.png")

# preview: 3x3 tiled plain wallpaper + one of each variant
prev = Image.new("RGB", (N*3*2, N*3*2), (0,0,0))
base = Image.open("out/wallpaper.png")
for i in range(6):
    for j in range(6):
        prev.paste(base, (i*N, j*N))
prev.save("preview_tiled.png")
sheet = Image.new("RGB", (N*5+4*4, N), (20,20,20))
for i, name in enumerate(out):
    sheet.paste(Image.open(f"out/{name}.png"), (i*(N+4), 0))
sheet = sheet.resize((sheet.width*3, sheet.height*3), Image.NEAREST)
sheet.save("preview_variants.png")
