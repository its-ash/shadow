"""Generates the pixel-art icon sources in assets/icon/ (16x16 grid, 64 px cells, two colors).

Motif: a pixel person and, next to it, a checkerboard "shadow" of the same figure.
Usage: python3 -I tools/icon.py assets/icon
"""
import sys

out = sys.argv[1]
C = 64


def fig(x0, checker):
    cells = []
    for r in (3, 4):
        for c in (x0 + 1, x0 + 2):
            cells.append((r, c))
    for r in range(5, 11):
        for c in range(x0, x0 + 4):
            cells.append((r, c))
    for r in (11, 12):
        for c in (x0, x0 + 3):
            cells.append((r, c))
    return [(r, c) for r, c in cells if not checker or (r + c) % 2 == 0]


cs = fig(3, False) + fig(8, True)
rects = ''.join(f'<rect x="{c*C}" y="{r*C}" width="{C}" height="{C}"/>' for r, c in cs)
BG, FG = '#0F172A', '#A78BFA'
h = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" shape-rendering="crispEdges">'
open(out + '/logo_background.svg', 'w').write(h + f'<rect width="1024" height="1024" fill="{BG}"/></svg>')
open(out + '/logo_foreground.svg', 'w').write(h + f'<g fill="{FG}">{rects}</g></svg>')
open(out + '/logo.svg', 'w').write(h + f'<rect width="1024" height="1024" fill="{BG}"/><g fill="{FG}">{rects}</g></svg>')
