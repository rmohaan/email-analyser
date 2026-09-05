# Phi-4 Mini Financial Email AI

Spring Boot service that accepts a financial-services email as an RFC/MIME `.eml`
file, extracts its readable content and reception date, asks a local Ollama
`phi4-mini` model to classify the request, validates the model output, and runs a
bounded post-classification workflow against mock downstream APIs.

## End-to-end processing

```text
.eml upload
    → file and MIME validation
    → subject/body extraction
    → email reception-date resolution
    → Phi-4 Mini classification and entity extraction
    → deterministic validation and normalization
    → primary downstream API selection
    → response-driven date/breadth workflow
    → analysis plus workflow audit response
```

The model classifies and extracts information. Java code owns validation, date
normalization, API selection, state transitions, search limits, and downstream
execution. The model cannot choose an endpoint or invoke an API directly.

## Prerequisites

- Java 25
- Maven 3.9+
- Ollama running locally
- The `phi4-mini` model installed in Ollama

Install and verify the model:

```bash
ollama pull phi4-mini
ollama list
```

The default Ollama endpoint is `http://localhost:11434`.

## Configuration

The defaults are defined in `src/main/resources/application.yml`.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama HTTP endpoint |
| `OLLAMA_MODEL` | `phi4-mini` | Ollama model name |
| `SERVER_PORT` | `8080` | Application HTTP port |
| `AI_RETRY_MAX_ATTEMPTS` | `3` | Maximum transient AI attempts |
| `AI_RETRY_INITIAL_INTERVAL` | `500ms` | Initial AI retry delay |
| `AI_RETRY_MAX_INTERVAL` | `2s` | Maximum AI retry delay |
| `MOCK_API_1_OUTCOME` | `MATCH_FOUND` | Mock API 1 payload scenario |
| `MOCK_API_2_OUTCOME` | `MATCH_FOUND` | Mock API 2 payload scenario |
| `MOCK_API_3_OUTCOME` | `MATCH_FOUND` | Mock API 3 payload scenario |
| `MOCK_API_4_OUTCOME` | `MATCH_FOUND` | Mock API 4 payload scenario |

The model uses `temperature: 0.0`, `num-ctx: 4096`, and
`num-predict: 500`, which are conservative defaults for local execution.

## Running the application

```bash
mvn spring-boot:run
```

The API is available at:

```text
POST http://localhost:8080/api/v1/email-analysis
Content-Type: multipart/form-data
```

Upload a file using the multipart field name `file`:

```bash
curl -X POST http://localhost:8080/api/v1/email-analysis \
  -F 'file=@src/main/resources/email-samples/sample-002.eml;type=message/rfc822'
```

The endpoint no longer accepts an `emailBody` JSON property.

## 1. Email reception and validation

The API accepts one non-empty file whose name ends with `.eml`.

- Maximum uploaded file size: 10 MB
- Maximum extracted subject/body size: 200,000 characters
- Maximum MIME nesting depth: 30
- Maximum MIME parts: 500
- Missing, empty, unsupported, and unreadable files return HTTP 400
- Oversized uploads return HTTP 413

The `EmlParser` uses Jakarta Mail to parse RFC/MIME messages.

## 2. MIME content extraction

The parser recursively walks the MIME part tree because a real email can contain
nested structures such as:

- `multipart/alternative` with plain-text and HTML versions
- `multipart/mixed` with a body and attachments
- Nested multipart sections
- Inline `message/rfc822` messages

The extraction rules are:

1. Ignore attachments.
2. Collect readable `text/plain` parts.
3. Use plain text when both plain text and HTML are available.
4. Fall back to cleaned HTML when no plain-text body exists.
5. Prefix the extracted body with the decoded subject when present.

Quoted reply history inside a plain-text or HTML body is currently retained as part
of the body. Separating the newest customer message from older conversation history
is a planned enhancement.

## 3. Email reception-date resolution

Relative expressions must be resolved from the email's reception date, not a static
model date. The parser uses this precedence:

1. Timestamp from the topmost valid `Received` header
2. Message `Date` header
3. Current upload date

