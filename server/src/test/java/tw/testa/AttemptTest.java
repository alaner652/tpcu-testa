package tw.testa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import tw.testa.attempt.AttemptService;

class AttemptTest extends IntegrationTestBase {
    private static int seq;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AttemptService attemptService;

    MockHttpSession admin;
    MockHttpSession owner;
    long exam;
    String ticket;
    String code;
    String ticket2;
    String code2;

    private static final String QUESTIONS = """
            {"id":"q1","type":"single","stem":"1+1=?","points":5,"explanation":"小學數學",
             "choices":[{"id":"a","text":"1"},{"id":"b","text":"2"}],"correct":["b"]},
            {"id":"q2","type":"multiple","stem":"哪些是質數？","points":6,
             "choices":[{"id":"a","text":"2"},{"id":"b","text":"3"},{"id":"c","text":"4"}],"correct":["a","b"]},
            {"id":"q3","type":"blank","stem":"首項＿＿，和＿＿","points":6,"caseSensitive":false,
             "blanks":[{"id":"b1","answers":["39"]},{"id":"b2","answers":["210","二一〇"]}]},
            {"id":"q4","type":"essay","stem":"試證明…","points":10,"rubric":"評分規準：機密","wordLimit":500}""";

    @BeforeEach
    void setUp() throws Exception {
        int n = ++seq;
        admin = adminSession();
        long unit = createUnit(admin, "作答測試單位 " + n);
        String ownerEmail = "attempt-owner" + n + "@test.tw";
        Object[] s = createStaff(admin, ownerEmail, "負責人");
        grant(admin, unit, ownerEmail, "OWNER");
        grant(admin, unit, ownerEmail, "AUTHOR");
        owner = login(ownerEmail, (String) s[1]);

        var b = mvc.perform(json(post("/api/banks"), "{\"unitId\":%d,\"name\":\"題庫\",\"questions\":[%s]}".formatted(unit, QUESTIONS))
                .session(owner)).andExpect(status().isCreated()).andReturn();
        long bank = ((Number) read(b, "$.id")).longValue();
        var e = mvc.perform(json(post("/api/exams"),
                "{\"name\":\"作答測試\",\"bankId\":%d,\"startAt\":\"%s\",\"duration\":30}".formatted(bank, Instant.now().minusSeconds(5)))
                .session(owner)).andExpect(status().isCreated()).andReturn();
        exam = ((Number) read(e, "$.id")).longValue();
        ticket = "T" + n + "-0001";
        ticket2 = "T" + n + "-0002";
        mvc.perform(json(post("/api/exams/" + exam + "/candidates"),
                "{\"rows\":[{\"ticket\":\"%s\",\"name\":\"王小明\",\"dept\":\"資工\"},{\"ticket\":\"%s\",\"name\":\"李宜華\"}]}"
                        .formatted(ticket, ticket2)).session(owner)).andExpect(status().isCreated());
        var list = mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner)).andReturn();
        code = read(list, "$[0].code");
        code2 = read(list, "$[1].code");
    }

    private void publish() throws Exception {
        mvc.perform(post("/api/exams/" + exam + "/publish").with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("live"));
    }

    private MockHttpSession candidate(String t, String c) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(json(post("/api/candidate/login"), "{\"ticket\":\"%s\",\"code\":\"%s\"}".formatted(t, c)).session(s))
                .andExpect(status().isOk());
        return s;
    }

    private void answer(MockHttpSession s, String q, String body, int expected) throws Exception {
        mvc.perform(json(put("/api/candidate/answers/" + q), body).session(s)).andExpect(status().is(expected));
    }

    @Test
    void cannotSignInBeforePublish_wrongCodesLock_proctorResetUnlocks() throws Exception {
        String body = "{\"ticket\":\"%s\",\"code\":\"%s\"}";
        mvc.perform(json(post("/api/candidate/login"), body.formatted(ticket, code))).andExpect(status().isUnauthorized());
        publish();
        // Lower case and spaces are fine
        mvc.perform(json(post("/api/candidate/login"),
                body.formatted(ticket.toLowerCase(), code.substring(0, 3) + " " + code.substring(3))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("王小明"))
                .andExpect(jsonPath("$.exam.status").value("live")).andExpect(jsonPath("$.exam.questionCount").value(4))
                .andExpect(jsonPath("$.attempt").isEmpty());

        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 3; i++) {
            mvc.perform(json(post("/api/candidate/login"), body.formatted(ticket, wrong))).andExpect(status().isUnauthorized());
        }
        mvc.perform(json(post("/api/candidate/login"), body.formatted(ticket, code)))
                .andExpect(status().isTooManyRequests());
        var list = mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner))
                .andExpect(jsonPath("$[0].locked").value(true)).andReturn();
        Number cid = read(list, "$[0].id");
        var reset = mvc.perform(post("/api/exams/" + exam + "/candidates/" + cid + "/reset-code").with(csrf()).session(owner))
                .andExpect(status().isOk()).andReturn();
        candidate(ticket, read(reset, "$.code"));
    }

    /** A whole lab signing in at once: sign-in must use one connection, or more people than the pool size deadlock. */
    @Test
    void manyConcurrentSignInsDoNotExhaustThePool() throws Exception {
        publish();
        int n = 40;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        var ready = new java.util.concurrent.CountDownLatch(n);
        var go = new java.util.concurrent.CountDownLatch(1);
        try {
            var calls = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < n; i++) {
                // Half unknown candidate numbers, half valid credentials (both paths write the audit log)
                String body = i % 2 == 0 ? "{\"ticket\":\"NOBODY-%d\",\"code\":\"000000\"}".formatted(i)
                        : "{\"ticket\":\"%s\",\"code\":\"%s\"}".formatted(ticket2, code2);
                calls.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mvc.perform(json(post("/api/candidate/login"), body)).andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();
            int ok = 0;
            int rejected = 0;
            for (var c : calls) {
                int status = c.get(15, java.util.concurrent.TimeUnit.SECONDS);
                if (status == 200) ok++;
                else if (status == 401) rejected++;
            }
            assertThat(ok).isEqualTo(n / 2);
            assertThat(rejected).isEqualTo(n / 2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void candidateAndStaffApisAreIsolated() throws Exception {
        publish();
        MockHttpSession c = candidate(ticket, code);
        for (String url : new String[] { "/api/banks", "/api/exams", "/api/units", "/api/auth/me", "/api/admin/staff",
                "/api/exams/" + exam + "/candidates" }) {
            mvc.perform(get(url).session(c)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/candidate/me").session(owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/candidate/paper").session(owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/candidate/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void fullAttempt_noAnswersSent_autosave_resume_submit_autoGrade() throws Exception {
        publish();
        MockHttpSession c = candidate(ticket, code);
        // No questions before starting
        mvc.perform(get("/api/candidate/paper").session(c)).andExpect(status().isConflict());

        mvc.perform(post("/api/candidate/start").with(csrf()).session(c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.submittedAt").isEmpty());
        var paper = mvc.perform(get("/api/candidate/paper").session(c)).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(4))
                .andExpect(jsonPath("$.questions[0].choices[1].text").value("2"))
                .andExpect(jsonPath("$.questions[2].blanks.length()").value(2))
                .andExpect(jsonPath("$.questions[3].wordLimit").value(500)).andReturn();
        // Nothing sent to the candidate may contain an answer
        String json = paper.getResponse().getContentAsString();
        assertThat(json).doesNotContain("correct", "answers\":[", "explanation", "rubric", "小學數學", "機密", "210");
        long remaining = ((Number) read(paper, "$.remainingSeconds")).longValue();
        assertThat(remaining).isBetween(1790L, 1800L);

        answer(c, "q1", "{\"choices\":[\"b\"]}", 200);
        answer(c, "q2", "{\"choices\":[\"a\"]}", 200);
        answer(c, "q2", "{\"choices\":[\"a\",\"b\"]}", 200);                    // change the answer
        answer(c, "q3", "{\"blanks\":{\"b1\":\" 39 \",\"b2\":\"211\"}}", 200);   // one blank right, one wrong
        answer(c, "q4", "{\"essay\":\"當 n=1 時成立…\"}", 200);
        // Invalid answers
        answer(c, "q1", "{\"choices\":[\"zzz\"]}", 400);
        answer(c, "q1", "{\"choices\":[\"a\",\"b\"]}", 400);                    // single choice cannot take two
        answer(c, "nope", "{\"choices\":[]}", 404);

        // The proctor sees the attempt in progress
        mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner)).andExpect(jsonPath("$[0].status").value("IN"))
                .andExpect(jsonPath("$[1].status").value("IDLE"));

        // Disconnect and sign in on another computer: resume where it was, the old session is kicked
        MockHttpSession c2 = candidate(ticket, code);
        mvc.perform(get("/api/candidate/me").session(c)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/candidate/start").with(csrf()).session(c2)).andExpect(status().isOk());
        mvc.perform(get("/api/candidate/paper").session(c2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.q1.choices[0]").value("b"))
                .andExpect(jsonPath("$.answers.q2.choices.length()").value(2))
                .andExpect(jsonPath("$.answers.q4.essay").value("當 n=1 時成立…"));
        mvc.perform(post("/api/candidate/leave").with(csrf()).session(c2)).andExpect(status().isNoContent());

        mvc.perform(post("/api/candidate/submit").with(csrf()).session(c2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.attempt.remainingSeconds").value(0));
        // After submitting: no edits, no questions
        answer(c2, "q1", "{\"choices\":[\"a\"]}", 409);
        mvc.perform(get("/api/candidate/paper").session(c2)).andExpect(status().isConflict());

        // Auto-grading: 5 + 6 + 3 (half the blanks) = 14, essays excluded
        assertThat(jdbc.queryForObject("select auto_score from attempt a join candidate c on c.id = a.candidate_id where c.ticket = ?",
                BigDecimal.class, ticket)).isEqualByComparingTo("14");
        assertThat(jdbc.queryForObject("select count(*) from answer where auto_score is null and question_id = 'q4'", Long.class))
                .isPositive();
        mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner)).andExpect(jsonPath("$[0].status").value("DONE"))
                .andExpect(jsonPath("$[0].leaveCount").value(1));
        mvc.perform(get("/api/exams/" + exam).session(owner)).andExpect(jsonPath("$.submitted").value(1));
    }

    @Test
    void extraTime_movesDeadline_needsReason_isCapped_notAfterSubmit() throws Exception {
        publish();
        String url = "/api/exams/" + exam + "/candidates/";
        long id = jdbc.queryForObject("select id from candidate where ticket = ?", Long.class, ticket);
        // Not started yet
        mvc.perform(json(post(url + id + "/extend"), "{\"minutes\":5,\"reason\":\"電腦當機\"}").session(owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_started"));

        MockHttpSession c = candidate(ticket, code);
        mvc.perform(post("/api/candidate/start").with(csrf()).session(c)).andExpect(status().isOk());
        // Less than a minute left
        jdbc.update("update attempt set deadline_at = now() + interval '30 seconds' where candidate_id = ?", id);
        mvc.perform(json(post(url + id + "/extend"), "{\"minutes\":5}").session(owner)).andExpect(status().isBadRequest());
        mvc.perform(json(post(url + id + "/extend"), "{\"minutes\":5,\"reason\":\"電腦當機\"}").session(c))
                .andExpect(status().is4xxClientError());
        mvc.perform(json(post(url + id + "/extend"), "{\"minutes\":5,\"reason\":\"電腦當機\"}").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.extraMinutes").value(5))
                .andExpect(jsonPath("$.deadlineAt").isNotEmpty());
        var me = mvc.perform(get("/api/candidate/me").session(c)).andReturn();
        assertThat(((Number) read(me, "$.attempt.remainingSeconds")).intValue()).isBetween(300, 330);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ATTEMPT_EXTENDED' and reason = '電腦當機'",
                Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner))
                .andExpect(jsonPath("$[0].extraMinutes").value(5)).andExpect(jsonPath("$[1].extraMinutes").value(0));

        // Capped at 120 minutes in total
        mvc.perform(json(post(url + id + "/extend"), "{\"minutes\":60,\"reason\":\"停電\"}").session(owner)).andExpect(status().isOk());
        mvc.perform(json(post(url + id + "/extend"), "{\"minutes\":60,\"reason\":\"停電\"}").session(owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("too_much"));

        mvc.perform(post("/api/candidate/submit").with(csrf()).session(c)).andExpect(status().isOk());
        mvc.perform(json(post(url + id + "/extend"), "{\"minutes\":5,\"reason\":\"電腦當機\"}").session(owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("done"));
    }

    @Test
    void severalCandidatesAtOnce_allOrNothing() throws Exception {
        publish();
        String url = "/api/exams/" + exam + "/candidates/";
        long id1 = jdbc.queryForObject("select id from candidate where ticket = ?", Long.class, ticket);
        long id2 = jdbc.queryForObject("select id from candidate where ticket = ?", Long.class, ticket2);
        String both = "\"ids\":[%d,%d]".formatted(id1, id2);
        mvc.perform(post("/api/candidate/start").with(csrf()).session(candidate(ticket, code))).andExpect(status().isOk());

        // The second has not started: nothing is extended, the error names who
        mvc.perform(json(post(url + "extend"), "{%s,\"minutes\":5,\"reason\":\"斷電\"}".formatted(both)).session(owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_started"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("李宜華（" + ticket2 + "）")));
        mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner)).andExpect(jsonPath("$[0].extraMinutes").value(0));

        mvc.perform(post("/api/candidate/start").with(csrf()).session(candidate(ticket2, code2))).andExpect(status().isOk());
        mvc.perform(json(post(url + "extend"), "{%s,\"minutes\":5,\"reason\":\"斷電\"}".formatted(both)).session(owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner)).andExpect(jsonPath("$[0].extraMinutes").value(5))
                .andExpect(jsonPath("$[1].extraMinutes").value(5));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'ATTEMPT_EXTENDED' and reason = '斷電'",
                Integer.class)).isEqualTo(2);

        mvc.perform(json(post(url + "reset-code"), "{%s}".formatted(both)).session(owner)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select login_code from candidate where id = ?", String.class, id1)).isNotEqualTo(code);
        assertThat(jdbc.queryForObject("select login_code from candidate where id = ?", String.class, id2)).isNotEqualTo(code2);

        // Both have started, so neither can be removed
        mvc.perform(json(post(url + "remove"), "{%s}".formatted(both)).session(owner)).andExpect(status().isConflict());
        mvc.perform(json(post(url + "remove"), "{\"ids\":[]}").session(owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void serverRefusesAfterDeadlineAndAutoSubmits() throws Exception {
        publish();
        MockHttpSession c = candidate(ticket, code);
        mvc.perform(post("/api/candidate/start").with(csrf()).session(c)).andExpect(status().isOk());
        answer(c, "q1", "{\"choices\":[\"b\"]}", 200);

        // Move the deadline a minute back (as if the client slowed its clock to write longer)
        jdbc.update("update attempt set deadline_at = now() - interval '1 minute' where candidate_id = (select id from candidate where ticket = ?)", ticket);
        answer(c, "q2", "{\"choices\":[\"a\",\"b\"]}", 409);

        attemptService.sweep();
        mvc.perform(get("/api/candidate/me").session(c)).andExpect(jsonPath("$.attempt.submittedAt").isNotEmpty());
        assertThat(jdbc.queryForObject("select auto_score from attempt a join candidate c on c.id = a.candidate_id where c.ticket = ?",
                BigDecimal.class, ticket)).isEqualByComparingTo("5");
        assertThat(jdbc.queryForObject("select auto_submitted from attempt a join candidate c on c.id = a.candidate_id where c.ticket = ?",
                Boolean.class, ticket)).isTrue();
    }

    @Test
    void endingTheExamSubmitsOpenAttempts_noStartAfterEnd() throws Exception {
        publish();
        MockHttpSession c = candidate(ticket, code);
        MockHttpSession late = candidate(ticket2, code2);
        mvc.perform(post("/api/candidate/start").with(csrf()).session(c)).andExpect(status().isOk());
        answer(c, "q1", "{\"choices\":[\"a\"]}", 200);

        mvc.perform(post("/api/exams/" + exam + "/end").with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(1));

        answer(c, "q1", "{\"choices\":[\"b\"]}", 409);
        mvc.perform(get("/api/candidate/me").session(c)).andExpect(jsonPath("$.attempt.submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.exam.status").value("ended"));
        mvc.perform(post("/api/candidate/start").with(csrf()).session(late)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ended"));
    }
}
