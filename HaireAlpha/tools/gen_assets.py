#!/usr/bin/env python3
"""Generates the versioned look/lens/calibration assets (single editable source of truth).
All looks are ORIGINAL transforms. 'Inspired' names describe an aesthetic direction only;
no proprietary film-stock or camera-maker colour data is used or claimed."""
import json, os
OUT = os.path.join(os.path.dirname(__file__), '..', 'imaging', 'src', 'main', 'resources', 'haire')
os.makedirs(OUT, exist_ok=True)

def band(c, w=40, hs=0.0, sat=1.0, light=0.0): return dict(center=c, width=w, hueShift=hs, sat=sat, light=light)
def tint(h, a): return dict(hue=h, amount=a)
def tone(**k): return k
def grain(a, s=1.0, r=0.5): return dict(amount=a, size=s, roughness=r)

# Oklch hue reference: red≈29  orange≈55-70  skin≈45-70  yellow≈100-110  green≈135-145  cyan≈195  sky≈235-255  blue≈264  magenta≈330
GREENS, BLUES, REDS, YELLOWS, ORANGES = 135, 250, 28, 100, 62

looks = [
 # ---------------------------------------------------------------- CREATIVE
 dict(id='alpha_natural', name='ALPHA NATURAL', category='CREATIVE',
      description='House rendering. Natural colour, rich skin, moderate contrast, smooth highlight roll-off.',
      tone=tone(contrast=1.0, midOut=0.46, shoulder=0.6), hueRestore=0.8, highlightDesat=0.5,
      saturation=1.05, chromaCompression=0.15, vibrance=0.05,
      hueBands=[band(GREENS, 60, -3, 0.92), band(BLUES, 50, -2, 0.95), band(REDS, 30, 0, 1.0)], skinProtect=0.9),
 dict(id='standard', name='STANDARD', category='CREATIVE', description='Clean everyday colour with more punch and contrast.',
      tone=tone(contrast=1.14, shadowDepth=0.06, shoulder=0.45), hueRestore=0.7, saturation=1.17, vibrance=0.05, chromaCompression=0.1,
      hueBands=[band(GREENS, 60, -2, 0.97)], skinProtect=0.9),
 dict(id='neutral', name='NEUTRAL', category='CREATIVE', description='Flat, honest colour with maximum latitude for editing.',
      tone=tone(contrast=0.82, midOut=0.46, shoulder=0.85), hueRestore=0.9, highlightDesat=0.35, saturation=0.9, sharpening=0.8, skinProtect=0.95),
 dict(id='portrait', name='PORTRAIT', category='CREATIVE', description='Soft contrast and luminous, gently warm skin for every complexion.',
      tone=tone(contrast=0.88, midOut=0.47, shoulder=0.85), warmth=1.2, saturation=0.97, hueRestore=0.9,
      hueBands=[band(58, 50, 0, 1.0, 0.014), band(REDS, 30, 0, 0.88), band(GREENS, 60, -3, 0.9)], skinProtect=1.0, sharpening=0.7),
 dict(id='vivid', name='VIVID', category='CREATIVE', description='Bold saturation and contrast, still protecting skin and highlights.',
      tone=tone(contrast=1.3, shadowDepth=0.15, shoulder=0.45), hueRestore=0.65, saturation=1.6, vibrance=0.15, chromaCompression=0.1,
      hueBands=[band(GREENS, 60, 0, 1.05), band(BLUES, 50, 0, 1.05)], skinProtect=0.8),
 dict(id='landscape', name='LANDSCAPE', category='CREATIVE', description='Crisp, cool daylight: deep skies and lush greens, protected highlights, extra definition.',
      tone=tone(contrast=1.06, midOut=0.44, shoulder=0.85), saturation=1.05, warmth=-1.8,
      hueBands=[band(GREENS, 70, 8, 1.35), band(245, 60, 7, 1.35, -0.055), band(YELLOWS, 40, 4, 1.15), band(REDS, 30, 0, 0.88)],
      sharpening=1.2, clarity=0.2, skinProtect=0.9),
 dict(id='deep', name='DEEP', category='CREATIVE', description='Dark, dense and rich: low-key rendering with heavy shadows.',
      tone=tone(contrast=1.16, midOut=0.405, shadowDepth=0.38, shoulder=0.6), saturation=1.12, warmth=1.0, chromaCompression=0.2,
      hueBands=[band(BLUES, 60, 0, 1.0, -0.03)], skinProtect=0.85),
 dict(id='soft', name='SOFT', category='CREATIVE', description='Airy, bright and pastel with lifted shadows.',
      tone=tone(contrast=0.8, midOut=0.495, shoulder=0.9, blackLift=0.03, whiteClip=0.985), saturation=0.8, warmth=-1.0, highlightDesat=0.7, sharpening=0.6, skinProtect=0.9),
 dict(id='monochrome', name='MONOCHROME', category='CREATIVE', description='Clean, gentle digital black and white.',
      tone=tone(contrast=0.92, shoulder=0.7), monochrome=dict(red=0.30, green=0.60, blue=0.10)),
 dict(id='mono_hc', name='HIGH CONTRAST MONO', category='CREATIVE', description='Graphic black and white: bright mid-tones, inky shadows.',
      tone=tone(contrast=1.5, midOut=0.49, shadowDepth=0.45, shoulder=0.35), monochrome=dict(red=0.42, green=0.50, blue=0.08), grain=grain(0.012, 1.1, 0.0)),
 dict(id='classic_chrome', name='CLASSIC CHROME INSPIRED', category='CREATIVE', inspiredBy='documentary slide-film aesthetic',
      description='Muted documentary colour, firm shadows, cyan-leaning skies.',
      tone=tone(contrast=1.08, shadowDepth=0.15, shoulder=0.55), saturation=0.78, chromaCompression=0.3, hueRestore=0.6,
      hueBands=[band(245, 60, -10, 0.78, -0.02), band(REDS, 40, 3, 0.86), band(GREENS, 60, -6, 0.76), band(YELLOWS, 40, 0, 0.85)],
      shadowTint=tint(200, 0.006), skinProtect=0.75),
 dict(id='classic_negative', name='CLASSIC NEGATIVE INSPIRED', category='CREATIVE', inspiredBy='consumer colour negative aesthetic',
      description='Hard tonality, teal-shifted greens, cool shadows and warm highlights.',
      tone=tone(contrast=1.2, shadowDepth=0.22, shoulder=0.5), saturation=0.88, hueRestore=0.4,
      hueBands=[band(GREENS, 60, 14, 0.78), band(REDS, 40, -6, 1.06), band(240, 50, -5, 0.9)],
      shadowTint=tint(195, 0.018), highlightTint=tint(65, 0.01), skinProtect=0.65),
 dict(id='cinema', name='CINEMA', category='CREATIVE', description='Soft cinematic contrast with teal shadows and warm highlights.',
      tone=tone(contrast=0.92, midOut=0.43, shoulder=0.75), saturation=0.86, chromaCompression=0.2,
      shadowTint=tint(210, 0.02), highlightTint=tint(65, 0.016), vignette=0.15, skinProtect=0.9),
 dict(id='soft_matte', name='SOFT MATTE', category='CREATIVE', description='Lifted blacks, matte highlights and cool shadows.',
      tone=tone(contrast=0.9, blackLift=0.075, whiteClip=0.93, shoulder=0.7), saturation=0.86, warmth=1.0, shadowTint=tint(230, 0.014), sharpening=0.7, skinProtect=0.9),
 dict(id='portra400', name='PORTRA 400 INSPIRED', category='FILM', inspiredBy='portrait colour negative, ISO 400',
      description='Natural skin, gentle highlights, restrained saturation, subtle warmth.',
      tone=tone(contrast=0.92, midOut=0.47, shoulder=0.85), hueRestore=0.4, saturation=0.9, warmth=2.2, chromaCompression=0.25,
      hueBands=[band(55, 50, 1, 0.98, 0.01), band(GREENS, 60, -11, 0.8), band(245, 50, -9, 0.82), band(REDS, 30, 4, 0.95)],
      highlightTint=tint(70, 0.009), shadowTint=tint(200, 0.005), grain=grain(0.012, 1.0, 0.4), skinProtect=0.85),
 dict(id='portra800', name='PORTRA 800 INSPIRED', category='FILM', inspiredBy='portrait colour negative, ISO 800',
      description='Rich skin, warmer and deeper colour, organic grain.',
      tone=tone(contrast=1.02, shadowDepth=0.18, midOut=0.445, shoulder=0.75), hueRestore=0.4, saturation=1.07, warmth=4.5, chromaCompression=0.12,
      hueBands=[band(REDS, 40, 0, 1.1), band(GREENS, 60, -7, 0.88), band(BLUES, 50, -5, 0.92)],
      shadowTint=tint(50, 0.012), grain=grain(0.02, 1.25, 0.5), skinProtect=0.85),
 dict(id='gold200', name='GOLD 200 INSPIRED', category='FILM', inspiredBy='consumer colour negative, ISO 200',
      description='Golden warmth and nostalgic everyday colour.',
      tone=tone(contrast=0.98, midOut=0.48, blackLift=0.02, shoulder=0.75), hueRestore=0.45, saturation=1.12, warmth=6.0, tint=0.5,
      hueBands=[band(YELLOWS, 50, -4, 1.18), band(GREENS, 60, -10, 0.88), band(245, 50, -8, 0.85, 0.01)],
      highlightTint=tint(80, 0.012), grain=grain(0.016, 1.1, 0.5), skinProtect=0.75),
 dict(id='ektar100', name='EKTAR 100 INSPIRED', category='FILM', inspiredBy='fine-grain saturated colour negative',
      description='Warm, rich colour separation with saturated reds and blues, crisp.',
      tone=tone(contrast=1.18, shoulder=0.55), hueRestore=0.35, saturation=1.2, chromaCompression=0.15, warmth=3.0,
      hueBands=[band(REDS, 40, 3, 1.22), band(ORANGES, 30, 0, 1.1), band(255, 50, 7, 1.22), band(140, 60, -5, 0.92)],
      highlightTint=tint(70, 0.006), grain=grain(0.007, 0.8, 0.3), sharpening=1.1, skinProtect=0.7),
 dict(id='cinestill800t', name='CINESTILL 800T INSPIRED', category='FILM', inspiredBy='tungsten-balanced motion picture stock',
      description='Tungsten-balanced night colour: teal shadows, glowing reds, optional warm halation.',
      tone=tone(contrast=1.05, midOut=0.44, shoulder=0.7), hueRestore=0.45, saturation=0.92, warmth=-1.0, tint=1.0, filmWhiteBalanceK=3200,
      hueBands=[band(REDS, 40, -6, 1.15), band(GREENS, 60, 10, 0.8)], shadowTint=tint(215, 0.016), highlightTint=tint(55, 0.01),
      halation=dict(amount=0.22, threshold=2.0, radius=0.014, hue=25), grain=grain(0.022, 1.3, 0.5), skinProtect=0.55),
 dict(id='trix400', name='TRI-X 400 INSPIRED', category='FILM', inspiredBy='classic high-speed B&W negative',
      description='Expressive documentary contrast and organic grain.',
      tone=tone(contrast=1.32, midOut=0.46, shadowDepth=0.18, shoulder=0.55), monochrome=dict(red=0.38, green=0.50, blue=0.12), grain=grain(0.03, 1.4, 0.0)),
 dict(id='hp5', name='HP5 PLUS 400 INSPIRED', category='FILM', inspiredBy='classic flexible B&W negative',
      description='Classic warm-toned print look, flexible contrast, natural texture.',
      tone=tone(contrast=1.1, midOut=0.48, shoulder=0.9), monochrome=dict(red=0.30, green=0.58, blue=0.12, toneHue=75, toneAmount=0.009), grain=grain(0.024, 1.3, 0.0)),
 dict(id='forever', name='FOREVER', category='SIGNATURE',
      description='Timeless editorial colour: natural skin, organic greens, gentle highlights, elegant contrast.',
      tone=tone(contrast=1.1, midOut=0.45, shadowDepth=0.2, shoulder=0.8), hueRestore=0.6, saturation=0.95, chromaCompression=0.22, warmth=1.2,
      hueBands=[band(132, 60, -10, 0.78, -0.012), band(245, 50, -4, 0.86), band(REDS, 34, 0, 0.96), band(YELLOWS, 40, -3, 0.93)],
      highlightTint=tint(72, 0.006), shadowTint=tint(225, 0.0055), grain=grain(0.008, 0.9, 0.4), skinProtect=0.95),
 dict(id='sunday_archive', name='SUNDAY ARCHIVE', category='SIGNATURE',
      description='Warm, faded family-album colour with soft highlights.',
      tone=tone(contrast=0.86, midOut=0.47, blackLift=0.045, whiteClip=0.95, shoulder=0.85), hueRestore=0.5, saturation=0.78, warmth=5.0, tint=1.5,
      hueBands=[band(130, 60, -10, 0.75), band(240, 50, -6, 0.8)], highlightTint=tint(75, 0.014), shadowTint=tint(40, 0.01),
      grain=grain(0.016, 1.1, 0.5), skinProtect=0.85),
 dict(id='after_hours', name='AFTER HOURS', category='SIGNATURE',
      description='The city at night: rich blacks, warm light, restrained colour.',
      tone=tone(contrast=1.1, midOut=0.41, shadowDepth=0.32, shoulder=0.7), saturation=0.9, chromaCompression=0.3, warmth=1.5,
      hueBands=[band(ORANGES, 50, -2, 1.08), band(BLUES, 50, 0, 0.85), band(140, 60, 8, 0.75)],
      shadowTint=tint(225, 0.014), halation=dict(amount=0.08, threshold=2.5, radius=0.01, hue=30), grain=grain(0.012, 1.0, 0.4), skinProtect=0.85),
 dict(id='grit', name='GRIT', category='SIGNATURE',
      description='Powerful monochrome for sport and documentary: dark skies, hard light.',
      tone=tone(contrast=1.36, midOut=0.41, shadowDepth=0.3, shoulder=0.6), monochrome=dict(red=0.70, green=0.30, blue=0.0),
      clarity=0.2, grain=grain(0.026, 1.2, 0.0), sharpening=1.1),
]
json.dump(dict(schema=1, version=1, looks=looks), open(os.path.join(OUT, 'looks.json'), 'w'), indent=1)

