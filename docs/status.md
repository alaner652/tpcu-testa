# Status

Last reviewed: 2026-11-15.

## Milestones (design document, section 9)

| Status | Milestone | Scope |
| --- | --- | --- |
| Done | 1. Backend skeleton and login | Staff login, sessions, CSRF, units and roles, permission checks, audit log, admin pages |
| Done | 2. Question banks | Bank editor on the API, autosave, version conflict detection |
| Done | 3. Exams and rosters | Create, publish (freezes a snapshot of the questions), end. Roster import (.xlsx, .csv including Big5), login codes, printable login slips, proctoring page |
| Done | 4. Taking the exam | Candidate number + login code, single session per candidate, paper without answers, server-side timer, autosave, resume after disconnect, submit, auto-submit on timeout, auto-grading |
| Done | 5. Grading | One or more graders per exam; anonymous, graders cannot see each other's scores; the average (one decimal) once all have submitted; a warning to the officer when graders differ by more than 20% of the points (`testa.grading.flag-ratio`); the officer can reopen grading with a reason |
| Done, rules to confirm | 6. Results and proctoring | Score sheet, publish, Excel and CSV export, proctoring (status, lock, leave-screen count, extra time per candidate), archive and deletion after retention. After publishing, candidates see their total and two subtotals (choice/fill-in, essay), not answers, comments or per-question scores |
| In progress | 7. Deployment and trial exam | Production setup built and verified on a development machine; not yet on the school's server |

## Testing

### Verified on a development machine

| Area | What |
| --- | --- |
| Deployment | Image build, Nginx (static files, SPA routing, security headers, rate limit), daily backup, restore into an emptied database |
| Mock exam | `deploy/smoke-test.py` runs through Nginx from writing questions to candidates checking scores, plus emergency access (grant, view, revoke) and archiving. Re-run on 2026-11-15 against freshly built production images |
| Load | 300 candidates from one IP signing in, answering and submitting at once: 8,400 requests, no failures, p99 12 ms |
| Sessions in the database | Mock exam on production images; sessions survive a backend restart; 300 candidates hitting the backend directly, 12,000 requests, no failures (p50 15 ms, p95 109 ms) |
| Failure cases | Backend restart mid-exam, database restart, the same candidate signing in twice, lockout after wrong login codes and reset, officer ending the exam early, auto-submit at time-up (including candidates who never reconnect), proctor adding time just before time-up (countdown extends, no auto-submit) |
| HTTPS | Behind a reverse proxy with a self-signed certificate and `TESTA_COOKIE_SECURE=true`, the full mock exam passes. With `set_real_ip_from`, rate limits and the audit log use the real client IP and forged `X-Forwarded-For` is ignored |
| Browser (Chromium) | Candidates: sign-in, all four question types, reload and resume, resend after disconnect, being kicked, submit, time-up, checking scores (including 390 and 320 px widths). Staff: password change, authoring, creating exams, Big5 roster import, login slips, proctoring, grading, publishing, export. Admin pages. Archive and unarchive, emergency access grant and view, grader disagreement warnings (grading page, results page, confirm before publishing) |
| Automated tests | 40 backend integration tests (Testcontainers with real PostgreSQL); 19 frontend unit tests |

### Needs the real site

- The school server and the computer lab network; the school's own HTTPS reverse proxy.
- Browsers other than Chromium (Safari, Firefox, Edge) and Windows.
- Login slips printed on a real printer (PDF output with Chinese text is confirmed).

## Not done yet

All seven first-release items in the design document are done. Below is what is missing or simpler than the design document.

### Decided by the school (2026-11-14)

| Question | Decision | Implementation |
| --- | --- | --- |
| How long to keep candidate data and papers | n+1 years, archived, then deleted by hand | After an exam ends the owner can **archive** it (read-only, candidates can no longer sign in). Once archived and past retention, it can be **deleted** with a reason: roster, papers and scores go together; the audit log keeps the exam name and head count. Retention ends on 31 Dec of the year after the exam ended (`testa.retention.years`) |
| What to do when graders disagree | Use the average; warn the officer when the gap is over 20 | When the gap exceeds 20% of the question's points, the grading and results pages warn, and publishing asks again. The officer can reopen grading |
| May authors grade their own questions | Yes | No recusal rule |
| Emergency access for system administrators | Yes, with the owner's grant and a record | The owner grants one administrator access to one exam with a reason and a duration (up to 3 days) and can revoke it any time. The administrator sees the roster, progress and scores only; no login codes, questions or answers, and no edits. Grants, revocations and every view are audited |

Two interpretations need confirming with the school before launch:

- **"n+1 years"**: currently 31 Dec of the year after the exam ended. If the school means school years, change `testa.retention.years` or the calculation.
- **"Gap over 20"**: currently 20% of the question's points, i.e. 20 points for a 100-point question. If the school means 20 points regardless, questions worth less than 20 would never warn and need separate handling.

### Waiting for the school

| Item | Current behaviour |
| --- | --- |
| Login codes stored in plain text | Kept so slips can be reprinted; readable only by the unit's owners and proctors |
| Can candidates check their scores | Assumed: publishing results lets candidates see their own score |

### Other gaps

- Staff who forget their password must ask an administrator to reset it; there is no self-service flow (it needs email, which the first release does not send).
- The admin "Backups" tab only lists backups and the latest time; it cannot download, trigger or restore.
- The frontend has unit tests for pure logic only (grading, question validation, formulas, API errors), no component or browser automation; browser flows were run by script by hand.
- The CI workflow (`.github/workflows/ci.yml`) is written but has never run, since the project is not on GitHub yet.

### Not in the first release

- AI question import, AI-assisted essay grading and admin system settings: UI only, no backend (see [AI research](ai.md)). Controlled by `DEMO_FEATURES` in `testa/src/lib/flags.ts`: visible in `npm run dev`, hidden in production builds.
- Long-lived student accounts, emailing or texting login slips, video proctoring and lockdown browsers, random question selection and multiple paper versions, integration with the school's systems and single sign-on.

## Differences from design document v0.2

- Graders are one or more, not a fixed three (confirmed 2026-11-14).
- System administrators can view an exam for a limited time with the owner's grant (confirmed 2026-11-14).
- Login codes are stored in plain text, not hashed, so slips can be reprinted (to be confirmed).
- The backend uses Spring Boot 4.1 instead of 3 (3.x is out of open-source support); the compile target is still Java 21.
- Publishing results lets candidates check their own score (open question 7 in the design document; assumed for now).
- The exam form no longer asks for the delivery mode (computer lab / online): it had no effect, so new exams are recorded as computer lab. The column stays and can come back for online exams.
- Chinese text uses the system sans-serif font; Latin fonts are bundled with the frontend, with no external CDN.
