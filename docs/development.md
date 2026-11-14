# Development

How to start and the demo accounts: see the [README](../README.md#quick-start).

## Layout

```
testa/                        frontend (Vue 3), see testa/README.md
server/                       backend (Spring Boot 4.1, Java 21)
  src/main/
    java/tw/testa/
      auth/                   login, session identity, AccessGuard (permission checks)
      staff/                  staff accounts
      unit/                   units, members and roles
      bank/                   question banks
      exam/                   exams, rosters, login codes
      attempt/                taking the exam, timing, auto-submit, auto-grading
      grading/                essay grading, results, export
      emergency/              administrator emergency access (granted by the owner)
      audit/                  audit log
      config/                 security, properties, first administrator, backup listing
      common/                 errors (problem+json), paging
    resources/db/migration/   Flyway (from V1, add only)
deploy/                       Nginx (HTTP and HTTPS override), backup script, env template, smoke test
docs/                         documentation
tools/                        developer tools
docker-compose.yml            development: PostgreSQL only
docker-compose.prod.yml       production: Nginx + backend + database + backup
```

## Tests

| Command | What |
| --- | --- |
| `cd server && ./mvnw test` | Backend integration tests with real PostgreSQL via Testcontainers (needs Docker) |
| `cd testa && npm test` | Frontend unit tests (vitest) |
| `cd testa && npm run build` | Type check + production build |
| `python3 deploy/smoke-test.py http://localhost:8080 EMAIL PASSWORD` | A full mock exam over real HTTP. Leaves test data behind |

`.github/workflows/ci.yml` runs the first three on GitHub.

## Backend conventions

- Permission checks go only through `AccessGuard` (`@PreAuthorize("@access...")` or `access.require*`). System administrators do not pass unit role checks automatically.
- Schema changes always add a new Flyway migration; never edit a shipped one.
- Errors are problem+json: `detail` is Chinese text shown to the user as is, `code` is for the program.
- Data sent to candidates goes only through `AttemptDtos` and must not include answers, explanations or rubrics (`AttemptTest` checks this).
- Timing, auto-submit and grading happen only on the backend (`AttemptService`). The frontend countdown is display only and syncs with the backend every 30 seconds and before time-up (extra time takes effect this way).
- Irreversible or sensitive actions are recorded with `AuditService`; `audit_log` forbids update and delete at the database level.
- Administrators reach exams only through `EmergencyAccessService`; the `*ForEmergency` methods skip role checks and must not be called from anywhere else.
- Session identities (`StaffPrincipal`, `CandidatePrincipal`) must be serializable.

> Integration tests disable database sessions (MockMvc uses a mock session). **After changing the login flow, run `deploy/smoke-test.py`**; it is the only test that exercises real sessions.

## Demo-only features

AI question import (mock data), the "AI-assisted grading" switch on essay questions and the admin "System settings" tab have no backend. They are controlled by `DEMO_FEATURES` in `testa/src/lib/flags.ts`: visible in `npm run dev`, hidden by `npm run build`.

## Conventions

- English for code, identifiers, comments, docs and commit messages. UI text, user-facing error messages and audit details are Traditional Chinese.
- No emojis in code, docs or commits.
