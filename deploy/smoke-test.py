#!/usr/bin/env python3
"""部署後的冒煙測試：透過 Nginx 用真的 HTTP 走完一場模擬考。

    python3 deploy/smoke-test.py http://localhost:8088 管理員帳號 管理員密碼

流程：管理員登入（必要時換掉臨時密碼）→ 建單位與教職員 → 建題庫 → 建考試、名單、發布
→ 考生登入、作答、交卷 → 指派評分者、評分 → 結束考試、公布成績 → 考生查到自己的分數 → 匯出 CSV。

注意：
- 會在資料庫裡留下「冒煙測試」開頭的單位、帳號與考試，**只對測試環境執行**，不要對正式資料庫跑。
- 管理員如果還是臨時密碼，會被換成「原密碼 + -smoke」（執行結束時會印出來）。
- 只用 Python 3 標準函式庫。
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
            raise SystemExit(f'✗ {method} {path} 回 {status}（預期 {expect}）：{text.decode(errors="replace")[:400]}')
        if raw:
            return text
        return json.loads(text) if text else None


def step(msg):
    print(f'✓ {msg}')


def staff_login(base, email, password):
    """登入；如果是臨時密碼就換掉。回傳 (client, 目前的密碼)。"""
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
    base, admin_email, admin_password = sys.argv[1:]
    tag = time.strftime('%m%d%H%M%S')

    health = Client(base).call('GET', '/health')
    assert health == {'status': 'ok'}, health
    step('健康檢查')

    admin, admin_password = staff_login(base, admin_email, admin_password)
    step('系統管理員登入')

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
    step('建立單位、三個教職員帳號並指派角色；臨時密碼登入後換密碼')

    # 系統管理員碰不到考卷
    admin.call('GET', '/banks', expect=(200,))
    questions = [
        {'id': 'q1', 'type': 'single', 'stem': '1+1=?', 'points': 5,
         'choices': [{'id': 'a', 'text': '1'}, {'id': 'b', 'text': '2'}], 'correct': ['b']},
        {'id': 'q2', 'type': 'blank', 'stem': '台灣最高的山是＿＿', 'points': 5, 'blanks': [{'id': 'b1', 'answers': ['玉山']}]},
        {'id': 'e1', 'type': 'essay', 'stem': '請說明你報考的動機。', 'points': 20, 'rubric': '動機明確、條理清楚'},
    ]
    bank = teacher.call('POST', '/banks', {'unitId': unit, 'name': f'冒煙測試題庫 {tag}', 'questions': questions})['id']
    step('教師建立題庫（選擇、填空、申論）')

    start = (datetime.now(timezone.utc) - timedelta(seconds=5)).isoformat().replace('+00:00', 'Z')
    exam = officer.call('POST', '/exams', {'name': f'冒煙測試考試 {tag}', 'bankId': bank, 'startAt': start, 'duration': 30})['id']
    rows = [{'ticket': f'S{tag}-{i}', 'name': n} for i, n in enumerate(['王小明', '李宜華', '缺考的人'], 1)]
    officer.call('POST', f'/exams/{exam}/candidates', {'rows': rows})
    officer.call('POST', f'/exams/{exam}/publish')
    roster = officer.call('GET', f'/exams/{exam}/candidates')
    codes = {c['ticket']: c['code'] for c in roster}
    step('承辦人建立考試、匯入名單、發布')

    def take(ticket, choice, blank, essay):
        c = Client(base)
        c.call('POST', '/candidate/login', {'ticket': ticket, 'code': codes[ticket]})
        c.call('POST', '/candidate/start')
        paper = c.call('GET', '/candidate/paper', raw=True).decode()
        for secret in ('correct', '玉山', '動機明確'):
            assert secret not in paper, f'試卷洩漏了答案：{secret}'
        c.call('PUT', '/candidate/answers/q1', {'choices': [choice]})
        c.call('PUT', '/candidate/answers/q2', {'blanks': {'b1': blank}})
        c.call('PUT', '/candidate/answers/e1', {'essay': essay})
        state = c.call('POST', '/candidate/submit')
        assert state['attempt']['submittedAt'] and state['result'] is None
        # 交卷後不能再改
        c.call('PUT', '/candidate/answers/q1', {'choices': ['a']}, expect=(409,))
        return c

    Client(base).call('POST', '/candidate/login', {'ticket': rows[0]['ticket'], 'code': '000000'}, expect=(401,))
    take(rows[0]['ticket'], 'b', '玉山', '我想學習。')
    take(rows[1]['ticket'], 'a', '阿里山', '因為有興趣。')
    step('兩位考生登入、作答、交卷（試卷不含答案；錯的登入碼被拒絕；交卷後不能改）')

    officer.call('PUT', f'/exams/{exam}/graders', {'staffIds': [people['teacher'][1], people['teacher2'][1]]})
    for grader, scores in ((teacher, {'我想學習。': 16, '因為有興趣。': 10}), (teacher2, {'我想學習。': 18, '因為有興趣。': 11})):
        items = grader.call('GET', f'/grading/exams/{exam}/items')
        assert len(items) == 2
        assert '王小明' not in json.dumps(items, ensure_ascii=False), '評分者看到了考生姓名'
        for it in items:
            grader.call('PUT', f'/grading/exams/{exam}/items/{it["anonId"]}/{it["questionId"]}',
                        {'score': scores[it['answer']], 'comment': ''})
    step('指派兩位評分者，匿名評分')

    officer.call('POST', f'/exams/{exam}/results/publish', expect=(409,))   # 考試還沒結束
    officer.call('POST', f'/exams/{exam}/end')
    results = officer.call('POST', f'/exams/{exam}/results/publish')
    totals = {r['name']: r['total'] for r in results['rows']}
    # 王小明 5+5+(16+18)/2 = 27；李宜華 0+0+(10+11)/2 = 10.5；缺考沒有分數
    assert totals == {'王小明': 27.0, '李宜華': 10.5, '缺考的人': None}, totals
    step(f'結束考試、公布成績：{totals}')

    c = Client(base)
    me = c.call('POST', '/candidate/login', {'ticket': rows[0]['ticket'], 'code': codes[rows[0]['ticket']]})
    assert me['result']['total'] == 27.0 and me['result']['fullMarks'] == 30.0, me['result']
    step('考生登入查到自己的成績')

    csv = officer.call('GET', f'/exams/{exam}/results.csv', raw=True).decode('utf-8-sig')
    assert '王小明' in csv and '27' in csv, csv
    teacher.call('GET', f'/exams/{exam}/results', expect=(404,))            # 教師看不到成績表
    admin.call('GET', f'/exams/{exam}/results', expect=(404,))              # 系統管理員也看不到
    logs = admin.call('GET', '/admin/audit-logs?action=RESULTS_PUBLISHED')
    assert logs['items'], '操作紀錄沒有公布成績'
    step('匯出 CSV、權限隔離、操作紀錄')

    print(f'\n全部通過。系統管理員目前的密碼：{admin_password}')


if __name__ == '__main__':
    main()
