package com.example.financialemail.service;

import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.ExtractedEntities;
import com.example.financialemail.domain.TransactionDate;
import com.example.financialemail.validation.ExtractionValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class EmailAnalysisService {
    private final ChatClient chatClient;
    private final String systemPrompt;
    private final ExtractionValidator extractionValidator;
    private final RelativeTransactionDateResolver relativeDateResolver;

    public EmailAnalysisService(ChatClient chatClient, String financialEmailSystemPrompt,
                                ExtractionValidator extractionValidator,
                                RelativeTransactionDateResolver relativeDateResolver) {
        this.chatClient = chatClient;
        this.systemPrompt = financialEmailSystemPrompt;
        this.extractionValidator = extractionValidator;
        this.relativeDateResolver = relativeDateResolver;
    }

    public EmailAnalysis analyze(String emailBody, LocalDate receptionDate) {
        EmailAnalysis response = chatClient.prompt()
                .system(systemPrompt)
                .user(user -> user.text("""
                                Analysis date: {analysisDate}
                                Customer email to classify and extract:
                                {emailBody}""")
                        .param("analysisDate", receptionDate.toString())
                        .param("emailBody", emailBody))
                .call()
                .entity(EmailAnalysis.class);
        return extractionValidator.validateAndNormalize(
                applyRelativeTransactionDate(response, emailBody, receptionDate));
    }

    private EmailAnalysis applyRelativeTransactionDate(EmailAnalysis analysis, String emailBody,
                                                       LocalDate receptionDate) {
        if (analysis == null || analysis.entities() == null) {
            return analysis;
        }

        TransactionDate resolvedDate = relativeDateResolver.resolve(emailBody, receptionDate).orElse(null);
        if (resolvedDate == null) {
            return analysis;
        }

        ExtractedEntities entities = analysis.entities();
        ExtractedEntities resolvedEntities = new ExtractedEntities(
                entities.pan(), entities.folioNumber(), entities.fundName(), entities.transactionType(),
                resolvedDate, entities.transactionReference());
        return new EmailAnalysis(analysis.intent(), analysis.confidence(), resolvedEntities,
                analysis.customerSummary());
    }
}
