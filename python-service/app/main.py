from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from pymongo import MongoClient

from app.ai.matching import Matcher
from app.ai.summarization import Summarizer
from app.config import Settings
from app.models import MatchRequest, MatchResponse, SummaryRequest, SummaryResponse
from app.pipeline import run
from app.scheduler import start
from app.security import require_internal_token

settings = Settings()
mongo_client = MongoClient(settings.mongo_url, serverSelectionTimeoutMS=2000)
database = mongo_client[settings.mongo_database]
matcher = Matcher(settings.embedding_model, database.embeddings)
summarizer = Summarizer(settings)


scheduler = None


@asynccontextmanager
async def lifespan(_: FastAPI):
    global scheduler
    scheduler = start(settings, database)
    yield
    scheduler.shutdown(wait=False)
    mongo_client.close()


app = FastAPI(title="TenderSense ingestion and AI service", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/internal/match-score", response_model=MatchResponse, dependencies=[Depends(require_internal_token)])
def match(request: MatchRequest):
    score, segment = matcher.score(request.tender_text, request.profile_segments)
    return MatchResponse(score=score, segment=segment)


@app.post("/internal/summarize", response_model=SummaryResponse, dependencies=[Depends(require_internal_token)])
async def summarize(request: SummaryRequest):
    return SummaryResponse(summary=await summarizer.summarize(request))


@app.post("/ingestion/run", dependencies=[Depends(require_internal_token)])
async def ingestion_run():
    return await run(settings, database)
