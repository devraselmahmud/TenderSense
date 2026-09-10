from datetime import date

from app.sources.adb import _parse


def test_parse_notices():
    html = """
    <a href="/business">Unrelated navigation link</a>
    <table class="table thead-heading mb-2">
      <tbody>
        <tr>
          <td><a href="/sites/default/files/notice.zip">Invitation to Bid: M365 DLP Rollout</a></td>
          <td>9 September 2026</td>
          <td>28 September 2026, 5:00 p.m. (Manila time)</td>
        </tr>
      </tbody>
    </table>
    """

    records = _parse(html, "https://www.adb.org/business/institutional-procurement/notices")

    assert len(records) == 1
    tender = records[0].tender
    assert tender.title == "Invitation to Bid: M365 DLP Rollout"
    assert tender.source_url == "https://www.adb.org/sites/default/files/notice.zip"
    assert tender.publish_date == date(2026, 9, 9)
    assert tender.deadline_date == date(2026, 9, 28)
