package com.example.financialemail.api;

import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.service.EmailAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/email-analysis")
public class EmailAnalysisController {
    private final EmailAnalysisService emailAnalysisService;

    public EmailAnalysisController(EmailAnalysisService emailAnalysisService) {
        this.emailAnalysisService = emailAnalysisService;
    }

    @PostMapping
    public ResponseEntity<EmailAnalysis> analyze(@Valid @RequestBody EmailAnalysisRequest request) {
        return ResponseEntity.ok(emailAnalysisService.analyze(request.emailBody()));
    }
}
