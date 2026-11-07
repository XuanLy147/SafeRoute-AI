from fastapi import APIRouter, File, UploadFile

from app.core.errors import error_responses
from app.modules.ai.schemas import AiDetectResponse
from app.modules.ai.service import ai_service
from app.modules.images.service import validate_image

router = APIRouter(responses=error_responses(400, 413, 500, 503))


@router.post("/detect", response_model=AiDetectResponse)
def detect_road_hazards(file: UploadFile = File(...)):
    contents, _, _, _ = validate_image(file)
    return ai_service.predict(contents, filename=file.filename or "image")
