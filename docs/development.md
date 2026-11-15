# Development

How the code is organized, the rules that keep exams fair and data private, and how to contribute. To get a local instance running, see the [Quick start](../README.md#quick-start).

## Layout

```
testa/                        frontend (Vue 3), see testa/README.md
server/                       backend (Spring Boot 4.1, Java 21)
  src/main/
    java/tw/testa/
      auth/                   sign-in, session identity, AccessGuard (permission checks)
      staff/                  staff accounts
      unit/                   units, members and roles
      bank/                   question banks
      exam/                   exams, rosters, login codes
      attempt/                taking the exam, timing, auto-submit, auto-grading
      grading/                essay grading, results, export
      emergency/              time-limited administrator access granted by an exam owner
      audit/                  audit log
      config/                 security, properties, first-run setup, backup listing
      common/                 errors (problem+json), paging
    resources/db/migration/   Flyway migrations (add only)
deploy/                       Nginx (HTTP, HTTPS override), backup script, env template, smoke test
docs/                         documentation
tools/                        developer tools
docker-compose.yml            development: PostgreSQL only
docker-compose.prod.yml       production: Nginx, backend, database, backup
```

## Backend rules

These protect exam integrity. Pull requests that break them will not be merged.

- **Permissions** go only through `AccessGuard` (`@PreAuthorize("@access...")` or `access.require*`). System administrators do not pass unit role checks.
- **Emergency access** goes only through `EmergencyAccessService`. Its `*ForEmergency` methods skip role checks and must not be called from anywhere else.
- **Candidate data** is sent only through `AttemptDtos` and never includes answers, explanations or rubrics. `AttemptTest` enforces this.
- **Timing, auto-submit and grading** happen only in `AttemptService`. The frontend countdown is display only; it syncs with the server every 30 seconds and before time-up, which is how extra time reaches the candidate.
- **Sensitive or irreversible actions** are recorded with `AuditService`. The database forbids updating or deleting `audit_log` rows. A new audit action also needs its Chinese label in `testa/src/pages/AdminPage.vue`.
- **Schema changes** add a new Flyway migration. Never edit a released `V*__*.sql`, not even a comment: Flyway checks checksums.
- **Errors** are problem+json: `detail` is Traditional Chinese text shown to the user as is, `code` is for programs.
- **Session identities** (`StaffPrincipal`, `CandidatePrincipal`) must stay serializable, since sessions are stored in the database.

## Tests

| Command | What it covers |
| --- | --- |
| `cd server && ./mvnw test` | Backend integration tests against real PostgreSQL via Testcontainers (needs Docker) |
| `cd testa && npm test` | Frontend unit tests (Vitest) |
| `cd testa && npm run build` | Type check and production build |
| `python3 deploy/smoke-test.py URL EMAIL PASSWORD` | A full exam over real HTTP. Leaves test data behind; use a test instance |

GitHub Actions runs the first three on every push and pull request.

Integration tests use mock sessions, so the smoke test is the only one that exercises real database sessions. **Run it after any change to sign-in or sessions.**

## Demo-only features

AI question import (mock data), the AI-assisted grading switch on essay questions and the admin System settings tab have no backend yet. They are behind `DEMO_FEATURES` in `testa/src/lib/flags.ts`: visible in `npm run dev`, removed from production builds.

## Contributing

1. Open an issue first for anything beyond a small fix, so the approach can be agreed before you write code.
2. Keep each pull request to one change. Avoid new dependencies unless they are clearly needed.
3. Before opening the pull request, run the tests above. For UI changes, check the page in a browser at desktop and phone widths, and retake the affected [screenshots](screenshots.md#retaking).
4. Update the documentation the change affects, such as this file, the [Deployment guide](deployment.md) or [Project status](status.md).

### Conventions

- English for code, identifiers, comments, documentation and commit messages. Only what end users see is Traditional Chinese: UI text, error messages, audit details, export headers and demo data.
- `README.md` has a Traditional Chinese copy, `README.zh-TW.md`; keep both in sync.
- Commit messages: a short imperative subject line ("Filter the audit log by action"), then a body explaining why when it is not obvious.
- No emojis in code, documentation or commits.
- Never commit credentials, real candidate data or site-specific addresses.
