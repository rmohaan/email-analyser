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

The default endpoint is `POST http://localhost:8080/api/v1/email-analysis`.

```bash
curl -X POST http://localhost:8080/api/v1/email-analysis \
  -H 'Content-Type: application/json' \
  -d '{"emailBody":"I redeemed units from HDFC Balanced Advantage Fund on 2026-08-15. My PAN is ABCDE1234F and folio is 12345678. Please share the status."}'
```

`entities.transactionDate` is always an object. Exact dates use the same value for
`fromDate` and `toDate`; relative periods such as "last week" and "last month" use
the corresponding previous calendar period, calculated from the date the API receives
the email. "Last week" means Monday through Sunday of the previous calendar week.

## Local-memory settings

`application.yml` defaults to `num-ctx: 4096`, `num-predict: 500`, and `temperature: 0.0`, intended as a conservative starting point for an 8-GB MacBook Air. Override `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, or `SERVER_PORT` if needed.

## Tests

```bash
mvn test
```
