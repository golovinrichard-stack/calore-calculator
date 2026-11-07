# Calore research server

The server for the optional "Help research" feature ([M10](../docs/implementation/M10-research-data.md)). It stores the weekly summaries of people who **agreed** to send them, exactly the fields in [policy appendix A](../docs/PRIVACY_POLICY.md#appendix-a-research-report-fields), and nothing else.

It is **not running anywhere yet**. It goes live only after the steps in M10.7: EU hosting chosen, the policy reviewed and translated, the owner's legal checklist (policy appendix C) done, and `calore.researchServerUrl` set for release builds of the app.

## What it does

| Method and path | Auth | What it does |
|---|---|---|
| `POST /v1/participants` | none (the body has the secret) | Register; only a SHA-256 hash of the secret is stored |
| `PUT /v1/participants/{id}` | `Authorization: Bearer <secret>` | Update categories, profile, consent version |
| `POST /v1/reports` | Bearer | Store one weekly report; the same `reportId` again changes nothing |
| `DELETE /v1/participants/{id}` | Bearer | Delete the participant and all their reports |
| `GET /v1/health` | none | For monitoring |

- **Validation:** unknown fields, missing fields, values out of range, sections the person didn't agree to, and bodies over 16 KB are refused.
- **No IP addresses** are stored or logged (no access logs here or in Caddy). Rate limits use them in memory only. A report stores the ISO week it arrived in, never a timestamp.
- **Statistics:** `export-stats <dir>` writes `nutrition-by-group.csv` (by week, age band, sex and region). Groups smaller than 10 people are left out, so only group statistics leave the database.

## Build and test

From the repository root, with the app's Gradle wrapper:

```bash
./gradlew -p server test
```

```bash
./gradlew -p server installDist
```

Tests use Ktor's test host and H2 in PostgreSQL mode. Production uses PostgreSQL.

## Run

1. A small Linux server **in the EU**, with Docker and a domain pointing to it.
2. `cp server/.env.example server/.env` and fill in the domain and a long random database password.
3. From the repository root:

```bash
docker compose -f server/docker-compose.yml --env-file server/.env up -d
```

Caddy gets the HTTPS certificate by itself. Only ports 80 and 443 are open; PostgreSQL is not reachable from outside.

Configuration of the server itself (environment variables): `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `PORT` (8080).

## Statistics export

```bash
docker compose -f server/docker-compose.yml --env-file server/.env run --rm server export-stats /tmp/out
```

## Backups (encrypted)

Back up the database every day and keep the copies encrypted, for example:

```bash
docker compose -f server/docker-compose.yml exec -T db pg_dump -U research research | gpg --symmetric --cipher-algo AES256 -o research-$(date +%F).sql.gpg
```

Keep the passphrase somewhere else than the backups. Delete backups older than the retention time in the policy (section 5.8), so deleted participants also disappear from backups.

## Updates

```bash
git pull
```

```bash
docker compose -f server/docker-compose.yml --env-file server/.env up -d --build
```

The tables are created or kept at start (`CREATE TABLE IF NOT EXISTS`); a change to them needs a migration in `Store.migrate()` first.
