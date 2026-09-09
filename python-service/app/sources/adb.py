from urllib.parse import urljoin

from bs4 import BeautifulSoup
import httpx

from app.models import SourceRecord, Tender


async def fetch(url: str, timeout: float) -> list[SourceRecord]:
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
        response = await client.get(url)
        response.raise_for_status()
    soup = BeautifulSoup(response.text, "html.parser")
    records = []
    for link in soup.select("a[href]"):
        title = link.get_text(" ", strip=True)
        href = link.get("href")
        if not title or not href or len(title) < 12:
            continue
        absolute = urljoin(url, href)
        records.append(SourceRecord(tender=Tender(source="ADB", externalId=absolute, title=title, description=title, sourceUrl=absolute), raw={"title": title, "url": absolute}))
    return records
