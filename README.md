# University Research Publication Tracking System

A web app for keeping track of the research papers published by university staff. You can add a paper by typing in its DOI, by importing it from someone's ORCID profile, or by filling in the details yourself. Every new paper goes through a few automatic checks before it shows up on the public dashboard.

The backend is Spring Boot (Java) with an H2 database stored in a file. The frontend is React, built with Vite.

```
backend/     Spring Boot API, database, tests
frontend/    React app (the website)
scripts/     helper scripts used by CI
Jenkinsfile  Jenkins pipeline
docker-compose.yml
```

## Running it on your machine

You need Java 17 or newer, Maven 3.9 and Node.js 20.

Start the backend in one terminal:

```bash
cd backend
mvn spring-boot:run
```

It runs on **http://localhost:8081**. We use 8081 rather than Spring's usual 8080 because Jenkins and other tools often sit on 8080.

Then start the frontend in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. The frontend passes every `/api/...` request on to the backend at 8081, so you don't need to configure anything if you use the default ports.

The first time the backend starts with an empty database, it adds 30 sample publications so there is something to look at.

### If you need different ports

- Backend: set `SERVER_PORT`, for example `SERVER_PORT=8090 mvn spring-boot:run` (or `--server.port=8090`).
- Frontend: tell it where the backend is with `BACKEND_URL=http://localhost:8090 npm run dev`.

`VITE_API_URL` also works. It makes the browser call the backend directly instead of going through the dev server. If you use it, the backend has to allow your frontend's address in `CORS_ALLOWED_ORIGINS`.

## Adding publications

The **Submit Publication** page has three tabs:

- **Fetch using DOI**: paste a DOI or a `https://doi.org/...` link. Title, authors, year and journal are filled in from Crossref, and you pick the department. If Crossref is missing something, the page tells you what to fill in.
- **Import from ORCID**: enter an ORCID iD to list that person's public works and import several at once. Works that have a DOI are filled in from Crossref, and works already in the register are greyed out.
- **Enter manually**: type everything in. A DOI is optional.

Before you submit, the page checks that the required fields are filled in. It also looks for an existing record with the same DOI or title, and warns you if a similar title is already in the register.

## How a paper goes live

New submissions don't appear on the dashboard straight away. When you submit, the app runs four checks:

1. **Input safety**: no HTML, scripts or other code hidden in the title, authors or journal.
2. **Identifiers**: the DOI looks genuine and any ORCID iD has a valid checksum.
3. **Duplicates and metadata**: no other paper with the same title and year, the year isn't more than a year in the future, and the title isn't a placeholder.
4. **Venue**: the journal isn't on the blocked list (predatory or unreviewed venues), and there are no web links in the title or journal name.

Anything that looks like code in the text is refused immediately and never saved. Otherwise a paper that passes all four checks is saved as **In review**, and one that fails is saved as **Rejected**.

To make a paper public, click the shield button next to it in the register. That runs the checks again and shows the result for each one. If they all pass, click **Approve & Publish Live** and the paper becomes **Verified**. Only verified papers are counted on the dashboard.

## The database

Records are kept in `backend/data/publications.mv.db`. That folder is ignored by Git, so everyone has their own copy.

- To start fresh, stop the backend and delete `backend/data/`. The sample data comes back on the next start.
- Each save is written to disk immediately (`WRITE_DELAY=0` in the database URL), so a crash doesn't lose recent changes.
- To use a different database, set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.

## Running with Docker

```bash
docker compose up -d --build
```

This builds both apps and starts them:

- Website: http://localhost:3000
- API: http://localhost:8080

If 8080 or 3000 is already taken on your machine (Jenkins often uses 8080), choose other ports, for example `BACKEND_PORT=8085 FRONTEND_PORT=3001 docker compose up -d --build`.

The database is kept in a Docker volume called `publication_data`, so it survives restarts. `docker compose down -v` deletes it.

## CI and Jenkins

**GitHub Actions** (`.github/workflows/ci.yml`) runs on every push and pull request to `main`:

