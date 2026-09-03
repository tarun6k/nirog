# Decode a geohash to its cell centroid. Mirrors the client encoder in :engine.
_BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"


def centroid(geohash: str) -> tuple[float, float]:
    lat_lo, lat_hi = -90.0, 90.0
    lon_lo, lon_hi = -180.0, 180.0
    even = True
    for ch in geohash:
        idx = _BASE32.index(ch)
        for bit in range(4, -1, -1):
            b = (idx >> bit) & 1
            if even:
                mid = (lon_lo + lon_hi) / 2
                if b:
                    lon_lo = mid
                else:
                    lon_hi = mid
            else:
                mid = (lat_lo + lat_hi) / 2
                if b:
                    lat_lo = mid
                else:
                    lat_hi = mid
            even = not even
    return (lat_lo + lat_hi) / 2, (lon_lo + lon_hi) / 2
