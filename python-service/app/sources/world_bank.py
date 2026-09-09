from datetime import date, datetime
from typing import Any

import httpx

from app.models import SourceRecord, Tender


async def fetch(url: str, timeout: float) -> list[SourceRecord]:
    async with httpx.AsyncClient(timeout=timeout) as client:
        response = await client.get(url, params={"format": "json", "rows": 50, "os": 0})
        response.raise_for_status()
        payload = response.json()
    return [_record(row) for row in payload.get("procnotices", [])]


def _record(row: dict[str, Any]) -> SourceRecord:
    deadline = _date(row.get("submission_deadline_date"))
    published = _date(row.get("noticedate"))
    return SourceRecord(tender=Tender(
        source="WORLD_BANK", externalId=str(row.get("id") or row.get("bid_reference_no")),
        title=row.get("bid_description") or row.get("project_name") or "World Bank procurement notice",
        procuringEntity=row.get("contact_organization"), description=row.get("notice_text") or row.get("bid_description") or row.get("project_name") or "",
        sourceUrl=row.get("notice_url") or row.get("url"),
        publishDate=published, deadlineDate=deadline, geography=row.get("project_ctry_name")), raw=row)


def _date(value: Any) -> date | None:
    if not value:
        return None
    text = str(value)
    try:
        return date.fromisoformat(text[:10])
    except ValueError:
        return datetime.strptime(text, "%d-%b-%Y").date()
