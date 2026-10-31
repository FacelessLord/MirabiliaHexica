# Checks every texture of the mod decodes to the size its header claims (a malformed pixel row shows as BAD).
import zlib, struct, glob, os
root = os.path.join('common', 'src', 'main', 'resources', 'assets', 'mirabilia', 'textures')
bad = 0
for p in sorted(glob.glob(os.path.join(root, '**', '*.png'), recursive=True)):
    d = open(p, 'rb').read()
    i, idat, w, h, ct = 8, b'', 0, 0, 6
    while i < len(d):
        l = struct.unpack('>I', d[i:i + 4])[0]
        t = d[i + 4:i + 8]
        if t == b'IHDR':
            w, h = struct.unpack('>II', d[i + 8:i + 16])
            ct = d[i + 17]
        if t == b'IDAT':
            idat += d[i + 8:i + 8 + l]
        i += 12 + l
    ok = len(zlib.decompress(idat)) == h * (w * {6: 4, 2: 3}[ct] + 1)
    bad += not ok
    print('OK ' if ok else 'BAD', os.path.relpath(p, root), w, h)
raise SystemExit(1 if bad else 0)
