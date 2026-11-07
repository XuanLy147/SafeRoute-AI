from geoalchemy2.elements import WKTElement
from geoalchemy2.shape import to_shape


def make_point(longitude: float, latitude: float) -> WKTElement:
    """
    Tạo điểm WKTElement WGS84 (SRID 4326) theo thứ tự POINT(lon lat) cho PostGIS.
    Quy tắc bất biến: PostGIS sử dụng (longitude latitude), KHÔNG đảo ngược.
    Ví dụ: TP.HCM lat=10.762622, lon=106.660172 -> POINT(106.660172 10.762622)
    """
    return WKTElement(f"POINT({longitude} {latitude})", srid=4326)

def point_to_coords(geography_point) -> tuple[float, float]:
    """
    Chuyển đổi PostGIS Geography Point thành (latitude, longitude).
    Trả về: (lat, lon)
    """
    if geography_point is None:
        return 0.0, 0.0
    try:
        shape = to_shape(geography_point)
        # shape.x is longitude, shape.y is latitude
        return shape.y, shape.x
    except Exception:
        # Fallback if text or already parsed
        return 0.0, 0.0
