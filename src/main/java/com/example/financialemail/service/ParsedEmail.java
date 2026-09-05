package com.example.financialemail.service;

import java.time.LocalDate;

public record ParsedEmail(
        String content,
        LocalDate receptionDate) {
}
