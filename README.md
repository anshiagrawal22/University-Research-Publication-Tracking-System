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

The API listens on `http://localhost:8080` by default (override with `--server.port=8082` if port `8080` is occupied).

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

The portal listens on `http://localhost:5173` and proxies `/api` to `http://localhost:8080` (or set `VITE_API_URL` if running the backend on another port).

## API

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/publications` | Create a publication. Duplicate DOI returns `409`. |
| GET | `/api/publications` | Filter by `search`, `department`, `faculty`, `year`, `journal`, `status`, `page`, `size` |
| GET | `/api/publications/filters` | Distinct `departments`, `years`, and `faculties` in the database |
| GET | `/api/publications/{id}` | Fetch one publication |
| PUT | `/api/publications/{id}` | Update a publication (`409` on duplicate DOI) |
| DELETE | `/api/publications/{id}` | Delete a publication (`204 No Content`) |
| GET | `/api/crossref/{doi}` | Crossref DOI metadata lookup |
| GET | `/api/orcid/{orcidId}/works` | Fetch public ORCID works enriched via Crossref |
| GET | `/api/dashboard/summary` | Verified totals (`department`, `faculty`, `fromYear`, `toYear`) |
| GET | `/api/dashboard/by-department` | `[{department, count}]` |
| GET | `/api/dashboard/by-year` | `[{year, count}]` |
| GET | `/api/dashboard/by-faculty` | Top 10 `[{faculty, count}]` |
| GET | `/api/dashboard/recent` | Latest 10 verified publications |

Errors share one JSON shape: `timestamp`, `status`, `error`, `message`, `path`, `fieldErrors`.
