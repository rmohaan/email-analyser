package com.example.financialemail.functional;

import com.example.financialemail.domain.EmailAnalysis;
import com.example.financialemail.domain.EmailProcessingResult;
import com.example.financialemail.workflow.WorkflowState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Tag("functional")
@EnabledIfEnvironmentVariable(named = "RUN_FUNCTIONAL_TESTS", matches = "(?i)true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmailAnalysisFunctionalTest {
    private static final Path SAMPLES = Path.of("src/main/resources/email-samples");
    private static final Path EXPECTATIONS = Path.of(
            "src/test/resources/functional/expected-email-analysis.csv");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    static Stream<ExpectedAnalysis> expectedAnalyses() throws IOException {
        List<String> lines = Files.readAllLines(EXPECTATIONS);
        assertThat(lines).as("functional expectation rows plus header").hasSize(101);
        String selectedSample = System.getenv().getOrDefault(
                "FUNCTIONAL_SAMPLE", "sample-001.eml");
        ExpectedAnalysis expectation = lines.stream()
                .skip(1)
                .map(ExpectedAnalysis::fromCsv)
                .filter(expected -> expected.filename().equals(selectedSample))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No functional expectation found for " + selectedSample));
        return Stream.of(expectation);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("expectedAnalyses")
    void analyzesUploadedEmlFile(ExpectedAnalysis expected) {
        Path eml = SAMPLES.resolve(expected.filename());
        assertThat(eml).exists();

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType("message/rfc822"));
        HttpEntity<FileSystemResource> filePart = new HttpEntity<>(new FileSystemResource(eml), fileHeaders);

        MultiValueMap<String, Object> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("file", filePart);
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<EmailProcessingResult> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/email-analysis",
                HttpMethod.POST,
                new HttpEntity<>(requestBody, requestHeaders),
                EmailProcessingResult.class);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("HTTP response for %s: %s", expected.filename(), response)
                .isTrue();

        EmailProcessingResult processingResult = Objects.requireNonNull(response.getBody());
        EmailAnalysis actual = processingResult.analysis();
        assertSoftly(softly -> {
            softly.assertThat(actual.intent().name()).as("intent").isEqualTo(expected.intent());
            softly.assertThat(actual.confidence()).as("confidence").isBetween(0.0, 1.0);
            softly.assertThat(actual.customerSummary()).as("customerSummary").isNotBlank();
            softly.assertThat(actual.entities().pan()).as("pan").isEqualTo(expected.pan());
            softly.assertThat(actual.entities().folioNumber()).as("folioNumber").isEqualTo(expected.folio());
            softly.assertThat(actual.entities().fundName()).as("fundName").isEqualTo(expected.fundName());
            softly.assertThat(actual.entities().transactionType().name()).as("transactionType")
                    .isEqualTo(expected.transactionType());
            softly.assertThat(actual.entities().transactionDate().fromDate()).as("fromDate")
                    .isEqualTo(expected.fromDate());
            softly.assertThat(actual.entities().transactionDate().toDate()).as("toDate")
                    .isEqualTo(expected.toDate());
            softly.assertThat(actual.entities().transactionReference()).as("transactionReference")
                    .isEqualTo(expected.transactionReference());
            softly.assertThat(actual.requestClassification().category().name()).as("request category")
                    .isEqualTo(expected.requestCategory());
            softly.assertThat(actual.requestClassification().subtype().name()).as("request subtype")
                    .isEqualTo(expected.requestSubtype());
            WorkflowState expectedState = "UNKNOWN".equals(expected.requestCategory())
                    ? WorkflowState.UNROUTABLE : WorkflowState.COMPLETED;
            softly.assertThat(processingResult.workflow().finalState()).as("workflow final state")
                    .isEqualTo(expectedState);
        });
    }

    record ExpectedAnalysis(
            String filename,
            String intent,
            String pan,
            String folio,
            String fundName,
            String transactionType,
            LocalDate fromDate,
            LocalDate toDate,
            String transactionReference,
            String requestCategory,
            String requestSubtype) {

        static ExpectedAnalysis fromCsv(String line) {
            String[] values = line.split(",", -1);
            if (values.length != 11) {
                throw new IllegalArgumentException("Invalid functional expectation: " + line);
            }
            return new ExpectedAnalysis(values[0], values[1], nullIfBlank(values[2]),
                    nullIfBlank(values[3]), nullIfBlank(values[4]), values[5],
                    dateIfPresent(values[6]), dateIfPresent(values[7]), nullIfBlank(values[8]),
                    values[9], values[10]);
        }

        private static String nullIfBlank(String value) {
            return value.isBlank() ? null : value;
        }

        private static LocalDate dateIfPresent(String value) {
            return value.isBlank() ? null : LocalDate.parse(value);
        }

        @Override
        public String toString() {
            return filename;
        }
    }
}
