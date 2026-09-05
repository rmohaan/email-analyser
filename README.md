# Phi-4-mini Financial Email AI

Spring Boot service that sends a financial-services email to local Ollama `phi4-mini`, receives a schema-constrained response through Spring AI's structured-output mapping, then validates and normalizes extracted data.

## Prerequisites

- Java 25
- Maven 3.9+
- Ollama running locally

Pull the model once:

```bash
ollama pull phi4-mini
```

## Run

```bash
mvn spring-boot:run
```

The default endpoint is `POST http://localhost:8080/api/v1/email-analysis`. Upload the
email as a multipart field named `file`:

```bash
curl -X POST http://localhost:8080/api/v1/email-analysis \
  -F 'file=@/path/to/customer-email.eml;type=message/rfc822'
```

The service accepts `.eml` files up to 10 MB, extracts the subject and readable body,
ignores attachments, and sends the extracted content for analysis.

`entities.transactionDate` is always an object. Exact dates use the same value for
`fromDate` and `toDate`; relative periods such as "last week" and "last month" use
the corresponding previous calendar period, calculated from the email's reception date.
For `.eml` uploads, the service uses the topmost valid `Received` header,
then falls back to the message `Date` header and finally the upload date. "Last week"
means Monday through Sunday of the previous calendar week.

## Local-memory settings

`application.yml` defaults to `num-ctx: 4096`, `num-predict: 500`, and `temperature: 0.0`, intended as a conservative starting point for an 8-GB MacBook Air. Override `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, or `SERVER_PORT` if needed.

## Tests

```bash
mvn test
```

### Functional tests

The functional suite starts the API, uploads all 100 files from `email-samples`, invokes
the configured Ollama model, and validates each response against the expected intent and
entities in `src/test/resources/functional/expected-email-analysis.csv`.

Make sure Ollama is running and the configured model is available, then run:

```bash
RUN_FUNCTIONAL_TESTS=true mvn -Dtest=EmailAnalysisFunctionalTest test
```

Functional tests are disabled during a normal `mvn test` because they make 100 real model
requests and their runtime depends on the local Ollama configuration.
