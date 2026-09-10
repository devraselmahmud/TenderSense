from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, File, Form, HTTPException, UploadFile
from pymongo import MongoClient

from app.ai.extraction import TenderExtractor
from app.ai.matching import Matcher
from app.ai.summarization import Summarizer
from app.config import Settings
from app.models import MatchRequest, MatchResponse, SummaryRequest, SummaryResponse
from app.pipeline import run
from app.scheduler import start
from app.security import require_internal_token
from app.uploads import UploadExtractionResponse, UploadProcessor, UploadValidationError

settings = Settings()
mongo_client = MongoClient(settings.mongo_url, serverSelectionTimeoutMS=2000)
database = mongo_client[settings.mongo_database]
matcher = Matcher(settings.embedding_model, database.embeddings)
summarizer = Summarizer(settings)
upload_processor = UploadProcessor(database, TenderExtractor(settings), settings.upload_max_bytes)


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


@app.post("/internal/uploads/extract", response_model=UploadExtractionResponse, dependencies=[Depends(require_internal_token)])
async def extract_upload(file: UploadFile = File(), uploader: str = Form()):
    content = bytearray()
    while chunk := await file.read(1024 * 1024):
        content.extend(chunk)
        if len(content) > settings.upload_max_bytes:
            raise HTTPException(status_code=413, detail={"code": "file_too_large", "message": "File exceeds 10 MiB limit"})
    try:
        return await upload_processor.process(file.filename or "upload", file.content_type, bytes(content), uploader)
    except UploadValidationError as error:
        raise HTTPException(status_code=422, detail={"code": error.code, "message": str(error)}) from error
    except ValueError as error:
        raise HTTPException(status_code=422, detail={"code": "content_too_large", "message": str(error)}) from error


@app.post("/ingestion/run", dependencies=[Depends(require_internal_token)])
async def ingestion_run():
    return await run(settings, database)
