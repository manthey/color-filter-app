# pip install colour-science scipy PILLOW numpy tqdm

import argparse
import os
import pprint
import subprocess

import colour
import numpy as np
import PIL.Image
import PIL.ImageOps
import scipy
import tqdm.contrib.concurrent

verbose = 0


def munsell_color_to_lab(munsell_notation):
    """
    Convert a munsell color notation to a L*a*b* color.

    :param: munsell notation string like N<number>/ or <hue> <number>/<chroma>.
    :returns: a L*a*b* triple.
    """
    xyY = colour.munsell_colour_to_xyY(munsell_notation)
    XYZ = colour.xyY_to_XYZ(xyY)
    lab = colour.XYZ_to_Lab(
        XYZ, colour.CCS_ILLUMINANTS['CIE 1931 2 Degree Standard Observer']['D65'])
    return lab


def munsell_table():
    """
    Create a list of hex colors based on munsell colors.  This includes 10
    neutrals and 40 x 8 chroma values.

    :returns: an array of 10 neutrals and an array of [8][40] chroma colors
        with the more saturated values in the first row.
    """
    grid = [[None for _ in range(40)] for _ in range(8)]
    gray = [None for _ in range(10)]

    # from
    # https://linguistics.berkeley.edu/wcs/data/cnum-maps/WCS-Munsell-chart.txt
    achromatic_values = [1.5, 2, 3, 4, 5, 6, 7, 8, 9, 9.5]

    for gidx, value in enumerate(achromatic_values):
        munsell_notation = f'N{value:.1f}/'
        gray[gidx] = munsell_color_to_lab(munsell_notation)

    hues = []
    for hue_letter in ['R', 'YR', 'Y', 'GY', 'G', 'BG', 'B', 'PB', 'P', 'RP']:
        for prefix in [2.5, 5.0, 7.5, 10.0]:
            hues.append(f'{prefix} {hue_letter}')

    values = np.linspace(2.0, 9.0, 8)

    # from
    # https://linguistics.berkeley.edu/wcs/data/cnum-maps/WCS-Munsell-chart.txt
    chromagrid = {
        9: [2, 2, 2, 2, 2, 2, 2, 2, 4, 6, 6, 6, 6, 4, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2],  # noqa
        8: [6, 6, 6, 6, 6, 6, 8, 14, 16, 14, 12, 12, 12, 10, 10, 8, 8, 6, 6, 6, 6, 4, 4, 4, 4, 4, 4, 4, 6, 6, 4, 4, 4, 4, 6, 6, 6, 6, 6, 6],  # noqa
        7: [8, 8, 10, 10, 10, 14, 14, 14, 12, 12, 12, 12, 12, 12, 10, 10, 10, 8, 8, 8, 8, 8, 6, 6, 6, 6, 6, 8, 8, 8, 6, 6, 6, 6, 8, 8, 10, 10, 8, 8],  # noqa
        6: [12, 12, 12, 14, 16, 12, 12, 12, 10, 10, 10, 10, 10, 10, 12, 12, 10, 10, 10, 10, 8, 8, 8, 8, 8, 8, 8, 10, 10, 10, 8, 8, 8, 8, 10, 10, 10, 10, 12, 12],  # noqa
        5: [14, 14, 14, 16, 14, 12, 10, 10, 8, 8, 8, 8, 8, 8, 10, 12, 12, 10, 10, 10, 10, 8, 8, 8, 8, 8, 8, 10, 12, 12, 10, 10, 10, 10, 10, 12, 12, 12, 14, 14],  # noqa
        4: [14, 14, 14, 14, 10, 8, 8, 6, 6, 6, 6, 6, 6, 6, 8, 8, 10, 10, 10, 10, 8, 8, 8, 8, 6, 6, 8, 8, 10, 10, 12, 10, 10, 10, 10, 10, 10, 10, 10, 10],  # noqa
        3: [10, 10, 12, 10, 8, 6, 6, 6, 4, 4, 4, 4, 4, 4, 6, 6, 8, 8, 10, 8, 6, 6, 6, 6, 6, 6, 6, 8, 10, 10, 12, 10, 10, 10, 10, 10, 10, 10, 10, 10],  # noqa
        2: [8, 8, 8, 6, 4, 4, 4, 2, 2, 2, 2, 2, 2, 2, 4, 4, 4, 6, 6, 6, 4, 4, 4, 4, 4, 4, 6, 6, 6, 8, 10, 8, 8, 8, 6, 6, 8, 8, 8, 8],  # noqa
    }

    for hidx, hue in enumerate(hues):
        for vidx, value in enumerate(values):
            chroma = chromagrid[int(value)][hidx]
            munsell_notation = f'{hue} {value:.1f}/{chroma:.1f}'
            grid[7 - vidx][hidx] = munsell_color_to_lab(munsell_notation)
    return grid, gray


