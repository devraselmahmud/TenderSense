from datetime import datetime
from urllib.parse import urljoin

from bs4 import BeautifulSoup
import httpx

from app.models import SourceRecord, Tender


async def fetch(url: str, timeout: float) -> list[SourceRecord]:
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
        response = await client.get(url)
        response.raise_for_status()
    return _parse(response.text, url)


def _parse(html: str, url: str) -> list[SourceRecord]:
    soup = BeautifulSoup(html, "html.parser")
    records = []
    for row in soup.select("table.thead-heading tbody tr"):
        cells = row.select(":scope > td")
        link = cells[0].select_one("a[href]") if len(cells) == 3 else None
        if link is None:
            continue
        title = link.get_text(" ", strip=True)
        absolute = urljoin(url, link["href"])
        start = cells[1].get_text(" ", strip=True)
        end = cells[2].get_text(" ", strip=True)
        records.append(SourceRecord(
            tender=Tender(
                source="ADB",
                externalId=absolute,
                title=title,
                procuringEntity="Asian Development Bank",
                description=title,
                sourceUrl=absolute,
                publishDate=_date(start),
                deadlineDate=_date(end),
            ),
            raw={"title": title, "url": absolute, "start_date": start, "end_date": end},
        ))
    return records


def _date(value: str):
    try:
        return datetime.strptime(value.split(",", 1)[0], "%d %B %Y").date()
    except ValueError:
        return None
