# Screenshots

**English** | [繁體中文](screenshots.zh-TW.md)

The production build with demo data, in the order an admission exam runs. Desktop at 1440 x 900; phone at 390 x 844 (2x).
Retake them after visible UI changes (see "Retaking" at the end).

## Signing in

| Desktop | Phone |
| --- | --- |
| ![Login page](images/login.png) | ![Login page on a phone](images/login-phone.png) |

Candidates sign in with the candidate number and login code from their slip; staff switch to the second tab.

## Teachers: question banks

![Bank list](images/banks.png)

![Bank editor](images/editor.png)

The editor saves automatically. Math is typed as `$...$` and rendered with KaTeX, bundled with the app.

![Try-it mode](images/editor-preview.png)

"Try it" shows the bank exactly as a candidate will see it.

## Exam officer: scheduling and proctoring

![Officer home](images/officer-home.png)

The home page lists what needs doing next for each exam.

![Exam list](images/exams.png)

![New exam](images/exam-new.png)

| Desktop | Phone |
| --- | --- |
| ![Proctoring](images/exam-proctor.png) | ![Proctoring on a phone](images/exam-proctor-phone.png) |

Proctoring refreshes every 10 seconds. Clicking a count shows only those candidates, for example "needs attention" (locked out by wrong codes, or left the exam window).

![Login slips](images/exam-slips.png)

One slip per candidate, printed from the browser.

## Candidates

![Candidate home](images/candidate-home.png)

![Identity check and rules](images/candidate-rules.png)

| Desktop | Phone |
| --- | --- |
| ![Taking the exam](images/candidate-exam.png) | ![Taking the exam on a phone](images/candidate-exam-phone.png) |

The clock is display only; timing and auto-submit are on the server. Answers save as they change.

## Grading and results

![Teacher home](images/teacher-home.png)

| Desktop | Phone |
| --- | --- |
| ![Anonymous grading](images/grading.png) | ![Grading on a phone](images/grading-phone.png) |

Graders see one anonymous answer at a time and must enter a score (0 included) before submitting.

![Graders and progress](images/exam-grading.png)

![Results](images/exam-results.png)

Results can be published once the exam has ended and every essay is graded.

## System administrators

![Accounts](images/admin-users.png)

![Audit log](images/admin-audit.png)

The audit log is append-only and can be filtered by action.

![Backups](images/admin-backups.png)

Backups can be taken, downloaded, deleted and restored here. Delete and restore sit in the "..." menu and ask for confirmation; restore also asks the administrator to type 「還原」, and is refused while candidates are answering.

![System settings](images/admin-settings.png)

Everything a site may need to adjust, in plain words: AI import, grading flags, data retention, backup retention and sign-in lockout.

## Retaking

`tools/capture-screenshots.mjs` creates the demo data through the API and saves every image above. It drives Chrome over the DevTools protocol, so it needs no extra dependency.

1. Run the backend in the dev profile, with a backup directory so the backup tab has content: `TESTA_BACKUP_PATH=/tmp/testa-backups TESTA_BACKUP_CONTROL_PATH=/tmp/testa-control ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (put a few `testa-YYYYmmdd-HHMMSS.sql.gz` files in the first one).
2. Serve the production build, so the dev-only demo sign-in buttons are hidden: `cd testa && npm run build && npx vite preview`.
3. From the repository root: `node tools/capture-screenshots.mjs` (set `CHROMIUM` if Chrome is not in `/Applications`, and `TESTA_APP` if the preview is not on `http://localhost:4173/`).

Keep the file names so links in the README keep working, and keep the demo data free of real people and school details.
