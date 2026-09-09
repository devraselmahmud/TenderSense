import anthropic

from app.config import Settings
from app.models import SummaryRequest


class Summarizer:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = None

    async def summarize(self, request: SummaryRequest) -> str:
        try:
            if self.client is None:
                self.client = anthropic.AsyncAnthropic(timeout=self.settings.api_timeout)
            response = await self.client.messages.create(
                model=self.settings.anthropic_model,
                max_tokens=300,
                system=[{"type": "text", "text": "Explain tender/profile match and eligibility gaps only. Never recommend BID, HOLD, or SKIP. Never override deterministic eligibility.", "cache_control": {"type": "ephemeral"}}],
                messages=[{"role": "user", "content": f"Tender title: {request.title}\nTender description: {request.description}\nMatched profile capability: {request.matched_segment}\nEligibility: {request.eligibility_reason}\nGrade: {request.grade}\nProfile version: {request.profile_version}"}],
            )
            return next((block.text for block in response.content if block.type == "text"), "")
        except Exception:
            return f"Matched profile capability: {request.matched_segment}. {request.eligibility_reason}"
