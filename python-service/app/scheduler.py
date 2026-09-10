from apscheduler.schedulers.asyncio import AsyncIOScheduler

from app.config import Settings
from app.pipeline import run


def start(settings: Settings, database=None) -> AsyncIOScheduler:
    scheduler = AsyncIOScheduler(timezone="Asia/Dhaka")
    scheduler.add_job(run, "cron", hour="9-20", minute=0, args=[settings, database], id="hourly-ingestion", replace_existing=True)
    scheduler.start()
    return scheduler
