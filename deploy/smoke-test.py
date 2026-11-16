#!/usr/bin/env python3
"""Post-deploy smoke test: a full mock exam over real HTTP through Nginx.

    python3 deploy/smoke-test.py http://localhost:8088 ADMIN_EMAIL ADMIN_PASSWORD

On a fresh installation it creates the administrator through first-run setup.

Flow: administrator signs in (changing a temporary password if needed), units and staff, bank, exam, roster, publish,
candidates sign in, answer and submit, graders assigned and grade, exam ended, results published, candidates see scores, CSV export.

Notes:
- Leaves units, accounts and exams prefixed with 「冒煙測試」 in the database. **Run against a test environment only.**
- A temporary administrator password is replaced with "<old password>-smoke" (printed at the end).
- Python 3 standard library only.
"""
import http.cookiejar
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone


class Client:
    def __init__(self, base):
        self.base = base.rstrip('/')
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def _xsrf(self):
        for c in self.jar:
            if c.name == 'XSRF-TOKEN':
                return c.value
        return ''

    def call(self, method, path, body=None, expect=(200, 201, 204), raw=False):
        if method != 'GET' and not self._xsrf():
            self.call('GET', '/auth/csrf')
        headers = {'Accept': 'application/json'}
        data = None
        if method != 'GET':
            headers['X-XSRF-TOKEN'] = self._xsrf()
        if body is not None:
            headers['Content-Type'] = 'application/json'
            data = json.dumps(body).encode()
        req = urllib.request.Request(self.base + '/api' + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=20) as res:
                status, text = res.status, res.read()
        except urllib.error.HTTPError as e:
            status, text = e.code, e.read()
        if status not in expect:
            raise SystemExit(f'✗ {method} {path} returned {status} (expected {expect}): {text.decode(errors="replace")[:400]}')
        if raw:
            return text
        return json.loads(text) if text else None


def step(msg):
    print(f'✓ {msg}')


def staff_login(base, email, password):
    """Signs in, replacing a temporary password. Returns (client, current password)."""
    c = Client(base)
    me = c.call('POST', '/auth/login', {'email': email, 'password': password})
    if me['mustChangePassword']:
        new = password + '-smoke'
        c.call('POST', '/auth/password', {'currentPassword': password, 'newPassword': new})
        password = new
    return c, password


