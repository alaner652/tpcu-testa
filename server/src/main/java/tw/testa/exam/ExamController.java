package tw.testa.exam;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import tw.testa.exam.ExamDtos.CandidateDto;
import tw.testa.exam.ExamDtos.CodeDto;
import tw.testa.exam.ExamDtos.CreateExamRequest;
import tw.testa.exam.ExamDtos.ExamDto;
import tw.testa.exam.ExamDtos.ImportRequest;
import tw.testa.exam.ExamDtos.ImportResult;
import tw.testa.exam.ExamDtos.RosterPreview;
import tw.testa.exam.ExamDtos.UpdateExamRequest;

@RestController
@RequestMapping("/api/exams")
public class ExamController {
    private final ExamService exams;

    public ExamController(ExamService exams) {
        this.exams = exams;
    }

    @GetMapping
    public List<ExamDto> list() {
        return exams.list();
    }

    @GetMapping("/{id}")
    public ExamDto get(@PathVariable Long id) {
        return exams.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExamDto create(@Valid @RequestBody CreateExamRequest body) {
        return exams.create(body);
    }

    @PatchMapping("/{id}")
    public ExamDto update(@PathVariable Long id, @Valid @RequestBody UpdateExamRequest body) {
        return exams.update(id, body);
    }

    @PostMapping("/{id}/publish")
    public ExamDto publish(@PathVariable Long id) {
        return exams.publish(id);
    }

    @PostMapping("/{id}/withdraw")
    public ExamDto withdraw(@PathVariable Long id) {
        return exams.withdraw(id);
    }

    @PostMapping("/{id}/end")
    public ExamDto end(@PathVariable Long id) {
        return exams.end(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        exams.delete(id);
    }

    @GetMapping("/{id}/candidates")
    public List<CandidateDto> candidates(@PathVariable Long id) {
        return exams.candidates(id);
    }

    /** 第一步：上傳名單檔案，回傳檢查結果（不寫入）。 */
    @PostMapping("/{id}/candidates/preview")
    public RosterPreview preview(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        return exams.preview(id, file.getOriginalFilename(), file.getBytes());
    }

    /** 第二步：確認後匯入。 */
    @PostMapping("/{id}/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult importRows(@PathVariable Long id, @Valid @RequestBody ImportRequest body) {
        return exams.importRows(id, body.rows());
    }

    @PostMapping("/{id}/candidates/{candidateId}/reset-code")
    public CodeDto resetCode(@PathVariable Long id, @PathVariable Long candidateId) {
        return new CodeDto(exams.resetCode(id, candidateId));
    }

    @DeleteMapping("/{id}/candidates/{candidateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCandidate(@PathVariable Long id, @PathVariable Long candidateId) {
        exams.removeCandidate(id, candidateId);
    }
}
