"""
DevFlow AI 服务 - 核心配置
"""
from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict
from functools import lru_cache

# .env 绝对路径（相对于 config.py 所在目录，不受 IDE CWD 影响）
_ENV_FILE = str(Path(__file__).resolve().parent.parent.parent / ".env")


class Settings(BaseSettings):
    """应用配置 - 读取 .env 文件和环境变量"""

    # ============ OpenAI ============
    openai_api_key: str = "sk-placeholder"
    openai_base_url: str = "https://api.openai.com/v1"
    openai_model: str = "gpt-4o-mini"

    # ============ DeepSeek ============
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com/v1"
    deepseek_model: str = "deepseek-chat"

    # ============ 通义千问 (阿里云百炼, OpenAI 兼容) ============
    qwen_api_key: str = ""
    qwen_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    qwen_model: str = "qwen-plus"

    # ============ Kimi (月之暗面, OpenAI 兼容) ============
    kimi_api_key: str = ""
    kimi_base_url: str = "https://api.moonshot.cn/v1"
    kimi_model: str = "moonshot-v1-8k"

    # ============ 火山方舟 (字节, OpenAI 兼容) ============
    volcano_api_key: str = ""
    volcano_base_url: str = "https://ark.cn-beijing.volces.com/api/v3"
    volcano_model: str = "deepseek-v3-250324"  # 需替换为实际 Endpoint ID

    # ============ GLM (智谱, OpenAI 兼容) ============
    glm_api_key: str = ""
    glm_base_url: str = "https://open.bigmodel.cn/api/paas/v4"
    glm_model: str = "glm-4-flash"

    # 当前启用的厂商: openai / deepseek / qwen / kimi / volcano / glm
    ai_provider: str = "openai"

    # 嵌入向量配置（OpenAI 兼容接口，可独立于对话厂商；如通义 text-embedding-v3、智谱等）
    # EMBEDDING_PROVIDER=local 时使用 sentence-transformers 本地模型，零 API 成本
    embedding_provider: str = "api"  # api / local
    embedding_api_key: str = "sk-placeholder"
    embedding_base_url: str = "https://api.openai.com/v1"
    embedding_model: str = "text-embedding-3-small"
    embedding_local_model: str = "BAAI/bge-small-zh-v1.5"

    # Redis 配置
    redis_url: str = "redis://localhost:6379/1"

    # ChromaDB 配置
    chroma_host: str = "localhost"
    chroma_port: int = 8001
    chroma_collection: str = "devflow_docs"

    # Java 后端地址
    backend_api_url: str = "http://localhost:8080/api/v1"

    # JWT 密钥
    jwt_secret: str = ""

    # 应用配置
    app_name: str = "DevFlow AI Service"
    debug: bool = False
    api_prefix: str = "/api/v1"

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ---- 多厂商 LLM 配置 ----
    @property
    def providers(self) -> dict:
        return {
            "openai": {
                "api_key": self.openai_api_key,
                "base_url": self.openai_base_url,
                "model": self.openai_model,
            },
            "deepseek": {
                "api_key": self.deepseek_api_key,
                "base_url": self.deepseek_base_url,
                "model": self.deepseek_model,
            },
            "qwen": {
                "api_key": self.qwen_api_key,
                "base_url": self.qwen_base_url,
                "model": self.qwen_model,
            },
            "kimi": {
                "api_key": self.kimi_api_key,
                "base_url": self.kimi_base_url,
                "model": self.kimi_model,
            },
            "volcano": {
                "api_key": self.volcano_api_key,
                "base_url": self.volcano_base_url,
                "model": self.volcano_model,
            },
            "glm": {
                "api_key": self.glm_api_key,
                "base_url": self.glm_base_url,
                "model": self.glm_model,
            },
        }

    def get_provider(self, name: str | None = None) -> dict:
        """获取指定（或当前启用）厂商的 LLM 配置"""
        key = (name or self.ai_provider).lower()
        return self.providers.get(key, self.providers[self.ai_provider.lower()])

    def is_provider_configured(self, name: str | None = None) -> bool:
        """判断厂商是否已配置有效 Key（占位符视为未配置）"""
        cfg = self.get_provider(name)
        key = (cfg.get("api_key") or "").strip()
        return bool(key) and key not in ("sk-placeholder", "sk-your-api-key-here")


@lru_cache()
def get_settings() -> Settings:
    """获取缓存的配置实例"""
    return Settings()
