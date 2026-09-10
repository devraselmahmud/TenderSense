# TenderSense Technical Guide

## 1. Scope

TenderSense is multi-service tender-ingestion and decision-support system:

- Angular 21 standalone frontend
- Spring Boot 3.5 / Java 21 backend
- FastAPI / Python 3.12 ingestion and AI service
- PostgreSQL 16 operational database
- MongoDB 8 raw-data, log, and embedding store
- Docker Compose deployment

Runtime matching uses BracIT profile entered in UI. A 40-labeled-tender dataset is not required in production; such data remains optional offline benchmark material.

## 2. Runtime Architecture

```text
Browser
  |
  | HTTP :4200
  v
Nginx + Angular
  |
  | /api/* reverse proxy
  v
Spring Boot :8080 --------------------> PostgreSQL
  |                                         |
  | X-Internal-Token                       | profiles, tenders,
  v                                         | rules, decisions
FastAPI :8000 ------------------------> MongoDB
  |
  +--> World Bank
  +--> ADB
  +--> Bangladesh e-GP
  +--> sentence-transformers
  +--> Anthropic-compatible provider through official Anthropic SDK
```

External host ports:

- Frontend: `4200`
- Backend: `8080`

PostgreSQL, MongoDB, and FastAPI remain internal to Compose network by default.

## 3. Component Responsibilities

### Frontend

Routes:

| Route | Component | Purpose |
|---|---|---|
| `/` | `Dashboard` | Today’s profile-matched shortlist and decisions |
| `/profile` | `Profile` | Load and save BracIT profile |
| Any unknown route | Redirect | Returns to dashboard |

Root component gates route outlet behind login state. Token is stored in browser `localStorage` and sent as `Authorization: Bearer <token>`.

Dashboard calls:

- `GET /api/tenders?publishedToday=true`
- `GET /api/tenders/{id}`
- `POST /api/tenders/{id}/decision`

Profile page calls:

- `GET /api/profile`
- `PUT /api/profile`

Nginx serves Angular SPA and proxies `/api/` to backend.

### Spring Boot backend

Backend owns:

- User authentication and role enforcement
- Immutable BracIT profile versions
- Normalized tender upsert
- Deterministic eligibility
- Similarity-grade mapping
- Latest-profile shortlist query
- Tender detail and rule evidence
- Bid-decision history
- Source-health state
- Admin APIs

### FastAPI service

Python service owns:

- Source adapters and normalization
- Hourly daytime scheduler
- Raw payload persistence
- Semantic embedding and nearest profile-segment matching
- Embedding cache
- AI explanation generation with deterministic fallback

## 4. Ingestion Pipeline

Entry points:

- Scheduled hourly from `09:00` through `20:00` in `Asia/Dhaka`
- `POST /ingestion/run`, protected by internal token
- Backend admin proxy: `POST /api/admin/ingestion/run`

Pipeline processes sources independently:

1. Fetch source records.
2. Normalize each record into common `Tender` model.
3. Upsert raw payload into MongoDB `raw_tenders` by `(source, external_id)`.
4. POST normalized record to `/api/internal/tenders`.
5. Write MongoDB `scrape_logs` record.
6. Update PostgreSQL `source_health` through `/api/internal/source-health`.
7. Source failure produces zero count for that source but does not stop later sources.

### World Bank

Uses procurement notices JSON API with `rows=50` and `os=0`. Date parser supports ISO dates and `DD-Mon-YYYY`.

### ADB

Fetches configured institutional-procurement page and emits sufficiently descriptive links as tender records. Current parser is generic HTML-link extraction.

### Bangladesh e-GP

Current bounds:

```text
PAGE_SIZE = 10
MAX_PAGES = 40
Maximum listing rows per run = 400
```

Behavior:

- POSTs pages 1 through 40 to `TenderDetailsServlet`.
- Stops when parsed page has no records.
- Waits 0.2 seconds between listing pages.
- Deduplicates by e-GP external tender ID.
- Keeps every unique listing row.
- Fetches detail page only when title/description matches bounded IT vocabulary.
- Enriches procuring entity, description, publication date, closing date, and geography where available.
- HTTP transport retries connection failures twice.

## 5. Normalized Tender Contract

Python sends JSON fields:

```json
{
  "source": "EGP",
  "externalId": "1329363",
  "title": "Supply of cloud software",
  "procuringEntity": "Example agency",
  "description": "...",
  "sourceUrl": "https://...",
  "publishDate": "2026-09-09",
  "deadlineDate": "2026-09-30",
  "geography": "Bangladesh",
  "requiredTurnover": null,
  "requiredCertifications": []
}
```

Backend enforces uniqueness on `(source, external_id)`. Conflict updates normalized fields and sets status to `NEW`, then immediately invokes processing.

## 6. Profile Data and Versioning

Profile contract contains:

- `turnoverAmount`
- `currency`
- `services[]`: `name`, `description`
- `pastProjects[]`: `title`, `client`, `sector`, nullable `year`, `description`
- `certifications[]`: `name`, `issuingBody`, nullable `validUntil`
- `geographies[]`

Each `PUT /api/profile` inserts next integer version. Existing profile rows remain unchanged. After insert:

1. Active tenders are marked `NEW`.
2. `TenderProcessingService.processToday()` starts asynchronously.
3. Today’s stored tender IDs are processed against latest profile.
4. Dashboard withholds stale and `NEW` rows until processing writes current profile version and `SCORED`.

Profile save does not refetch procurement sources.

## 7. Matching Algorithm

Profile segments are constructed as:

```text
<Service name>: <service description>
<Past-project title>: <past-project description>
Certification: <certification name>
```

Tender text is title plus description. `sentence-transformers` encodes tender and segments with normalized embeddings. Dot product therefore gives cosine similarity. Highest-scoring segment becomes `matched_segment`.

MongoDB `embeddings` cache key hashes:

- Embedding model name
- Tender text
- Ordered profile segments

Grade thresholds are loaded from PostgreSQL:

| Grade | Minimum score |
|---|---:|
| S | 0.85 |
| A | 0.70 |
| B | 0.55 |
| C | No configured threshold reached |

## 8. Deterministic Eligibility

`EligibilityEngine` evaluates three dimensions.

### Turnover

- Missing tender minimum: `NOT_VERIFIABLE`
- Profile turnover meets/exceeds minimum: `PASS`
- Otherwise: `FAIL`

### Certifications

- Expired profile certifications are ignored.
- Every stated tender certification requires normalized exact name match.
- Missing required certification: `FAIL`
- No stated certification requirements: `NOT_VERIFIABLE`

### Geography

- Missing tender geography: `NOT_VERIFIABLE`
- Normalized exact profile geography match: `PASS`
- Otherwise: `FAIL`

Aggregate status:

```text
Any FAIL             = INELIGIBLE
No FAIL, any unknown = NEEDS_VERIFICATION
All PASS             = ELIGIBLE
```

Per-rule evidence is replaced on each processing run in `tender_rule_results`.

## 9. AI Summary Boundary

Backend passes to Python:

- Tender title and description
- Strongest profile segment
- Eligibility reason
- Grade
- Profile version

Python calls configured Anthropic-compatible provider through official `anthropic` SDK. System instruction limits model to explaining match and gaps; model must not recommend `BID`, `HOLD`, or `SKIP`, and must not override deterministic eligibility.

If provider call fails, fallback is:

```text
Matched profile capability: <segment>. <eligibility reason>
```

Provider settings come only from environment:

- `ANTHROPIC_BASE_URL`
- `ANTHROPIC_AUTH_TOKEN`
- `ANTHROPIC_MODEL`
- `API_TIMEOUT_MS`

Application never reads `.claude/settings.json`.

## 10. Shortlist Semantics

`GET /api/tenders?publishedToday=true` applies:

```sql
status = 'SCORED'
profile_version = (SELECT MAX(version) FROM bracit_profiles)
grade IN ('S', 'A', 'B')
eligibility_status IN ('ELIGIBLE', 'NEEDS_VERIFICATION')
publish_date = current Asia/Dhaka date
```

Ordering:

1. S
2. A
3. B
4. Earliest non-null deadline within grade

Consequences:

- C results excluded.
- `INELIGIBLE` results excluded.
- Stale profile versions excluded.
- `NEW`, failed, and unscored records excluded.
- Dashboard can be temporarily empty during profile-triggered rescoring.

## 11. Decision Workflow

`POST /api/tenders/{id}/decision` accepts:

```json
{
  "decision": "BID",
  "note": "Optional note"
}
```

Allowed values: `BID`, `HOLD`, `SKIP`.

Every save inserts new `bid_decisions` row. Existing history remains. Decision write does not mutate tender score, eligibility, summary, or profile.

## 12. API Inventory

### Public

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/login` | Authenticate and issue JWT |
| `GET` | `/actuator/health` | Backend health |

### Authenticated

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/profile` | Latest profile and version history |
| `PUT` | `/api/profile` | Create profile version; lead/admin only |
| `GET` | `/api/tenders` | Profile-driven shortlist |
| `GET` | `/api/tenders/{id}` | Tender detail, rule results, decisions |
| `POST` | `/api/tenders/{id}/decision` | Append user decision |

### Admin

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/admin/source-health` | Source run status |
| `GET` | `/api/admin/rules` | Eligibility rows and grade thresholds |
| `PUT` | `/api/admin/rules/{id}` | Update stored rule metadata |
| `GET` | `/api/admin/users` | List users |
| `POST` | `/api/admin/users` | Create user |
| `POST` | `/api/admin/ingestion/run` | Start synchronous ingestion request |

### Internal service endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/internal/tenders` | Normalize/upsert/process tender |
| `POST` | `/api/internal/source-health` | Update source-health row |
| `POST` | `/internal/match-score` | Calculate semantic match |
| `POST` | `/internal/summarize` | Generate explanation |
| `POST` | `/ingestion/run` | Run all source adapters |
| `GET` | `/health` | Python service health |

