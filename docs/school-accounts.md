# Proposal: internal exams with school accounts

| | |
| --- | --- |
| Status | Proposed, deferred until after the first admission exam; not implemented |
| Scope | Midterms, finals and quizzes where teachers and students sign in with school accounts |

## Summary

Add an optional `school` module to the existing backend. It verifies school accounts and reads classes from the school's systems; everything else (banks, papers, timing, grading, results, audit) is reused unchanged. Without configuration the module is off, and Testa behaves exactly as it does for admission exams.

## Motivation

Admission exams have external candidates with no accounts, so they use rosters and login codes. Internal exams are different: teachers and students already have school accounts, and the roster is a class that already exists in the school database. Asking teachers to export and import class lists for every quiz is the main obstacle to using Testa for coursework.

## Design

Internal exams differ from admission exams in only two places: **who signs in** and **where the roster comes from**. The module covers exactly those, behind two interfaces:

```
school/
  SchoolLogin            verify(account, password) -> SUCCESS(account) | BAD_CREDENTIALS | UNAVAILABLE
  HttpSchoolLogin        forwards credentials to the school's login service (or LdapSchoolLogin for an LDAP bind)
  SchoolDirectory        person(id), classesTaughtBy(teacher), studentsIn(class), classesOf(student)
  JdbcSchoolDirectory    read-only, cached SQL against the school database
  DemoSchoolDirectory    fixed data for development, tests and other schools
```

Nothing outside `school/` knows the school's database or tables. Each school writes its own adapters; the demo implementation keeps the module testable without one.

**Passwords never leave the school's systems.** Testa does not read or store password hashes; credentials are checked by the school's own login service or an LDAP bind.

### Flow

1. A teacher signs in with a school account. A local staff record is created on first sign-in and linked to the school id.
2. The teacher creates an exam whose audience is **Class** (today's exams become audience **Roster**) and picks one or more classes.
3. On publish, the students of those classes are copied into the exam's roster, with the school id as the candidate number. The snapshot keeps add/drop changes from altering a running exam and means the exam never needs the school database while it runs. The teacher can refresh the roster until the exam starts.
4. Students sign in with their school accounts and see their published exams: upcoming, in progress and finished, with results once published.
5. Taking the exam, grading and results work as they do today.

Login codes remain a fallback: if the school login service is unreachable on exam day, the proctor prints slips and students sign in as admission candidates do.

## Alternatives considered

| Option | Why not |
| --- | --- |
| A new backend built around the school database | Duplicates the exam engine; two systems to secure and keep in sync |
| A separate service that syncs classes and signs users into Testa | Clean isolation, but two deployments and a single sign-on protocol between them; too heavy for one school |

## Open questions

| Question | Why it matters |
| --- | --- |
| Login service or LDAP, and can students (not only staff) authenticate through it? | Some school login services cover staff only |
| Which records link teachers to classes and students to courses? | If a course's teacher is stored as free text, mapping needs another source or a manual step |
| Is the login service reachable from the labs when the Internet is cut off? | Otherwise nobody can sign in during an offline exam (login codes cover this) |
| Teaching assistants, co-taught courses, students in several sections | Decide what the first version supports |
| A read-only database account and the school's permission to use it | Required before any real adapter |

## Milestones

1. `school` module with demo implementations: teachers and students sign in with demo school accounts.
2. Class audience: pick classes, roster snapshot on publish, student exam list.
3. Real adapters (login service or LDAP, school database), enabled by configuration.
4. A pilot with one real class.
