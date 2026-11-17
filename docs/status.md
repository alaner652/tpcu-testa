# Project status

Testa's first target is a school admission exam: a few hundred candidates in computer labs, one exam officer, a handful of teachers. Everything that use case needs is built and verified on production images. What remains is validation at a real site and the features listed under [Roadmap](#roadmap).

## Features

| Area | Status | Scope |
| --- | --- | --- |
| Accounts and access | Done | First-run setup, staff sign-in, database-backed sessions, CSRF, units and roles, batch account creation (pasted from a spreadsheet, optionally into a unit), joining a unit when the account is created, colleague search when adding unit members, acting on several accounts at once (add to a unit, reset passwords, disable), safe deletion of unused accounts, audit log, system settings in the admin pages |
| Question banks | Done | Four question types, math, autosave, version conflict detection, try-it mode, deleting or setting points for several questions at once |
| Exams and rosters | Done | Create, publish (freezes the paper), end; roster import (.xlsx, .csv, Big5); login codes and printable slips |
| Taking the exam | Done | Candidate sign-in, single session, server-side timer, autosave, resume, submit, auto-submit, auto-grading |
| Proctoring | Done | Live status with filters, lockout and reset, leave-window count, extra time per candidate or for several at once, reprinting only the slips whose codes were reset |
| Grading | Done | One or more anonymous graders, averaged scores, disagreement warning, reopening with a reason |
| Results | Done | Score sheet, publishing, Excel and CSV export, optional candidate access |
| Data lifecycle | Done | Archiving, retention period, deletion with a reason |
| Emergency access | Done | An exam owner grants one administrator time-limited, read-only access; every view is audited |
| Deployment | Done | Docker Compose with Nginx, HTTPS option, daily backups, health checks, smoke test |
| Backups in the admin panel | Done | Take, download, delete and restore backups; retention in System settings; restore puts the site in maintenance, backs up the current data first, needs a typed confirmation, and is refused during an exam or for a dump from another schema version |
| AI question import | Experimental, off by default | A local model drafts questions from a Word, PDF or text exam paper; the author confirms each one. See [AI features](ai.md#question-import) |
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
| Automated tests | 59 backend integration tests against real PostgreSQL (Testcontainers) and 23 frontend unit tests, run by GitHub Actions on every push |

## Before production

These depend on the deploying site and cannot be tested in development:

- The production server and the lab network, including the site's reverse proxy or certificate.
- A rehearsal with the Internet actually cut off: internal DNS for the exam host name, routing from every lab subnet, NTP on the VM (see [Deployment](deployment.md#network-and-offline-exams)).
- Safari, Firefox and Edge, and Windows lab PCs.
- Login slips printed on the site's printers.

## Policies

Defaults agreed with the first deploying school. Those marked "System settings" can be changed by a system administrator in the admin pages, without touching the server; every change is audited.

| Policy | Default | Where |
| --- | --- | --- |
| Data retention | Until 31 December of the year after the exam ends; then an archived exam can be deleted | Admin > System settings |
| Grader disagreement | Warn when graders differ by more than 20% of a question's points | Admin > System settings |
| Sign-in lockout | Locked for 15 minutes after 5 wrong passwords or login codes | Admin > System settings |
| Authors as graders | Allowed, no recusal rule | |
| Login codes | Stored in plain text so slips can be reprinted; visible only to the unit's owners and proctors | |
| Candidate results | Off: publishing locks scores for export and the school announces them. When an owner turns it on for an exam, candidates see their total and subtotals, never answers or comments | Per exam, on the Results tab |

## Known limitations

- First-run setup has no token: on a fresh installation the first person to open the site becomes the system administrator, so the deployer must do it before the site is reachable by others (see [Deployment](deployment.md#going-live)).
- Staff password recovery needs an administrator; there is no email-based reset.
- Restoring from the admin panel only accepts a dump with the same database migrations as the running version; older dumps are restored on the host (see [Deployment](deployment.md#restoring-on-the-host)).
- Frontend tests cover pure logic only; there are no component or browser-automation tests.

## Roadmap

**Next**
- On-site trial exam and the checks under [Before production](#before-production).
- Try AI question import with real past papers and decide whether to enable it for teachers (see [AI features](ai.md#question-import)).

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
