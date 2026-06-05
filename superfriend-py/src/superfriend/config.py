from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    app_name: str = "SuperFriend"
    debug: bool = False
    host: str = "0.0.0.0"
    port: int = 8080

    database_url: str = "mysql+asyncmy://root:123456@localhost:3306/superfriend"
    redis_url: str = "redis://localhost:6379/0"

    jwt_secret: str = "superfriend-secret-key"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 1440

    llm_api_url: str = "https://api.deepseek.com/v1"
    llm_api_key: str = ""
    llm_default_model: str = "deepseek-chat"

    mcp_config_path: str = ""
    mcp_react_max_iterations: int = 80
    mcp_react_max_consecutive_errors: int = 8
    mcp_react_step_timeout_ms: int = 120000
    mcp_react_total_timeout_ms: int = 600000
    mcp_idle_timeout_ms: int = 3000000

    oss_endpoint: str = ""
    oss_access_key_id: str = ""
    oss_access_key_secret: str = ""
    oss_bucket_name: str = ""

    tencent_secret_id: str = ""
    tencent_secret_key: str = ""
    tencent_cos_region: str = ""
    tencent_cos_bucket: str = ""

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()