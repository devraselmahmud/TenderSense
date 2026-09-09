from datetime import datetime, timezone

import httpx

from app.config import Settings
from app.models import SourceRecord
from app.sources import adb, egp, world_bank


async def run(settings: Settings, database=None) -> dict[str, int]:
    sources = {"WORLD_BANK": lambda: world_bank.fetch(settings.world_bank_url, settings.api_timeout), "ADB": lambda: adb.fetch(settings.adb_url, settings.api_timeout), "EGP": lambda: egp.fetch(settings.egp_search_url, settings.api_timeout)}
    result = {}
    for name, fetch in sources.items():
        try:
            records = await fetch()
            for record in records:
                if database is not None:
                    database.raw_tenders.replace_one(
                        {"source": record.tender.source, "external_id": record.tender.external_id},
                        {"source": record.tender.source, "external_id": record.tender.external_id, "payload": record.raw, "ingested_at": datetime.now(timezone.utc)},
                        upsert=True,
                    )
                await _send(settings, record)
            result[name] = len(records)
            if database is not None:
                database.scrape_logs.insert_one({"source": name, "at": datetime.now(timezone.utc), "records": len(records), "error": None})
            await _health(settings, name, len(records), None)
        except Exception as exc:
            result[name] = 0
            if database is not None:
                database.scrape_logs.insert_one({"source": name, "at": datetime.now(timezone.utc), "records": 0, "error": type(exc).__name__})
            await _health(settings, name, 0, type(exc).__name__)
    return result


async def _send(settings: Settings, record: SourceRecord) -> None:
    async with httpx.AsyncClient(timeout=settings.api_timeout) as client:
        response = await client.post(f"{settings.backend_url}/api/internal/tenders", headers={"X-Internal-Token": settings.internal_token}, json=record.tender.model_dump(by_alias=True, mode="json"))
        response.raise_for_status()


async def _health(settings: Settings, source: str, count: int, error: str | None) -> None:
    try:
        async with httpx.AsyncClient(timeout=settings.api_timeout) as client:
            response = await client.post(f"{settings.backend_url}/api/internal/source-health", headers={"X-Internal-Token": settings.internal_token}, json={"source": source, "records": count, "error": error, "at": datetime.now(timezone.utc).isoformat()})
            response.raise_for_status()
    except Exception:
        return
