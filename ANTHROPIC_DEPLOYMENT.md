# Deploy TenderSense with Anthropic API

This guide deploys TenderSense with Docker Compose and authenticates Python AI extraction and summaries directly against Anthropic API.

## 1. Server requirements

Install:

- Docker Engine
- Docker Compose v2
- Git
- outbound HTTPS access to `api.anthropic.com`
- enough disk for PostgreSQL, MongoDB uploads, Docker images, and embedding-model cache

Recommended production setup also includes:

- DNS name for TenderSense
- TLS reverse proxy or load balancer
- firewall allowing only SSH and HTTPS from approved networks
- scheduled PostgreSQL and MongoDB backups

Do not expose PostgreSQL, MongoDB, or Python service publicly. Current Compose exposes only frontend port `4200` and backend port `8080` on host.

## 2. Create Anthropic API key

Create production API key in Anthropic Console. Use service-specific key rather than personal development key.

Key must remain server-side. Never:

- commit it to Git;
- place it in Angular frontend code;
- include it in Docker image;
- print it in logs or screenshots;
- send it to browser.

If key is exposed, revoke it and create replacement immediately.

## 3. Create deployment environment

From repository root:

```bash
cp .env.example .env
chmod 600 .env
```

Generate application secrets:

```bash
openssl rand -base64 48
openssl rand -hex 32
```

Use first generated value for `JWT_SECRET` and second for `INTERNAL_API_TOKEN`. Set `.env`:

```dotenv
POSTGRES_DB=tendersense
POSTGRES_USER=tendersense
POSTGRES_PASSWORD=replace-with-strong-database-password
MONGO_DATABASE=tendersense

JWT_SECRET=replace-with-generated-secret
INTERNAL_API_TOKEN=replace-with-generated-token

ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=replace-with-strong-initial-password

ANTHROPIC_API_KEY=replace-with-anthropic-api-key
ANTHROPIC_BASE_URL=https://api.anthropic.com
ANTHROPIC_AUTH_TOKEN=
ANTHROPIC_MODEL=claude-opus-4-7
API_TIMEOUT_MS=300000

EGP_SEARCH_URL=https://www.eprocure.gov.bd/resources/common/StdTenderSearch.jsp?h=t
```

Direct Anthropic API uses:

- `ANTHROPIC_API_KEY` for authentication;
- `https://api.anthropic.com` as base URL;
- `claude-opus-4-7` as configured model;
- empty `ANTHROPIC_AUTH_TOKEN`.

`ANTHROPIC_AUTH_TOKEN` is only for intentional custom gateways using bearer-token authentication. Do not set both API key and auth token. SDK gives `ANTHROPIC_API_KEY` precedence.

`.env` is ignored by Git. Verify before deployment:

```bash
git check-ignore .env
```

Expected output:

```text
.env
```

## 4. Build and start

```bash
docker compose config --quiet
docker compose up -d --build
```

Flyway applies PostgreSQL migrations automatically. Named volumes preserve PostgreSQL and MongoDB data.

Check services:

```bash
docker compose ps
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:4200/
```

Expected backend response:

```json
{"status":"UP"}
```

## 5. Verify Anthropic configuration

Confirm variable names exist inside Python container without printing secret value:

```bash
docker compose exec -T python-service python -c "import os; assert os.environ.get('ANTHROPIC_API_KEY'); assert os.environ.get('ANTHROPIC_BASE_URL') == 'https://api.anthropic.com'; print(os.environ['ANTHROPIC_MODEL'])"
```

Expected model:

```text
claude-opus-4-7
```

Then test AI path through application:

1. Sign in to TenderSense.
2. Open **Upload**.
3. Upload selectable-text PDF or DOCX containing tender information.
4. Confirm extraction returns structured tender records without `Structured extraction unavailable` warning.
5. Check Python logs:

   ```bash
   docker compose logs --tail=100 python-service
   ```

Recognized XLSX rows use deterministic extraction and can succeed without Anthropic call. Use DOCX, selectable-text PDF, or nonstandard XLSX layout when testing API connectivity.

## 6. Production network setup

