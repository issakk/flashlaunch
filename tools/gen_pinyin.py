#!/usr/bin/env python3
"""Generate app/src/main/assets/pinyin.dat from mozillazg/pinyin-data.

Input format (one line per codepoint):
    U+4E00: yī  # 一

Output binary (little endian):
    magic   : 4 bytes  b"PYNT"
    u16     : syllable count N
    N times : u8 length + ascii bytes   (toneless syllable, 'v' for u-umlaut)
    u32     : base codepoint
    u32     : codepoint count M
    M * u16 : syllable index for base+i, 0xFFFF = unknown

Download input with:
    curl -sL -o /tmp/pinyin.txt \
      https://raw.githubusercontent.com/mozillazg/pinyin-data/master/pinyin.txt
"""
import os
import re
import struct
import sys
import unicodedata

BASE = 0x3400
END = 0x9FFF  # inclusive

UMLAUT = str.maketrans({"ü": "v", "ǖ": "v", "ǘ": "v", "ǚ": "v", "ǜ": "v"})
LINE_RE = re.compile(r"^U\+([0-9A-Fa-f]{4,6}):\s*([^#]*)")


def toneless(py: str) -> str:
    py = py.strip().translate(UMLAUT)
    py = unicodedata.normalize("NFD", py)
    py = "".join(c for c in py if unicodedata.category(c) != "Mn")
    return py.lower()


def main() -> int:
    src = sys.argv[1] if len(sys.argv) > 1 else "/tmp/pinyin.txt"
    dst = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
        os.path.dirname(__file__), "..", "app", "src", "main", "assets", "pinyin.dat"
    )

    readings = {}
    with open(src, encoding="utf-8") as fh:
        for line in fh:
            m = LINE_RE.match(line)
            if not m:
                continue
            cp = int(m.group(1), 16)
            if not (BASE <= cp <= END):
                continue
            first = m.group(2).split(",")[0].strip()
            first = toneless(first)
            if not re.fullmatch(r"[a-z]+", first):
                continue
            readings[cp] = first

    syllables = sorted(set(readings.values()))
    index_of = {s: i for i, s in enumerate(syllables)}

    out = bytearray(b"PYNT")
    out += struct.pack("<H", len(syllables))
    for s in syllables:
        raw = s.encode("ascii")
        out.append(len(raw))
        out += raw
    out += struct.pack("<II", BASE, END - BASE + 1)
    for cp in range(BASE, END + 1):
        s = readings.get(cp)
        out += struct.pack("<H", index_of[s] if s else 0xFFFF)

    os.makedirs(os.path.dirname(os.path.abspath(dst)), exist_ok=True)
    with open(dst, "wb") as fh:
        fh.write(out)

    print(f"chars mapped : {len(readings)}")
    print(f"syllables    : {len(syllables)}")
    print(f"output       : {dst} ({len(out)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