def image_to_colors(image_path):
    """
    Converts the given image arays for each color.  This parses Figure 9 of
    Lindsey, D. T., and A. M. Brown. 2014. "The Color Lexicon of American
    English." Journal of Vision. Association for Research in Vision and
    Ophthalmology (ARVO). https://doi.org/10.1167/14.2.17.

    :param image_path: Figure 9 as an image file.
    :returns: a dictionary where the keys are one of 17 colors and the values
        are [8][40] arrays of values from 0 to 1 with the response rate of that
        color for the munsell color grid.
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


def process_difference_chunk(args):
    labrgb, labcat, catvals, delta2000 = args
    results = []
    for rgblab in labrgb:
        if delta2000:
            deltas = np.array(colour.difference.delta_E_CIE2000(rgblab, labcat))
        else:
            deltas = np.array(colour.difference.delta_E_CIE1976(rgblab, labcat))
        term_idx = np.argmin(deltas)
        results.append(catvals[term_idx])
    return results


def rgb_categories(labrgb, labcat, catvals, delta2000=False):
    """
    Compute categoric values.

    :param labrgb: A numpy array of [256][256][256][3] of L*a*b* values for the
        sRGB or other colorspace color cube with axes in the order R, G, B,
        L*a*b*.
    :param labcat: An array of L*a*b* colors associated with differet categoric
        labels.
    :param catvals: A list matching the length of labcat where the values are
        the indices into the terms list.
    :param delta2000: False to use delta_E_CIE1976, True to use delta_E_CIE2000
    :returns: A numpy uint8 array of [256][256][256] of the color terms indices
        that are closest to the L*a*b* values of the labcat values.
    """
    labrgb = labrgb.reshape(-1, 3)
    cats = np.zeros((labrgb.shape[0], ), dtype=np.uint8)
    chunksize = 8192
    results = tqdm.contrib.concurrent.process_map(
        process_difference_chunk,
        [(labrgb[ridx:ridx + chunksize, :], labcat, catvals, delta2000)
         for ridx in range(0, labrgb.shape[0], chunksize)],
        chunksize=1)
    cats = np.array([item for sublist in results for item in sublist])
    return cats.reshape(256, 256, 256)


def find_center_indices(array):
    categories = np.unique(array)
    center_indices = {}
    for category in categories:
        mask = (array == category)
        distance = scipy.ndimage.distance_transform_edt(mask)
        max_distance = np.amax(distance)
        indices = np.argwhere(distance == max_distance)
        center_index = indices[0]
        center_indices[category] = tuple(center_index)
    return center_indices


def ansicolor(color, text):
    if isinstance(color, str):
        color = color.strip('#')
        color = [int(color[i:i + 2], 16) for i in range(0, 6, 2)]
    return f'\033[48;2;{color[0]};{color[1]};{color[2]}m{text}\033[49m'


def make_base_data(colorspace='sRGB'):
    labgrid, labgray = munsell_table()

    basergb = np.indices((256, 256, 256)).transpose((1, 2, 3, 0))
    basergb = basergb.reshape(-1, 3)
    basergb = basergb / 255.0
    if colorspace.lower() == 'srgb':
        xyz = colour.sRGB_to_XYZ(basergb)
    else:
        model = None
        for key in dir(colour.models):
            if (key.startswith('RGB_COLOURSPACE_') and
                    key.split('RGB_COLOURSPACE_')[1].lower() == colorspace.lower()):
                model = getattr(colour.models, key)
                break
        xyz = colour.RGB_to_XYZ(basergb, model, apply_cctf_decoding=True)
    labrgb = colour.XYZ_to_Lab(xyz)
    return labrgb, labgrid, labgray


def generate_bct20(labgrid, labgray, labrgb):
    # This image is Figure 9 of
    #   Lindsey, D. T., and A. M. Brown. 2014. "The Color Lexicon of American
    #   English." Journal of Vision. Association for Research in Vision and
    #   Ophthalmology (ARVO). https://doi.org/10.1167/14.2.17.
    # Accessed via https://jov.arvojournals.org/article.aspx?articleid=2121523
    #   I think I could use Figure 5 to extract BCT11 (rather than this, which
    #   is BCT20).
    image_path = 'i1534-7362-14-2-17-f09.jpeg'
    results = image_to_colors(image_path)

    # This is used for the palette for the viewable color terms map
    viewingColors = {
        'black': '000000',
        'red': 'FF0000',
        'orange': 'E87D53',
        'yellow': 'FFFF00',
        'green': '00FF00',
        'teal': '00FFFF',
        'blue': '0000FF',
        'purple': '420063',
        'maroon': '660029',
        'pink': 'FF7DA8',
        'gold': 'A67700',
        'peach': 'FFB593',
        'beige': 'FFD9AA',
        'brown': '8F3E00',
        'olive': '4D4300',
        'gray': '808080',
        'lavender': 'D5ABEA',
        'magenta': 'FF00FF',
        'lime': 'ACD489',
        'white': 'FFFFFF',
    }

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
    # Figure 5 supports the top one or two being white and the bottom two to
    # four being black.
    graytbl = ['bk', 'bk', 'gy', 'gy', 'gy', 'gy', 'gy', 'gy', 'white', 'white']

    if verbose >= 2:
        print(results)
        print(table)
    for row in table:
        print(''.join([str(val)[:2] for val in row]))
    # Prepopulate this with colors at some extremes
    hexdict = {
        'FFFFFF': 'white',
        '000000': 'bk',
        'FF0000': 'red',
        '00FF00': 'green',
        '0000FF': 'blue',
        '00FFFF': 'teal',
        'FF00FF': 'magenta',
        'FFFF00': 'yellow',
    }
    labdict = {tuple(labrgb[int(hx, 16)]): v for hx, v in hexdict.items()}
    for lab, val in zip(labgray, graytbl):
        labdict[tuple(lab)] = val
    for y in range(len(table)):
        for lab, val in zip(labgrid[y], table[y]):
            labdict[tuple(lab)] = val
    if verbose >= 1:
        pprint.pprint(labdict)
    return labdict, viewingColors


def generate_bct11(labgrid, labgray, labrgb):
    image_path = 'i1534-7362-14-2-17-f05.jpeg'
    left, upper, right, lower = 699, 102, 1269, 212
    xstride = (right - left) / 40
    ystride = (lower - upper) / 8
    x0 = left + xstride / 2
    y0 = upper + ystride / 2

    img = np.array(PIL.Image.open(image_path))

    viewingColors = {
        'black': '000000',
        'red': 'FF0000',
        'orange': 'E87D53',
        'yellow': 'FFFF00',
        'green': '00FF00',
        'blue': '0000FF',
        'purple': '420063',
        'pink': 'FF7DA8',
        'brown': '8F3E00',
        'gray': '808080',
        'white': 'FFFFFF',
    }

    colors = {
        'pink': 'FF95B2',
        'red': 'FF0000',
        'orange': 'FF7942',
        'brown': '7B6939',
        'yellow': 'FFFF00',
        'green': '00FF00',
        'blue': '0000FF',
        'purple': '8500FF',
    }
    revclr = {}
    color_cats = {}
    color_cats_maxed = {}
    for idx, (key, color) in enumerate(colors.items()):
        color_cats[idx] = [int(color[i:i + 2], 16) for i in range(0, 6, 2)]
        color_cats_maxed[idx] = np.array(color_cats[idx]).astype(float)
        color_cats_maxed[idx] /= np.amax(color_cats_maxed[idx])
        revclr[idx] = key

    # Initialize the grid
    grid = np.zeros((8, 40), dtype=int)

    table = [['white' for _ in range(40)] for _ in range(8)]
    # Iterate over the pixels in the image
    for j in range(8):
        for i in range(40):
            rgb = img[int(y0 + ystride * j), int(x0 + xstride * i), :].astype(float)
            rgb /= np.amax(rgb)
            closest_color = min(
                color_cats.keys(),
                key=lambda x: np.linalg.norm(color_cats_maxed[x] - rgb))
            if j == 7 and closest_color == 2:
                closest_color = 3
            grid[j, i] = closest_color
            if j or 0 < i < 28 or i >= 36:
                table[j][i] = revclr[closest_color]
    for pos in (28, 29, 30, 35):
        table[0][pos] = table[1][pos]
    for row in table:
        print(''.join([str(val)[:2] for val in row]))
    graytbl = ['bk', 'bk', 'gy', 'gy', 'gy', 'gy', 'gy', 'gy', 'white', 'white']

    if verbose >= 2:
        print('Numpy array (8x40):')
        for row in range(grid.shape[0]):
            print(''.join([f'{grid[row][col]:1d}' for col in range(grid.shape[1])]))

    hexdict = {
        'FFFFFF': 'white',
        '000000': 'bk',
        'FF0000': 'red',
        '00FF00': 'green',
        '0000FF': 'blue',
        'FFFF00': 'yellow',
    }
    labdict = {tuple(labrgb[int(hx, 16)]): v for hx, v in hexdict.items()}
    for lab, val in zip(labgray, graytbl):
        labdict[tuple(lab)] = val
    for y in range(len(table)):
        for lab, val in zip(labgrid[y], table[y]):
            labdict[tuple(lab)] = val
    if verbose >= 1:
        pprint.pprint(labdict)
    return labdict, viewingColors


def labdict_to_termmap(labdict, viewcolors, labrgb, basename, delta2000=False):
    cats = {}
    labcat = []
    labcatidx = []
    for clr in viewcolors:
        cat = clr
        cats[cat] = len(cats)
    for labval, cat in labdict.items():
        cat = {'bk': 'black', 'gy': 'gray'}.get(cat, cat)
        labcat.append(list(labval))
        if cat not in cats:
            cats[cat] = len(cats)
        labcatidx.append(cats[cat])
    labcat = np.array(labcat)

    catrgb = rgb_categories(labrgb, labcat, np.array(labcatidx), delta2000)
    print([k.capitalize() for k in cats.keys()])
    counts = np.bincount(catrgb.flatten())
    countlist = sorted([(counts[idx], k.capitalize()) for idx, k in enumerate(cats)], reverse=True)
    for v, k in countlist:
        print(f'{k:8s} {v:8d}')
    flatimg = np.zeros((4096, 4096), np.uint8)
    for r in range(256):
        x = (r % 16) * 256
        y = (r // 16) * 256
        flatimg[y:y + 256, x:x + 256] = catrgb[r]
    greyimg = PIL.Image.fromarray(flatimg, mode='L')
    greyimg.save(f'{basename}.png', optimize=True)
    try:
        subprocess.run(
            ["optipng", f'{basename}.png'], stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL, check=True)
    except Exception:
        pass
    palimg = PIL.Image.fromarray(flatimg, mode='P')
    palette = []
    for clr in cats.keys():
        hx = viewcolors[clr]
        palette.extend(int(hx[i * 2:i * 2 + 2], 16) for i in range(3))
    palimg.putpalette(palette)
    palimg.save(f'{basename}_pal.png', optimize=True)
    try:
        subprocess.run(
            ["optipng", f'{basename}_pal.png'], stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL, check=True)
    except Exception:
        pass
    centers = find_center_indices(catrgb)
    for idx, clr in enumerate(cats.keys()):
        cref = list(int(viewcolors[clr][i * 2:i * 2 + 2], 16) for i in range(3))
        print(f'{centers[idx][0]:02X}{centers[idx][1]:02X}{centers[idx][2]:02X}',
              viewcolors[clr], ansicolor(centers[idx], '  '),
              ansicolor(viewcolors[clr], '  '), f'{clr:8s}',
              catrgb[cref[0]][cref[1]][cref[2]] == idx)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description='Convert figures from the Lindsey and Brown paper to term '
        'maps that can be used by the color-filter-app.')
    parser.add_argument(
        '--bct20', action=argparse.BooleanOptionalAction, default=True,
        help='Generate the BCT20 term map.  The file '
        'i1534-7362-14-2-17-f09.jpeg must be in the currenct directory.')
    parser.add_argument(
        '--bct11', action=argparse.BooleanOptionalAction, default=True,
        help='Generate the BCT11 term map.  The file '
        'i1534-7362-14-2-17-f05.jpeg must be in the currenct directory.')
    parser.add_argument(
        '--delta2000', '--delta_e_cie2000', action='store_true', default=True,
        help='Use delta_E_CIE2000 for color comparisions (default)')
    parser.add_argument(
        '--delta1976', '--delta_e_cie1976', dest='delta2000', action='store_false',
        help='Use delta_E_CIE1976 for color comparisions.')
    parser.add_argument(
        '--colorspace', default='sRGB',
        help='Specify a color space.  sRGB is the default.  Any of the colour '
        'science colour.models.RGB_COLOURSPACE_... values can be used.')
    parser.add_argument(
        '--srgb', dest='colorspace', action='store_const', const='sRGB',
        help='Use the sRGB color space')
    parser.add_argument(
        '--p3', dest='colorspace', action='store_const', const='DISPLAY_P3',
        help='Use the Display P3 color space')
    parser.add_argument(
        '--bt2020', dest='colorspace', action='store_const', const='BT2020',
        help='Use the BT2020 color space')
    parser.add_argument(
        '--verbose', '-v', action='count', default=0,
        help='Increase verbosity')
    opts = parser.parse_args()
    verbose = opts.verbose

    labrgb, labgrid, labgray = make_base_data(opts.colorspace)
    if opts.bct20:
        labdict20, viewcolors20 = generate_bct20(labgrid, labgray, labrgb)
        labdict_to_termmap(labdict20, viewcolors20, labrgb,
        f'bct20_en_us_{opts.colorspace.lower()}', opts.delta2000)
    if opts.bct11:
        labdict11, viewcolors11 = generate_bct11(labgrid, labgray, labrgb)
        labdict_to_termmap(labdict11, viewcolors11, labrgb,
        f'bct11_en_us_{opts.colorspace.lower()}', opts.delta2000)
