package tw.testa.bank;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.testa.bank.BankDtos.BankDto;
import tw.testa.bank.BankDtos.CreateBankRequest;
import tw.testa.bank.BankDtos.SaveBankRequest;

@RestController
@RequestMapping("/api/banks")
public class BankController {
    private final BankService banks;

    public BankController(BankService banks) {
        this.banks = banks;
    }

    @GetMapping
    public List<BankDto> list() {
        return banks.list();
    }

    @GetMapping("/{id}")
    public BankDto get(@PathVariable Long id) {
        return banks.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankDto create(@Valid @RequestBody CreateBankRequest body) {
        return banks.create(body);
    }

    @PutMapping("/{id}")
    public BankDto save(@PathVariable Long id, @Valid @RequestBody SaveBankRequest body) {
        return banks.save(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        banks.delete(id);
    }
}
