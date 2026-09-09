import asyncio
import re
from datetime import date, datetime
from typing import Any
from urllib.parse import urlencode, urlsplit, urlunsplit

import httpx
from bs4 import BeautifulSoup

from app.models import SourceRecord, Tender


PAGE_SIZE = 10
MAX_PAGES = 40
IT_TERMS = {
    "cloud",
    "computer",
    "cyber",
    "data",
    "digital",
    "ict",
    "information technology",
    "network",
    "software",
    "system",
    "technology",
    "web",
}


async def fetch(url: str, timeout: float) -> list[SourceRecord]:
    search_url = _search_endpoint(url)
    detail_base = _detail_endpoint(url)
    transport = httpx.AsyncHTTPTransport(retries=2)
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True, transport=transport) as client:
        records: dict[str, SourceRecord] = {}
        for page in range(1, MAX_PAGES + 1):
            response = await client.post(
                search_url,
                data={
                    "funName": "AllTenders",
                    "keyword": "",
                    "pageNo": str(page),
                    "size": str(PAGE_SIZE),
                    "homeWSearch": "homeWSearch",
                    "approve": "false",
                    "h": "t",
                },
            )
            response.raise_for_status()
            page_records = _parse_listing_page(response.text, detail_base)
            if not page_records:
                break
            for record in page_records:
                if record.tender.external_id not in records:
                    records[record.tender.external_id] = record
            if page < MAX_PAGES:
                await asyncio.sleep(0.2)

        for record in records.values():
            if _is_it_relevant(record.tender):
                await _enrich(client, record, detail_base)

    return list(records.values())


def _parse_listing_page(html: str, detail_base: str) -> list[SourceRecord]:
    soup = BeautifulSoup(html, "html.parser")
    records = []
    for row in soup.select("tr"):
        cells = row.select(":scope > td")
        form = row.select_one("form[id^='viewtenderform_']")
        tender_id = form and form.select_one("input[name='id']")
        if len(cells) < 6 or tender_id is None:
            continue
        values = [_text(cell) for cell in cells]
        meta = [line for line in cells[5].stripped_strings]
        title = _text(cells[2].select_one("span[id^='tenderBrief_']") or cells[2])
        records.append(SourceRecord(
            tender=Tender(
                source="EGP",
                externalId=tender_id["value"],
                title=title or "Bangladesh e-GP tender",
                procuringEntity=values[3],
                description="; ".join(filter(None, (values[2], values[3], values[4]))),
                sourceUrl=_detail_url(detail_base, tender_id["value"]),
                publishDate=_date(meta[-2] if len(meta) >= 2 else ""),
                deadlineDate=_date(meta[-1] if meta else ""),
            ),
            raw={"listing": values, "tender_id": tender_id["value"]},
        ))
    return records


async def _enrich(client: httpx.AsyncClient, record: SourceRecord, detail_base: str) -> None:
    try:
        response = await client.get(record.tender.source_url or _detail_url(detail_base, record.tender.external_id))
        response.raise_for_status()
    except httpx.HTTPError:
        return
    fields = _parse_detail(response.text)
    tender = record.tender
    record.tender = tender.model_copy(update={
        "procuring_entity": fields.get("procuring entity") or tender.procuring_entity,
        "description": "\n".join(filter(None, (tender.description, fields.get("package description"), fields.get("category"), fields.get("eligibility")))),
        "publish_date": _date(fields.get("publication")) or tender.publish_date,
        "deadline_date": _date(fields.get("closing")) or tender.deadline_date,
        "geography": fields.get("location") or "Bangladesh",
    })
    record.raw["detail"] = fields


def _parse_detail(html: str) -> dict[str, str]:
    soup = BeautifulSoup(html, "html.parser")
    fields: dict[str, str] = {}
    for row in soup.select("tr"):
        cells = row.select(":scope > td")
        if len(cells) < 2:
            continue
        for index in range(0, len(cells) - 1, 2):
            label = _detail_key(_text(cells[index]))
            value = _text(cells[index + 1])
            if label and value:
                fields[label] = value
    return fields


def _is_it_relevant(tender: Tender) -> bool:
    text = f"{tender.title} {tender.description}".casefold()
    return any(re.search(rf"(?<!\w){re.escape(term)}(?!\w)", text) for term in IT_TERMS)


def _text(node: Any) -> str:
    return " ".join(node.stripped_strings) if node else ""


def _key(value: str) -> str:
    return re.sub(r"[^a-z ]", "", value.casefold()).strip()


def _detail_key(value: str) -> str:
    label = _key(value)
    aliases = {
        "procuring entity name": "procuring entity",
        "tenderproposal package no and description": "package description",
        "eligibility of tenderer": "eligibility",
        "brief description of goods and related service": "category",
        "scheduled tenderproposal publication date and time": "publication",
        "tenderproposal closing date and time": "closing",
        "place of delivery": "location",
    }
    return aliases.get(label, label)


def _date(value: str) -> date | None:
    if not value:
        return None
    value = value.strip()
    for pattern in ("%d-%b-%Y %H:%M", "%d-%b-%Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(value[:16] if pattern.endswith("%H:%M") else value[:10], pattern).date()
        except ValueError:
            continue
    return None


def _search_endpoint(url: str) -> str:
    parts = urlsplit(url)
    return urlunsplit((parts.scheme, parts.netloc, "/TenderDetailsServlet", "", ""))


def _detail_endpoint(url: str) -> str:
    parts = urlsplit(url)
    return urlunsplit((parts.scheme, parts.netloc, "/resources/common/ViewTender.jsp", "", ""))


def _detail_url(base: str, tender_id: str) -> str:
    return f"{base}?{urlencode({'id': tender_id, 'h': 't'})}"
