from datetime import datetime, timedelta
import hashlib
import structlog
from fastapi import APIRouter, HTTPException, Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel
from jose import jwt, JWTError

from superfriend.config import settings

router = APIRouter()
security = HTTPBearer()
logger = structlog.get_logger()


class LoginRequest(BaseModel):
    username: str
    password: str


class RegisterRequest(BaseModel):
    username: str
    password: str
    email: str | None = None


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int


def _hash_password(password: str) -> str:
    """简单密码哈希（兼容 Java Spring Security 的 BCrypt 或简单哈希）"""
    return hashlib.sha256(password.encode()).hexdigest()


def create_token(user_id: int, username: str) -> str:
    expire = datetime.utcnow() + timedelta(minutes=settings.jwt_expire_minutes)
    payload = {
        "sub": str(user_id),
        "username": username,
        "exp": expire,
        "iat": datetime.utcnow(),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def verify_token(credentials: HTTPAuthorizationCredentials = Depends(security)) -> dict:
    token = credentials.credentials
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
        return payload
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid or expired token")


@router.post("/login", response_model=TokenResponse)
async def login(request: LoginRequest):
    """使用数据库验证用户登录"""
    try:
        from superfriend.db.session import async_session_factory
        from superfriend.db.models import User
        from sqlalchemy import select

        async with async_session_factory() as session:
            # 按用户名查找
            result = await session.execute(
                select(User).where(User.username == request.username)
            )
            user = result.scalar_one_or_none()

            if user is None:
                raise HTTPException(status_code=401, detail="用户名或密码错误")

            # 验证密码：先尝试明文匹配，再尝试哈希匹配
            password_match = False
            if user.password == request.password:
                password_match = True
            elif user.password == _hash_password(request.password):
                password_match = True
            # 兼容 BCrypt 格式（$2a$ 开头）
            elif user.password and user.password.startswith("$2"):
                try:
                    import bcrypt
                    password_match = bcrypt.checkpw(
                        request.password.encode(), user.password.encode()
                    )
                except ImportError:
                    pass

            if not password_match:
                raise HTTPException(status_code=401, detail="用户名或密码错误")

            token = create_token(user.id, user.username)
            return TokenResponse(
                access_token=token,
                expires_in=settings.jwt_expire_minutes * 60,
            )

    except HTTPException:
        raise
    except Exception as e:
        logger.error("login_error", error=str(e))
        # 降级：如果数据库不可用，允许 admin/admin123 登录
        if request.username == "admin" and request.password == "admin123":
            token = create_token(1, request.username)
            return TokenResponse(
                access_token=token,
                expires_in=settings.jwt_expire_minutes * 60,
            )
        raise HTTPException(status_code=401, detail="登录服务暂时不可用")


@router.post("/register")
async def register(request: RegisterRequest):
    """注册新用户"""
    try:
        from superfriend.db.session import async_session_factory
        from superfriend.db.models import User
        from sqlalchemy import select

        async with async_session_factory() as session:
            # 检查用户名是否已存在
            result = await session.execute(
                select(User).where(User.username == request.username)
            )
            if result.scalar_one_or_none():
                raise HTTPException(status_code=400, detail="用户名已存在")

            # 创建用户
            user = User(
                username=request.username,
                password=_hash_password(request.password),
                email=request.email,
                nickname=request.username,
            )
            session.add(user)
            await session.commit()
            await session.refresh(user)

            return {"status": "registered", "username": request.username, "user_id": user.id}

    except HTTPException:
        raise
    except Exception as e:
        logger.error("register_error", error=str(e))
        raise HTTPException(status_code=500, detail="注册服务暂时不可用")


@router.get("/me")
async def get_current_user(payload: dict = Depends(verify_token)):
    return {
        "user_id": payload.get("sub"),
        "username": payload.get("username"),
    }
