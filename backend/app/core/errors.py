from pydantic import BaseModel


class ApiError(BaseModel):
    detail: str


def error_responses(*codes):
    return {code: {"model": ApiError} for code in codes}
