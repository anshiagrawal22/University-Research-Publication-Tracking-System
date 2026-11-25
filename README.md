# University Research Publication Tracking System

A full-stack Spring Boot and React/Vite application for centralizing university research publication records via manual entry, Crossref DOI lookup, and ORCID profile import.

## Layout

```
backend/     Spring Boot 3 API (Java 17+, Maven, H2 / JPA)
frontend/    React + Vite portal
```

## Run

Use two terminals.

### 1. Backend

```bash
cd backend
mvn clean test package
mvn spring-boot:run
```

The API listens on `http://localhost:8081` by default (port `8080` is often taken by other local tools such as Jenkins). Override it with the `SERVER_PORT` environment variable or `--server.port=8082`.

- **Database:** File-backed H2 (`./data/publications`) persists records across restarts (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JPA_DDL_AUTO` can override defaults).
- **Seed data:** The `dev` profile is active by default and seeds 30 sample publications only when the database is empty (`app.seed.enabled=true`). Tests run with the `test` profile against an isolated in-memory database.
- **ORCID API:** Uses the ORCID Public API (`https://pub.orcid.org/v3.0`) and enriches works with Crossref metadata when DOIs are present. Optionally set `ORCID_CLIENT_ID` and `ORCID_CLIENT_SECRET` environment variables for authenticated `/read-public` requests.

### 2. Frontend

```bash
cd frontend
npm install
npm run dev
```

For production bundling:

```bash
cd frontend
npm run build
```

The portal listens on `http://localhost:5173` and proxies `/api` to `http://localhost:8081`. If the backend runs on another port, start the dev server with `BACKEND_URL=http://localhost:<port>` (or set `VITE_API_URL` to call the API directly).

## CI/CD & Docker

The repository includes containerization and two integrated CI/CD pipelines with security gates:

### Run with Docker Compose

```bash
docker compose up -d --build
```

- **Frontend (Nginx):** `http://localhost:3000` (proxies `/api` to the backend container)
- **Backend API:** `http://localhost:8080` (persists H2 data in the `publication_data` Docker volume)

### Pipelines & Security Gates

- **GitHub Actions:** [`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on pushes/PRs to `main` (and `workflow_dispatch`):
  1. **Security Gate (Static & Dependency Scan):** Runs `scripts/security-scan.sh` (secret hygiene + `npm audit`) and Trivy filesystem vulnerability/misconfiguration/secret scanning.
  2. **Backend & Frontend Builds:** Runs `mvn -B -f backend/pom.xml clean verify` and `npm run build`, uploading build and test artifacts.
  3. **Docker Build, Image Scan & Live Gate Verify:** Builds `backend` and `frontend` Docker images, scans both images with Trivy, boots the stack with `docker compose up -d`, and runs `scripts/verify-deployment.sh` to verify container health and live publication security gates.
  4. **Jenkins Trigger:** On `main` pushes, optionally triggers `JENKINS_WEBHOOK_URL` (with optional `JENKINS_WEBHOOK_TOKEN`) when configured in repository secrets.
- **Jenkins:** [`Jenkinsfile`](Jenkinsfile) runs `Checkout` -> `Frontend install & audit` -> `Security Gate — Code & Dependencies` -> `Backend build & test` -> `Frontend production build` -> `Docker image creation` -> `Security Gate — Container scan` (when `trivy` is installed) -> `Deploy containers & verify`, archiving JUnit reports, the backend JAR, and the frontend `dist` bundle.

### Configure Jenkins

1. Install Jenkins with Git, Pipeline, NodeJS, and Docker Pipeline/CLI access on the agent. Configure these global tools under **Manage Jenkins → Tools**:
   - A JDK 17 installation named `JDK17`.
   - Maven 3.9 (or compatible) named `Maven3`.
   - Node.js 20 named `Node20` (provided by the NodeJS plugin).
2. Create a Pipeline or Multibranch Pipeline job pointing at this repository. For a Pipeline job, select **Pipeline script from SCM**, choose Git, and set the script path to `Jenkinsfile`.
3. Configure your repository webhook (or set `JENKINS_WEBHOOK_URL` in GitHub Actions secrets) so Jenkins is triggered automatically on pushes to `main`. Store SCM/webhook credentials in Jenkins Credentials or GitHub Secrets; never commit secrets into `Jenkinsfile`.
4. Run the job. Jenkins builds, tests, scans, deploys the Docker containers, verifies live endpoints, and archives build artifacts.

## Pre-Publish Security Gates

Before any submitted paper becomes live (`VERIFIED`) on the dashboard and verified register, it is evaluated by `SecurityGateService`:

1. **Input & XSS Sanitization (`INPUT_SECURITY`):** Blocks `<script>`/HTML tags, dangerous URI schemes (`javascript:`, `data:text/html`), inline DOM event handlers, control characters, and injection payloads immediately (`400 Bad Request`).
2. **DOI & ORCID Verification (`IDENTIFIER_INTEGRITY`):** Blocks suspicious DOI domains/path traversal and verifies ORCID ISO 7064 Mod 11-2 checksums and persistent identifier presence.
3. **Duplicate & Metadata Integrity (`DUPLICATE_AND_METADATA`):** Rejects duplicate title+year entries, future years beyond `currentYear + 1`, and short/placeholder titles.
4. **Venue & Release Policy (`PUBLICATION_POLICY`):** Blocks untrusted/unreviewed preprint or predatory venues and embedded URLs in title/journal fields.

New submissions enter `IN_REVIEW` (or `REJECTED` if a gate fails) and can only transition to `VERIFIED` via `POST /api/publications/{id}/publish` (or `PUT /api/publications/{id}`) after passing all security gates (`422 Unprocessable Entity` if blocked).

## API

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/publications` | Submit a publication through security gates (`IN_REVIEW` or `REJECTED`). Duplicate DOI returns `409`. |
| GET | `/api/publications` | Filter by `search`, `department`, `faculty`, `year`, `journal`, `status`, `page`, `size` |
| GET | `/api/publications/filters` | Distinct `departments`, `years`, and `faculties` in the database |
| GET | `/api/publications/{id}` | Fetch one publication |
| PUT | `/api/publications/{id}` | Update a publication (`409` on duplicate DOI, `422` if publishing fails security gates) |
| GET | `/api/publications/{id}/security-gate` | Inspect detailed 4-gate security evaluation report |
| POST | `/api/publications/{id}/publish` | Run security gates and publish live (`VERIFIED`); blocked papers return `422` |
| DELETE | `/api/publications/{id}` | Delete a publication (`204 No Content`) |
| GET | `/api/crossref/{doi}` | Crossref DOI metadata lookup |
| GET | `/api/orcid/{orcidId}/works` | Fetch public ORCID works enriched via Crossref |
| GET | `/api/dashboard/summary` | Verified totals (`department`, `faculty`, `fromYear`, `toYear`) |
| GET | `/api/dashboard/by-department` | `[{department, count}]` |
| GET | `/api/dashboard/by-year` | `[{year, count}]` |
| GET | `/api/dashboard/by-faculty` | Top 10 `[{faculty, count}]` |
| GET | `/api/dashboard/recent` | Latest 10 verified publications |

Errors share one JSON shape: `timestamp`, `status`, `error`, `message`, `path`, `fieldErrors`.
