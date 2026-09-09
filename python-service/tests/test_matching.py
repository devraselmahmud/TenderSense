from datetime import date

from app.ai.matching import Matcher
from app.sources.world_bank import _date


def test_empty_profile_has_zero_score():
    assert Matcher("unused").score("tender", []) == (0.0, "")


def test_world_bank_date_formats():
    assert _date("2026-10-13T00:00:00Z") == date(2026, 10, 13)
    assert _date("07-Sep-2026") == date(2026, 9, 7)
