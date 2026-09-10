#!/usr/bin/env python3
"""生成 gametest 用的空结构模板 (.nbt)。

现有 empty.nbt 是 3x1x1（只够放单方块锅炉），多方块结构放不进去
（GameTestHelper.setBlock 会断言越界）。本脚本生成任意尺寸的纯空气模板。

用法:
    python tools/gen_empty_gametest_structure.py            # 默认 32
    python tools/gen_empty_gametest_structure.py 32 24 32   # 自定义 x y z

输出: src/main/resources/data/gregsteamexpansion/structures/empty_<x>x<y>x<z>.nbt
"""

import gzip
import struct
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_DIR = PROJECT_ROOT / "src" / "main" / "resources" / "data" / "gregsteamexpansion" / "structures"
DATA_VERSION = 3465

TAG_END = 0
TAG_INT = 3
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_STRING = 8


def _string(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _tag_header(tag_type: int, name: str) -> bytes:
    return bytes([tag_type]) + _string(name)


def build_nbt(size_x: int, size_y: int, size_z: int) -> bytes:
    out = bytearray()
    out += _tag_header(TAG_COMPOUND, "")
    out += _tag_header(TAG_INT, "DataVersion")
    out += struct.pack(">i", DATA_VERSION)

    out += _tag_header(TAG_LIST, "size")
    out += bytes([TAG_INT]) + struct.pack(">i", 3)
    for value in (size_x, size_y, size_z):
        out += struct.pack(">i", value)

    out += _tag_header(TAG_LIST, "palette")
    out += bytes([TAG_COMPOUND]) + struct.pack(">i", 1)
    out += _tag_header(TAG_STRING, "Name")
    out += _string("minecraft:air")
    out += bytes([TAG_END])

    for name in ("blocks", "entities"):
        out += _tag_header(TAG_LIST, name)
        out += bytes([TAG_COMPOUND]) + struct.pack(">i", 0)

    out += bytes([TAG_END])
    return bytes(out)


def main() -> None:
    args = [int(a) for a in sys.argv[1:]]
    if len(args) == 1:
        size_x = size_y = size_z = args[0]
    elif len(args) == 3:
        size_x, size_y, size_z = args
    else:
        size_x = size_y = size_z = 32

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    target = OUTPUT_DIR / f"empty_{size_x}x{size_y}x{size_z}.nbt"
    with open(target, "wb") as handle:
        with gzip.GzipFile(fileobj=handle, mode="wb", mtime=0) as gz:
            gz.write(build_nbt(size_x, size_y, size_z))
    print(target)


if __name__ == "__main__":
    main()
