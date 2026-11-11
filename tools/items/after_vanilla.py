"""Every item of the mod drawn after a vanilla item texture, as the Tetherite gear is (tools/items/tetherite.py).

Each map keeps its vanilla texture's silhouette, outline and shading pixel for pixel; only the colours change. Each
vanilla colour is given a role (which material it draws: a rope, a clasp, a label, a frame) and turned into the tone of
that material's palette that holds the same place by lightness: the darkest (the outline and the deepest shadow) to the
darkest tone, the lights to the lightest. Where vanilla's tones would collapse into one, the palette has a tone more.
Where an item needs a mark of its own, a pixel or two of accent is added on top (a bead of the Tether's teal, a drop of
blood, a flame). The maps are derived from the vanilla textures named in the comments; vanilla's files are not kept.

The comment above each item says which vanilla texture it follows and why, and what each of its colours became.

Four keep the drawings they had, as no vanilla item has their shape and drawn after the nearest one they no longer read
as themselves (a lock as a jar, a crown as a helmet, a hand as a paw, a plumb line as a chain): the Pale Lock
(tools/items/home_or_lock.py), the Hollow Crown and the Hungering Hand (relics_3.py) and the Plumb Line
(first_weaver.py); their textures are left as those drew them.

usage: python3 tools/items/after_vanilla.py [preview.png]
"""
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
ITEMS = os.path.join(ROOT, "src/client/resources/assets/extremedimensions/textures/item")

DRAWN = {}


