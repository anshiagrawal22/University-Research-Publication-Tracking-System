# University Research Publication Tracking System

Phase 2 of the research publication tracking project: a Spring Boot API and a React portal. H2 keeps the data in memory. Later phases can replace the database, add the review queue, and wrap the same folders in Jenkins and Docker.

## Layout

```
backend/     Maven project (Java 17, Spring Boot 3)
frontend/    Vite + React
```

## Run

Use two terminals.

```bash
cd backend
mvn clean test package
mvn spring-boot:run
```

The API listens on http://localhost:8080. The `dev` profile is active by default and seeds about 30 publications. Tests use the `test` profile and do not seed.

```bash
cd frontend
npm install
npm run dev
```

The portal listens on http://localhost:5173 and proxies `/api` to the backend. CORS also allows that origin if you call the API directly.

## API

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/publications` | Create a publication. Duplicate DOI returns 409. |
| GET | `/api/publications` | `search`, `department`, `year`, `journal`, `status`, `page`, `size` |
| GET | `/api/publications/{id}` | One record |
| GET | `/api/crossref/{doi}` | Crossref lookup. DOI slashes stay in the path. |
| GET | `/api/orcid/{orcidId}/works` | Stub. Real ORCID credentials are a later phase. |
| GET | `/api/dashboard/summary` | Verified records only |
| GET | `/api/dashboard/by-department` | `[{department, count}]` |
| GET | `/api/dashboard/by-year` | `[{year, count}]` |
| GET | `/api/dashboard/by-faculty` | Top 10 `[{faculty, count}]` |
| GET | `/api/dashboard/recent` | Latest 10 verified publications |

Dashboard routes also accept `department`, `fromYear`, and `toYear`.

Errors share one JSON shape: `timestamp`, `status`, `error`, `message`, `path`, `fieldErrors`.

## Hooks for later phases

- `SimilarityReviewService` is the extension point for title/year similarity and the review queue. The current implementation finds nothing, so new submissions are stored as `VERIFIED`.
- `OrcidWorksService` is implemented by `StubOrcidWorksService` until ORCID credentials exist.
- JPA entities and repositories avoid vendor SQL so the H2 datasource can be swapped.
- `PublicationDataSeeder` runs only for the `dev` profile, and only when `app.seed.enabled` is true.
