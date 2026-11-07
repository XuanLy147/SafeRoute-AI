"""Google Encoded Polyline encoder and decoder utilities (TV5).

Follows the official Google Encoded Polyline Algorithm Format.
Provides conversion between encoded strings, (lat, lon) coordinates,
GeoJSON LineString (lon, lat order for PostGIS / TV4), and bounding boxes.
"""

from typing import Any


def decode_polyline(polyline_str: str, precision: int = 5) -> list[tuple[float, float]]:
    """Decode a Google Encoded Polyline string into a list of (latitude, longitude) tuples.

    Args:
        polyline_str: Encoded string (e.g. "_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        precision: Decimal precision, default 5 (standard 1e-5 for Google Routes)

    Returns:
        List of (latitude, longitude) floats.
    """
    if not polyline_str:
        return []

    coordinates: list[tuple[float, float]] = []
    index = 0
    lat = 0
    lng = 0
    factor = 10**precision
    length = len(polyline_str)

    while index < length:
        # Decode delta latitude
        shift = 0
        result = 0
        while index < length:
            b = ord(polyline_str[index]) - 63
            index += 1
            result |= (b & 0x1F) << shift
            shift += 5
            if b < 0x20:
                break
        dlat = ~(result >> 1) if (result & 1) else (result >> 1)
        lat += dlat

        # Decode delta longitude
        shift = 0
        result = 0
        while index < length:
            b = ord(polyline_str[index]) - 63
            index += 1
            result |= (b & 0x1F) << shift
            shift += 5
            if b < 0x20:
                break
        dlng = ~(result >> 1) if (result & 1) else (result >> 1)
        lng += dlng

        coordinates.append((round(lat / factor, precision), round(lng / factor, precision)))

    return coordinates


def _encode_val(val: int) -> str:
    """Helper to encode a single signed integer delta into polyline characters."""
    val = ~(val << 1) if val < 0 else (val << 1)
    chunks = []
    while val >= 0x20:
        chunks.append(chr((0x20 | (val & 0x1F)) + 63))
        val >>= 5
    chunks.append(chr(val + 63))
    return "".join(chunks)


def encode_polyline(coordinates: list[tuple[float, float]], precision: int = 5) -> str:
    """Encode a list of (latitude, longitude) tuples into a Google Encoded Polyline string.

    Args:
        coordinates: List of (latitude, longitude) floats.
        precision: Decimal precision, default 5.

    Returns:
        Encoded polyline string.
    """
    if not coordinates:
        return ""

    factor = 10**precision
    output: list[str] = []
    prev_lat = 0
    prev_lng = 0

    for lat, lng in coordinates:
        ilat = round(lat * factor)
        ilng = round(lng * factor)
        dlat = ilat - prev_lat
        dlng = ilng - prev_lng
        prev_lat = ilat
        prev_lng = ilng

        output.append(_encode_val(dlat))
        output.append(_encode_val(dlng))

    return "".join(output)


def calculate_bounds(coordinates: list[tuple[float, float]]) -> tuple[float, float, float, float]:
    """Calculate the bounding box of a list of (latitude, longitude) points.

    Returns:
        (min_latitude, min_longitude, max_latitude, max_longitude)
    Raises:
        ValueError: if coordinates list is empty.
    """
    if not coordinates:
        raise ValueError("Cannot calculate bounding box of empty coordinates list")

    lats = [c[0] for c in coordinates]
    lngs = [c[1] for c in coordinates]
    return (min(lats), min(lngs), max(lats), max(lngs))


def to_geojson_linestring(coordinates: list[tuple[float, float]]) -> dict[str, Any]:
    """Convert (latitude, longitude) points into a GeoJSON LineString geometry.

    CRITICAL NOTE (PostGIS contract):
    GeoJSON coordinates standard is [longitude, latitude], whereas
    Android LatLng and Google Maps standard is (latitude, longitude).
    This function outputs [longitude, latitude] for TV4 PostGIS consumption.
    """
    return {"type": "LineString", "coordinates": [[lng, lat] for lat, lng in coordinates]}
