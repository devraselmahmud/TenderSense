from datetime import date

import pytest

from app.ai.extraction import ExtractedTender, TenderExtractor, _records, _spreadsheet_records


def test_records_are_stable_and_deduplicated():
    item = ExtractedTender(
        title="Cloud Security Services",
        procuring_entity="Example Bank",
        description="Deploy cloud controls",
        source_reference="RFP-42",
        deadline_date=date(2026, 10, 1),
        evidence="Cloud Security Services, RFP-42",
    )

    first = _records([item, item], "list.xlsx", "abc")
    second = _records([item], "renamed.xlsx", "abc")

    assert len(first) == 1
    assert first[0].tender.external_id == second[0].tender.external_id
    assert first[0].tender.external_id == "abc:c9e16d0ea1cae0f66aa9"
    assert first[0].tender.source == "UPLOAD"


@pytest.mark.asyncio
async def test_fallback_retains_document_when_structured_extraction_fails(monkeypatch):
    extractor = TenderExtractor(type("Settings", (), {"api_timeout": 1, "anthropic_model": "test"})())

    async def fail(*_):
        raise ValueError("bad response")

    monkeypatch.setattr(extractor, "_extract_chunk", fail)

    records, warnings = await extractor.extract("network-tenders.pdf", "Tender evidence", "hash")

    assert records[0].tender.external_id == "hash:document:1"
    assert records[0].tender.title == "network tenders"
    assert records[0].tender.description == "Tender evidence"
    assert warnings == ["Structured extraction unavailable (ValueError); imported document as one reviewable record"]


def test_spreadsheet_fallback_imports_each_data_row():
    text = """Worksheet: Tenders
Tender Title\tProcuring Organization\tDescription / Scope\tPublication Date\tSubmission Deadline\tLocation\tEstimated Value\tCurrency\tMinimum Turnover\tRequired Certifications\tReference URL
Cloud Security Operations Center\tDigital Services Authority\tManaged SOC\t2026-09-10\t2026-10-15\tDhaka\t150,000\tbdt\t50,000,000\tISO 27001; ISO 20000-1\thttps://example.com/soc
ERP Modernization\tNational Development Agency\tReplace ERP\t2026-09-08\t2026-10-22\tBangladesh\t99999\tBDT\t80000000\tISO 9001\thttps://example.com/erp
Network Upgrade\tPublic Administration Department\tUpgrade network\t2026-09-05\t2026-10-30\tChattogram\t2000\tUSD\t35000000\tISO 27001\thttps://example.com/network"""

    records = _spreadsheet_records("tenders.xlsx", text, "abc")

    assert [record.tender.title for record in records] == [
        "Cloud Security Operations Center",
        "ERP Modernization",
        "Network Upgrade",
    ]
    assert records[0].tender.estimated_value == 150_000
    assert records[0].tender.estimated_value_currency == "BDT"
    assert records[0].tender.required_turnover == 50_000_000
    assert records[0].tender.required_certifications == ["ISO 27001", "ISO 20000-1"]
    assert records[0].tender.deadline_date == date(2026, 10, 15)
    assert records[0].tender.source_url == "https://example.com/soc"


@pytest.mark.asyncio
async def test_recognized_spreadsheet_skips_ai_extraction(monkeypatch):
    extractor = TenderExtractor(type("Settings", (), {"api_timeout": 1, "anthropic_model": "test"})())

    async def fail(*_):
        raise AssertionError("AI extraction should not run")

    monkeypatch.setattr(extractor, "_extract_chunk", fail)
    text = "Tender Title\tDescription\nCloud Security\tManaged SOC\nERP Modernization\tReplace ERP"

    records, warnings = await extractor.extract("tenders.xlsx", text, "hash")

    assert len(records) == 2
    assert warnings == []
