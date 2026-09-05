package com.example.financialemail.api;

import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.service.EmlParser;
import com.example.financialemail.service.EmailAnalysisService;
import com.example.financialemail.service.ParsedEmail;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/email-analysis")
public class EmailAnalysisController {
    private final EmailAnalysisService emailAnalysisService;
    private final EmlParser emlParser;

    public EmailAnalysisController(EmailAnalysisService emailAnalysisService, EmlParser emlParser) {
        this.emailAnalysisService = emailAnalysisService;
        this.emlParser = emlParser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmailAnalysis> analyze(@RequestParam("file") MultipartFile file) {
        ParsedEmail email = emlParser.parse(file);
        return ResponseEntity.ok(emailAnalysisService.analyze(email.content(), email.receptionDate()));
    }
}
