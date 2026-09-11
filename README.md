# TenderSense

TenderSense collects procurement notices, imports external tender documents, compares opportunities with BracIT capabilities, applies deterministic eligibility rules, and presents a qualified dashboard shortlist.

## Architecture

| Component | Technology | Responsibility |
|---|---|---|
| Frontend | Angular 21, Nginx | Login, profile, uploads, shortlist, tender decisions |
| Backend | Java 21, Spring Boot 3.5 | Authentication, profiles, ingestion, eligibility, grading, dashboard API |
| AI/ingestion service | Python 3.12, FastAPI | Source collection, document extraction, semantic matching, summaries |
| Business database | PostgreSQL 16 | Users, profile versions, normalized tenders, rules, decisions |
| Document/raw store | MongoDB 8, GridFS | Raw source payloads, original uploads, extraction audit data, embedding cache |

```text
Browser :4200
  -> Nginx
  -> Spring Boot :8080
     -> PostgreSQL
     -> FastAPI :8000
        -> MongoDB
        -> procurement sources
        -> embedding model
        -> configured Claude-compatible API
```

## Requirements

### Recommended: Docker

- Docker Engine or Docker Desktop
- Docker Compose v2
- Internet access for images, procurement sources, Python model download, and configured AI endpoint

### Native development

- Java 21
- Maven 3.9+
- Python 3.12+
- Node.js 22 and npm 10
- PostgreSQL 16
- MongoDB 8

Application dependencies are declared in:

- `backend/pom.xml`
- `python-service/pyproject.toml`
- `frontend/package.json`

Major runtime dependencies include Spring Boot, Spring Security, Flyway, PostgreSQL JDBC, FastAPI, Anthropic Python SDK, sentence-transformers, Beautiful Soup, openpyxl, python-docx, pypdf, PyMongo, Angular, and RxJS.

## Deployment guides

- [`DOCKER_RUNTIME_DEPLOYMENT.md`](DOCKER_RUNTIME_DEPLOYMENT.md): run all five services from Docker images with runtime environment variables or external databases.
- [`ANTHROPIC_DEPLOYMENT.md`](ANTHROPIC_DEPLOYMENT.md): configure direct Anthropic API authentication, verification, rotation, networking, backups, and troubleshooting.

## Build and push Docker images

After code changes, rebuild and push all three service images:

```bash
make login
make push-images TAG=1.0.2
```

Push the current commit short SHA:

```bash
make tag-commit
```

Image naming:

| Service | Image |
|---|---|
| Spring backend | `raselmahmudbits/tendersense-backend:<TAG>` |
| Python service | `raselmahmudbits/tendersense-python-service:<TAG>` |
| Angular frontend | `raselmahmudbits/tendersense-frontend:<TAG>` |

Script signature:

```bash
scripts/push-images.sh [TAG]
```

Environment variables: `DOCKERHUB_USER`, `TAG`, `PLATFORMS`, `PUSH`.

## Quick start with Docker

1. Create local environment file:

   ```bash
   cp .env.example .env
   ```

2. Change placeholder credentials and secrets in `.env`, especially:

   ```dotenv
   POSTGRES_PASSWORD=replace-this
   JWT_SECRET=replace-with-at-least-32-random-characters
   INTERNAL_API_TOKEN=replace-with-a-random-token
   ADMIN_PASSWORD=replace-this
   ANTHROPIC_API_KEY=replace-with-anthropic-api-key
   ```

3. Start and build all services:

   ```bash
   docker compose up -d --build
   ```

4. Check status and backend health:

   ```bash
   docker compose ps
   curl http://localhost:8080/actuator/health
   ```

5. Open:

   ```text
   http://localhost:4200
   ```

### Default development login

When Compose defaults are unchanged:

```text
Email: admin@bracits.net
Password: change-me-now
```

Change these before shared or production-like use. Admin creation runs only when email does not already exist. Changing `ADMIN_PASSWORD` after first startup does not update an existing account.

## Service URLs

| Service | Host address |
|---|---|
| Frontend | `http://localhost:4200` |
| Backend API | `http://localhost:8080` |
| Backend health | `http://localhost:8080/actuator/health` |
| FastAPI | Internal only: `http://python-service:8000` |
| PostgreSQL | Internal only: `postgres:5432` |
| MongoDB | Internal only: `mongo:27017` |

## Configuration

