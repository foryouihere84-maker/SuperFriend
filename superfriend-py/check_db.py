import asyncio
from sqlalchemy import text
from superfriend.db.session import engine

async def check():
    async with engine.begin() as conn:
        r = await conn.execute(text(
            "SELECT id, name, provider, api_url, LEFT(api_key,20) as api_key_prefix, "
            "model_id, is_default, is_enabled FROM ai_model_config"
        ))
        for row in r:
            print(dict(row._mapping))

asyncio.run(check())
