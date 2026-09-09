from datetime import date

import pytest

from app.sources import egp


LISTING = """
<table><tr><th>header</th></tr>
<tr><td>1</td><td>1329363,<br/>BMAS/Store/GR-OTM-02,<br/><label>Live</label></td>
<td>Goods,<br/><form id="viewtenderform_0"><input name="id" value="1329363"/><span id="tenderBrief_0"><p>Supply of cloud software.</p></span></form></td>
<td>Ministry of Shipping,<br/>Bangladesh Marine Academy, Sylhet,<br/>Bangladesh Marine Academy, Sylhet</td>
<td>NCT,<br/>OTM</td><td>09-Sep-2026 15:30,<br/>30-Sep-2026 16:00</td></tr>
<tr><td>2</td><td>1327279,<br/>REF,<br/><label>Live</label></td>
<td>Works,<br/><form id="viewtenderform_1"><input name="id" value="1327279"/><span id="tenderBrief_1"><p>Renovation of road.</p></span></form></td>
<td>Ministry of Energy</td><td>NCT,<br/>OTM</td><td>09-Sep-2026 15:30,<br/>21-Sep-2026 14:00</td></tr>
<input id="totalPages" value="62472"/></table>
"""

DETAIL = """
<table><tr><td class="ff">Procuring Entity Name :</td><td>Bangladesh Marine Academy, Sylhet</td></tr>
<tr><td class="ff">Tender/Proposal Package No. and Description :</td><td>Supply of cloud software.</td></tr>
<tr><td class="ff">Eligibility of Tenderer :</td><td>As Per TDS.</td></tr>
<tr><td class="ff">Brief Description of Goods and Related Service :</td><td>Cloud platform support.</td></tr>
<tr><td class="ff">Scheduled Tender/Proposal Publication Date and Time :</td><td>09-Sep-2026 15:30</td></tr>
<tr><td class="ff">Tender/Proposal Closing Date and Time :</td><td>30-Sep-2026 16:00</td></tr></table>
"""


def test_listing_parser_and_relevance():
    records = egp._parse_listing_page(LISTING, "https://www.eprocure.gov.bd/resources/common/ViewTender.jsp")
    assert len(records) == 2
    assert records[0].tender.external_id == "1329363"
    assert records[0].tender.publish_date == date(2026, 9, 9)
    assert records[0].tender.deadline_date == date(2026, 9, 30)
    assert egp._is_it_relevant(records[0].tender)
    assert not egp._is_it_relevant(records[1].tender)
    assert records[0].tender.source_url.endswith("id=1329363&h=t")


def test_detail_parser():
    fields = egp._parse_detail(DETAIL)
    assert fields["procuring entity"] == "Bangladesh Marine Academy, Sylhet"
    assert fields["eligibility"] == "As Per TDS."
    assert fields["closing"] == "30-Sep-2026 16:00"


@pytest.mark.asyncio
async def test_enrich_merges_detail_fields():
    class Client:
        async def get(self, url):
            return type("Response", (), {"text": DETAIL, "raise_for_status": lambda self: None})()

    record = egp._parse_listing_page(LISTING, "https://www.eprocure.gov.bd/resources/common/ViewTender.jsp")[0]
    await egp._enrich(Client(), record, "")
    assert record.tender.procuring_entity == "Bangladesh Marine Academy, Sylhet"
    assert record.tender.geography == "Bangladesh"
    assert record.tender.publish_date == date(2026, 9, 9)
    assert record.tender.deadline_date == date(2026, 9, 30)
    assert "As Per TDS." in record.tender.description
    assert "detail" in record.raw


@pytest.mark.asyncio
async def test_fetch_limits_pages_and_details(monkeypatch):
    class Response:
        def __init__(self, text):
            self.text = text

        def raise_for_status(self):
            return None

    class Client:
        posts = []
        gets = []

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def post(self, url, data):
            self.posts.append(data["pageNo"])
            return Response(LISTING if int(data["pageNo"]) <= 40 else "<table></table>")

        async def get(self, url):
            self.gets.append(url)
            return Response(DETAIL)

    client = Client()
    monkeypatch.setattr(egp.httpx, "AsyncClient", lambda **kwargs: client)
    monkeypatch.setattr(egp.asyncio, "sleep", lambda _: _done())
    result = await egp.fetch("https://www.eprocure.gov.bd/resources/common/StdTenderSearch.jsp?h=t", 1)
    assert len(result) == 2
    assert len(client.posts) == 40
    assert len(client.gets) == 1


@pytest.mark.asyncio
async def test_fetch_stops_on_empty_page(monkeypatch):
    class Response:
        def __init__(self, text):
            self.text = text

        def raise_for_status(self):
            return None

    class Client:
        posts = []

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def post(self, url, data):
            self.posts.append(data["pageNo"])
            return Response(LISTING if data["pageNo"] == "1" else "<table></table>")

        async def get(self, url):
            return Response(DETAIL)

    client = Client()
    monkeypatch.setattr(egp.httpx, "AsyncClient", lambda **kwargs: client)
    monkeypatch.setattr(egp.asyncio, "sleep", lambda _: _done())
    result = await egp.fetch("https://www.eprocure.gov.bd/resources/common/StdTenderSearch.jsp?h=t", 1)
    assert len(result) == 2
    assert client.posts == ["1", "2"]


async def _done():
    return None