def item(name, pal, rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
    assert set("".join(rows)) - {"."} <= set(pal), name
    DRAWN[name] = (pal, rows)


# ----------------------------------------------------------------------------------- tethers and cords

# The map of vanilla's lead, shared by tether, soul_woven_tether, tether_cord and mending_thread (only their palettes
# differ).
LEAD = [
    "................",
    "................",
    "....1333321.....",
    "...131100021....",
    "...031....031...",
    "...012.....031..",
    "....012....120..",
    ".....002222130..",
    ".......0001dc0..",
    "..........0ca...",
    "..........130...",
    "..........130...",
    "...2....1320....",
    "...12332300.....",
    "...b00000.......",
    "................",
]

# Tether: vanilla's lead, as the Tether is a leash run out to an anchor. The rope in string white with a grey shadow
# side, the clasp in the iron of the chain it is wound on.
item("tether", {
    # the string
    '0': (46, 48, 62), '1': (126, 124, 122), '2': (204, 200, 190), '3': (238, 236, 228),
    # the chain's iron
    'a': (84, 92, 114), 'b': (124, 134, 158), 'c': (172, 182, 202), 'd': (204, 212, 228),
}, LEAD)

# Soul-Woven Tether: the lead again, its rope the Seam's silk spun in soul light, a cool cyan; the clasp iron.
item("soul_woven_tether", {
    # the silk
    '0': (26, 58, 58), '1': (63, 133, 125), '2': (157, 240, 228), '3': (196, 255, 248),
    # the chain's iron
    'a': (84, 92, 114), 'b': (124, 134, 158), 'c': (172, 182, 202), 'd': (204, 212, 228),
}, LEAD)

# Seam-Woven Tether: the lead in the Weavers' violet silk, the clasp iron, one glint of the Tether's cyan.
item("seam_woven_tether", {
    # the silk
    '0': (56, 38, 74), '1': (128, 91, 162), '2': (229, 172, 250), '3': (255, 196, 255),
    # the chain's iron
    'a': (84, 92, 114), 'b': (124, 134, 158), 'c': (172, 182, 202), 'd': (204, 212, 228),
    # the Tether's thread
    'X': (210, 255, 248),
}, [
    "................",
    "................",
    "....13X3321.....",
    "...131100021....",
    "...031....031...",
    "...012.....031..",
    "....012....120..",
    ".....002222130..",
    ".......0001dc0..",
    "..........0ca...",
    "..........130...",
    "..........130...",
    "...2....1320....",
    "...12332300.....",
    "...b00000.......",
    "................",
])

# Blood Cord: the lead in the tribe's dark red cord, its clasp a knot of the same cord in a brighter red, a drop of
# blood on the frayed end.
item("blood_cord", {
    # the cord
    '0': (38, 6, 12), '1': (99, 15, 26), '2': (173, 43, 52), '3': (196, 58, 64),
    # the knot
    'a': (120, 20, 32), 'b': (210, 82, 84), 'c': (236, 128, 120),
    # a drop of blood
    'X': (232, 60, 60),
}, [
    "................",
    "................",
    "....1333321.....",
    "...131100021....",
    "...031....031...",
    "...012.....031..",
    "....012....120..",
    ".....002222130..",
    ".......0001cb0..",
    "..........0ba...",
    "..........130...",
    "..........130...",
    "...2....1320....",
    "...12332300.....",
    "...X00000.......",
    "................",
])

# Tether Cord: the lead as a hank of the Tether's teal thread, edged in Tetherite's darkest tone, tied off with a bead
# of the metal.
item("tether_cord", {
    # the thread
    '0': (22, 19, 27), '1': (29, 93, 98), '2': (71, 172, 167), '3': (96, 200, 190),
    # the metal
    'a': (64, 58, 78), 'b': (82, 75, 98), 'c': (106, 99, 124), 'd': (140, 133, 158),
}, LEAD)

# The map of vanilla's end crystal, shared by ember_tether and void_tether (only their palettes differ).
END_CRYSTAL = [
    "................",
    "..1222222222222.",
    "..2.....b.....0.",
    "..2....beb....0.",
    "..2.aabcecbaa.0.",
    "..2.abccdccba.1.",
    "..2.bccdhdccb.1.",
    "..2bccdhfddcca0.",
    "..1ceehhcghddba.",
    "..0bccdcccdcba0.",
    "..2.bccdhgcbb.1.",
    "..2.abbcdcbba.1.",
    "..2.aabbdbbaa.1.",
    "..2....aba....1.",
    "..2011110011111.",
    "................",
]

# Ember Tether: vanilla's end crystal, a core held in a frame as the Ember Tether holds its charge in lodestone. The
# frame in lodestone grey, the core in ember orange, the rune at its heart white-hot.
item("ember_tether", {
    # the lodestone frame
    '0': (96, 92, 98), '1': (129, 124, 132), '2': (176, 170, 178),
    # the ember
    'a': (116, 38, 16), 'b': (188, 86, 34), 'c': (238, 146, 58), 'd': (252, 196, 112), 'e': (255, 230, 164),
    # its white-hot heart
    'f': (255, 214, 120), 'g': (255, 238, 178), 'h': (255, 252, 228),
}, END_CRYSTAL)

# Void Tether: the end crystal again, the frame in a darker, colder lodestone, the core in the void's violet and its
# rune black, as the eye the Void Tether was always drawn with.
item("void_tether", {
    # the lodestone frame
    '0': (70, 64, 88), '1': (94, 86, 118), '2': (134, 124, 160),
    # the void
    'a': (58, 22, 100), 'b': (107, 54, 164), 'c': (156, 102, 210), 'd': (190, 132, 250), 'e': (232, 200, 255),
    # its black heart
    'f': (14, 0, 30), 'g': (24, 4, 46), 'h': (36, 10, 62),
}, END_CRYSTAL)

# ------------------------------------------------------------------------------------ threads and silk

# The map of vanilla's string, shared by seam_silk, echo_thread, ember_thread, pale_thread and silk_thread (only their
# palettes differ).
STRING = [
    "................",
    "..........1.....",
    ".........202....",
    ".........1.0....",
    ".........021....",
    "....2.....002...",
    "...10.......01..",
    "..20..1212...02.",
    "..1..2000012..1.",
    "..02.01...001.2.",
    "...01200...20.1.",
    "....00121210.20.",
    "......00000.10..",
    "........02120...",
    ".........000....",
    "................",
]

# Seam Silk: vanilla's string, as it is the Seam's own thread; pale aqua and white where the old one was, its shadow
# side a deeper aqua.
item("seam_silk", {
    # the silk
    '0': (58, 150, 140), '1': (176, 246, 234), '2': (226, 255, 250),
}, STRING)

# Echo Thread: the string, one of the First Weaver's four threads, in sculk's deep teal (shadow, thread, light).
item("echo_thread", {
    # the thread
    '0': (14, 82, 92), '1': (34, 150, 160), '2': (150, 238, 236),
}, STRING)

# Ember Thread: the string, one of the First Weaver's four threads, in the Nether's orange (shadow, thread, light).
item("ember_thread", {
    # the thread
    '0': (128, 46, 14), '1': (218, 112, 34), '2': (255, 206, 104),
}, STRING)

# Pale Thread: the string, one of the First Weaver's four threads, in end stone's cream with a grey-violet shadow
# (shadow, thread, light).
item("pale_thread", {
    # the thread
    '0': (124, 112, 140), '1': (212, 206, 168), '2': (250, 248, 222),
}, STRING)

# Silk Thread: the string, one of the First Weaver's four threads, in the Seam's violet (shadow, thread, light).
item("silk_thread", {
    # the thread
    '0': (96, 64, 150), '1': (176, 146, 232), '2': (240, 232, 255),
}, STRING)

# Mending Thread: vanilla's lead, a hank of the one thread that mends the first wound: a warm white glow (the old one's
# gold-white tip) over a cyan shadow, held by a bead of pale gold.
item("mending_thread", {
    # the thread
    '0': (30, 110, 104), '1': (120, 214, 200), '2': (255, 244, 214), '3': (255, 252, 236),
    # the gold bead
    'a': (150, 106, 40), 'b': (200, 150, 60), 'c': (240, 206, 110), 'd': (255, 240, 200),
}, LEAD)


# The map of vanilla's brush, shared by thread_of_roots, thread_of_toll, thread_of_unravelling, thread_of_the_chain,
# thread_of_the_knot and thread_of_the_heart (only their palettes differ).
BRUSH = [
    "................",
    ".........111....",
    "........1543111.",
    ".......15545541.",
    "......14545551..",
    "......153455340.",
    "......143553440.",
    ".....143453440..",
    ".....bb432400...",
    "....bdeb200.....",
    "...gkaca0.......",
    "..gkhja.........",
    ".gkijf..........",
    ".gkjf...........",
    "..gf............",
    "................",
]

# Thread of Roots: vanilla's brush, read as a distaff: a wooden handle with a hank of the deed's thread bound on it (the
# bristles), in the Matriarch's moss green, a gold ferrule binding it. Vanilla has no shuttle (the old drawing); this is
# the nearest tool that carries a bundle of fibre.
item("thread_of_roots", {
    # the thread
    '0': (22, 30, 12), '1': (42, 59, 21), '2': (62, 88, 30), '3': (90, 122, 44), '4': (118, 156, 58),
    '5': (157, 191, 93),
    # the gold binding
    'a': (96, 58, 28), 'b': (150, 96, 40), 'c': (196, 140, 60), 'd': (230, 182, 98), 'e': (246, 214, 150),
    # the wooden handle
    'f': (40, 25, 13), 'g': (70, 44, 22), 'h': (92, 58, 30), 'i': (120, 80, 42), 'j': (146, 98, 54),
    'k': (176, 124, 72),
}, BRUSH)

# Thread of Toll: vanilla's brush, read as a distaff: a wooden handle with a hank of the deed's thread bound on it (the
# bristles), in the Ferryman's soul-fire teal, a gold ferrule binding it. Vanilla has no shuttle (the old drawing); this
# is the nearest tool that carries a bundle of fibre.
item("thread_of_toll", {
    # the thread
    '0': (12, 30, 30), '1': (17, 67, 64), '2': (22, 104, 98), '3': (40, 143, 133), '4': (58, 182, 168),
    '5': (114, 214, 199),
    # the gold binding
    'a': (96, 58, 28), 'b': (150, 96, 40), 'c': (196, 140, 60), 'd': (230, 182, 98), 'e': (246, 214, 150),
    # the wooden handle
    'f': (40, 25, 13), 'g': (70, 44, 22), 'h': (92, 58, 30), 'i': (120, 80, 42), 'j': (146, 98, 54),
    'k': (176, 124, 72),
}, BRUSH)

# Thread of Unravelling: vanilla's brush, read as a distaff: a wooden handle with a hank of the deed's thread bound on
# it (the bristles), in the Last Weaver's lilac silk, a gold ferrule binding it. Vanilla has no shuttle (the old
# drawing); this is the nearest tool that carries a bundle of fibre.
item("thread_of_unravelling", {
    # the thread
    '0': (34, 24, 52), '1': (72, 57, 110), '2': (110, 90, 168), '3': (148, 129, 204), '4': (186, 168, 240),
    '5': (215, 203, 247),
    # the gold binding
    'a': (96, 58, 28), 'b': (150, 96, 40), 'c': (196, 140, 60), 'd': (230, 182, 98), 'e': (246, 214, 150),
    # the wooden handle
    'f': (40, 25, 13), 'g': (70, 44, 22), 'h': (92, 58, 30), 'i': (120, 80, 42), 'j': (146, 98, 54),
    'k': (176, 124, 72),
}, BRUSH)

# Thread of the Chain: vanilla's brush, read as a distaff: a wooden handle with a hank of the deed's thread bound on it
# (the bristles), in the Held's chain grey, a gold ferrule binding it. Vanilla has no shuttle (the old drawing); this is
# the nearest tool that carries a bundle of fibre.
item("thread_of_the_chain", {
    # the thread
    '0': (26, 24, 28), '1': (61, 58, 64), '2': (96, 92, 100), '3': (129, 125, 133), '4': (162, 158, 166),
    '5': (193, 189, 190),
    # the gold binding
    'a': (96, 58, 28), 'b': (150, 96, 40), 'c': (196, 140, 60), 'd': (230, 182, 98), 'e': (246, 214, 150),
    # the wooden handle
    'f': (40, 25, 13), 'g': (70, 44, 22), 'h': (92, 58, 30), 'i': (120, 80, 42), 'j': (146, 98, 54),
    'k': (176, 124, 72),
}, BRUSH)

# Thread of the Knot: vanilla's brush, read as a distaff: a wooden handle with a hank of the deed's thread bound on it
# (the bristles), in the Knot Mother's sea green, a gold ferrule binding it. Vanilla has no shuttle (the old drawing);
# this is the nearest tool that carries a bundle of fibre.
item("thread_of_the_knot", {
    # the thread
    '0': (10, 30, 26), '1': (25, 66, 56), '2': (40, 102, 86), '3': (75, 149, 127), '4': (110, 196, 168),
    '5': (153, 221, 198),
    # the gold binding
    'a': (96, 58, 28), 'b': (150, 96, 40), 'c': (196, 140, 60), 'd': (230, 182, 98), 'e': (246, 214, 150),
    # the wooden handle
    'f': (40, 25, 13), 'g': (70, 44, 22), 'h': (92, 58, 30), 'i': (120, 80, 42), 'j': (146, 98, 54),
    'k': (176, 124, 72),
}, BRUSH)

# Thread of the Heart: vanilla's brush, read as a distaff: a wooden handle with a hank of the deed's thread bound on it
# (the bristles), in the ending's crimson, a gold ferrule binding it. Vanilla has no shuttle (the old drawing); this is
# the nearest tool that carries a bundle of fibre.
item("thread_of_the_heart", {
    # the thread
    '0': (40, 8, 14), '1': (84, 15, 27), '2': (128, 22, 40), '3': (169, 40, 59), '4': (210, 58, 78),
    '5': (232, 118, 132),
    # the gold binding
    'a': (96, 58, 28), 'b': (150, 96, 40), 'c': (196, 140, 60), 'd': (230, 182, 98), 'e': (246, 214, 150),
    # the wooden handle
    'f': (40, 25, 13), 'g': (70, 44, 22), 'h': (92, 58, 30), 'i': (120, 80, 42), 'j': (146, 98, 54),
    'k': (176, 124, 72),
}, BRUSH)

# ---------------------------------------------------------------------------------------------- relics

# Kindred Knot: the string as a knot of red cord, one pixel of the Tether's teal tied into it.
item("kindred_knot", {
    # the cord
    '0': (110, 20, 36), '1': (214, 70, 76), '2': (255, 168, 148),
    # the Tether's thread
    'X': (122, 234, 214),
}, [
    "................",
    "..........1.....",
    ".........202....",
    ".........1.0....",
    ".........021....",
    "....2.....002...",
    "...10.......01..",
    "..20..1X12...02.",
    "..1..2000012..1.",
    "..02.01...001.2.",
    "...01200...20.1.",
    "....00121210.20.",
    "......00000.10..",
    "........02120...",
    ".........000....",
    "................",
])

# Ember Heart: vanilla has no heart-shaped item, so it follows the Heart of the Sea, the one heart it has: the same orb
# and swirl, turned from the sea's blues to ember, dark red to gold.
item("ember_heart", {
    # ember, dark to light
    '0': (40, 12, 8), '1': (80, 22, 12), '2': (120, 34, 16), '3': (162, 48, 20), '4': (200, 80, 28),
    '5': (232, 116, 34), '6': (248, 156, 58), '7': (255, 214, 120),
}, [
    "................",
    "......4444......",
    "....44566544....",
    "...4566666652...",
    "..456776666552..",
    "..456776666541..",
    ".45666666655431.",
    ".45566665555441.",
    ".13455555554430.",
    ".11123444432001.",
    "..121111100020..",
    "..122333333210..",
    "...1223333210...",
    "....00211100....",
    "......0000......",
    "................",
])

# Pale Shard: vanilla's echo shard, its dark crystal turned pale: the End's lavender to white, the outline violet.
item("pale_shard", {
    # the shard, dark to light
    '0': (30, 20, 46), '1': (78, 60, 118), '2': (110, 86, 168), '3': (146, 118, 208), '4': (180, 156, 236),
    '5': (222, 210, 255), '6': (255, 255, 255),
}, [
    "................",
    "..22............",
    ".23411..........",
    ".244541.........",
    ".2356541........",
    ".12154321.......",
    ".121123321......",
    "..121124330.....",
    "...041354330....",
    "....021313230...",
    ".....021122220..",
    "......03113240..",
    ".......04114540.",
    "........0532430.",
    ".........00000..",
    "................",
])


# Featherbone: vanilla's bone, whiter and cooler, a touch of the cyan the old one was edged in at its upper knob.
item("featherbone", {
    # the bone
    '0': (110, 112, 118), '1': (192, 196, 196), '2': (226, 228, 226), '3': (246, 246, 242), '4': (255, 255, 255),
    # cyan
    'X': (110, 227, 208), 'Y': (60, 170, 156),
}, [
    "................",
    "...........1X1..",
    "..........1Y300.",
    "..........134320",
    "..........133210",
    ".........133000.",
    "........1330....",
    ".......1330.....",
    "......1330......",
    "....11330.......",
    "..224430........",
    ".234440.........",
    ".121420.........",
    "..10210.........",
    "....00..........",
    "................",
])

# Keeper's Eye: vanilla's eye of ender. Its sea-green rim turned the grey of the Keeper's lids, the pale ring round the
# iris its white, the green iris the Tether's teal, the pupil black.
item("keepers_eye", {
    # the lids, and the white
    '0': (18, 16, 22), '1': (52, 48, 60), '2': (90, 84, 98), '3': (136, 130, 144), '4': (232, 226, 214),
    # the iris
    'a': (10, 10, 14), 'b': (30, 118, 110), 'c': (46, 170, 156), 'd': (80, 200, 184), 'e': (111, 227, 208),
    'f': (176, 250, 236),
    # the glint
    'g': (250, 255, 252),
}, [
    "................",
    "................",
    ".......211......",
    ".....2233311....",
    "....234444421...",
    "...134gfdc4421..",
    "...14gfe1bc440..",
    "..124fe1a1bc420.",
    "..12ccd1a1bc420.",
    "..124cb1a1dcc20.",
    "...134cd1ec340..",
    "...1234cbc3c20..",
    "....023333420...",
    ".....0022200....",
    ".......000......",
    "................",
])

# Tithe Bell: vanilla's bell in an older, warmer gold, hung from an iron bar where vanilla's is wood, with one bead of
# the Tether's teal on it.
item("tithe_bell", {
    # the bar
    '0': (28, 26, 34), '1': (46, 44, 56), '2': (70, 68, 82), '3': (96, 94, 108),
    # the gold
    'a': (122, 78, 18), 'b': (160, 108, 28), 'c': (204, 152, 40), 'd': (228, 184, 62), 'e': (240, 200, 82),
    'f': (250, 220, 110), 'g': (254, 236, 168), 'h': (255, 248, 214),
    # the Tether's teal
    'X': (46, 170, 156),
}, [
    "................",
    "..23221X222121..",
    "..121000111010..",
    ".....bccccb.....",
    "....bdeffeca....",
    "....cefgffea....",
    "....ceggffea....",
    "....ceggffea....",
    "....beggffea....",
    "....cghhggga....",
    "....bceeeeca....",
    "...cghhhhgggb...",
    "...befeeeffca...",
    "....aaaaaaaa....",
    "................",
    "................",
])

# Muffled Bell: the bell in dark iron, its bar the strip of sculk-teal cloth bound over it, and a faint cyan glow at its
# mouth.
item("muffled_bell", {
    # the cloth
    '0': (8, 40, 48), '1': (10, 62, 72), '2': (16, 100, 108), '3': (52, 160, 160),
    # the iron
    'a': (30, 34, 44), 'b': (50, 54, 66), 'c': (78, 82, 96), 'd': (96, 100, 116), 'e': (110, 114, 130),
    'f': (126, 130, 146), 'g': (150, 154, 170), 'h': (176, 180, 196),
    # the glow
    'X': (60, 210, 214), 'Y': (170, 255, 248),
}, [
    "................",
    "..232211222121..",
    "..121000111010..",
    ".....bccccb.....",
    "....bdeffeca....",
    "....cefgffea....",
    "....ceggffea....",
    "....ceggffea....",
    "....beggffea....",
    "....cghhggga....",
    "....bceeeeca....",
    "...cghhhhgggb...",
    "...befeeeffca...",
    "....aaaXYaaa....",
    "................",
    "................",
])

# Lurker's Tooth: vanilla's prismarine shard, the nearest to a broken tooth, in ivory, the root at its wide end violet
# as the old one's.
item("lurkers_tooth", {
    # the tooth
    '0': (92, 84, 82), '1': (98, 90, 88), '2': (104, 96, 94), '3': (140, 132, 120), '4': (190, 182, 166),
    '5': (196, 188, 172), '6': (222, 216, 202), '7': (238, 234, 224), '8': (250, 248, 240),
    # the violet root
    'X': (110, 60, 170), 'Y': (170, 120, 230),
}, [
    "................",
    "................",
    "...XY...........",
    "...4744.........",
    "..3488855.......",
    "...08878844.....",
    "...0678864655...",
    "....07774887852.",
    "....0676788861..",
    ".....067778841..",
    ".....04776762...",
    "......0677642...",
    "......046441....",
    ".......2441.....",
    "........11......",
    "................",
])

# Seam Lantern: vanilla's lantern, its iron darker and colder, its copper bands the Seam's violet silk wound round it
# (so that it is not taken for a soul lantern), the flame the Seam's teal.
item("seam_lantern", {
    # the iron
    '0': (40, 42, 50), '1': (62, 66, 78),
    # the silk bands
    'a': (96, 64, 140), 'b': (130, 96, 180),
    # the light
    'c': (30, 120, 112), 'd': (46, 170, 156), 'e': (111, 227, 208), 'f': (190, 250, 240), 'g': (230, 255, 250),
}, [
    "................",
    "........0.......",
    ".......01.......",
    ".......10.......",
    ".......0........",
    "......0110......",
    "......baab......",
    ".....011110.....",
    ".....acddca.....",
    ".....bdeedb.....",
    ".....begfeb.....",
    ".....befgeb.....",
    ".....adeeda.....",
    ".....011110.....",
    "................",
    "................",
])

# Kindled Seam Lantern (the block, as an item): the Seam Lantern lit from a lantern's flame and a soul lantern's, the
# same lantern, its flame burning blue-violet.
item("kindled_seam_lantern", {
    # the iron
    '0': (40, 42, 50), '1': (62, 66, 78),
    # the silk bands
    'a': (96, 64, 140), 'b': (130, 96, 180),
    # the blue-violet flame
    'c': (58, 56, 150), 'd': (92, 90, 214), 'e': (146, 138, 244), 'f': (204, 194, 255), 'g': (240, 236, 255),
}, [
    "................",
    "........0.......",
    ".......01.......",
    ".......10.......",
    ".......0........",
    "......0110......",
    "......baab......",
    ".....011110.....",
    ".....acddca.....",
    ".....bdeedb.....",
    ".....begfeb.....",
    ".....befgeb.....",
    ".....adeeda.....",
    ".....011110.....",
    "................",
    "................",
])

# Held's Chain: vanilla's iron chain, darker, one link still glowing warm.
item("helds_chain", {
    # the iron
    '0': (40, 38, 46), '1': (72, 70, 80), '2': (112, 110, 122),
    # the warm link
    'X': (255, 180, 90), 'Y': (200, 90, 36),
}, [
    "................",
    "......0.0.......",
    "......020.......",
    ".......1........",
    "......201.......",
    "......1.0.......",
    "......0.0.......",
    "......020.......",
    ".......X........",
    ".......Y........",
    "......101.......",
    "......1.0.......",
    "......0.0.......",
    "......021.......",
    ".......1........",
    "................",
])

# Pilgrim's Sandal: vanilla's leather boots as they show undyed (the base in leather's brown, the soles from their own
# layer). The uppers turned the sandal's red-brown straps, the soles the pale, worn footbed.
item("pilgrims_sandal", {
    # the straps
    '0': (52, 28, 14), '1': (80, 44, 24), '2': (139, 80, 44), '3': (161, 95, 53), '4': (181, 114, 65),
    # the footbed
    'P': (60, 38, 22), 'Q': (104, 72, 42), 'R': (156, 116, 70), 'S': (198, 160, 108), 'T': (226, 194, 144),
}, [
    "................",
    "................",
    "................",
    "....111..111....",
    "...1440..1440...",
    "...1440..1430...",
    "...1430..1330...",
    "...1330..1330...",
    "...1320..1330...",
    "..QSSSP..QSSRP..",
    ".QTTSRP..QRSTSP.",
    ".QSSRPP..PPSSRP.",
    ".QPPP......PPPP.",
    "................",
    "................",
    "................",
])

# Lodestone Chip: vanilla's flint, a knapped chip of stone, in lodestone's greys instead of flint's black, its lightest
# facet a cold glint.
item("lodestone_chip", {
    # the stone
    '0': (24, 22, 30), '1': (48, 46, 56), '2': (70, 70, 82), '3': (98, 96, 106), '4': (132, 130, 140),
    '5': (176, 176, 184), '6': (226, 232, 242),
}, [
    "................",
    "................",
    "........111.....",
    ".......13210....",
    "......133340....",
    ".....12344540...",
    "....123446430...",
    "...13334633120..",
    "..132245332120..",
    "..1222533211140.",
    "..1223232111350.",
    "..013222112350..",
    "...0222223340...",
    "....01123330....",
    ".....000000.....",
    "................",
])

# Thornward Bark: vanilla's armadillo scute, a curved plate that turns blows, in dark bark brown, with a bead of green
# sap and one of red where the sap runs.
item("thornward_bark", {
    # the bark
    '0': (40, 26, 16), '1': (62, 42, 28), '2': (84, 60, 42), '3': (106, 78, 54), '4': (126, 92, 58),
    '5': (156, 118, 78), '6': (186, 146, 96),
    # the sap
    'X': (126, 184, 76), 'Y': (204, 56, 56),
}, [
    "................",
    "................",
    "................",
    ".......222......",
    "......24552.....",
    ".....2556651....",
    ".....244X5641...",
    "....255664631...",
    "....24456Y530...",
    "....15635340....",
    "....1443400.....",
    ".....1000.......",
    "................",
    "................",
    "................",
    "................",
])

# Hollow Whistle: vanilla's goat horn, its one instrument, in the Hollowlings' bone, and their soul-blue light deep in
# its mouth.
item("hollow_whistle", {
    # the bone
    '0': (60, 56, 54), '1': (84, 78, 74), '2': (88, 82, 78), '3': (117, 109, 101), '4': (132, 124, 114),
    '5': (162, 154, 140), '6': (182, 174, 158), '7': (221, 214, 197), '8': (248, 244, 232),
    # the soul light
    'X': (110, 190, 255), 'Y': (60, 120, 210),
}, [
    "................",
    "................",
    "................",
    "..44444.........",
    ".471XY74........",
    ".4687764........",
    ".4575554........",
    ".4565564........",
    ".4687654.....44.",
    "..4555654...410.",
    "..4687553444110.",
    "...455335331310.",
    "....2156335110..",
    ".....00136100...",
    ".......0000.....",
    "................",
])

# Last Stitch: vanilla's fishing rod, which is laid out as a needle and thread are: the rod a steel needle, the line one
# silver-white thread hanging from its eye, the hook the knot at its end, glowing faintly.
item("last_stitch", {
    # the steel
    '0': (46, 50, 66), '1': (100, 108, 128), '2': (160, 168, 188), '3': (214, 220, 234),
    # the thread
    'a': (120, 124, 140), 'b': (184, 188, 204), 'c': (214, 218, 230), 'd': (228, 232, 240), 'e': (240, 242, 248),
    'f': (250, 250, 252),
    # the knot's glow
    'g': (176, 196, 255),
}, [
    "................",
    ".............cb.",
    "............c10.",
    "...........b120.",
    "..........b130b.",
    ".........b130.b.",
    "........b130..c.",
    ".......b130...c.",
    "......b120....d.",
    ".....b120.....c.",
    ".....120.....c..",
    "....12ffa....b..",
    "...110efa...b...",
    "..110.aa..g.g...",
    "..00......eg....",
    "................",
])

# Hunger's Fang: vanilla's pointed dripstone, a long point hanging from a root, in ivory; its top rows (the root) dark
# gum, and a drop of red at the point.
item("hungers_fang", {
    # the fang
    '0': (150, 138, 118), '1': (200, 190, 170), '2': (230, 224, 208), '3': (248, 244, 232),
    # the gum
    'P': (74, 20, 32), 'Q': (118, 32, 46), 'R': (150, 44, 58),
    # the drop of blood
    'X': (214, 40, 44),
}, [
    "....PPQQQQRQ....",
    "....QPPQQRR.....",
    ".....002222.....",
    ".....012333.....",
    ".....001221.....",
    "......0222......",
    "......0333......",
    "......0233......",
    ".......122......",
    ".......121......",
    ".......X2.......",
    "................",
    "................",
    "................",
    "................",
    "................",
])

# Debtor's Seal: vanilla's compass, a round iron case seen face on: the case iron, the dark ring round the face gold,
# the face purple, the needle the crimson sigil.
item("debtors_seal", {
    # the iron
    '0': (24, 20, 28), '1': (70, 66, 78), '2': (88, 84, 96), '3': (96, 92, 104), '4': (122, 116, 132),
    '5': (156, 150, 166), '6': (200, 196, 208), '7': (236, 234, 242),
    # the purple face
    'a': (90, 26, 84),
    # the gold ring
    'b': (176, 124, 40),
    # the sigil
    'c': (186, 30, 70), 'd': (228, 60, 104),
}, [
    "................",
    "................",
    ".....bbbbbb.....",
    "...bb554242bb...",
    "..024baaaab220..",
    ".02baaaaaaaab20.",
    ".0baaaaa3aaaab0.",
    ".0baaaa1d1aaab0.",
    ".05baaaadaaab40.",
    ".0477aaacaa4410.",
    ".04667756642110.",
    "..056664552210..",
    "...0056455200...",
    ".....000000.....",
    "................",
    "................",
])

# Unbound Pinion: vanilla's elytra, as its gift is the elytra's: the membrane the pinion's pale white, the frame its
# violet veins, and a snapped link of chain at the root.
item("unbound_pinion", {
    # the veins
    '0': (40, 26, 64), '1': (80, 52, 124), '2': (128, 80, 186), '3': (150, 98, 210), '4': (176, 120, 236),
    # the vane
    'a': (200, 196, 214), 'b': (226, 222, 236), 'c': (244, 242, 250),
    # the snapped link
    'X': (92, 90, 100), 'Y': (148, 146, 158),
}, [
    "................",
    "................",
    "....111XY111....",
    "..113b1111b311..",
    ".13bcc0..1ccb31.",
    ".1b44c0..1c44b0.",
    ".144bb0..1bb440.",
    ".13bba0..1abb30.",
    ".1b33a0..1a33b0.",
    ".123aa0..1aa310.",
    "..12a30..03a20..",
    "..02a20..02a20..",
    "...020....020...",
    "...00......00...",
    "................",
    "................",
])

# Ashen Locket: vanilla's clock, a round case with a face, which a locket is: the gold case tarnished brass, the sky of
# the dial grey ash, its sun a dull ember, the horizon dark ash.
item("ashen_locket", {
    # the brass
    '0': (58, 46, 28), '1': (104, 86, 54), '2': (138, 116, 74), '3': (160, 138, 92), '4': (190, 168, 118),
    '5': (214, 192, 140), '6': (236, 220, 180),
    # the ash
    'a': (52, 46, 44), 'b': (96, 92, 90), 'c': (140, 136, 132),
    # the ember
    'd': (168, 70, 34), 'e': (230, 130, 58),
}, [
    "......1111......",
    "....11554411....",
    "...1353bb3431...",
    "..135bbddbb431..",
    "..15bbeeeecb40..",
    ".153bceeeecc340.",
    ".15bbcceecccb40.",
    ".14bbcc65bccb40.",
    ".141aaa54aaa140.",
    ".05466543554340.",
    ".02544443333420.",
    "..025544334420..",
    "..012255542110..",
    "...0122211110...",
    "....00121100....",
    "......0000......",
])

# Miner's Candle: vanilla's candle in a greyer tallow, lit (a small warm flame where the wick's tip was), its foot
# standing in an iron dish.
item("miners_candle", {
    # the tallow
    '0': (150, 128, 96), '1': (196, 178, 136), '2': (216, 198, 152), '3': (232, 218, 180), '4': (242, 232, 200),
    '5': (250, 244, 222),
    # the wick
    'a': (30, 22, 18),
    # the flame
    'X': (255, 214, 128), 'Y': (240, 128, 44),
    # the iron dish
    'P': (60, 62, 74), 'Q': (96, 98, 112), 'R': (130, 132, 146),
}, [
    "................",
    "........X.......",
    "......12Y21.....",
    ".....134a431....",
    ".....1442441....",
    ".....1544430....",
    ".....1455320....",
    ".....1454220....",
    ".....1353220....",
    ".....1343220....",
    ".....1333220....",
    ".....0333210....",
    ".....0233110....",
    ".....0122110....",
    "......PQRQP.....",
    ".......PPP......",
])

# Magpie's Glint: vanilla's feather as a magpie's: its greys black and an iridescent blue-black, its whites white, and a
# stolen gold bead tied at the quill.
item("magpies_glint", {
    # the feather
    '0': (20, 20, 28), '1': (44, 48, 66), '2': (80, 88, 118), '3': (244, 244, 240),
    # the gold bead
    'X': (238, 186, 58), 'Y': (166, 112, 30),
}, [
    "................",
    "................",
    "............2...",
    "............22..",
    "...........203..",
    "..........23032.",
    "..........2012..",
    "........23102...",
    "......2.320232..",
    ".....23220232...",
    ".....332012.....",
    "...223103332....",
    "....100232......",
    "..XY01232.......",
    ".....11.........",
    "................",
])

# Anchor Rivet: vanilla's iron nugget, a small cast bit of iron, the nearest it has to a rivet; in lodestone grey, one
# fleck of soul-blue light caught in it.
item("anchor_rivet", {
    # the iron
    '0': (40, 40, 50), '1': (70, 70, 84), '2': (98, 96, 106), '3': (136, 136, 148), '4': (170, 170, 176),
    '5': (174, 174, 180), '6': (200, 200, 208), '7': (226, 226, 232),
    # the soul-blue fleck
    'X': (110, 190, 255),
}, [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......11........",
    ".....146111.....",
    "....16776X30....",
    "....17663220....",
    "....1544000.....",
    ".....000........",
    "................",
    "................",
    "................",
    "................",
    "................",
])

# Veil Scale: vanilla's turtle scute, a scale, in the Veil Ray's pale violet-blue, a vein of light across it.
item("veil_scale", {
    # the scale
    '0': (60, 52, 128), '1': (96, 88, 180), '2': (130, 124, 214), '3': (164, 160, 236), '4': (204, 200, 250),
    # the vein of light
    'X': (172, 234, 255), 'Y': (236, 232, 255),
}, [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....111111.....",
    "....12344320....",
    "...1234Y44320...",
    "..0234X4444320..",
    "..023344443310..",
    "...0023443200...",
    ".....000000.....",
    "................",
    "................",
    "................",
    "................",
])

# Ferryman's Obol: vanilla's recovery compass, a round case with sculk's glints on its rim: the case worn gold, the
# glints the Ferryman's soul fire (its needle the stamped oar), and a violet thread through the edge.
item("ferrymans_obol", {
    # the gold
    '0': (120, 80, 22), '1': (166, 118, 36), '2': (206, 158, 54), '3': (236, 198, 96), '4': (255, 240, 180),
    # the stamped field
    'a': (44, 26, 8), 'b': (110, 72, 22), 'c': (140, 96, 30),
    # the soul fire
    'd': (16, 80, 78), 'e': (20, 96, 100), 'f': (24, 112, 118), 'g': (40, 150, 146), 'h': (58, 182, 168),
    'i': (150, 240, 222),
    # the violet thread
    'X': (150, 92, 200), 'Y': (78, 40, 110),
}, [
    "................",
    ".....cccccc.....",
    "...Xc334321cc...",
    "..Y12dggddd11c..",
    ".c10dbbbbbbd02c.",
    "c20dbbbbfbbbd03c",
    "c3cbbbbeiebbbc3c",
    "c23cbbbbibbbc32c",
    "a0221bbbhbb1220a",
    "ad012121111210da",
    "acg0034332200gca",
    ".acii111110ggca.",
    "..ac0iiiigg0ca..",
    "...aac0000caa...",
    ".....aaaaaa.....",
    "................",
])


# --------------------------------------------------------------------------- the journal and its pages

# Tether Journal: vanilla's written book (a closed book, written in): the cover in the journal's darker leather, the
# pages a warm cream, and a clasp of the Tether's teal on the cover.
item("tether_journal", {
    # the leather
    '0': (22, 14, 10), '1': (46, 28, 18), '2': (60, 36, 24), '3': (76, 48, 32), '4': (90, 58, 38), '5': (100, 64, 42),
    # the pages
    'a': (120, 108, 88), 'b': (176, 160, 130), 'c': (214, 200, 170), 'd': (240, 230, 206),
    # the teal clasp
    'X': (140, 240, 250), 'Y': (70, 150, 165),
}, [
    "................",
    "........111.....",
    "......113452....",
    "....113455452...",
    "..113555115452..",
    "113551555X15452.",
    "2354551555Y15412",
    "21354551555411a.",
    "21c354555311bcb.",
    "03dc354311bccb12",
    ".03dc311bccb1100",
    "..03dcbccb1200..",
    "...03dcb1200....",
    "....031200......",
    ".....000........",
    "................",
])

# Wanderer's Page: vanilla's paper, a loose sheet, in the page's yellowed parchment, a mark of the Tether's teal on it
# as the old one had.
item("wanderers_page", {
    # the parchment
    '0': (100, 76, 50), '1': (150, 118, 74), '2': (170, 138, 90), '3': (194, 166, 114), '4': (212, 186, 132),
    '5': (226, 204, 152),
    # the teal mark
    'X': (80, 220, 200), 'Y': (200, 255, 245),
}, [
    "................",
    "................",
    ".........11.....",
    "........1431....",
    "......1135541...",
    ".....145555530..",
    "...113555555510.",
    "..13455555555310",
    ".13455555555540.",
    "..0345555X5430..",
    "...03455Y5400...",
    "....0345420.....",
    ".....03200......",
    "......00........",
    "................",
    "................",
])

# Loose Leaf: vanilla's paper in the leaf's greyer cream, a stroke of a dark hand across it.
item("loose_leaf", {
    # the paper
    '0': (118, 102, 80), '1': (160, 146, 118), '2': (176, 162, 134), '3': (192, 178, 148), '4': (206, 192, 162),
    '5': (222, 212, 186),
    # the ink
    'X': (46, 34, 26),
}, [
    "................",
    "................",
    ".........11.....",
    "........1431....",
    "......1135541...",
    ".....145555530..",
    "...113555555510.",
    "..13455555555310",
    ".13455XX5555540.",
    "..034555555430..",
    "...0345555400...",
    "....0345420.....",
    ".....03200......",
    "......00........",
    "................",
    "................",
])

# ----------------------------------------- the Pale Mason's pearl, keystone and lock; the Seam's tools

# Homing Pearl: vanilla's ender pearl, the Endermen's own, in the void's violet, a pale glint, and a touch of the gold
# band the old one wore.
item("homing_pearl", {
    # the pearl
    '0': (20, 8, 34), '1': (34, 14, 58), '2': (38, 16, 64), '3': (50, 20, 86), '4': (70, 28, 120), '5': (96, 44, 160),
    '6': (108, 50, 174), '7': (122, 58, 196), '8': (176, 120, 238), '9': (236, 220, 255),
    # gold
    'X': (222, 178, 74), 'Y': (150, 106, 40),
}, [
    "................",
    "................",
    ".......222......",
    ".....2277622....",
    "....277644660...",
    "...27698334460..",
    "...27981113340..",
    "..2768100013X40.",
    "..0743100013Y40.",
    "..0643100013740.",
    "...06331113870..",
    "...06433338940..",
    "....054367740...",
    ".....0044400....",
    ".......000......",
    "................",
])

# Homeward Keystone: vanilla's brick, a dressed block of stone, as the Pale Mason's keystone is one: the clay turned end
# stone's cream on its lit top and the Pale Reach's lavender grey down its shaded face, a slit of violet-white light in
# that face.
item("homeward_keystone", {
    # the stone
    '0': (44, 36, 58), '1': (118, 108, 140), '2': (146, 136, 164), '3': (178, 168, 178), '4': (212, 206, 182),
    '5': (232, 228, 200), '6': (246, 244, 222),
    # the light
    'X': (236, 220, 255), 'Y': (196, 168, 244),
}, [
    "................",
    "................",
    "..........11....",
    ".......111453...",
    "....1114555553..",
    ".11145555555553.",
    "1655555555556643",
    "1465555556664330",
    "1446556664333330",
    "14446643X3333320",
    "13445333Y333200.",
    ".134533332000...",
    "..13532000......",
    "...1100.........",
    "................",
    "................",
])


# Severing Shears: vanilla's shears, the blades a cold pale steel, the handles violet, an ember-orange rivet where they
# cross.
item("severing_shears", {
    # the steel
    '0': (70, 70, 84), '1': (120, 120, 135), '2': (170, 170, 185), '3': (214, 214, 224),
    # the handles
    'a': (64, 20, 92), 'b': (98, 34, 136), 'c': (128, 52, 172), 'd': (160, 84, 210),
    # the rivet
    'X': (255, 110, 40),
}, [
    "................",
    "................",
    "........b2332...",
    ".......b1332.2..",
    "......bc232.23..",
    ".....bd232.233..",
    "....bda12.2332..",
    "....bda..2321a..",
    "...bda...12ba...",
    "...bca..bbXa....",
    "...22bbbcca.....",
    "..2..1ccaa......",
    "..1..0aa........",
    "...00...........",
    "................",
    "................",
])

# Seam Needle: vanilla's breeze rod, the slimmest straight thing it has, in the needle's pale bone-white, a turn of the
# Tether's thread at its eye.
item("seam_needle", {
    # the needle
    '0': (96, 90, 80), '1': (124, 116, 100), '2': (156, 148, 128), '3': (172, 164, 144), '4': (190, 182, 160),
    '5': (208, 200, 180), '6': (228, 222, 202), '7': (240, 236, 220),
    # the thread
    'X': (110, 230, 210), 'Y': (200, 255, 245),
}, [
    "................",
    "............2X..",
    "...........2Y40.",
    "..........2741..",
    ".........2751...",
    "........2641....",
    ".......2641.....",
    "......2741......",
    ".....2740.......",
    "....2640........",
    "...2740.........",
    "..2740..........",
    ".1641...........",
    ".351............",
    ".01.............",
    "................",
])

# Wound Lens: vanilla's spyglass, which it already is in kind: the copper tube kept copper, its gold rings turned the
# violet of the old one's bands, the lens the Tether's teal.
item("wound_lens", {
    # the copper
    '0': (40, 20, 18), '1': (110, 54, 34), '2': (168, 88, 52), '3': (220, 140, 92),
    # the violet rings
    'a': (52, 34, 70), 'b': (100, 76, 130), 'c': (140, 112, 168), 'd': (174, 146, 200), 'e': (210, 190, 230),
    # the lens
    'f': (20, 92, 104), 'g': (30, 130, 140), 'h': (120, 230, 220),
    # its glint
    'i': (240, 255, 250),
}, [
    "................",
    "..........dcc...",
    ".........dfghc..",
    "........beghihc.",
    ".......bdefghhd.",
    "......bdedefgfd.",
    "......2eiedeed..",
    ".....23eedcca...",
    "....2332dcca....",
    "....132110a.....",
    "...ab0110.......",
    "..accb00........",
    ".a2cba..........",
    ".0220...........",
    "..00............",
    "................",
])

# --------------------------------------------------------------------- materials, and the veil lantern

# Tether Fragment: vanilla's amethyst shard, a broken crystal, in the Tether's cyan, from deep teal to white.
item("tether_fragment", {
    # the shard
    '0': (14, 40, 48), '1': (30, 100, 120), '2': (60, 170, 190), '3': (120, 230, 240), '4': (160, 244, 248),
    '5': (205, 252, 255), '6': (245, 255, 255),
}, [
    "................",
    "................",
    ".........11111..",
    "........155550..",
    ".......1355640..",
    "......15336420..",
    ".....145341220..",
    "....134542120...",
    "...133461210....",
    "..122253120.....",
    "..01232310......",
    "..0001230.......",
    "..001020........",
    "...0021.........",
    "....11..........",
    "................",
])

# Mending Cloth: vanilla's cushion, a soft pad of cloth, as the cloth is a pad of silk pressed to the skin; in the
# silk's teal, a stitch of violet at its middle.
item("mending_cloth", {
    # the silk
    '0': (30, 90, 86), '1': (46, 127, 120), '2': (80, 180, 166), '3': (111, 227, 208), '4': (200, 250, 240),
    # the violet stitch
    'X': (200, 150, 255), 'Y': (150, 90, 220),
}, [
    "................",
    "................",
    "................",
    "......1111......",
    "....11444411....",
    "..113334433311..",
    ".144443XY344441.",
    ".03443444434430.",
    ".02333444433320.",
    ".02223333332220.",
    "..002222222200..",
    "....00222200....",
    "......0000......",
    "................",
    "................",
    "................",
])

# Veil Wing: vanilla's phantom membrane, the wing of a thing that flies, in the Veil Ray's violet, its pale seams
# glowing.
item("veil_wing", {
    # the wing
    '0': (36, 24, 66), '1': (61, 41, 111), '2': (72, 48, 129), '3': (93, 62, 162), '4': (104, 69, 178),
    '5': (106, 71, 181), '6': (125, 87, 203), '7': (177, 151, 236), '8': (230, 200, 255),
}, [
    "................",
    "................",
    ".........222....",
    ".....22.2788222.",
    "....277278837830",
    "...2788536378830",
    "...2883377332110",
    "..2763778867840.",
    "..2337788578440.",
    ".27867886332110.",
    ".2836666377840..",
    ".2337862788840..",
    ".0378227884410..",
    "..04410022210...",
    "...000..0000....",
    "................",
])

# Veil Lantern: vanilla's soul lantern, being a soul ward; the iron darker and cooler, the flame the veil's cyan, and
# the Veil Wing in the glass, two violet pixels at the heart of the light.
item("veil_lantern", {
    # the iron
    '0': (26, 24, 32), '1': (52, 50, 62),
    # the light
    'a': (60, 98, 112), 'b': (84, 170, 180), 'c': (120, 214, 220), 'd': (200, 240, 246),
    # the wing
    'X': (196, 168, 244),
}, [
    "................",
    "........0.......",
    ".......01.......",
    ".......10.......",
    ".......0........",
    "......0110......",
    "......1001......",
    ".....011110.....",
    ".....0abba0.....",
    ".....1bccb1.....",
    ".....1cXdc1.....",
    ".....1cdXc1.....",
    ".....0bccb0.....",
    ".....011110.....",
    "................",
    "................",
])

# --------------------------------------------------------- Tetherite: ingot, scrap, smithing templates

# Tetherite Ingot: vanilla's netherite ingot. Its neutral greys in Tetherite's ramp, the three wine-tinged greys
# (netherite's warmth) in a violet-tinged step between them, and a thread of the Tether's teal in the front face.
item("tetherite_ingot", {
    # the metal (neutral greys)
    '0': (22, 19, 27), '1': (49, 44, 60), '2': (64, 58, 78), '3': (76, 70, 92), '4': (82, 75, 98), '5': (106, 99, 124),
    '6': (140, 133, 158),
    # the metal's violet-tinged greys (netherite's wine)
    'a': (40, 30, 54), 'b': (58, 46, 76), 'c': (78, 62, 100),
    # the Tether's thread
    'X': (42, 140, 142), 'Y': (27, 86, 92),
}, [
    "................",
    "................",
    "..........aa....",
    ".......aaacc1...",
    "....aaa3344cc1..",
    ".aaa33455554431.",
    "a5cc4c4455556631",
    "ac655444c6663120",
    "abb6556663b11b20",
    "a222663b11112220",
    "ab2232111XY2b00.",
    ".1b23b11bb000...",
    "..1b2b1000......",
    "...1100.........",
    "................",
    "................",
])

# Tetherite Scrap: vanilla's netherite scrap, its seven browns Tetherite's seven tones, a fleck of the thread in each of
# the two pieces.
item("tetherite_scrap", {
    # the metal
    '0': (22, 19, 27), '1': (35, 31, 43), '2': (49, 44, 60), '3': (64, 58, 78), '4': (82, 75, 98), '5': (106, 99, 124),
    '6': (140, 133, 158),
    # the Tether's thread
    'X': (42, 140, 142), 'Y': (27, 86, 92),
}, [
    "................",
    ".........11.....",
    ".......11661....",
    ".....11664351...",
    "...1155443X451..",
    "..1454444444420.",
    ".0334433444200..",
    "..0134334200....",
    "...0034200220...",
    "..132000234320..",
    ".133333Y44420...",
    "..1443344420....",
    "...13444200.....",
    "....03200.......",
    ".....00.........",
    "................",
])

# Tetherite Upgrade: vanilla's netherite upgrade template, its wine-red slab Tetherite's dark metal, its arrow the
# Tether's teal (its mid and light tones and one between them; its darkest would sink into the slab).
item("tetherite_upgrade_smithing_template", {
    # the slab
    '0': (22, 19, 27), '1': (35, 31, 43), '2': (49, 44, 60), '3': (64, 58, 78), '4': (82, 75, 98),
    # the arrow
    'a': (42, 140, 142), 'b': (70, 176, 172), 'c': (96, 200, 190),
}, [
    "................",
    "....222222222...",
    "...23444444342..",
    "...24443433330..",
    "...03333123330..",
    "...03321a13320..",
    "...0321acb1210..",
    "...021accbb120..",
    "...0344bbb4430..",
    "...0434aba4330..",
    "...0333aaa3420..",
    "...02334443310..",
    "...01233222210..",
    "....011221000...",
    ".....00000......",
    "................",
])

# Weaver: vanilla's rib template, whose glyph runs across the slab in bands as a weft does; the nether-brick slab the
# Seam silk's violet, the glyph the Tether's teal as every vanilla template's glyph is cyan.
item("weaver_armor_trim_smithing_template", {
    # the silk slab
    '0': (40, 28, 58), '1': (62, 42, 86), '2': (86, 62, 114), '3': (118, 90, 148), '4': (152, 122, 182),
    '5': (186, 158, 210),
    # the glyph
    'a': (14, 40, 48), 'b': (20, 64, 72), 'c': (27, 86, 92), 'd': (42, 140, 142), 'e': (96, 200, 190),
}, [
    "......443.......",
    ".....445443.....",
    ".....45455431...",
    "....35544434311.",
    "....bd444de44220",
    "...243de455dc220",
    "...24455ee455cba",
    "..2244345534110.",
    "..bd234ee442110.",
    ".235cd355ed110..",
    ".2235532255cba..",
    ".2323de332110...",
    ".023355dc2110...",
    "..0022355bba....",
    "....00232110....",
    "......00000.....",
])

# Fray: vanilla's tide template, whose glyph is one strand splitting into loose ends, as a thread frays (silence's
# broken glyph would fit too, but on a dark slab it would be taken for the silence template itself). Its pale prismarine
# slab Tetherite's dark metal, the glyph the Tether's teal.
item("fray_armor_trim_smithing_template", {
    # the slab
    '0': (22, 19, 27), '1': (35, 31, 43), '2': (49, 44, 60), '3': (56, 51, 69), '4': (64, 58, 78), '5': (82, 75, 98),
    '6': (106, 99, 124), '7': (140, 133, 158),
    # the glyph
    'a': (42, 140, 142), 'b': (72, 182, 176), 'c': (110, 214, 204),
}, [
    "......554.......",
    ".....566654.....",
    ".....57766541...",
    "....4676b755411.",
    "....4555cb644220",
    "...2555bc7455220",
    "...2554cb7552210",
    "..254ccb7655110.",
    "..24c766c552110.",
    ".232b644c55110..",
    ".22b6444b52110..",
    ".23a644ba5110...",
    ".024444b52110...",
    "..00244a5110....",
    "....00242110....",
    "......00000.....",
])

# ---------------------------------------------------------------------- music discs and disc fragments

# Tick of the Boundary: vanilla's Mellohi, whose label is violet split by white. The record in the mod's violet-black,
# the label violet silk above and the Fray's deep teal below, the white the boundary between them.
item("music_disc_tick_of_the_boundary", {
    # the record
    '0': (16, 12, 22), '1': (32, 28, 40), '2': (38, 34, 46), '3': (60, 56, 72), '4': (80, 76, 96),
    # the label
    'a': (150, 106, 222),
    # the boundary
    'b': (226, 250, 246),
}, [
    "................",
    "................",
    "................",
    ".....11111......",
    "..11133333111...",
    ".1333343443331..",
    "133443aba334331.",
    "13433ba2ab33431.",
    "133433aba344331.",
    "123334434333321.",
    ".0222333332220..",
    "..00022222000...",
    ".....00000......",
    "................",
    "................",
    "................",
])

# What the Wound Remembers: vanilla's Lava Chicken, whose red and white label runs on into the grooves: the label raw
# crimson, the white the pale scar, the red in the grooves dark red.
item("music_disc_wound_remembers", {
    # the record
    '0': (16, 12, 22), '1': (32, 28, 40), '2': (44, 40, 54), '3': (60, 56, 72), '4': (80, 76, 96),
    # the wound
    'a': (110, 20, 32), 'b': (176, 36, 48),
    # the scar
    'c': (232, 150, 140), 'd': (196, 160, 150), 'e': (240, 206, 196),
}, [
    "................",
    "................",
    "................",
    ".....00000......",
    "..00033333000...",
    ".0333343443330..",
    "033443ced334330.",
    "03433bb1ed33430.",
    "a33433bbc34433a.",
    "ab33344343333ba.",
    ".abbd43334dbba..",
    "..aa2deeed2aa...",
    ".....22222......",
    "................",
    "................",
    "................",
])

# What Listens: vanilla's Wait, whose label is three rings of one hue (not music disc 5, which it would be taken for):
# the rings sculk's deep teal, teal, and the cyan ring of a sound.
item("music_disc_what_listens", {
    # the record
    '0': (16, 12, 22), '1': (32, 28, 40), '2': (38, 34, 46), '3': (60, 56, 72), '4': (80, 76, 96),
    # the hole
    'a': (10, 8, 16),
    # the label
    'b': (16, 70, 78), 'c': (24, 104, 112), 'd': (40, 220, 226),
}, [
    "................",
    "................",
    "................",
    ".....11111......",
    "..11133333111...",
    ".1333343443331..",
    "133443cdc334331.",
    "13433dbabd33431.",
    "133433cdc344331.",
    "123334434333321.",
    ".0222333332220..",
    "..00022222000...",
    ".....00000......",
    "................",
    "................",
    "................",
])

# Elegy of the Elders: vanilla's Far, a ring round a centre: the ring the gold, the centre the Matriarch's moss, with
# the Ferryman's bronze and the Last Weaver's silver beside the hole.
item("music_disc_elegy_of_the_elders", {
    # the record
    '0': (16, 12, 22), '1': (32, 28, 40), '2': (38, 34, 46), '3': (60, 56, 72), '4': (80, 76, 96),
    # the moss
    'a': (78, 116, 58),
    # the gold ring
    'b': (228, 198, 122),
    # bronze and silver
    'X': (168, 112, 50), 'Y': (198, 202, 216),
}, [
    "................",
    "................",
    "................",
    ".....11111......",
    "..1113X3Y3111...",
    ".1333343443331..",
    "133443bab334331.",
    "13433ba2ab33431.",
    "133433bab344331.",
    "123334434333321.",
    ".0222333332220..",
    "..00022222000...",
    ".....00000......",
    "................",
    "................",
    "................",
])

# The Abyss Answers: vanilla's Mall, a violet label round a pale centre: the label abyssal purple, the centre the pale
# ring, and a crack through the label.
item("music_disc_the_abyss_answers", {
    # the record
    '0': (16, 12, 22), '1': (32, 28, 40), '2': (38, 34, 46), '3': (60, 56, 72), '4': (80, 76, 96),
    # the pale ring
    'a': (176, 104, 236),
    # the label
    'b': (96, 40, 140),
    # the crack
    'X': (36, 12, 52),
}, [
    "................",
    "................",
    "................",
    ".....111X1......",
    "..11133333111...",
    ".1333343443331..",
    "133443bbb334331.",
    "13433ba2ab33431.",
    "133433bbb344331.",
    "123334434333321.",
    ".0222333332220..",
    "..00022222000...",
    ".....00000......",
    "................",
    "................",
    "................",
])

# The map of vanilla's disc fragment 5, shared by disc_fragment_elegy and disc_fragment_abyss (only their palettes
# differ).
DISC_FRAGMENT_5 = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "....0013........",
    "...0013343......",
    "...01334322a....",
    "....024322b0....",
    ".....0222c0.....",
    "......0bc0......",
    ".......00.......",
    "................",
    "................",
    "................",
    "................",
]