## 13. PostgreSQL Data Model

| Table | Purpose |
|---|---|
| `users` | Identity, BCrypt hash, role, enabled state |
| `bracit_profiles` | Immutable profile versions |
| `eligibility_rules` | Stored rule configuration metadata |
| `grade_thresholds` | S/A/B score thresholds |
| `tenders` | Normalized tender, match, eligibility, summary, processing state |
| `tender_rule_results` | Per-rule outcomes and reasons |
| `bid_decisions` | Append-only human decision history |
| `source_health` | Latest source run status |

PostgreSQL is authoritative for product behavior.

Useful checks:

```sql
SELECT version, created_at
FROM bracit_profiles
ORDER BY version DESC;

SELECT source, status, grade, eligibility_status, profile_version, count(*)
FROM tenders
GROUP BY source, status, grade, eligibility_status, profile_version
ORDER BY source, status, grade;

SELECT t.title, r.rule_type, r.outcome, r.reason
FROM tender_rule_results r
JOIN tenders t ON t.id = r.tender_id
ORDER BY t.id, r.id;

SELECT t.title, d.decision, d.note, u.email, d.decided_at
FROM bid_decisions d
JOIN tenders t ON t.id = d.tender_id
JOIN users u ON u.id = d.user_id
ORDER BY d.decided_at DESC;
```

## 14. MongoDB Collections

| Collection | Purpose |
|---|---|
| `raw_tenders` | Latest raw payload per `(source, external_id)` |
| `scrape_logs` | Per-source run count and error category |
| `embeddings` | Semantic score and segment cache by content hash |

MongoDB is not authoritative for shortlist or decisions.

Useful checks:

```javascript
db.raw_tenders.countDocuments({})
db.raw_tenders.find({source: "EGP"}, {external_id: 1, ingested_at: 1}).limit(10)
db.scrape_logs.find().sort({at: -1}).limit(20)
db.embeddings.countDocuments({})
```

## 15. Security

- Passwords: BCrypt
- Session token: HS256 JWT, 8-hour expiry, issuer `tendersense`
- Roles: `BD_EXECUTIVE`, `BD_UNIT_LEAD`, `ADMIN`
- `PUT /api/profile`: lead or admin
- `/api/admin/**`: admin only
- Internal endpoints: shared `X-Internal-Token`, compared in constant time on Java side
- CORS: configured local frontend origins
- Original source links: new tab with `noopener noreferrer`
- Secrets: environment variables, not source or image layers

Initial admin is inserted at startup only when configured email does not already exist. Changing environment password does not update existing admin hash.

## 16. Deployment and Operations

Start stack:

```bash
docker compose -f /home/raselm/Documents/TenderSense/compose.yaml up -d --build
```

Status:

```bash
docker compose -f /home/raselm/Documents/TenderSense/compose.yaml ps
```

Backend health:

```bash
curl http://localhost:8080/actuator/health
```

Logs:

```bash
docker compose -f /home/raselm/Documents/TenderSense/compose.yaml logs --since=30m backend
docker compose -f /home/raselm/Documents/TenderSense/compose.yaml logs --since=30m python-service
```

Do not use dashboard Refresh to diagnose ingestion. Check `source_health`, `scrape_logs`, and Python logs.

## 17. Verification

Current automated checks:

- Java eligibility-engine tests
- Java shortlist SQL-policy test
- Java today-batch processing test
- Python e-GP parser, detail merge, relevance, page ceiling, and early-stop tests
- Python empty-profile matching and World Bank date tests
- Angular production build

Commands:

```bash
mvn -f /home/raselm/Documents/TenderSense/backend/pom.xml test
/home/raselm/Documents/TenderSense/python-service/.venv/bin/pytest /home/raselm/Documents/TenderSense/python-service/tests
npm --prefix /home/raselm/Documents/TenderSense/frontend run build
```

Manual golden path:

1. Sign in.
2. Open `/profile`, update valid profile, and save.
3. Wait for background rescoring.
4. Open dashboard and click Refresh.
5. Confirm every row is today’s latest-profile S/A/B result.
6. Open tender and verify summary, eligibility, deadline, and source link.
7. Save decision and verify newest decision in detail history/database.

## 18. Known Limits and Next Work

- No frontend admin pages.
- No client-side grade/deadline/eligibility controls.
- Stored `eligibility_rules` updates do not yet alter engine behavior.
- Grade is currently based on similarity alone; explicit eligibility-based grade cap is not implemented.
- ADB parser needs source-specific selectors and live regression tests.
- No email, Slack, or other notification delivery.
- No benchmark CLI for keyword top-5 versus semantic top-5.
- No graph database. Add relational match-edge tables in PostgreSQL before considering another datastore.
- Integration and security test coverage remains smaller than unit/parser coverage.
