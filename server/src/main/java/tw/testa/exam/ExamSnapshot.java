package tw.testa.exam;

import java.util.List;

import tw.testa.bank.BankDtos.QuestionDto;

/** The paper frozen at publish time (with answers; never send it to candidates as a whole). */
public record ExamSnapshot(String bankName, List<QuestionDto> questions) {}