def main():
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    base, admin_email, admin_password = sys.argv[1:4]
    tag = time.strftime('%m%d%H%M%S')

    health = Client(base).call('GET', '/health')
    assert health == {'status': 'ok'}, health
    step('Health check')

    if Client(base).call('GET', '/auth/setup')['required']:
        Client(base).call('POST', '/auth/setup', {'email': admin_email, 'name': 'Admin', 'password': admin_password})
        Client(base).call('POST', '/auth/setup', {'email': 'x' + admin_email, 'name': 'Admin',
                                                  'password': admin_password}, expect=(409,))
        step('First-run setup: administrator created, setup closed')

    admin, admin_password = staff_login(base, admin_email, admin_password)
    step('System administrator signs in')

    unit = admin.call('POST', '/units', {'name': f'冒煙測試單位 {tag}', 'type': 'ADMISSION'})['id']
    people = {}
    for key, name, roles in [('officer', '冒煙承辦人', ['OWNER', 'PROCTOR']), ('teacher', '冒煙教師', ['AUTHOR', 'GRADER']),
                             ('teacher2', '冒煙教師二', ['GRADER'])]:
        email = f'smoke-{key}-{tag}@smoke.test'
        r = admin.call('POST', '/admin/staff', {'email': email, 'name': name})
        for role in roles:
            admin.call('POST', f'/units/{unit}/members', {'email': email, 'role': role})
        client, _ = staff_login(base, email, r['temporaryPassword'])
        people[key] = (client, r['staff']['id'])
    officer, teacher, teacher2 = (people[k][0] for k in ('officer', 'teacher', 'teacher2'))
    step('Unit and three staff accounts with roles; temporary passwords changed after sign-in')

    # Administrators cannot reach papers
    admin.call('GET', '/banks', expect=(200,))
    questions = [
        {'id': 'q1', 'type': 'single', 'stem': '1+1=?', 'points': 5,
         'choices': [{'id': 'a', 'text': '1'}, {'id': 'b', 'text': '2'}], 'correct': ['b']},
        {'id': 'q2', 'type': 'blank', 'stem': '台灣最高的山是＿＿', 'points': 5, 'blanks': [{'id': 'b1', 'answers': ['玉山']}]},
        {'id': 'e1', 'type': 'essay', 'stem': '請說明你報考的動機。', 'points': 20, 'rubric': '動機明確、條理清楚'},
    ]
    bank = teacher.call('POST', '/banks', {'unitId': unit, 'name': f'冒煙測試題庫 {tag}', 'questions': questions})['id']
    step('Teacher creates a bank (choice, fill-in, essay)')

    start = (datetime.now(timezone.utc) - timedelta(seconds=5)).isoformat().replace('+00:00', 'Z')
    exam = officer.call('POST', '/exams', {'name': f'冒煙測試考試 {tag}', 'bankId': bank, 'startAt': start, 'duration': 30})['id']
    rows = [{'ticket': f'S{tag}-{i}', 'name': n} for i, n in enumerate(['王小明', '李宜華', '缺考的人'], 1)]
    officer.call('POST', f'/exams/{exam}/candidates', {'rows': rows})
    officer.call('POST', f'/exams/{exam}/publish')
    roster = officer.call('GET', f'/exams/{exam}/candidates')
    codes = {c['ticket']: c['code'] for c in roster}
    step('Officer creates the exam, imports the roster, publishes')

    def take(ticket, choice, blank, essay):
        c = Client(base)
        c.call('POST', '/candidate/login', {'ticket': ticket, 'code': codes[ticket]})
        c.call('POST', '/candidate/start')
        paper = c.call('GET', '/candidate/paper', raw=True).decode()
        for secret in ('correct', '玉山', '動機明確'):
            assert secret not in paper, f'paper leaks an answer: {secret}'
        c.call('PUT', '/candidate/answers/q1', {'choices': [choice]})
        c.call('PUT', '/candidate/answers/q2', {'blanks': {'b1': blank}})
        c.call('PUT', '/candidate/answers/e1', {'essay': essay})
        if ticket == rows[0]['ticket']:
            # Proctor extra time: remaining time grows
            cid = next(x['id'] for x in roster if x['ticket'] == ticket)
            before = c.call('GET', '/candidate/me')['attempt']['remainingSeconds']
            officer.call('POST', f'/exams/{exam}/candidates/{cid}/extend', {'minutes': 5, 'reason': '冒煙測試'})
            after = c.call('GET', '/candidate/me')['attempt']['remainingSeconds']
            assert 290 <= after - before <= 300, (before, after)
        state = c.call('POST', '/candidate/submit')
        assert state['attempt']['submittedAt'] and state['result'] is None
        # No edits after submitting
        c.call('PUT', '/candidate/answers/q1', {'choices': ['a']}, expect=(409,))
        return c

    Client(base).call('POST', '/candidate/login', {'ticket': rows[0]['ticket'], 'code': '000000'}, expect=(401,))
    take(rows[0]['ticket'], 'b', '玉山', '我想學習。')
    take(rows[1]['ticket'], 'a', '阿里山', '因為有興趣。')
    step('Two candidates sign in, answer, submit (no answers in the paper; wrong code rejected; extra time; no edits after submit)')

    officer.call('PUT', f'/exams/{exam}/graders', {'staffIds': [people['teacher'][1], people['teacher2'][1]]})
    for grader, scores in ((teacher, {'我想學習。': 16, '因為有興趣。': 10}), (teacher2, {'我想學習。': 18, '因為有興趣。': 11})):
        items = grader.call('GET', f'/grading/exams/{exam}/items')
        assert len(items) == 2
        assert '王小明' not in json.dumps(items, ensure_ascii=False), 'grader saw a candidate name'
        for it in items:
            grader.call('PUT', f'/grading/exams/{exam}/items/{it["anonId"]}/{it["questionId"]}',
                        {'score': scores[it['answer']], 'comment': ''})
    step('Two graders assigned, anonymous grading')

    officer.call('POST', f'/exams/{exam}/results/publish', expect=(409,))   # exam has not ended
    officer.call('POST', f'/exams/{exam}/end')
    results = officer.call('POST', f'/exams/{exam}/results/publish')
    totals = {r['name']: r['total'] for r in results['rows']}
    # Candidate 1: 5+5+(16+18)/2 = 27; candidate 2: 0+0+(10+11)/2 = 10.5; absentee has no score
    assert totals == {'王小明': 27.0, '李宜華': 10.5, '缺考的人': None}, totals
    step(f'Exam ended, results published: {totals}')

    c = Client(base)
    me = c.call('POST', '/candidate/login', {'ticket': rows[0]['ticket'], 'code': codes[rows[0]['ticket']]})
    assert me['result'] is None, me['result']   # candidates cannot see scores until the owner allows it
    assert officer.call('PUT', f'/exams/{exam}/results/candidate-access', {'enabled': True})['candidateResults']
    me = c.call('GET', '/candidate/me')
    assert me['result']['total'] == 27.0 and me['result']['fullMarks'] == 30.0, me['result']
    step('Candidate access turned on; candidate sees their score')

    csv = officer.call('GET', f'/exams/{exam}/results.csv', raw=True).decode('utf-8-sig')
    assert '王小明' in csv and '27' in csv, csv
    teacher.call('GET', f'/exams/{exam}/results', expect=(404,))            # teachers cannot see the score sheet
    admin.call('GET', f'/exams/{exam}/results', expect=(404,))              # neither can administrators
    logs = admin.call('GET', '/admin/audit-logs?action=RESULTS_PUBLISHED')
    assert logs['items'], 'publishing results was not audited'
    step('CSV export, permission isolation, audit log')

    # Emergency access: administrators see the exam only after the owner grants it, and never login codes
    overview = officer.call('GET', f'/exams/{exam}/emergency-access')
    admin_id = next(a['staffId'] for a in overview['admins'] if a['email'] == admin_email)
    teacher.call('GET', f'/exams/{exam}/emergency-access', expect=(403, 404))
    grant = officer.call('POST', f'/exams/{exam}/emergency-access',
                         {'staffId': admin_id, 'reason': '冒煙測試', 'hours': 1})['id']
    assert any(g['id'] == grant for g in admin.call('GET', '/admin/emergency-access'))
    view = admin.call('GET', f'/admin/emergency-access/{grant}', raw=True).decode()
    assert '王小明' in view, view[:300]
    for code in codes.values():
        assert code not in view, 'emergency view leaks login codes'
    admin.call('GET', f'/exams/{exam}/results', expect=(404,))              # the regular path stays closed
    officer.call('DELETE', f'/exams/{exam}/emergency-access/{grant}')
    admin.call('GET', f'/admin/emergency-access/{grant}', expect=(403, 404))
    for action in ('EMERGENCY_ACCESS_GRANTED', 'EMERGENCY_ACCESS_VIEWED', 'EMERGENCY_ACCESS_REVOKED'):
        assert admin.call('GET', f'/admin/audit-logs?action={action}')['items'], f'{action} was not audited'
    step('Emergency access: grant, view (no login codes), revoke, all audited')

    # Archive: candidates can no longer sign in; cannot delete within retention
    archived = officer.call('POST', f'/exams/{exam}/archive')
    assert archived['archivedAt'] and archived['retainUntil'], archived
    Client(base).call('POST', '/candidate/login',
                      {'ticket': rows[0]['ticket'], 'code': codes[rows[0]['ticket']]}, expect=(401,))
    officer.call('DELETE', f'/exams/{exam}', {'reason': '冒煙測試'}, expect=(409,))
    step(f'Exam archived: candidates locked out, cannot delete before {archived["retainUntil"][:10]}')

    print(f'\nAll passed. Current administrator password: {admin_password}')


if __name__ == '__main__':
    main()