# Disc Fragment (Elegy): vanilla's disc fragment, the record in the mod's violet-black, the teal of disc 5's rim the
# Elegy's moss and gold.
item("disc_fragment_elegy", {
    # the record
    '0': (16, 12, 22), '1': (38, 34, 46), '2': (54, 50, 66), '3': (72, 68, 86), '4': (96, 92, 112),
    # the label
    'a': (52, 78, 38), 'b': (78, 116, 58), 'c': (228, 198, 122),
}, DISC_FRAGMENT_5)

# Disc Fragment (Abyss): the disc fragment again, its label the Abyss's purple and pale ring.
item("disc_fragment_abyss", {
    # the record
    '0': (16, 12, 22), '1': (38, 34, 46), '2': (54, 50, 66), '3': (72, 68, 86), '4': (96, 92, 112),
    # the label
    'a': (46, 14, 66), 'b': (96, 40, 140), 'c': (176, 104, 236),
}, DISC_FRAGMENT_5)


def draw(pal, rows):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c != ".":
                px[x, y] = pal[c] + (255,)
    return im


def main():
    out = {name: draw(pal, rows) for name, (pal, rows) in DRAWN.items()}
    if len(sys.argv) > 1:
        # every item at 8x on an inventory slot's grey, eight to a row
        S, gap, cols = 8, 6, 8
        rows = (len(out) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * (16 * S + gap) + gap, rows * (16 * S + gap) + gap), (139, 139, 139, 255))
        for i, im in enumerate(out.values()):
            x, y = gap + (i % cols) * (16 * S + gap), gap + (i // cols) * (16 * S + gap)
            sheet.alpha_composite(im.resize((16 * S, 16 * S), Image.NEAREST), (x, y))
        sheet.save(sys.argv[1])
        return
    for name, im in out.items():
        im.save(os.path.join(ITEMS, name + ".png"))


if __name__ == "__main__":
    main()