IMAP `INTERNALDATE`, UID, and flags are mailbox metadata and are not normally
contained in a raw `.eml` file. A future direct IMAP integration should pass those
values separately when mailbox-level metadata is required.

## 4. Classification and entity extraction

The extracted subject and body, together with the resolved reception date, are sent
to Phi-4 Mini through Spring AI.

The model returns:

- Customer intent
- Confidence between 0 and 1
- PAN, folio, fund name, transaction type, date range, and transaction reference
- Routing category and subtype
- Short customer summary

### Transaction-date rules

`transactionDate` is always an object:

```json
{
  "transactionDate": {
    "fromDate": "2026-08-24",
    "toDate": "2026-08-30"
  }
}
```

- Exact date: `fromDate` and `toDate` are identical.
- Explicit date period: the supplied start and end dates are used.
- “Last week”: previous calendar Monday through Sunday.
- “Last month”: complete previous calendar month.
- No identifiable date: both properties are `null`.

If the model supplies only one boundary, validation uses it for both boundaries.
An inverted range is rejected.

### Model-output safeguards

Model output is treated as untrusted structured data:

- PAN values are normalized and validated.
- Unknown enum strings safely become `UNKNOWN` instead of causing JSON mapping
  failure.
- Routing category is derived from the normalized subtype in Java.
- Transaction type and routing subtype are cross-checked.
- Contradictory transaction and routing classifications are rejected.
- Statement-of-account, capital-gains-statement, and tax-statement requests cannot
  be routed as financial transactions, even if the model hallucinates a transaction
  type.

## 5. Deterministic API routing

| Canonical classification | Supported subtypes | Destination |
| --- | --- | --- |
| `FINANCIAL_TRANSACTION` | Purchase, redemption, switch | API 1 |
| `NFT_SIP` | SIP | API 2 |
| `NFT_STP_SWP_PROSPECT` | STP, SWP, prospect | API 3 |
| `NFT_MODIFICATION` | Nominee, address, bank, other modifications | API 4 |
| `UNKNOWN` | Unsupported or unrelated request | No API |

The current downstream client is a mock. Every invocation logs the selected API,
workflow identifier, phase, and search window, for example:

```text
API 1 called: workflowId=..., phase=INITIAL, fromDate=2026-08-24, toDate=2026-08-30
```

## 6. Response-driven workflow

The application currently uses an in-process Java state machine. It does **not** use
Temporal.

The initial downstream response controls every subsequent transition:

- `MATCH_FOUND`: complete immediately.
- `PARTIAL_MATCH`: return a partial result without broad searching.
- `NO_MATCH`: permit date expansion.
- `RETRYABLE_ERROR` or `FATAL_ERROR`: terminate as a technical failure.

A timeout, HTTP failure, or malformed response is never treated as “no results.”

### Common adapter response model

Each API-specific client must translate its native response into the same normalized
model before workflow reasoning occurs. A normalized record includes its stable ID,
transaction reference, PAN/folio/fund identifiers, canonical subtype, event date,
status, status reason, last-updated timestamp, and API-specific attributes.

Adapters map vendor-specific status strings into the canonical vocabulary:
`PROCESSED`, `COMPLETED`, `PENDING`, `IN_PROGRESS`, `FAILED`, `REJECTED`,
`CANCELLED`, `APPROVED`, `ACTIVE`, or `UNKNOWN`. `UNKNOWN` is not considered a
complete status answer.

The adapter response carries the selected API, HTTP status, whether a body was
present, normalized records, transport failure classification, error code, and a
diagnostic message. Adapters do not declare `MATCH_FOUND` or `NO_MATCH`.

`ApiResponseEvaluator` derives the outcome by applying these rules in order:

1. Validate the API identity, HTTP status, failure classification, body, and records
   schema.
2. Filter out records that conflict with the requested reference, PAN, folio,
   subtype, or search window.
3. Return `NO_MATCH` only when the response is valid and no relevant records remain.
4. Return `PARTIAL_MATCH` when relevant records exist but any lacks a usable status
   or required correlation data.
5. Return `MATCH_FOUND` when all relevant records contain enough information to
   answer the status request.

