from datetime import date
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


class Tender(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    source: str
    external_id: str = Field(alias="externalId")
    title: str
    procuring_entity: str | None = Field(default=None, alias="procuringEntity")
    description: str
    source_url: str | None = Field(default=None, alias="sourceUrl")
    publish_date: date | None = Field(default=None, alias="publishDate")
    deadline_date: date | None = Field(default=None, alias="deadlineDate")
    geography: str | None = None
    estimated_value: float | None = Field(default=None, alias="estimatedValue", ge=0)
    estimated_value_currency: str | None = Field(default=None, alias="estimatedValueCurrency", pattern=r"^[A-Z]{3}$")
    required_turnover: float | None = Field(default=None, alias="requiredTurnover")
    required_certifications: list[str] = Field(default_factory=list, alias="requiredCertifications")

    @field_validator("estimated_value_currency", mode="before")
    @classmethod
    def normalize_currency(cls, value: str | None) -> str | None:
        return value.strip().upper() if value else None


class SourceRecord(BaseModel):
    tender: Tender
    raw: dict[str, Any]


class MatchRequest(BaseModel):
    tender_text: str
    profile_segments: list[str]


class MatchResponse(BaseModel):
    score: float
    segment: str


class SummaryRequest(BaseModel):
    title: str
    description: str
    matched_segment: str
    eligibility_reason: str
    grade: str
    profile_version: int


class SummaryResponse(BaseModel):
    summary: str
