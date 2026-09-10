# TenderSense: Project Overview

## 1. Purpose

TenderSense is a procurement-intelligence application for BracIT. It reduces manual tender discovery by collecting procurement notices, comparing them with BracIT capabilities, checking stated eligibility requirements, and presenting a ranked daily shortlist.

TenderSense supports business-development decisions; it does not make bid decisions automatically. Users review evidence and record `BID`, `HOLD`, or `SKIP` themselves.

## 2. Problem Being Solved

Procurement notices appear across multiple portals and vary in structure and wording. Manual review creates three problems:

- Relevant opportunities can be missed.
- Staff spend time reading tenders that do not fit BracIT.
- Match and rejection reasoning can be inconsistent.

TenderSense addresses these problems with one repeatable workflow: collect, normalize, match, check, explain, rank, and review.

## 3. Current Capabilities

### BracIT profile management

Authorized users maintain BracIT capability data through `/profile`:

- Annual turnover and currency
- Service names and descriptions
- Past contracts: title, client, sector, year, and description
- Certifications, issuing bodies, and validity dates
- Supported countries or regions

Every save creates a new immutable profile version. Today’s stored tenders are then rescored in background against that new version.

### Procurement collection

TenderSense currently connects to:

- World Bank procurement notices
- Asian Development Bank procurement notices
- Bangladesh e-GP

Bangladesh e-GP collection reads up to 40 result pages with 10 listings per page, for a maximum of 400 listings per run. Duplicate tender IDs are removed. Detail pages are fetched only for listings that appear related to IT or BracIT services.

Collection runs daily at **4:00 AM Asia/Dhaka**. Administrators can also start ingestion through the admin API. Dashboard **Refresh** only reloads today’s saved shortlist; it does not start a long source crawl.

### Profile matching

Each tender is compared semantically with profile segments built from:

- Service name and description
- Past-contract title and description
- Certification name

System records strongest matching profile segment and similarity score. Configured thresholds assign grades:

| Grade | Minimum similarity |
|---|---:|
| S | 0.85 |
| A | 0.70 |
| B | 0.55 |
| C | Below 0.55 |

Dashboard currently includes only `S`, `A`, and `B` results scored against latest profile.

### Deterministic eligibility

Eligibility is calculated with fixed rules, not generative AI:

- BracIT turnover compared with stated minimum turnover
- Active BracIT certifications compared with stated requirements
- BracIT geographies compared with tender geography

Possible outcomes:

- `ELIGIBLE`: all stated requirements pass
- `INELIGIBLE`: at least one requirement fails
- `NEEDS_VERIFICATION`: no requirement fails, but some information is absent or cannot be verified

Default dashboard excludes `INELIGIBLE` tenders. It includes `ELIGIBLE` and `NEEDS_VERIFICATION` tenders so uncertain cases still receive human review.

### AI-generated explanation

AI writes readable “why this matched” and “what is missing” summaries. It receives match and eligibility results already calculated by system.

AI is explicitly instructed not to:

- Decide eligibility
- Override deterministic rules
- Recommend `BID`, `HOLD`, or `SKIP`

If AI provider is unavailable, deterministic fallback text still identifies matched capability and eligibility reason.

### Human decision record

Selecting tender opens details, including source, procuring entity, summary, grade, eligibility, deadline, and original procurement link. User can save `BID`, `HOLD`, or `SKIP` with optional note.

Saving decision creates history entry. It does not change tender source data, rerun matching, or launch ingestion.

## 4. Daily Operating Flow

```text
09:00-20:00 hourly, Asia/Dhaka
        |
        v
Collect World Bank, ADB, and Bangladesh e-GP notices
        |
        v
Keep raw source evidence in MongoDB
        |
        v
Normalize and upsert tenders in PostgreSQL
        |
        v
Match each tender against latest BracIT profile
        |
        v
Run deterministic eligibility checks
        |
        v
Generate explanatory summary
        |
        v
Show today’s current-profile S/A/B shortlist
        |
        v
Human records BID, HOLD, or SKIP
```

## 5. What Dashboard Shows

Today’s dashboard requires all following conditions:

- Tender was published today using Asia/Dhaka date.
- Processing status is `SCORED`.
- Score used latest BracIT profile version.
- Grade is `S`, `A`, or `B`.
- Eligibility is `ELIGIBLE` or `NEEDS_VERIFICATION`.

This prevents low-fit, stale, failed, and explicitly ineligible records from appearing as current opportunities.

After profile save, dashboard can temporarily be empty while background rescoring runs. Click **Refresh** after processing completes.

## 6. System Components

| Component | Responsibility |
|---|---|
| Angular frontend | Login, profile editing, daily shortlist, tender details, decision entry |
| Spring Boot backend | Authentication, authorization, profile versions, tender persistence, eligibility, grading, shortlist, decisions |
| FastAPI service | Source ingestion, semantic embeddings, AI summary, hourly daytime scheduler |
| PostgreSQL | Structured operational records and business history |
| MongoDB | Raw source payloads, scrape logs, embedding cache |
| Docker Compose | Runs and connects all services |

PostgreSQL and MongoDB are complementary. PostgreSQL holds authoritative business state; MongoDB preserves variable source payloads and cache/log data. Current scale does not require another graph database. Relationship tables in PostgreSQL can support future context-graph features first.

## 7. Security and Access

- Users authenticate with email and password.
- Passwords are stored as BCrypt hashes.
- Browser API calls use signed JWT bearer tokens.
- Roles are `BD_EXECUTIVE`, `BD_UNIT_LEAD`, and `ADMIN`.
- Profile updates require `BD_UNIT_LEAD` or `ADMIN`.
- Admin APIs require `ADMIN`.
- Backend-to-Python communication uses `X-Internal-Token`.
- Provider URL, token, model, and timeout come from environment variables; secrets are not stored in source documents or images.

## 8. Data Ownership and Traceability

TenderSense retains traceability through:

- Source and external tender ID
- Original procurement URL
- Raw source payload in MongoDB
- Profile version used for scoring
- Strongest matched profile segment
- Similarity score and grade
- Per-rule eligibility outcomes and reasons
- Generated summary or fallback explanation
- User decision history with timestamp and user
- Source run status and error category

## 9. Current Limitations

- Dashboard has no interactive grade, deadline, or eligibility filter controls yet.
- Admin capabilities exist as APIs; full admin webpage is not implemented.
- ADB adapter currently extracts qualifying links from source page and needs stronger source-specific validation.
- Configurable eligibility-rule rows are stored, but eligibility engine currently applies built-in turnover, certification, and geography logic.
- No notification channel is implemented.
- No separate graph database is used; current relationships remain in PostgreSQL.
- Ingestion quality depends on source availability and HTML/API stability.

## 10. Success Criteria

TenderSense is working as intended when:

- Sources update daily and failures are visible.
- Profile changes create new version and trigger rescoring.
- Dashboard contains only today’s latest-profile S/A/B opportunities.
- Eligibility decisions remain deterministic and auditable.
- Every visible tender has useful explanation or fallback.
- Original source remains accessible for human verification.
- Final `BID`, `HOLD`, or `SKIP` decision remains human-controlled.
