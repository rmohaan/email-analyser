package com.example.financialemail.service;

import com.example.financialemail.api.InvalidEmailFileException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmlParserTest {
    private final EmlParser parser = new EmlParser(Clock.fixed(
            Instant.parse("2026-09-05T00:00:00Z"), ZoneId.of("Asia/Kolkata")));

    @Test
    void extractsTheSubjectAndPlainTextBody() {
        MockMultipartFile file = eml("purchase.eml", """
                From: customer@example.com
                To: support@example.com
                Subject: Purchase status
                Content-Type: text/plain; charset=UTF-8

                I purchased 310 units last week. What is the current status?
                """);

        assertThat(parser.parse(file).content()).isEqualTo("""
                Subject: Purchase status

                I purchased 310 units last week. What is the current status?""");
    }

    @Test
    void prefersPlainTextOverHtmlInAMultipartAlternative() {
        MockMultipartFile file = eml("purchase.eml", """
                MIME-Version: 1.0
                Content-Type: multipart/alternative; boundary=boundary

                --boundary
                Content-Type: text/plain; charset=UTF-8

                Plain purchase request
                --boundary
                Content-Type: text/html; charset=UTF-8

                <p>HTML purchase request</p>
                --boundary--
                """);

        assertThat(parser.parse(file).content()).isEqualTo("Plain purchase request");
    }

    @Test
    void rejectsFilesWithoutAnEmlExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "message.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(InvalidEmailFileException.class)
                .hasMessage("Only .eml files are supported");
    }

    @Test
    void allBundledEmailSamplesAreReadable() throws Exception {
        Path samplesDirectory = Path.of("src/main/resources/email-samples");
        List<Path> samples;
        try (var paths = Files.list(samplesDirectory)) {
            samples = paths.filter(path -> path.getFileName().toString().endsWith(".eml"))
                    .sorted()
                    .toList();
        }

        assertThat(samples).hasSize(100);
        for (Path sample : samples) {
            MockMultipartFile file = new MockMultipartFile(
                    "file", sample.getFileName().toString(), "message/rfc822", Files.readAllBytes(sample));
            assertThat(parser.parse(file).content()).as(sample.getFileName().toString()).isNotBlank();
        }
    }

    @Test
    void usesTheTopmostReceivedHeaderAsTheReceptionDate() {
        MockMultipartFile file = eml("purchase.eml", """
                Received: from outbound.example.com by mx.support.example.com;
                 Fri, 4 Sep 2026 23:55:00 -0400
                Received: from client.example.com by outbound.example.com;
                 Fri, 4 Sep 2026 20:00:00 -0400
                Date: Thu, 3 Sep 2026 10:00:00 +0530
                Content-Type: text/plain; charset=UTF-8

                I purchased units last week.
                """);

        assertThat(parser.parse(file).receptionDate()).isEqualTo(LocalDate.of(2026, 9, 4));
    }

    @Test
    void fallsBackToTheMessageDateWhenReceivedIsMissing() {
        MockMultipartFile file = eml("purchase.eml", """
                Date: Thu, 3 Sep 2026 10:00:00 +0530
                Content-Type: text/plain; charset=UTF-8

                I purchased units last week.
                """);

        assertThat(parser.parse(file).receptionDate()).isEqualTo(LocalDate.of(2026, 9, 3));
    }

    private MockMultipartFile eml(String filename, String contents) {
        return new MockMultipartFile(
                "file", filename, "message/rfc822", contents.getBytes(StandardCharsets.UTF_8));
    }
}
