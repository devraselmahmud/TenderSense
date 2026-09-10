import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    backend_url: str = os.getenv("BACKEND_URL", "http://localhost:8080")
    internal_token: str = os.getenv("INTERNAL_API_TOKEN", "development-internal-token")
    mongo_url: str = os.getenv("MONGO_URL", "mongodb://localhost:27017")
    mongo_database: str = os.getenv("MONGO_DATABASE", "tendersense")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "sentence-transformers/all-MiniLM-L6-v2")
    anthropic_model: str = os.getenv("ANTHROPIC_MODEL", "USIS-COMBO")
    api_timeout: float = int(os.getenv("API_TIMEOUT_MS", "300000")) / 1000
    world_bank_url: str = os.getenv("WORLD_BANK_URL", "https://search.worldbank.org/api/v2/procnotices")
    adb_url: str = os.getenv("ADB_URL", "https://www.adb.org/business/institutional-procurement/notices")
    egp_search_url: str = os.getenv(
        "EGP_SEARCH_URL",
        "https://www.eprocure.gov.bd/resources/common/StdTenderSearch.jsp?h=t",
    )