1. A security scan: `scripts/security-scan.sh` checks for committed secrets and runs `npm audit`, then Trivy scans the files.
2. The backend is built and tested (`mvn clean verify`), and the frontend is built.
3. The Docker images are built and scanned, the stack is started, and `scripts/verify-deployment.sh` checks that it works.
4. If the `JENKINS_WEBHOOK_URL` secret is set, Jenkins is triggered.

**Jenkins** uses the `Jenkinsfile` in the repo and runs the same kind of steps, ending with a deploy. To set it up:

1. Install the Git, Pipeline, NodeJS and Docker Pipeline plugins. Under *Manage Jenkins → Tools*, add a JDK 17 called `JDK17`, Maven called `Maven3` and Node.js 20 called `Node20`.
2. Create a Pipeline job, choose *Pipeline script from SCM*, point it at this repo and set the script path to `Jenkinsfile`.
3. Add a webhook (or the `JENKINS_WEBHOOK_URL` secret in GitHub) so pushes to `main` start a build. Keep credentials in Jenkins or GitHub secrets, never in the repo.

## Tests

```bash
cd backend
mvn test
```

The tests use their own in-memory database, so they don't touch your data. They cover validation, duplicate DOIs, the dashboard numbers and filters, the security checks, and the Crossref/ORCID code (with recorded responses, so no internet is needed).

## Settings

| Variable | Default | What it's for |
| --- | --- | --- |
| `SERVER_PORT` | `8081` | Backend port |
| `DB_URL` | file database in `backend/data` | Database connection |
| `CORS_ALLOWED_ORIGINS` | localhost:5173, :3000 | Which websites may call the API directly |
| `ORCID_CLIENT_ID`, `ORCID_CLIENT_SECRET` | empty | Optional ORCID API credentials. Without them, the public API is used, which is fine for normal use |
| `BACKEND_URL` | `http://localhost:8081` | Where the frontend dev server sends `/api` requests |
| `VITE_API_URL` | empty | Makes the browser call the API at this address directly |

## API

All endpoints are under `/api`. Errors come back as JSON with `status`, `message` and, for form problems, a `fieldErrors` list.

| Method | Path | What it does |
| --- | --- | --- |
| GET | `/api/publications` | List publications. Filters: `search`, `department`, `faculty`, `year`, `journal`, `status`, `page`, `size` |
| GET | `/api/publications/filters` | Departments, years and author names for the filter dropdowns |
| GET | `/api/publications/{id}` | One publication |
| POST | `/api/publications` | Submit a publication (starts as In review). A duplicate DOI returns 409 |
| PUT | `/api/publications/{id}` | Edit a publication |
| DELETE | `/api/publications/{id}` | Delete a publication |
| GET | `/api/publications/{id}/security-gate` | Run the four checks and show the results |
| POST | `/api/publications/{id}/publish` | Publish if all checks pass (returns 422 if not) |
| GET | `/api/crossref/{doi}` | Look up a DOI on Crossref |
| GET | `/api/orcid/{orcidId}/works` | List a person's public works from ORCID |
| GET | `/api/dashboard/summary` | Totals for verified papers. Filters: `department`, `faculty`, `fromYear`, `toYear` |
| GET | `/api/dashboard/by-department` | Paper count per department |
| GET | `/api/dashboard/by-year` | Paper count per year |
| GET | `/api/dashboard/by-faculty` | Top 10 authors by paper count |
| GET | `/api/dashboard/recent` | 10 most recently verified papers |

## When something goes wrong

**The website loads but shows no data.** The frontend can't reach the backend. Check that the backend is running and that it's on the port the frontend expects (8081 unless you changed it). If you started the frontend with `VITE_API_URL`, make sure that address is right.

**"Port 8081 is already in use."** Something else is using it, often an old copy of the backend. Stop it, or start the backend on another port (see above).

**A DOI or ORCID lookup fails.** The message on the page says whether the service was slow, unreachable or returned an error. Crossref and ORCID sometimes have short outages. You can always enter the details by hand instead.

**ORCID import takes a while.** For works with a DOI, the app fetches the details from Crossref, two at a time (Crossref turns away clients that send more at once). It looks up at most 20 works per import; anything beyond that, or anything Crossref hasn't answered within about 20 seconds, keeps the details from ORCID.
