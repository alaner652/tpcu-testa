package tw.testa.ai;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tw.testa.ai.AiDtos.Job;
import tw.testa.ai.AiDtos.Status;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final QuestionImportService imports;

    public AiController(QuestionImportService imports) {
        this.imports = imports;
    }

    @GetMapping("/status")
    public Status status() {
        return imports.status();
    }

    /** Starts an import and returns at once; poll the job until it is done or failed. */
    @PostMapping("/question-import")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Job start(@RequestParam Long unitId, @RequestParam("file") MultipartFile file) throws IOException {
        return imports.start(unitId, file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/question-import/{id}")
    public Job get(@PathVariable String id) {
        return imports.get(id);
    }
}
