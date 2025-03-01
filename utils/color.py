import os
import pprint
import sys

import colour
import numpy as np
import PIL.Image
import PIL.ImageOps

ViewingColors = {
    'black': '000000',
    'red': 'FF0000',
    'orange': 'FF9000',
    'yellow': 'FFFF00',
    'green': '00FF00',
    'teal': '00FFFF',
    'blue': '0000FF',
    'purple': '8200FA',
    'maroon': 'B2007C',
    'pink': 'FF80E4',
    'gold': 'FECC00',
    'peach': 'FFB080',
    'beige': 'FFE5CC',
    'brown': '806434',
    'olive': '809700',
    'gray': '808080',
    'lavender': 'C17EFF',
    'magenta': '800019',
    'lime': '80F000',
    'white': 'FFFFFF',
}


def xyY_to_rgb(xyY):
    XYZ = colour.xyY_to_XYZ(xyY)
    whitepoint_c = colour.xy_to_XYZ(
        colour.CCS_ILLUMINANTS['CIE 1931 2 Degree Standard Observer']['C'])
    whitepoint_d65 = colour.xy_to_XYZ(
        colour.CCS_ILLUMINANTS['CIE 1931 2 Degree Standard Observer']['D65'])
    XYZ_adapted = colour.adaptation.chromatic_adaptation_VonKries(XYZ, whitepoint_c, whitepoint_d65)

    sRGB = colour.XYZ_to_sRGB(XYZ_adapted)
    #  same as:
    # RGB_linear = colour.XYZ_to_RGB(
    #     XYZ,
    #     colour.RGB_COLOURSPACES['sRGB'].whitepoint,
    #     colour.RGB_COLOURSPACES['sRGB'].whitepoint,
    #     colour.RGB_COLOURSPACES['sRGB'].matrix_XYZ_to_RGB
    # )
    # sRGB = colour.models.eotf_inverse_sRGB(RGB_linear)
    return sRGB


def munsell_color_to_sRGB(munsell_notation):
    xyY = colour.munsell_colour_to_xyY(munsell_notation)
    rgb = xyY_to_rgb(xyY)
    return rgb


def rgb_to_hex(rgb):
    rgb = (np.clip(rgb, 0, 1) * 255).astype(int)
    return f'{rgb[0]:02X}{rgb[1]:02X}{rgb[2]:02X}'


def munsell_table():
    colors = []
    grid = [[None for _ in range(40)] for _ in range(8)]
    gray = [None for _ in range(10)]

    achromatic_values = np.linspace(1.5, 9.5, 10)

    for gidx, value in enumerate(achromatic_values):
        munsell_notation = f'N{value:.1f}/'
        rgb = munsell_color_to_sRGB(munsell_notation)
        rgb = np.clip(rgb, 0, 1)
        colors.append(rgb_to_hex(rgb))
        gray[gidx] = rgb_to_hex(rgb)

    hues = []
    for hue_letter in ['R', 'YR', 'Y', 'GY', 'G', 'BG', 'B', 'PB', 'P', 'RP']:
        for prefix in [2.5, 5.0, 7.5, 10.0]:
            hues.append(f'{prefix} {hue_letter}')

    values = np.linspace(2.0, 9.0, 8)

    for hidx, hue in enumerate(hues):
        for vidx, value in enumerate(values):
            # Start with a moderate chroma that should exist for most hue-value
            # combinations
            chroma = 6

            # Try to find a valid chroma for this hue-value combination
            valid_color = False
            while not valid_color and chroma > 0:
                try:
                    munsell_notation = f'{hue} {value:.1f}/{chroma:.1f}'
                    rgb = munsell_color_to_sRGB(munsell_notation)

                    # Check if RGB values are valid (not NaN and within range
                    # after clipping)
                    rgb_clipped = np.clip(rgb, 0, 1)
                    if not np.isnan(rgb).any() and np.allclose(rgb, rgb_clipped, atol=0.2):
                        valid_color = True
                        colors.append(rgb_to_hex(rgb_clipped))
                        grid[7 - vidx][hidx] = rgb_to_hex(rgb_clipped)
                    else:
                        chroma -= 1
                except Exception:
                    chroma -= 1

            if not valid_color:
                print(f'Could not find valid color for {hue} {value:.1f}/')
    return colors, grid, gray


