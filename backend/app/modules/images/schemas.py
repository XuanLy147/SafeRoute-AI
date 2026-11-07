from pydantic import BaseModel


class ImageUploadResponse(BaseModel):
    image_id: str
    filename: str
    mime: str
    size_bytes: int
    url: str
