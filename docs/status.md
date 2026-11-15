# Project status

Testa's first target is a school admission exam: a few hundred candidates in computer labs, one exam officer, a handful of teachers. Everything that use case needs is built and verified on production images. What remains is validation at a real site and the features listed under [Roadmap](#roadmap).

## Features

| Area | Status | Scope |
| --- | --- | --- |
| Accounts and access | Done | First-run setup with a one-time token, staff sign-in, database-backed sessions, CSRF, units and roles, audit log |
| Question banks | Done | Four question types, math, autosave, version conflict detection, try-it mode |
| Exams and rosters | Done | Create, publish (freezes the paper), end; roster import (.xlsx, .csv, Big5); login codes and printable slips |
| Taking the exam | Done | Candidate sign-in, single session, server-side timer, autosave, resume, submit, auto-submit, auto-grading |
| Proctoring | Done | Live status with filters, lockout and reset, leave-window count, per-candidate extra time |
| Grading | Done | One or more anonymous graders, averaged scores, disagreement warning, reopening with a reason |
| Results | Done | Score sheet, publishing, Excel and CSV export, optional candidate access |
| Data lifecycle | Done | Archiving, retention period, deletion with a reason |
| Emergency access | Done | An exam owner grants one administrator time-limited, read-only access; every view is audited |
| Deployment | Done | Docker Compose with Nginx, HTTPS option, daily backups, health checks, smoke test |
| On-site trial | Not started | Real lab network, printers and browsers; see [Before production](#before-production) |

## Verification

| Area | How it was verified |
| --- | --- |
| End to end | `deploy/smoke-test.py` runs a full exam through Nginx on production images: first-run setup, authoring, roster, answering, grading, publishing, turning on candidate access and checking a score, emergency access, archiving |
| Load | 300 candidates signing in, answering and submitting at once: no failures, p99 12 ms through Nginx; p95 109 ms hitting the backend directly |
| Failure cases | Backend and database restarts mid-exam, duplicate sign-in, lockout and reset, ending early, auto-submit for candidates who never reconnect, extra time just before time-up |
| Offline | Database, backend and backup on a network with no Internet access: start-up, setup and the full exam pass, and the browser makes no external requests |
| HTTPS | Both TLS at a reverse proxy (with real client IPs and forged `X-Forwarded-For` ignored) and TLS on the VM (`deploy/https.yml`) |
| Backup | Daily dump restored into an emptied database |
| Browser | Every page in Chromium at 1440 px and 390 px (candidate pages also at 320 px); see [Screenshots](screenshots.md) |
| Automated tests | 42 backend integration tests against real PostgreSQL (Testcontainers) and 19 frontend unit tests, run by GitHub Actions on every push |

## Before production

These depend on the deploying site and cannot be tested in development:

- The production server and the lab network, including the site's reverse proxy or certificate.
- A rehearsal with the Internet actually cut off: internal DNS for the exam host name, routing from every lab subnet, NTP on the VM (see [Deployment](deployment.md#network-and-offline-exams)).
- Safari, Firefox and Edge, and Windows lab PCs.
- Login slips printed on the site's printers.

## Policies

Defaults agreed with the first deploying school. Each is a setting or a small change if another site needs something different.

| Policy | Default | Where |
| --- | --- | --- |
| Data retention | Until 31 December of the year after the exam ends; then an archived exam can be deleted | `testa.retention.years` |
| Grader disagreement | Warn when graders differ by more than 20% of a question's points | `testa.grading.flag-ratio` |
| Authors as graders | Allowed, no recusal rule | |
| Login codes | Stored in plain text so slips can be reprinted; visible only to the unit's owners and proctors | |
| Candidate results | Off: publishing locks scores for export and the school announces them. When an owner turns it on for an exam, candidates see their total and subtotals, never answers or comments | Per exam, on the Results tab |

## Known limitations

- Staff password recovery needs an administrator; there is no email-based reset.
- The admin Backups tab shows status only; restoring is done on the server.
- Frontend tests cover pure logic only; there are no component or browser-automation tests.

## Roadmap

**Next**
- On-site trial exam and the checks under [Before production](#before-production).
- AI question import: turn a Word or PDF exam into questions for review. The UI exists behind `DEMO_FEATURES` (see [AI features](ai.md)).

**Later**
- Internal exams with school accounts and class rosters ([proposal](school-accounts.md)).
- An AI second opinion on essay grading, as a reminder to graders, never a score.
- An MCP server limited to question banks.

**Out of scope**
- Video proctoring and lockdown browsers.
- Emailing or texting login slips.
- Random question selection and multiple paper versions.

## Differences from the design document

[The design document](testa-system-design.html) (v0.2) describes the original requirements. The implementation differs where requirements changed:

- One or more graders per exam instead of exactly three.
- Administrators can get time-limited, owner-granted access to an exam.
- Login codes are stored in plain text, not hashed, so slips can be reprinted.
- Spring Boot 4.1 instead of 3, which is out of open-source support; the target is still Java 21.
- Whether candidates can check their own published scores is a per-exam option, off by default.
- The exam form has no delivery-mode field (lab or online); it had no effect. The column remains for future online exams.
