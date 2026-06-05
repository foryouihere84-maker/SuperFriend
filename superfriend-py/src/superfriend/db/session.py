from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy import text

from superfriend.config import settings

engine = create_async_engine(
    settings.database_url,
    echo=settings.debug,
    pool_size=10,
    max_overflow=20,
    pool_recycle=3600,
    pool_pre_ping=True,
)

async_session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


async def get_db() -> AsyncSession:
    async with async_session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise


async def init_db() -> None:
    """验证数据库连接，复用 Java 项目已有的表，不自动建表"""
    async with engine.begin() as conn:
        result = await conn.execute(text("SELECT 1"))
        print(f"[DB] 数据库连接成功: {settings.database_url.split('@')[-1]}")

        # 检查核心表是否存在
        tables = ["users", "chat_history", "chat_message", "ai_model_config",
                   "skills", "knowledge_node", "knowledge_relation", "memory_palace"]
        for table in tables:
            try:
                await conn.execute(text(f"SELECT 1 FROM {table} LIMIT 1"))
            except Exception:
                print(f"[DB] 警告: 表 {table} 不存在，需要先启动 Java 项目创建表")
