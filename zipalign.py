#!/usr/bin/env python3
"""Minimal zipalign: rewrites a zip so every STORED entry's data starts on a 4-byte boundary."""
import struct, sys, zipfile

src, dst = sys.argv[1], sys.argv[2]
zin = zipfile.ZipFile(src)
raw = open(src, 'rb').read()
out = bytearray()
central = bytearray()
count = 0
for info in zin.infolist():
    off = info.header_offset
    (sig, ver, flag, method, mtime, mdate, crc, csize, usize, nlen, elen) = struct.unpack('<IHHHHHIIIHH', raw[off:off + 30])
    name = raw[off + 30:off + 30 + nlen]
    data = raw[off + 30 + nlen + elen: off + 30 + nlen + elen + csize]
    flag &= ~0x08  # sizes are in the local header
    new_off = len(out)
    extra = b''
    if method == 0:
        pad = (4 - (new_off + 30 + nlen) % 4) % 4
        extra = b'\0' * pad
    out += struct.pack('<IHHHHHIIIHH', 0x04034b50, ver, flag, method, mtime, mdate, crc, csize, usize, nlen, len(extra))
    out += name + extra + data
    central += struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, 20, ver, flag, method, mtime, mdate, crc, csize, usize,
                           nlen, 0, 0, 0, 0, info.external_attr, new_off) + name
    count += 1
cd_off = len(out)
out += central
out += struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, count, count, len(central), cd_off, 0)
open(dst, 'wb').write(out)
