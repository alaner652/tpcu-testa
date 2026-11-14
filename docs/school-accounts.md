# School accounts for internal exams (proposal)

Status: proposed 2026-11-15, not implemented. Scope: midterms, finals and quizzes where teachers and students sign in with their school accounts, teachers pick their classes, and students see their own classes' exams.

## Decision: same backend, separate module

Internal exams do not need a new backend. What differs from admission exams is only **who signs in** and **where the roster comes from**. Question banks, the published paper snapshot, timing, autosave, submission, grading, results, audit and backups are the same, and they are the parts that are hardest to get right and already verified.

| Option | Verdict |
| --- | --- |
| Rewrite a backend around the school database | Duplicates the exam engine; two systems to keep secure and in sync. Rejected |
| A separate service that syncs classes and signs users into Testa | Clean isolation, but two deployments and a single sign-on protocol between them. Too much for one school |
| **A `school` module in this backend behind interfaces** | One deployment and one audit log; the school-specific code is isolated and switched off unless configured. Recommended |

The module follows LabSlot's proven pattern (`SiwClient`, `StaffDirectory`, `SchoolData`):

```
school/
  SchoolLogin            interface: verify(account, password) -> SUCCESS(account) | BAD_CREDENTIALS | UNAVAILABLE
  HttpSchoolLogin        forwards credentials to the school's login service, as LabSlot does (or LdapSchoolLogin)
  SchoolDirectory        interface: person(id), classesTaughtBy(teacher), studentsIn(class), classesOf(student)
  JdbcSchoolDirectory    read-only SQL against the school database, cached
  DemoSchoolDirectory    fixed data for development, tests and other schools
```

Nothing outside `school/` knows about the school's database or tables. Without `TESTA_SCHOOL_*` settings the module is off and the system behaves exactly as today, which keeps it usable for admission-only deployments and as open source.

**Passwords are never read from the school database.** Copying or comparing them would make this system a second place to leak them. Credentials are checked by the school's own login service (as in LabSlot) or an LDAP bind.

## Data flow

1. A teacher signs in with the school account. A local staff record is created on first sign-in, linked to the school id; their classes come from `SchoolDirectory`.
2. The teacher creates an exam with audience **Class** (today's exams become audience **Roster**) and picks one or more classes.
3. On publish, the students of those classes are copied into the exam's roster (school id as the candidate number). This snapshot means add/drop changes during the term cannot alter a running exam, and the exam never needs the school database while it runs. The teacher can refresh the roster until the exam starts.
4. A student signs in with the school account and sees their published exams: upcoming, in progress, finished (with results once published).
5. From there the existing attempt, grading and result flow is unchanged.

Login codes stay available as a fallback for class exams: if the school login service is unreachable on exam day, the proctor prints slips and students sign in the current way.

## Risks and questions to settle first

| Question | Why it matters |
| --- | --- |
| Login service or LDAP for verifying passwords? Can students (not only staff) sign in through it? | LabSlot only verifies staff |
| Which records link a teacher to a course/class and a student to a course? | In LabSlot's data the course's teacher is free text, not an id, so mapping teachers to classes may need another source or a manual step |
| Is the login service reachable on the internal network when the Internet is cut off? | Otherwise nobody can sign in during an offline exam (the login-code fallback covers it) |
| Read-only database account and permission from the school | Same arrangement as LabSlot |
| Teaching assistants, co-taught courses, students in several sections | Decide whether the first version supports them |

## Suggested milestones

1. `school` module with `SchoolLogin` and `SchoolDirectory`, demo implementations only; teachers and students sign in with demo school accounts.
2. Exam audience Class: pick classes, roster snapshot on publish, student exam list.
3. Real adapters (login service or LDAP, school database) on the school network, behind configuration.
4. Rehearsal with one real class.
