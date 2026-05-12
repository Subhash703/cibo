"""Email + password authentication.

The Android client posts email + password to /auth/register or /auth/login;
the backend hashes the password (bcrypt) and issues a signed JWT. The token
is sent on every authenticated request as `Authorization: Bearer <jwt>`.

JWT secret comes from the JWT_SECRET env var. For dev, set anything random.
For prod, set a long random string and never commit it.

Google OAuth was removed (Play Console credentials weren't available).
The User.google_sub column is left in place — re-enabling the Google path
is just adding back a /auth/google endpoint plus the verification helper.
"""

from __future__ import annotations

import os
from datetime import datetime, timedelta, timezone

import bcrypt
from fastapi import Depends, Header, HTTPException
from jose import JWTError, jwt
from sqlalchemy.orm import Session

from .db import get_db
from .models import User

_JWT_ALGORITHM = "HS256"
_JWT_EXPIRY_DAYS = 30

# bcrypt has a hard 72-byte input limit. We hash UTF-8 bytes truncated to 72
# so that very long passphrases still hash deterministically rather than
# error out. This is the same approach Django and passlib use.
_BCRYPT_MAX_BYTES = 72


def _jwt_secret() -> str:
    secret = os.environ.get("JWT_SECRET")
    if not secret:
        raise HTTPException(
            status_code=500,
            detail="JWT_SECRET env var is not set on the backend.",
        )
    return secret


def _truncate_password(password: str) -> bytes:
    return password.encode("utf-8")[:_BCRYPT_MAX_BYTES]


def hash_password(password: str) -> str:
    hashed = bcrypt.hashpw(_truncate_password(password), bcrypt.gensalt())
    return hashed.decode("utf-8")


def verify_password(password: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(_truncate_password(password), hashed.encode("utf-8"))
    except (ValueError, TypeError):
        return False


def create_access_token(user_id: int) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(days=_JWT_EXPIRY_DAYS)).timestamp()),
    }
    return jwt.encode(payload, _jwt_secret(), algorithm=_JWT_ALGORITHM)


def _decode_user_id(token: str) -> int:
    try:
        payload = jwt.decode(token, _jwt_secret(), algorithms=[_JWT_ALGORITHM])
    except JWTError as e:
        raise ValueError(f"Invalid token: {e}") from e
    sub = payload.get("sub")
    if sub is None:
        raise ValueError("Missing 'sub' in token")
    try:
        return int(sub)
    except (TypeError, ValueError) as e:
        raise ValueError("'sub' is not an integer user id") from e


def current_user(
    authorization: str = Header(default=""),
    db: Session = Depends(get_db),
) -> User:
    """Required-auth dependency. Returns the user or raises 401."""
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing Bearer token")
    token = authorization[len("Bearer ") :]
    try:
        user_id = _decode_user_id(token)
    except ValueError as e:
        raise HTTPException(status_code=401, detail=str(e)) from e
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=401, detail="User not found")
    return user


def optional_current_user(
    authorization: str = Header(default=""),
    db: Session = Depends(get_db),
) -> User | None:
    """Optional-auth dependency. Returns the user if a valid token is present,
    or None if no token was sent. Invalid tokens still raise 401."""
    if not authorization:
        return None
    return current_user(authorization=authorization, db=db)
