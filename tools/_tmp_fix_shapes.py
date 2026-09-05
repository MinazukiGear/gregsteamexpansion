# -*- coding: utf-8 -*-
"""One-shot: crusher shapeInfos -> front-first aisles + NORTH facings."""
import io

p = 'src/main/java/com/hoshino/gregsteamexpansion/registry/GSECrusherPatterns.java'
s = io.open(p, encoding='utf-8').read()

# --- small crusher ---
old_small = '''    return MultiblockShapeInfo.builder()
            .aisle("XXX", "XXX", "XXX")
            .aisle("XGX", "GFG", "XGX")
            .aisle("ISO", "XKX", "XXX")
            .where('X', bronzeSteamCasing())
            .where('G', GSEBlocks.STEAM_GRINDING_BLOCK.get())
            .where('F', bronzeFrame())
            .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.EAST)
            .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.EAST)
            .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.EAST)
            .where('K', definition, Direction.EAST)
            .build();'''
new_small = '''    return MultiblockShapeInfo.builder()
            .aisle("XXX", "XKX", "XGX")
            .aisle("XGX", "GFG", "XGX")
            .aisle("XXX", "XXX", "XXX")
            .where('X', bronzeSteamCasing())
            .where('G', GSEBlocks.STEAM_GRINDING_BLOCK.get())
            .where('F', bronzeFrame())
            .where('I', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)
            .where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)
            .where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)
            .where('K', definition, Direction.NORTH)
            .build();'''
assert old_small in s, 'small'
s = s.replace(old_small, new_small)

# --- large crusher: reverse aisle order ---
A1 = '''            .aisle(
                    "CCCCCCC",
                    "  CCC  ",
                    "  CCC  ",
                    "  CCC  ",
                    "  CCC  ",
                    "  CCC  ",
                    "       ",
                    "       ",
                    "       ")
'''
A2 = '''            .aisle(
                    "CCCCCCC",
                    " C   C ",
                    " C   C ",
                    " C   C ",
                    " C   C ",
                    " C   C ",
                    "       ",
                    "       ",
                    " CCCCC ")
'''
A3 = '''            .aisle(
                    "CCCCCCC",
                    "C     C",
                    "C     C",
                    "C     C",
                    "C     C",
                    "C     C",
                    "  CCC  ",
                    "  CCC  ",
                    " CCCCC ")
'''
A4 = '''            .aisle(
                    "CCCPCCC",
                    "C  P  C",
                    "C  P  C",
                    "C  P  C",
                    "C  G  C",
                    "C  G  C",
                    "  CGC  ",
                    "  CGC  ",
                    " CCGCC ")
'''
front_block = '''            .aisle(
                    "CCCCCCC",
                    "  CSC  ",
                    "  IKO  ",
                    "  CEC  ",
                    "  CCC  ",
                    "  CCC  ",
                    "       ",
                    "       ",
                    "       ")
'''
old_seq = front_block + A2 + A3 + A4 + A3 + A2 + A1
new_seq = A1 + A2 + A3 + A4 + A3 + A2 + front_block
assert old_seq in s, 'large aisles'
s = s.replace(old_seq, new_seq)

# --- large crusher facings: EAST -> NORTH ---
for ch in ('I', 'S', 'O', 'E', 'K'):
    old_f = ".where('%s', GTMachines.STEAM_IMPORT_BUS, Direction.EAST)" % ch
    if old_f in s:
        s = s.replace(old_f, ".where('%s', GTMachines.STEAM_IMPORT_BUS, Direction.NORTH)" % ch)
s = s.replace(".where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.EAST)",
              ".where('S', GSEMachines.STEAM_SUPPLY_HATCH, Direction.NORTH)")
s = s.replace(".where('O', GTMachines.STEAM_EXPORT_BUS, Direction.EAST)",
              ".where('O', GTMachines.STEAM_EXPORT_BUS, Direction.NORTH)")
s = s.replace(".where('E', GSEMachines.STEAM_EXHAUST_HATCH, Direction.EAST)",
              ".where('E', GSEMachines.STEAM_EXHAUST_HATCH, Direction.NORTH)")
s = s.replace(".where('K', definition, Direction.EAST)",
              ".where('K', definition, Direction.NORTH)")

# --- doc comment ---
s = s.replace("aisles = front/back rows back to front, rows = layers bottom to top,\n * controller and interfaces face EAST out of the front row (last aisle).",
              "aisles = front/back rows FRONT FIRST (GTCEu shapeInfo convention), rows\n * = layers bottom to top, controller and interfaces face NORTH out of the\n * front section (first aisle).")

io.open(p, 'w', encoding='utf-8').write(s)
print('crusher shapeinfos fixed')
