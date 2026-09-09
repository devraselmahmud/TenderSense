import hmac

from fastapi import Header, HTTPException

from app.config import Settings


def require_internal_token(x_internal_token: str = Header()) -> None:
    if not hmac.compare_digest(x_internal_token, Settings().internal_token):
        raise HTTPException(status_code=401)