Status values do not control searching. For example, ten relevant records that are
all `PROCESSED` produce `MATCH_FOUND`; the workflow stops after that call because
“all processed” is a complete customer answer.

HTTP 408, 425, 429, 5xx responses, and explicitly retryable client failures become
`RETRYABLE_ERROR`. Other non-2xx responses, invalid API identity, invalid HTTP status,
missing successful response bodies, and missing/malformed record collections become
`FATAL_ERROR`.

### Initial search window

- Extracted date or date range when present
- Otherwise, reception date minus seven days through reception date

### Date expansion

After a valid initial `NO_MATCH`, the same API is searched using adjacent windows:

```text
Backward: initial.fromDate - 30 days → initial.fromDate - 1 day
Forward:  initial.toDate + 1 day   → initial.toDate + 30 days
```

The forward window is capped at the processing date and is omitted when it would be
entirely in the future.

### Specific request

A specific purchase, redemption, switch, SIP, STP, SWP, prospect, or modification
request searches only its selected API. If the initial and expanded windows return no
match, the workflow ends as `NOT_FOUND`.

### Ambiguous transaction request

A generic `TRANSACTION_STATUS` request with no recognized subtype:

1. Uses API 1 as the primary candidate.
2. Searches the initial window.
3. Searches API 1's backward and forward windows after `NO_MATCH`.
4. Searches the remaining API families only when all preceding searches returned
   valid `NO_MATCH` responses.
5. Stops on the first usable match.

The workflow permits at most six downstream calls.

### Workflow states

- `CLASSIFIED`
- `INITIAL_CALL`
- `DATE_EXPANSION`
- `BREADTH_SEARCH`
- `COMPLETED`
- `PARTIAL_RESULT`
- `NOT_FOUND`
- `TECHNICAL_FAILURE`
- `UNROUTABLE`

## 7. API response

The response contains the normalized model analysis and a workflow audit:

```json
{
  "analysis": {
    "intent": "PURCHASE_STATUS",
    "confidence": 0.92,
    "entities": {
      "pan": null,
      "folioNumber": "12345678",
      "fundName": "Example Equity Fund",
      "transactionType": "PURCHASE",
      "transactionDate": {
        "fromDate": "2026-08-24",
        "toDate": "2026-08-30"
      },
      "transactionReference": "TXN123"
    },
    "requestClassification": {
      "category": "FINANCIAL_TRANSACTION",
      "subtype": "PURCHASE"
    },
    "customerSummary": "Customer requests the status of a purchase."
  },
  "workflow": {
    "workflowId": "generated-uuid",
    "finalState": "COMPLETED",
    "outcome": "MATCH_FOUND",
    "selectedApi": "API_1",
    "downstreamResponse": {
      "api": "API_1",
      "httpStatus": 200,
      "responseBodyPresent": true,
      "records": [
        {
          "recordId": "TXN123",
          "transactionReference": "TXN123",
          "subtype": "PURCHASE",
          "eventDate": "2026-08-30",
          "status": "PROCESSED"
        }
      ],
      "failureType": "NONE",
      "errorCode": null,
      "message": "Mock response containing a completed record"
    },
    "stateHistory": [
      "CLASSIFIED",
      "INITIAL_CALL",
      "COMPLETED"
    ],
    "attempts": [
      {
        "api": "API_1",
        "phase": "INITIAL",
        "window": {
          "fromDate": "2026-08-24",
          "toDate": "2026-08-30"
        },
        "outcome": "MATCH_FOUND",
        "relevantRecordCount": 1,
        "reason": "1 relevant record(s) contained a usable status"
      }
    ]
  }
}
```

## Mocking adapter payloads

The following configuration values select payload scenarios; they do not bypass the
response evaluator:

- `MATCH_FOUND`
- `NO_MATCH`
- `PARTIAL_MATCH`
- `RETRYABLE_ERROR`
- `FATAL_ERROR`

Example configuration that makes API 1 and API 2 return no match while API 3 finds a
result:

```bash
MOCK_API_1_OUTCOME=NO_MATCH \
MOCK_API_2_OUTCOME=NO_MATCH \
MOCK_API_3_OUTCOME=MATCH_FOUND \
mvn spring-boot:run
```

