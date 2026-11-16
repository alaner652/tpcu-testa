# CLAUDE.md - tpcu-testa (Testa, online exam system)

Context for Claude Code working on this repo. The user writes in Traditional Chinese; reply in Traditional Chinese.

## What this project is

A self-hosted exam system for one school, first used for its admission exam (target: live before the end of 2026).
Teachers write question banks, an exam officer schedules an exam and imports the roster, candidates sign in with a candidate number and login code in a computer lab,
choice and fill-in questions are auto-graded, essays are graded anonymously by one or more graders, and the officer publishes results.

- Frontend `testa/`: Vue 3, TypeScript, Vite, Tailwind CSS v4, reka-ui, Lucide. Details in `testa/README.md`.
- Backend `server/`: Spring Boot 4.1, Java 21, PostgreSQL 17, Flyway, Spring Session JDBC. Layout and rules in `docs/development.md`.
- Deployment: Docker Compose (Nginx + backend + PostgreSQL + backup) on a Linux VM on the school's Proxmox VE. See `docs/deployment.md`.
- Status, verified behaviour and open questions: `docs/status.md`. Read it before planning work.
- Internal exams with school accounts are a proposal in `docs/school-accounts.md` (separate `school` module, not a new backend); deferred until after the admission exam launch.

## Rules that are easy to break

- No default credentials in production: the first system administrator comes from first-run setup (`SetupService`, one-time token from the server log). Demo accounts exist only in the dev profile.
- Permission checks only through `AccessGuard`. System administrators never pass unit role checks; they reach exams only via `EmergencyAccessService` (`*ForEmergency` methods).
- Timing, auto-submit and grading live only in `AttemptService`. The frontend clock is display only.
- Nothing sent to candidates may contain answers, explanations or rubrics (`AttemptDtos`).
- Sensitive or irreversible actions go through `AuditService`; `audit_log` is append-only. A new action also needs its Chinese label in `AdminPage.vue`.
- Flyway migrations are add-only. Never edit a shipped `V*__*.sql`, not even its comments (checksums).
- Errors are problem+json: `detail` is Chinese text for the user, `code` is for the program.
- After changing the login or session flow, run `deploy/smoke-test.py`; integration tests use mock sessions.
- `docs/testa-system-design.html` is the client-facing design document: do not edit it without asking.

## Product decisions

- The people who run Testa are not engineers: anything a site may need to adjust (AI endpoint, grading and retention rules, sign-in lockout) is changed in Admin > System settings (`SettingsService`), in plain words, never by editing a file on the server. New adjustable values go there too.
- Keep the UI simple: one primary button per row, rarely used actions in the "..." menu (`MoreMenu.vue`), Lucide icons on buttons and tabs, short text.
- Graders are one to many per exam; one person may hold several roles in a unit.
- No video proctoring, no lockdown browser. AI is off by default: question import (`ai` package) appears only after an administrator turns it on in the system settings, the model's output is checked by code and confirmed by the author, and candidate data never goes to a model (`docs/ai.md`).
- Single VM, not distributed (recommended 2026-11-15): one VM handles the expected load; resilience comes from restart, extra time, backups and a rehearsed restore.

## Conventions

- English for code, identifiers, test names, comments, docs, commit messages and script output. Only what end users see is Chinese: UI text, API error messages, audit details, export headers, demo and test data.
- No emojis in code, docs or commits.
- `README.md` and `docs/server-requirements.md` have Traditional Chinese copies (`*.zh-TW.md`); update both. Other docs are English only. Keep docs concise and prefer editing existing files.
- Keep code minimal: no speculative features, unrelated changes or new dependencies without a reason.

## Workflow

Work in milestones. For each one:

1. Implement.
2. Verify:
   ```bash
   cd server && ./mvnw test                  # backend integration tests (Docker needed for Testcontainers)
   cd testa && npm test && npm run build     # frontend unit tests, type check, production build
   ```
   UI changes are checked in a real browser (no component tests). Deployment changes are checked by building the production images and running `python3 deploy/smoke-test.py` against them (set `TESTA_COOKIE_SECURE=false` for plain HTTP).
3. Update existing docs where needed (`docs/status.md` for scope or test changes).
4. Review the diff and commit on `main` with a plain, descriptive message. Never push unless asked.

## Running locally

```bash
docker compose up -d                                                  # PostgreSQL
cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # :8080, demo accounts
cd testa && npm run dev                                               # :5173
```

Demo accounts are listed in `README.md`. The dev profile must never be enabled in production.
