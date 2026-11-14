package tw.testa.grading;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.testa.grading.GradingDtos.GraderInfo;
import tw.testa.grading.GradingDtos.GradingExam;
import tw.testa.grading.GradingDtos.Item;
import tw.testa.grading.GradingDtos.ReopenRequest;
import tw.testa.grading.GradingDtos.Results;
import tw.testa.grading.GradingDtos.ScoreRequest;
import tw.testa.grading.GradingDtos.SetGradersRequest;

@RestController
@RequestMapping("/api")
public class GradingController {
    private final GradingService grading;

    public GradingController(GradingService grading) {
        this.grading = grading;
    }

    // 評分者
    @GetMapping("/grading/exams")
    public List<GradingExam> myExams() {
        return grading.myExams();
    }

    @GetMapping("/grading/exams/{examId}/items")
    public List<Item> items(@PathVariable Long examId) {
        return grading.items(examId);
    }

    @PutMapping("/grading/exams/{examId}/items/{anonId}/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void score(@PathVariable Long examId, @PathVariable String anonId, @PathVariable String questionId,
            @Valid @RequestBody ScoreRequest body) {
        grading.score(examId, anonId, questionId, body.score(), body.comment());
    }

    // 承辦人
    @GetMapping("/exams/{examId}/graders")
    public List<GraderInfo> graders(@PathVariable Long examId) {
        return grading.graders(examId);
    }

    @PutMapping("/exams/{examId}/graders")
    public List<GraderInfo> setGraders(@PathVariable Long examId, @Valid @RequestBody SetGradersRequest body) {
        return grading.setGraders(examId, body.staffIds());
    }

    @GetMapping("/exams/{examId}/results")
    public Results results(@PathVariable Long examId) {
        return grading.results(examId);
    }

    @PostMapping("/exams/{examId}/results/publish")
    public Results publish(@PathVariable Long examId) {
        return grading.publish(examId);
    }

    @PostMapping("/exams/{examId}/grading/reopen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopen(@PathVariable Long examId, @Valid @RequestBody ReopenRequest body) {
        grading.reopen(examId, body.anonId(), body.questionId(), body.reason());
    }

    @GetMapping("/exams/{examId}/results.csv")
    public ResponseEntity<byte[]> export(@PathVariable Long examId) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"results-" + examId + ".csv\"")
                .body(grading.exportCsv(examId).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/exams/{examId}/results.xlsx")
    public ResponseEntity<byte[]> exportXlsx(@PathVariable Long examId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"results-" + examId + ".xlsx\"")
                .body(grading.exportXlsx(examId));
    }
}