Put TLS reverse proxy or load balancer in front of frontend. Route public HTTPS traffic to frontend port `4200`.

Frontend Nginx proxies `/api/` to Spring backend inside Compose. Public clients should not need direct backend port `8080` access. Restrict or remove host binding for `8080` after reverse-proxy topology is configured and tested.

Required outbound access:

- `api.anthropic.com:443` for AI requests;
- configured procurement source hosts;
- model-download host during first sentence-transformers initialization, unless model is preloaded.

Required internal access:

- frontend to backend `8080`;
- backend to Python service `8000`;
- backend to PostgreSQL `5432`;
- Python service to MongoDB `27017`.

## 7. Updates and safe restarts

### Rebuild and push images from source

After code changes, rebuild and push service images to Docker Hub using the helper script.

Login:

```bash
make login
```

Push version tag:

```bash
make push-images TAG=1.0.2
```

Push current commit:

```bash
make tag-commit
```

Manual equivalent:

```bash
DOCKERHUB_USER=raselmahmudbits TAG=1.0.2 scripts/push-images.sh
```

Image naming convention:

| Service | Image |
|---|---|
| Spring backend | `raselmahmudbits/tendersense-backend:<TAG>` |
| Python service | `raselmahmudbits/tendersense-python-service:<TAG>` |
| Angular frontend | `raselmahmudbits/tendersense-frontend:<TAG>` |

Multi-arch build:

```bash
PLATFORMS=linux/amd64,linux/arm64 make push-images TAG=1.0.2
```

Script signature:

```bash
scripts/push-images.sh [TAG]
```

Environment variables:

- `DOCKERHUB_USER` (default `raselmahmudbits`)
- `TAG` (default `latest`)
- `PLATFORMS` (default `linux/amd64`)
- `PUSH=0` builds locally without pushing

Pull and rebuild code:

```bash
git pull --ff-only
docker compose up -d --build
docker compose ps
```

Restart without rebuilding:

```bash
docker compose stop
docker compose start
```

Never run this when stored data must survive:

```bash
docker compose down -v
```

`-v` deletes named PostgreSQL and MongoDB volumes.

## 8. Rotate Anthropic API key

1. Create replacement key in Anthropic Console.
2. Replace `ANTHROPIC_API_KEY` in `.env`.
3. Recreate Python service:

   ```bash
   docker compose up -d --force-recreate python-service
   ```

4. Test DOCX or selectable-text PDF upload.
5. Revoke old key after successful verification.

## 9. Backups

PostgreSQL example:

```bash
docker compose exec -T postgres pg_dump -U tendersense tendersense > tendersense-postgres.sql
```

MongoDB example:

```bash
docker compose exec -T mongo mongodump --archive > tendersense-mongo.archive
```

Store backups encrypted outside application server. Test restoration before relying on backups.

## 10. Troubleshooting

### `Could not resolve authentication method`

`ANTHROPIC_API_KEY` is missing inside Python container. Check `.env`, then recreate service:

```bash
docker compose up -d --force-recreate python-service
```

### Anthropic returns `401`

Key is invalid, revoked, malformed, or unavailable to container. Replace key and recreate Python service. Do not print full key while debugging.

### Anthropic returns `403`

Key or workspace lacks access required by configured model. Verify workspace permissions and `ANTHROPIC_MODEL`.

### Anthropic returns `429`

Workspace reached rate or usage limit. Review Anthropic usage limits and retry after limit resets or capacity changes.

### `API returned an empty or malformed response (HTTP 200)`

Usually custom proxy or gateway returned nonstandard Messages API payload. For direct Anthropic API, use:

```dotenv
ANTHROPIC_BASE_URL=https://api.anthropic.com
ANTHROPIC_API_KEY=replace-with-anthropic-api-key
ANTHROPIC_AUTH_TOKEN=
ANTHROPIC_MODEL=claude-opus-4-7
```

Recreate Python service afterward.

### First scoring request is slow

Python service may download sentence-transformers model weights. Monitor:

```bash
docker compose logs -f python-service
```

### Scanned PDF fails

OCR is not implemented. Convert file to selectable-text PDF, DOCX, or recognized XLSX format.
