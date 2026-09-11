# Deploy TenderSense Images with Runtime Environment Variables

This guide runs frontend, backend, Python service, PostgreSQL, and MongoDB from Docker images. Configuration and secrets are supplied at container runtime, not embedded into images.

## 1. Files required on deployment server

Copy checked-in `compose.prod.yaml` to deployment server and create private runtime file beside it:

```text
compose.prod.yaml
.env.prod
```

Keep `.env.prod` private. Do not commit, copy into images, print in logs, or send to browser.

## 2. Create `.env.prod`

```dotenv
# Application image version
IMAGE_TAG=latest

# Public frontend port
FRONTEND_PORT=4200

# PostgreSQL
POSTGRES_DB=tendersense
POSTGRES_USER=tendersense
POSTGRES_PASSWORD=replace-with-strong-password
DATABASE_URL=jdbc:postgresql://postgres:5432/tendersense

# MongoDB
MONGO_URL=mongodb://mongo:27017
MONGO_DATABASE=tendersense

# Internal service locations
BACKEND_UPSTREAM=backend:8080
PYTHON_SERVICE_URL=http://python-service:8000
BACKEND_URL=http://backend:8080

# Application security
JWT_SECRET=replace-with-at-least-32-random-characters
INTERNAL_API_TOKEN=replace-with-random-token

# Initial administrator
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=replace-with-strong-password

# Direct Anthropic API
ANTHROPIC_API_KEY=replace-with-anthropic-api-key
ANTHROPIC_BASE_URL=https://api.anthropic.com
ANTHROPIC_AUTH_TOKEN=
ANTHROPIC_MODEL=claude-opus-4-7
API_TIMEOUT_MS=300000
```

Generate secrets:

```bash
openssl rand -base64 48
openssl rand -hex 32
```

Use generated values for `JWT_SECRET` and `INTERNAL_API_TOKEN`. Protect file:

```bash
chmod 600 .env.prod
```

`ADMIN_PASSWORD` creates initial admin only when `ADMIN_EMAIL` does not exist. Changing it later does not update existing account.

## 3. Production Compose

Repository includes authoritative `compose.prod.yaml`. It defines all five services, Docker Hub images, health checks, restart policy, runtime variables, and persistent PostgreSQL/MongoDB volumes.

Inspect changes without rendering secret values:

```bash
git diff -- compose.prod.yaml
```

## 4. Validate, pull, and start

Validate without printing rendered secrets:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml config --quiet
```

Pull images:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml pull
```

Start services:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d
```

Check status:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml ps
```

Open:

```text
http://<server-host>:4200
```

## 5. Docker service-address rules

Inside Compose, `localhost` means current container. Use Compose service names to reach other containers:

| Destination | Address |
|---|---|
| PostgreSQL | `postgres:5432` |
| MongoDB | `mongo:27017` |
| Backend | `backend:8080` |
| Python service | `python-service:8000` |

Frontend setting does not include scheme because Nginx template adds `http://`:

```dotenv
BACKEND_UPSTREAM=backend:8080
```

Backend/Python URL settings include scheme:

```dotenv
PYTHON_SERVICE_URL=http://python-service:8000
BACKEND_URL=http://backend:8080
```

## 6. External PostgreSQL or MongoDB

External PostgreSQL:

```dotenv
DATABASE_URL=jdbc:postgresql://db.example.internal:5432/tendersense?sslmode=require
POSTGRES_USER=tendersense
POSTGRES_PASSWORD=replace-with-database-password
```

Remove `postgres` service and its backend `depends_on` entry from `compose.prod.yaml` when fully using external PostgreSQL.

External MongoDB:

```dotenv
MONGO_URL=mongodb+srv://user:password@cluster.example.mongodb.net/?retryWrites=true&w=majority
MONGO_DATABASE=tendersense
```

Remove `mongo` service and its Python `depends_on` entry from `compose.prod.yaml` when fully using external MongoDB.

Credentials with URL-reserved characters must be percent-encoded inside `MONGO_URL`.

## 7. External backend host for frontend

Keep Angular API calls relative to `/api`. Change Nginx upstream at runtime:

```dotenv
BACKEND_UPSTREAM=10.0.0.25:8080
```

Recreate frontend only:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d --force-recreate frontend
```

No frontend image rebuild required.

## 8. Change environment values

Edit `.env.prod`, then recreate affected services:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d --force-recreate
```

No image rebuild required for runtime environment changes.

## 9. Update images

For safer releases, replace `IMAGE_TAG=latest` with immutable version such as:

```dotenv
IMAGE_TAG=1.0.2
```

Pull and recreate:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml pull
docker compose --env-file .env.prod -f compose.prod.yaml up -d
```

If continuing with `latest`, same commands fetch latest published manifests.

## 10. Logs and health

```bash
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail=100 backend python-service frontend
docker compose --env-file .env.prod -f compose.prod.yaml ps
```

Backend is not exposed publicly in sample production Compose. Check health from Docker network:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml exec -T backend wget -qO- http://localhost:8080/actuator/health
```

Expected:

```json
{"status":"UP"}
```

## 11. Stop and restart safely

```bash
docker compose --env-file .env.prod -f compose.prod.yaml stop
docker compose --env-file .env.prod -f compose.prod.yaml start
```

Do not run this when data must survive:

```bash
docker compose --env-file .env.prod -f compose.prod.yaml down -v
```

`-v` deletes named PostgreSQL and MongoDB volumes.