`compose.yaml` reads these variables from `.env`:

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_DB` | `tendersense` | PostgreSQL database name |
| `POSTGRES_USER` | `tendersense` | PostgreSQL user |
| `POSTGRES_PASSWORD` | `change-me` | PostgreSQL password |
| `MONGO_DATABASE` | `tendersense` | MongoDB database name |
| `JWT_SECRET` | Development placeholder | JWT signing secret |
| `INTERNAL_API_TOKEN` | Development placeholder | Shared backend/FastAPI credential |
| `ADMIN_EMAIL` | `admin@bracits.net` | Initial admin email |
| `ADMIN_PASSWORD` | `change-me-now` | Initial admin password |
| `ANTHROPIC_API_KEY` | Empty | Direct Anthropic API credential |
| `ANTHROPIC_BASE_URL` | Empty | Anthropic or Claude-compatible Messages API base URL |
| `ANTHROPIC_AUTH_TOKEN` | Empty | Optional custom-gateway bearer credential; leave empty for direct Anthropic API |
| `ANTHROPIC_MODEL` | `USIS-COMBO` | Extraction/summary model; direct Anthropic deployment overrides this |
| `API_TIMEOUT_MS` | `300000` | External API timeout in milliseconds |

Python also supports these environment variables when run directly:

- `EMBEDDING_MODEL`
- `WORLD_BANK_URL`
- `ADB_URL`
- `EGP_SEARCH_URL`
- `UPLOAD_MAX_BYTES`

Default embedding model is `sentence-transformers/all-MiniLM-L6-v2`. First scoring request may download model weights and take longer.

## Docker operations

View logs:

```bash
docker compose logs -f backend python-service frontend
```

Stop and restart while preserving PostgreSQL and MongoDB data:

```bash
docker compose stop
docker compose start
```

Rebuild changed services while retaining named volumes:

```bash
docker compose up -d --build
```

Do not run `docker compose down -v` when data must survive. `-v` deletes named PostgreSQL and MongoDB volumes.

## Native development

Docker is preferred because `compose.yaml` wires service addresses and databases automatically. Native startup requires reachable PostgreSQL and MongoDB instances.

### Backend

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/tendersense \
POSTGRES_USER=tendersense \
POSTGRES_PASSWORD=tendersense \
mvn -f backend/pom.xml spring-boot:run
```

Backend starts at `http://localhost:8080`. Flyway applies database migrations automatically.

### Python service

```bash
python3.12 -m venv python-service/.venv
python-service/.venv/bin/pip install torch --index-url https://download.pytorch.org/whl/cpu
python-service/.venv/bin/pip install -e "python-service[test]"
python-service/.venv/bin/python python-service/run.py
```

Python service defaults to:

```text
Backend: http://localhost:8080
MongoDB: mongodb://localhost:27017
Port: 8000
```

### Frontend

```bash
npm --prefix frontend ci
npm --prefix frontend start
```

Angular development server starts at `http://localhost:4200` and proxies `/api` to `http://localhost:8080`.

## User manual

### 1. Sign in

1. Open `http://localhost:4200`.
2. Enter configured admin email and password.
3. Select **Sign in**.

JWT session lasts eight hours. **Sign out** removes browser token.

### 2. Configure BracIT profile

Open **Profile** and enter:

- **Turnover and currency**: Used by hard eligibility check against tender minimum turnover.
- **Minimum tender budget (BDT)**: Hides tenders with known BDT estimates below configured amount. Existing profiles default to BDT 100,000.
- **Services**: Name and description of each offered service.
- **Past contracts**: Title and description provide evidence of relevant delivery experience.
- **Certifications**: Name and validity date. Expired certifications do not pass eligibility.
- **Geographies**: Countries or regions where BracIT can bid.

Select **Save profile**. Each save creates a new immutable profile version and starts tender rescoring. Dashboard may be temporarily empty while rescoring runs.

Profile matching uses:

- service name and description;
- past-contract title and description;
- certification name.

Tender title and description are compared with those profile segments. Highest semantic similarity becomes tender match score.

### 3. Understand matching grades

| Grade | Similarity score | Dashboard eligible by relevance |
|---|---:|---|
| S | 85% or higher | Yes |
| A | 70%–84.99% | Yes |
| B | 55%–69.99% | Yes |
| C | Below 55% | No |

Grade measures relevance only. Tender must also satisfy eligibility and budget rules.

### 4. Understand hard eligibility

Eligibility is rules-based Java logic, not an AI decision.

#### Turnover

- Profile turnover meets or exceeds tender minimum: `PASS`
- Profile turnover is lower: `FAIL`
- Tender has no stated minimum: `NOT_VERIFIABLE`

#### Certifications

- Every required certification must match an active profile certification.
- Matching is trimmed, case-insensitive exact text.
- Missing or expired required certification: `FAIL`
- No tender certification requirement: `NOT_VERIFIABLE`

#### Geography

- Tender geography exactly matches one configured profile geography, ignoring case and surrounding spaces: `PASS`
- Different geography: `FAIL`
- Missing tender geography: `NOT_VERIFIABLE`

Aggregate result:

```text
Any FAIL                         -> INELIGIBLE
No FAIL, at least one unknown    -> NEEDS_VERIFICATION
Every rule passes                -> ELIGIBLE
```

Clear rule reasons are stored with tender, such as `Missing active certification: ISO 9001` or `Required turnover exceeds BracIT profile turnover`. AI summaries cannot override these results.

### 5. Upload external tender documents

Open **Upload**.

Supported files:

- DOCX
- XLSX
- selectable-text PDF
- maximum 10 MiB