def image_to_colors(image_path):
    """
    Converts the given image arays for each color
    """
    img = PIL.Image.open(image_path).convert('RGB')

    results = {}
    xval = [18, 335, 652, 969]
    yval = [48, 208, 368, 528, 688]
    wval = 290
    hval = 106
    seq = ['red', 'yellow', 'green', 'blue', 'orange', 'pink', 'brown',
           'purple', 'peach', 'teal', 'lavender', 'maroon', 'gold', 'beige',
           'magenta', 'lime', 'olive']
    # beige is also tan (but tan can also be olive)
    # teal is also turquoise
    # For reduction to 11:
    #  peach -> pink
    #  teal -> green? maybe split with blue
    #  lavender -> purple
    #  maroon -> red
    #  gold -> orange
    #  beige -> yellow
    #  magenta -> purple
    #  lime -> green
    #  olive -> brown
    for cidx, clr in enumerate(seq):
        x = xval[cidx % len(xval)]
        y = yval[cidx // len(xval)]
        subimg = img.crop((x + 1, y + 1, x + wval - 2, y + hval - 2)).convert('L')
        subimg = PIL.ImageOps.autocontrast(subimg)
        subimg = subimg.resize((40, 8), PIL.Image.Resampling.LANCZOS)
        results[clr] = np.clip((np.array(subimg) / 255.0) * 1.2 - 0.1, 0, 1).tolist()
    return results


def rgb_categories(labrgb, labcat, catvals):
    """
    Compute categoric values
    """
    labrgb = labrgb.reshape(-1, 3)
    cats = np.zeros((labrgb.shape[0], ), dtype=np.uint8)
    chunksize = 65536
    for chunk in range(0, labrgb.shape[0], chunksize):
        print(chunk // chunksize)
        # Calculate the Euclidean distance between each color
        distances = np.linalg.norm(labrgb[chunk:chunk + chunksize, np.newaxis] - labcat, axis=2)
        indices = np.argmin(distances, axis=1)
        cats[chunk:chunk + chunksize] = catvals[indices]
    return cats.reshape(256, 256, 256)


# This image is Figure 9 of
#   Lindsey, D. T., and A. M. Brown. 2014. "The Color Lexicon of American
#   English." Journal of Vision. Association for Research in Vision and
#   Ophthalmology (ARVO). https://doi.org/10.1167/14.2.17.
# Accessed via https://jov.arvojournals.org/article.aspx?articleid=2121523
#   I think I could use Figure 5 to extract BCT11 (rather than this, which is
# BCT20).
image_path = 'i1534-7362-14-2-17-f09.jpeg'
results = image_to_colors(image_path)
_, hexgrid, hexgray = munsell_table()

table = [[None for _ in range(40)] for _ in range(8)]
maxt = [[0.1 for _ in range(40)] for _ in range(8)]
# Set defaults for where the certainty appears to be less than 0.1
for x in range(len(table[0])):
    table[0][x] = 'white'
    table[1][x] = 'gy'
    table[-2][x] = 'gy'
    table[-1][x] = 'bk'
for clr, grid in results.items():
    for y, row in enumerate(grid):
        for x, val in enumerate(row):
            if val > maxt[y][x]:
                maxt[y][x] = val
                table[y][x] = clr
# There isn't a corresponding figure in the paper for the neutrals.  Maybe
# Figure 5 supports the top one or two being white and the bottom two to four
# being black.
graytbl = ['bk', 'bk', 'gy', 'gy', 'gy', 'gy', 'gy', 'gy', 'white', 'white']

print(results)
print(table)
for row in table:
    print(''.join([str(val)[:2] for val in row]))
if len(sys.argv) >= 2:
    for row in table:
        print(''.join([str(val)[:2] if str(val)[:2] == sys.argv[1] else '  ' for val in row]))
# Prepopulate this with colors at some extremes
hexdict = {
    'FFFFFF': 'white',
    '000000': 'bk',
    'FF0000': 'red',
    '00FF00': 'green',
    '0000FF': 'blue',
    '00FFFF': 'teal',
    'FF00FF': 'purple',
    'FFFF00': 'yellow',
}
for hx, val in zip(hexgray, graytbl):
    hexdict[hx] = val
for y in range(len(table)):
    for hx, val in zip(hexgrid[y], table[y]):
        hexdict[hx] = val
pprint.pprint(hexdict)

labdict = {idx: colour.XYZ_to_Lab(colour.sRGB_to_XYZ(
    [int(hx[i:i + 2], 16) / 255 for i in range(0, 6, 2)]))
    for idx, hx in enumerate(hexdict)}
cats = {}
labcat = []
labcatidx = []
for clr in ViewingColors:
    cat = clr
    cats[cat] = len(cats)
for hx, cat in hexdict.items():
    cat = {'bk': 'black', 'gy': 'gray'}.get(cat, cat)
    labval = colour.XYZ_to_Lab(colour.sRGB_to_XYZ(
        [int(hx[i:i + 2], 16) / 255 for i in range(0, 6, 2)]))
    labcat.append(labval)
    if cat not in cats:
        cats[cat] = len(cats)
    labcatidx.append(cats[cat])
labcat = np.array(labcat)

if os.path.exists('labrgb.npz'):
    labrgb = np.load('labrgb.npz')['arr_0']
else:
    labrgb = np.zeros((256 ** 3, 3), dtype=float)
    for r in range(256):
        print(r)
        for g in range(256):
            for b in range(256):
                labrgb[r * 65536 + g * 256 + b, :3] = colour.XYZ_to_Lab(
                    colour.sRGB_to_XYZ([r / 255, g / 255, b / 255]))
    np.savez('labrgb.npz', labrgb)

catrgb = rgb_categories(labrgb, labcat, np.array(labcatidx))
counts = np.bincount(catrgb.flatten())
print([k.capitalize() for k in cats.keys()])
pprint.pprint({counts[idx]: k.capitalize() for idx, k in enumerate(cats.keys())})
print(list(cats.keys())[catrgb[0x81][0x71][0x67]])
img = PIL.Image.fromarray(catrgb.reshape(4096, 4096), mode='L')
img.save('bct20.png', optimize=True)
flatimg = np.zeros((4096, 4096), np.uint8)
for r in range(256):
    x = (r % 16) * 256
    y = (r // 16) * 256
    flatimg[y:y + 256, x:x + 256] = catrgb[r]
flatimg = PIL.Image.fromarray(flatimg, mode='L')
flatimg.save('bct20flat.png', optimize=True)
palimg = np.zeros((4096, 4096), np.uint8)
for r in range(256):
    x = (r % 16) * 256
    y = (r // 16) * 256
    palimg[y:y + 256, x:x + 256] = catrgb[r]
palimg = PIL.Image.fromarray(palimg, mode='P')
palette = []
for clr in cats.keys():
    hx = ViewingColors[clr]
    palette.extend(int(hx[i * 2:i * 2 + 2], 16) for i in range(3))
palimg.putpalette(palette)
palimg.save('bct20pal.png', optimize=True)