Mock scenarios are fixed per API for the lifetime of that application process.
`MATCH_FOUND` creates a relevant `PROCESSED` record, `PARTIAL_MATCH` creates a relevant
record without status, and `NO_MATCH` creates an empty record list. Error scenarios
create corresponding transport/HTTP failures. Workflow unit tests use scripted
payloads to test phase-specific behavior.

## Failure handling

| Stage | Failure | Result |
| --- | --- | --- |
| Upload | Missing/empty/wrong extension/malformed multipart | HTTP 400 |
| Upload | File larger than 10 MB | HTTP 413 or guarded HTTP 400 |
| MIME parsing | Unreadable body, excessive depth/parts, decoding failure | HTTP 400 |
| AI invocation/parsing | Ollama unavailable or unparseable structured output | HTTP 502 |
| Extraction validation | Invalid PAN, confidence, date range, or conflicting classification | HTTP 422 |
| Routing | Unsupported validated classification | `UNROUTABLE`, no downstream call |
| Downstream transport | Timeout or explicitly retryable client failure | `TECHNICAL_FAILURE` |
| Response contract | Wrong API, invalid status/body/records schema | `TECHNICAL_FAILURE` |
| Valid empty response | No relevant records | Date expansion; breadth search only when ambiguous |
| Relevant incomplete response | Missing answer fields | `PARTIAL_RESULT`, no expansion |
| Relevant complete response | Any usable status, including all `PROCESSED` | `COMPLETED`, no expansion |

Unexpected runtime exceptions are contained at two final boundaries. A downstream
adapter/evaluator exception becomes a workflow `TECHNICAL_FAILURE`; an unexpected
request-level exception becomes a sanitized HTTP 500 response while its full stack
trace is retained only in server logs. JVM-fatal conditions such as out-of-memory
errors are deliberately not swallowed.

Transient Ollama calls are limited to three attempts with bounded exponential
backoff. Spring's default error output is configured not to expose exception names,
messages, binding details, or stack traces.

## Tests

### Unit and application tests

```bash
mvn test
```

The model-backed functional test is skipped unless `RUN_FUNCTIONAL_TESTS=true`.

### Run one functional test

The functional suite deliberately executes exactly one `.eml` sample per run. It
starts the application on a random port, uploads the selected file through the real
HTTP endpoint, invokes the configured Ollama model, and validates the response against:

```text
src/test/resources/functional/expected-email-analysis.csv
```

Run the default `sample-001.eml` test:

```bash
RUN_FUNCTIONAL_TESTS=true \
mvn -Dtest=EmailAnalysisFunctionalTest test
```

### Run a selected functional sample

Use `FUNCTIONAL_SAMPLE` to choose one of the 100 samples:

```bash
RUN_FUNCTIONAL_TESTS=true \
FUNCTIONAL_SAMPLE=sample-002.eml \
mvn -Dtest=EmailAnalysisFunctionalTest test
```

Only the selected sample is executed. An unknown filename fails with a clear
“No functional expectation found” error.

## Synthetic email samples

The project contains 100 synthetic messages under:

```text
src/main/resources/email-samples
```

The samples cover multiple financial intents, routing classifications, date phrases,
plain text, HTML, multipart alternatives, and attachment-containing messages. They use
RFC-style CRLF line endings and synthetic `Return-Path`, `Received`, `Date`, and
`Message-ID` headers.

## Design diagrams

- Editable Mermaid source:
  `src/main/resources/design-flow-chart/post-classification-workflow.mmd`
- Rendered SVG:
  `src/main/resources/design-flow-chart/post-classification-workflow.svg`

## Current limitations and planned enhancements

- Downstream APIs are mocked.
- The workflow is synchronous and in-process; it is not durable across application
  restarts.
- Temporal is not currently used. It is a future option for durable retries,
  long-running execution, recovery, and human intervention.
- Quoted email conversation history is not yet separated into customer/company turns.
- Direct IMAP fetching and IMAP mailbox metadata are not implemented.
- Functional output can vary because Phi-4 Mini is a generative model; deterministic
  validators guard routing and schema boundaries.