Procedure:

1. Select **Choose tender document**.
2. Choose file.
3. Select **Import and score**.
4. Wait for extraction, ingestion, eligibility checks, semantic scoring, and summaries.
5. Review imported and matched counts.
6. Select **View dashboard**.

Original file is retained in MongoDB GridFS for audit and reprocessing. Re-uploading identical file reuses stable upload identity and upserts same tenders instead of creating duplicate source IDs.

#### XLSX guidance

No fixed template is required for AI extraction. For fast deterministic row extraction, use one header row and one tender per subsequent row. Recognized headers include:

- `Tender Title` or `Title`
- `Procuring Organization` or `Procuring Entity`
- `Description / Scope` or `Description`
- `Publication Date`
- `Submission Deadline` or `Deadline`
- `Location` or `Geography`
- `Estimated Value`, `Tender Value`, or `Budget`
- `Currency` or `Budget Currency`
- `Minimum Turnover` or `Required Turnover`
- `Required Certifications`
- `Reference URL` or `Source URL`

Dates should use `YYYY-MM-DD`. Use numeric values without currency symbols. Separate certifications with semicolons or commas.

Repository includes manual test fixture:

```text
TenderSense_Upload_Test.xlsx
```

#### Document limitations

- Scanned or image-only PDFs are rejected because OCR is not implemented.
- DOCX/PDF and arbitrary spreadsheet extraction depend on configured Claude-compatible API.
- When structured AI extraction fails, TenderSense retains file and may import deterministic fallback record for review.

### 6. Dashboard visibility rules

Dashboard shows tender only when all conditions pass:

- processing status is `SCORED`;
- score grade is S, A, or B;
- eligibility is `ELIGIBLE` or `NEEDS_VERIFICATION`;
- tender estimated value meets latest profile's **Minimum tender budget (BDT)**;
- estimated value currency is exactly BDT;
- score uses latest profile version;
- source tender was published today, or uploaded tender was imported today, in Asia/Dhaka time.

Tenders are hidden when budget is missing, below latest profile's BDT threshold, or uses another currency. No currency conversion is performed. Hidden tenders remain stored.

Exact semantic duplicates are filtered from dashboard but retained in database because separate source IDs or URLs may represent distinct procurement packages.

### 7. Review and decide

1. Select tender card from dashboard.
2. Review grade, eligibility, deadline, summary, and source link.
3. Choose decision:
   - `BID`
   - `HOLD`
   - `SKIP`
4. Add optional note.
5. Select **Save decision**.

Decisions are appended to tender history.

## Automatic ingestion

Python scheduler runs source ingestion hourly from 09:00 through 20:00 in `Asia/Dhaka` timezone.

Configured sources:

- World Bank procurement notices
- Asian Development Bank institutional procurement notices
- Bangladesh e-GP

Pipeline stores raw source records in MongoDB, normalizes tenders, upserts PostgreSQL records, applies hard eligibility, calculates semantic score, assigns grade, and creates summary.

Source records without explicit normalized tender budget remain stored but are hidden by BDT 100,000 dashboard policy.

## Tests

Backend:

```bash
mvn -f backend/pom.xml test
```

Python, after test dependencies are installed:

```bash
python-service/.venv/bin/python -m pytest -c python-service/pyproject.toml python-service/tests
```

Frontend production build:

```bash
npm --prefix frontend ci
npm --prefix frontend run build
```

Current automated coverage includes eligibility rules, shortlist policy, upload forwarding, document validation, XLSX/DOCX/PDF parsing, fallback extraction, source parsing, semantic matching, stable upload IDs, and budget extraction.

## Troubleshooting

### Login fails after changing `.env`

Existing admin password is not overwritten during startup. `ADMIN_PASSWORD` only applies when admin email is first inserted. Use existing password or update account through authorized administration process.

### Upload says `0 matched current profile`

Import succeeded, but no tender passed every dashboard condition. Check:

- grade is S/A/B;
- hard eligibility has no `FAIL`;
- estimated value meets latest profile's minimum tender budget;
- currency is BDT;
- profile contains relevant services/contracts/certifications.

### `API returned an empty or malformed response (HTTP 200)`

Configured proxy or AI gateway returned HTTP 200 without valid Claude Messages API content. Verify `ANTHROPIC_BASE_URL`, `ANTHROPIC_AUTH_TOKEN`, model name, and gateway compatibility. Recognized XLSX tables use deterministic extraction and do not require AI.

### Scanned PDF rejected

Convert file to selectable-text PDF or DOCX/XLSX. OCR is not supported.

### First scoring request is slow

Python service may be downloading sentence-transformers model weights. Monitor:

```bash
docker compose logs -f python-service
```

### Backend is unavailable

```bash
docker compose ps
docker compose logs --tail=100 backend
curl http://localhost:8080/actuator/health
```

### Preserve data during restart

Use:

```bash
docker compose stop
docker compose start
```

Never use `docker compose down -v` when PostgreSQL or MongoDB data must survive.
