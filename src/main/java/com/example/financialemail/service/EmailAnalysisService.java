package com.example.financialemail.service;

import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.EmailProcessingResult;
import com.example.financialemail.domain.ExtractedEntities;
import com.example.financialemail.domain.TransactionDate;
import com.example.financialemail.workflow.PostClassificationWorkflow;
import com.example.financialemail.validation.ExtractionValidator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class EmailAnalysisService {
    private final ChatClient chatClient;
    private final String systemPrompt;
    private final ExtractionValidator extractionValidator;
    private final RelativeTransactionDateResolver relativeDateResolver;
    private final PostClassificationWorkflow postClassificationWorkflow;
    private final Clock clock;

    public EmailAnalysisService(ChatClient chatClient, String financialEmailSystemPrompt,
                                ExtractionValidator extractionValidator,
                                RelativeTransactionDateResolver relativeDateResolver,
                                PostClassificationWorkflow postClassificationWorkflow,
                                Clock clock) {
        this.chatClient = chatClient;
        this.systemPrompt = financialEmailSystemPrompt;
        this.extractionValidator = extractionValidator;
        this.relativeDateResolver = relativeDateResolver;
        this.postClassificationWorkflow = postClassificationWorkflow;
        this.clock = clock;
    }

    public EmailProcessingResult analyze(String emailBody, LocalDate receptionDate) {
        if (emailBody == null || emailBody.isBlank() || receptionDate == null) {
            throw new IllegalArgumentException("Email content and reception date are required");
        }
        EmailAnalysis response;
        try {
            response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(user -> user.text("""
                                    Analysis date: {analysisDate}
                                    Customer email to classify and extract:
                                    {emailBody}""")
                            .param("analysisDate", receptionDate.toString())
                            .param("emailBody", emailBody))
                    .call()
                    .entity(EmailAnalysis.class);
        } catch (RuntimeException exception) {
            throw new AiAnalysisException(
                    "The AI service failed to return a parseable analysis", exception);
        }
        EmailAnalysis validated = extractionValidator.validateAndNormalize(
                applyRelativeTransactionDate(response, emailBody, receptionDate));
        return new EmailProcessingResult(validated,
                postClassificationWorkflow.execute(validated, receptionDate, LocalDate.now(clock)));
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
                analysis.requestClassification(),
                analysis.customerSummary());
    }
}
