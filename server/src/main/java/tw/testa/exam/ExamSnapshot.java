package tw.testa.exam;

import java.util.List;

import tw.testa.bank.BankDtos.QuestionDto;

/** 發布時凍結的試卷內容（含答案，絕對不能整包送到考生端）。 */
public record ExamSnapshot(String bankName, List<QuestionDto> questions) {}