def prime(id, name, f, ap, cat, core=True, minAp=16.0, note=''):
    return dict(id=id, name=name, kind='PRIME', minFocal=f, maxFocal=f, maxAperture=ap, minAperture=minAp, category=cat, core=core, note=note)
def zoom(id, name, a, b, ap, cat, core=True):
    return dict(id=id, name=name, kind='ZOOM', minFocal=a, maxFocal=b, maxAperture=ap, minAperture=22.0, category=cat, core=core)
lenses = [
 prime('p24_14', '24mm F1.4 GM Inspired', 24, 1.4, 'WIDE'),
 prime('p35_14', '35mm F1.4 GM Inspired', 35, 1.4, 'STANDARD'),
 prime('p40_25', '40mm F2.5 G Inspired', 40, 2.5, 'STANDARD', minAp=22.0),
 prime('p50_12', '50mm F1.2 GM Inspired', 50, 1.2, 'STANDARD'),
 prime('p85_14', '85mm F1.4 GM II Inspired', 85, 1.4, 'PORTRAIT'),
 zoom('z24_50', '24-50mm F2.8 G Inspired', 24, 50, 2.8, 'STANDARD'),
 zoom('z24_70', '24-70mm F2.8 GM II Inspired', 24, 70, 2.8, 'STANDARD'),
 zoom('z70_200', '70-200mm F2.8 GM II Inspired', 70, 200, 2.8, 'TELE'),
 zoom('z16_35', '16-35mm F2.8 GM II Inspired', 16, 35, 2.8, 'WIDE', core=False),
 prime('p20_18', '20mm F1.8 G Inspired', 20, 1.8, 'WIDE', core=False),
 prime('p28_2', '28mm F2 Inspired', 28, 2.0, 'WIDE', core=False, minAp=22.0),
 prime('p50_14', '50mm F1.4 GM Inspired', 50, 1.4, 'STANDARD', core=False),
 prime('p90_28m', '90mm F2.8 Macro G Inspired', 90, 2.8, 'MACRO', core=False, minAp=22.0,
       note='Framing only: a virtual macro profile does not change the phone\'s minimum focus distance.'),
 prime('p135_18', '135mm F1.8 GM Inspired', 135, 1.8, 'TELE', core=False, minAp=22.0),
]
json.dump(dict(schema=1, version=1, lenses=lenses), open(os.path.join(OUT, 'lenses.json'), 'w'), indent=1)

calibration = dict(schema=1, version=1, profiles=[
 dict(id='default', version=1, source='METADATA_DERIVED',
      notes='Generic baseline for any physical camera. Colour comes from the camera\'s own DNG calibration metadata at '
            'runtime; NR/sharpening tables are conservative starting points validated on synthetic targets only. '
            'Raw exposure normalisation is anchored at runtime to the camera\'s metered preview (see docs/IMAGING.md).'),
])
json.dump(calibration, open(os.path.join(OUT, 'calibration.json'), 'w'), indent=1)
print('assets written:', len(looks), 'looks,', len(lenses), 'lenses')
