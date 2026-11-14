# Screenshots

The production build with demo data, in the order an admission exam runs. Desktop at 1440 x 900; phone at 390 px wide.
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

## Retaking

1. Run the backend in the dev profile against an empty database, and serve the production build: `cd testa && npm run build && npx vite preview`.
2. Create realistic demo data through the API (banks, an exam in progress, an ended exam with essays half graded, a draft), the same way `deploy/smoke-test.py` does.
3. Capture with a headless browser at 1440 x 900 (1x) and 390 x 844 (2x). Keep names and the demo data free of real people and school details.
