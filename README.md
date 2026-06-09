# Azure AI Decision Pipeline

A full-stack, microservice-based AI decision support system that combines deterministic risk scoring, LLM-powered explainability, and DevOps observability.

The project demonstrates how an AI-enabled system can be structured so that the core decision remains deterministic, auditable, and rule-based while the LLM is used as a constrained explanation layer.

---

## Project Overview

This project simulates a production-style decision pipeline where:

* Applicant inputs are submitted through a React frontend
* A Spring Boot decision engine applies deterministic scoring logic
* A FastAPI AI explanation service generates natural language explanations and suggestions
* OpenAI is used as the current LLM provider
* Prometheus collects application metrics
* Grafana visualizes service health, request volume, errors, and latency
* Docker Compose runs the full local stack

The main design principle is separation of responsibility:

```text
The decision engine makes the decision.
The LLM explains the decision.
The observability layer helps operate and debug the system.
```

This is intentionally not a simple GPT wrapper. The LLM is not responsible for approving or rejecting applicants. The deterministic decision is produced first, then the LLM receives structured decision data and generates an explanation.

---

## Recent Demo Hardening Changes

This iteration focused on making the project reliable for a live technical demo and interview walkthrough.

### Reasoning

Three demo risks were addressed:

1. **Unreliable local startup** — The Spring Boot Docker image previously required a pre-built JAR in `target/`, which is gitignored. A fresh clone could fail on `docker compose up --build` unless Maven was run manually first.
2. **Weak decision/explanation separation** — The interview narrative depends on proving that deterministic rules make the decision and the LLM only explains it. The pipeline needed a clearer structured contract between services and stricter LLM guardrails.
3. **Inconsistent demo experience** — Browser launch depended on the Windows default handler (opening Arc instead of Chrome), and the UI used different explanation labels than the demo script.

### Process

1. **Docker build reliability** — Converted `services/decision-engine/Dockerfile` to a multi-stage build that runs `./mvnw clean package -DskipTests` inside Docker and copies the JAR into a JRE runtime image.
2. **Deterministic decision extraction** — Moved scoring and hard-stop logic into `DecisionEngine.java` and introduced `DecisionContext` as the structured decision artifact passed to the AI layer.
3. **Constrained multi-step LLM workflow** — Updated FastAPI to accept full `DecisionContext`, run separate explanation and recommendation LLM calls, validate strict JSON for each step, and return `llmStatus` (`success` or `fallback`).
4. **Frontend and script alignment** — Updated the result card to show `LLM-generated explanation` or `Rule-based fallback explanation`, display `decisionContext` fields, and aligned `full-demo.sh` expected output with the UI.
5. **Demo launcher polish** — Updated `full-demo.sh` to open all demo URLs in Google Chrome when available, regardless of the OS default browser.

### Resulting Changes

| Area | Files | What changed |
| ---- | ----- | ------------ |
| Docker build | `services/decision-engine/Dockerfile` | Multi-stage Maven build; no host `mvn package` required |
| Decision engine | `DecisionEngine.java`, `DecisionContext.java`, `EligibilityService.java`, `EligibilityResponse.java` | Structured decision context, reason codes, next-step category, `llmStatus` in API response |
| AI explanation | `services/ai-service/main.py`, `services/ai-service/llm.py` | Two-step LLM prompts (explanation + recommendations), forbidden-field validation, rule-based fallbacks |
| Frontend | `frontend/src/App.jsx` | Shows decision context, recommendations, and consistent explanation labels |
| Demo script | `scripts/full-demo.sh` | Chrome-only browser launch and updated expected UI text |

From a clean clone, the supported startup path is:

```bash
docker compose up -d --build
./scripts/full-demo.sh
```

No manual Maven packaging step is required before Docker Compose.

---

## Local Architecture

```text
User
 |
 v
React Frontend
 |
 | applicant input
 v
Spring Boot Decision Engine
 |
 | decision, risk score, reasons
 v
FastAPI AI Explanation Service
 |
 | constrained LLM prompt
 v
OpenAI API

FastAPI /metrics
 |
 v
Prometheus
 |
 v
Grafana
```

---

## Implemented Local Services

| Service                        | Purpose                                          | Local URL             |
| ------------------------------ | ------------------------------------------------ | --------------------- |
| Frontend                       | React UI for applicant input and decision output | http://localhost:3000 |
| Spring Boot Decision Engine    | Deterministic decisioning and scoring            | http://localhost:8080 |
| FastAPI AI Explanation Service | LLM explanation generation and fallback handling | http://localhost:8000 |
| Prometheus                     | Raw metrics scraping and querying                | http://localhost:9090 |
| Grafana                        | Observability dashboard                          | http://localhost:3001 |

---

## Service Responsibilities

### Frontend UI

The frontend provides the user-facing experience.

Responsibilities:

* Collect applicant input:

  * income
  * credit score
  * employment status
* Send requests to the Spring Boot decision engine
* Display:

  * decision
  * risk score
  * key decision factors
  * explanation
  * suggestions
  * explanation source

The frontend does not call the LLM directly. It calls the backend decision engine.

---

### Spring Boot Decision Engine

The Spring Boot service owns the deterministic decisioning logic.

Responsibilities:

* Expose the `/api/evaluate` endpoint
* Validate applicant input
* Delegate deterministic evaluation to `DecisionEngine`
* Apply scoring rules and hard-stop rejection rules
* Produce a structured `DecisionContext` containing:

  * `decision`
  * `status` (`EVALUATED` or `HARD_STOP`)
  * `reasonCodes`
  * `ruleFactors`
  * `score`
  * `nextStepCategory`
* Call the FastAPI AI explanation service with the full `DecisionContext`
* Return a combined response to the frontend
* Provide rule-based fallback output if the FastAPI service is unavailable

The Spring Boot service is the decision authority. The LLM does not make the decision.

---

### FastAPI AI Explanation Service

The FastAPI service owns the AI explanation layer.

Responsibilities:

* Expose the `/analyze` endpoint
* Accept the full `DecisionContext` from Spring Boot
* Run a two-step constrained LLM workflow:

  1. Explanation prompt — explain why the decision was already made
  2. Recommendation prompt — suggest practical next steps
* Validate that each LLM response is strict JSON with only the allowed fields
* Reject LLM output that attempts to return decision fields (`decision`, `status`, `reasonCodes`, etc.)
* Return:

  * explanation
  * recommendations
  * source
  * `llmStatus` (`success` or `fallback`)
* Provide rule-based fallback behavior when the LLM call fails or returns invalid output

The AI service is intentionally constrained. It receives decision context and generates an explanation, but it does not control the final decision.

---

## Decision Engine

The decision engine evaluates applicants using deterministic scoring and hard-stop rules.

Example input:

```json
{
  "income": 65000,
  "creditScore": 705,
  "employmentStatus": "EMPLOYED"
}
```

Example output:

```json
{
  "decisionContext": {
    "decision": "REVIEW",
    "status": "EVALUATED",
    "reasonCodes": ["CREDIT_MODERATE", "INCOME_BELOW_PREFERRED"],
    "ruleFactors": [
      "Credit score is in the moderate range (700-749)",
      "Income is below preferred threshold (60000-74999)"
    ],
    "score": 82.0,
    "nextStepCategory": "MANUAL_REVIEW"
  },
  "decision": "REVIEW",
  "riskScore": 82.0,
  "reasons": [
    "Credit score is in the moderate range (700-749)",
    "Income is below preferred threshold (60000-74999)"
  ],
  "explanation": "...",
  "recommendations": ["...", "...", "..."],
  "suggestions": ["...", "...", "..."],
  "source": "llm",
  "llmStatus": "success"
}
```

The decision engine can return:

* `APPROVE`
* `REVIEW`
* `REJECT`

Hard-stop rules can reject an application before calling the AI explanation service.

---

## AI Explanation Service

The AI explanation service receives structured decision data from the decision engine.

Example request to FastAPI:

```json
{
  "decision": "REVIEW",
  "status": "EVALUATED",
  "reasonCodes": ["CREDIT_MODERATE", "INCOME_BELOW_PREFERRED"],
  "ruleFactors": [
    "Credit score is in the moderate range (700-749)",
    "Income is below preferred threshold (60000-74999)"
  ],
  "score": 82.0,
  "nextStepCategory": "MANUAL_REVIEW"
}
```

Expected response shape:

```json
{
  "decision": "REVIEW",
  "reasonCodes": ["CREDIT_MODERATE", "INCOME_BELOW_PREFERRED"],
  "ruleFactors": [
    "Credit score is in the moderate range (700-749)",
    "Income is below preferred threshold (60000-74999)"
  ],
  "explanation": "The decision was made because...",
  "recommendations": [
    "Conduct a detailed manual review...",
    "Verify income sources...",
    "Request additional financial documentation..."
  ],
  "source": "llm",
  "llmStatus": "success"
}
```

The service validates LLM output before returning it. If the response is invalid or unavailable, it returns a controlled fallback response.

Possible explanation sources:

| Source                    | Meaning                                                                  |
| ------------------------- | ------------------------------------------------------------------------ |
| `llm`                     | OpenAI successfully generated the explanation and recommendations        |
| `partial-fallback`        | One LLM step succeeded and one fell back to rule-based output            |
| `fallback`                | FastAPI used rule-based fallback logic for all LLM steps                 |
| `springboot-fallback`     | Spring Boot could not reach FastAPI and used fallback output             |
| `deterministic-hard-stop` | Spring Boot rejected the applicant through deterministic hard-stop rules |

UI explanation labels:

| `llmStatus` | Frontend badge |
| ----------- | -------------- |
| `success`   | LLM-generated explanation |
| `fallback`  | Rule-based fallback explanation |

---

## Observability

The FastAPI AI explanation service exposes Prometheus-compatible metrics at:

```text
http://localhost:8000/metrics
```

Prometheus scrapes the FastAPI service through Docker networking:

```text
fastapi:8000/metrics
```

Grafana visualizes these metrics in an operator-friendly dashboard.

Tracked custom metrics:

| Metric                | Purpose                               |
| --------------------- | ------------------------------------- |
| `app_requests_total`  | Total requests by endpoint and status |
| `app_errors_total`    | Total error responses by endpoint     |
| `app_latency_seconds` | Request latency histogram by endpoint |

The Grafana dashboard includes panels for:

* FastAPI target health
* total analyze requests
* analyze requests over time
* average analyze latency
* P95 analyze latency
* analyze errors
* request status distribution
* total errors

Observability is included because AI-enabled services still need normal DevOps visibility. The system should be measurable, debuggable, and operable.

---

## Tech Stack

| Layer                       | Technology                                                                     |
| --------------------------- | ------------------------------------------------------------------------------ |
| Frontend                    | React, Vite, Nginx                                                             |
| Decision Engine             | Java, Spring Boot                                                              |
| AI Explanation Service      | Python, FastAPI                                                                |
| LLM Provider                | OpenAI API                                                                     |
| Metrics                     | Prometheus Python client                                                       |
| Monitoring                  | Prometheus                                                                     |
| Dashboarding                | Grafana                                                                        |
| Runtime                     | Docker Compose                                                                 |
| Infrastructure Future State | Azure Container Apps, Azure Container Registry, Azure Key Vault, Azure Monitor |

---

## Local Demo Quickstart

### 1. Start Docker Desktop

Make sure Docker Desktop is running.

### 2. Build and start all services

From the repository root:

```bash
docker compose up -d --build
```

### 3. Verify containers

```bash
docker ps
```

Expected container names:

```text
frontend
springboot
fastapi
prometheus
grafana
```

### 4. Run the full demo script

```bash
./scripts/full-demo.sh
```

The full demo script:

* rebuilds and starts the Docker Compose stack
* lists running containers
* checks FastAPI health
* tests the Spring Boot to FastAPI to LLM path
* generates demo traffic
* confirms Prometheus metrics exist
* opens the frontend, Prometheus, and Grafana in Google Chrome when available

---

## Demo URLs

| Service         | URL                                |
| --------------- | ---------------------------------- |
| Frontend UI     | http://localhost:3000              |
| Spring Boot API | http://localhost:8080/api/evaluate |
| FastAPI Health  | http://localhost:8000/health       |
| FastAPI Analyze | http://localhost:8000/analyze      |
| FastAPI Metrics | http://localhost:8000/metrics      |
| Prometheus      | http://localhost:9090              |
| Grafana         | http://localhost:3001              |

---

## Frontend Demo Flow

Open:

```text
http://localhost:3000
```

Use this demo input:

```text
Income: 65000
Credit Score: 705
Employment Status: EMPLOYED
```

Expected result:

```text
Decision: REVIEW
Risk Score: 82%
Explanation source: LLM-generated explanation
```

The result card should display:

* decision
* risk score
* explanation type badge
* decision context (`status`, `nextStepCategory`, `reasonCodes`)
* key factors
* explanation
* recommendations

This demonstrates the user-facing flow:

```text
Frontend UI -> Spring Boot Decision Engine -> FastAPI AI Explanation Service -> OpenAI
```

---

## API Testing

### FastAPI Health Check

```bash
curl http://localhost:8000/health
```

Expected response:

```json
{"status":"ok"}
```

---

## Spring Boot End-to-End Evaluation

This tests the main backend path:

```text
Spring Boot Decision Engine -> FastAPI AI Explanation Service -> OpenAI
```

Command:

```bash
curl -i -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "income": 65000,
    "creditScore": 705,
    "employmentStatus": "EMPLOYED"
  }'
```

Expected response shape:

```json
{
  "decisionContext": {
    "decision": "REVIEW",
    "status": "EVALUATED",
    "reasonCodes": ["CREDIT_MODERATE", "INCOME_BELOW_PREFERRED"],
    "ruleFactors": [
      "Credit score is in the moderate range (700-749)",
      "Income is below preferred threshold (60000-74999)"
    ],
    "score": 82.0,
    "nextStepCategory": "MANUAL_REVIEW"
  },
  "decision": "REVIEW",
  "riskScore": 82.0,
  "reasons": [
    "Credit score is in the moderate range (700-749)",
    "Income is below preferred threshold (60000-74999)"
  ],
  "explanation": "...",
  "recommendations": ["...", "...", "..."],
  "suggestions": ["...", "...", "..."],
  "source": "llm",
  "llmStatus": "success"
}
```

This endpoint proves that the frontend-facing backend path is working.

---

## FastAPI Analyze Endpoint

This tests the AI explanation service directly.

Command:

```bash
curl -i -X POST http://localhost:8000/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "REVIEW",
    "status": "EVALUATED",
    "reasonCodes": ["CREDIT_MODERATE", "INCOME_BELOW_PREFERRED"],
    "ruleFactors": [
      "Credit score is in the moderate range (700-749)",
      "Income is below preferred threshold (60000-74999)"
    ],
    "score": 82.0,
    "nextStepCategory": "MANUAL_REVIEW"
  }'
```

Expected result:

* `HTTP/1.1 200 OK`
* `X-Request-ID` response header
* JSON response containing:

  * explanation
  * recommendations
  * source
  * llmStatus

Example response shape:

```json
{
  "decision": "REVIEW",
  "reasonCodes": ["CREDIT_MODERATE", "INCOME_BELOW_PREFERRED"],
  "ruleFactors": [
    "Credit score is in the moderate range (700-749)",
    "Income is below preferred threshold (60000-74999)"
  ],
  "explanation": "...",
  "recommendations": ["...", "...", "..."],
  "source": "llm",
  "llmStatus": "success"
}
```

---

## Demo Traffic Script

Run:

```bash
./scripts/demo-traffic.sh
```

The script generates traffic for the observability stack.

It performs:

* one health check
* one successful analyze request
* repeated successful analyze requests
* one invalid request that returns `422`
* a metrics sample from `/metrics`

The validation error is intentional. It proves that failed requests are tracked by the metrics layer.

---

## Observability Demo

### Prometheus

Open:

```text
http://localhost:9090
```

Useful queries:

```promql
app_requests_total
```

```promql
app_errors_total
```

```promql
app_latency_seconds_count
```

What Prometheus proves:

* FastAPI metrics are being scraped
* `/analyze` request volume is visible
* validation errors are tracked
* latency observations are available

Prometheus is the raw metrics validation layer.

---

### Grafana

Open:

```text
http://localhost:3001
```

Dashboard:

```text
Azure AI Decision Pipeline Observability
```

Dashboard JSON path:

```text
observability/grafana/dashboards/azure-ai-decision-pipeline-observability.json
```

Grafana provides the operator-facing view of:

* service health
* request count
* error count
* request status distribution
* average latency
* P95 latency

Grafana is used to show that the AI explanation service can be monitored like a real production service.

---

## Fallback Behavior

The project includes multiple fallback paths.

### FastAPI LLM Fallback

FastAPI returns a controlled fallback response if:

* `OPENAI_API_KEY` is missing
* the OpenAI API call fails
* the LLM response is not valid JSON
* the LLM response is missing required fields
* the LLM suggestions are not returned in the expected format

Possible source:

```text
fallback
```

---

### Spring Boot Fallback

Spring Boot returns a fallback explanation if the FastAPI AI explanation service is unavailable.

Possible source:

```text
springboot-fallback
```

This protects the user-facing decision flow from failing completely when the AI explanation service is unavailable.

---

### Deterministic Hard-Stop

Some applications are rejected directly by deterministic rules before the LLM explanation path.

Possible source:

```text
deterministic-hard-stop
```

Example hard-stop input:

```text
Income: 45000
Credit Score: 580
Employment Status: EMPLOYED
```

This demonstrates that rule-based decisions can override the AI explanation path when needed.

---

## Development Commands

### Start all services

```bash
docker compose up -d --build
```

### Stop all services

```bash
docker compose down
```

### View logs

FastAPI:

```bash
docker compose logs -f fastapi
```

Spring Boot:

```bash
docker compose logs -f springboot
```

Frontend:

```bash
docker compose logs -f frontend
```

Prometheus:

```bash
docker compose logs -f prometheus
```

Grafana:

```bash
docker compose logs -f grafana
```

---

### Rebuild all services

```bash
docker compose down
docker compose up -d --build
```

---

### Rebuild frontend only

```bash
docker compose build --no-cache frontend
docker compose up -d frontend
```

---

### Rebuild Spring Boot service

The Spring Boot Docker image uses a multi-stage build. Maven packaging runs inside Docker, so a host `target/` directory is not required.

After changing Java code:

```bash
docker compose up -d --build springboot
```

Optional local Maven build for IDE or troubleshooting:

```bash
cd services/decision-engine
./mvnw clean package -DskipTests
```

---

### Run full demo launcher

```bash
./scripts/full-demo.sh
```

---

### Run observability traffic only

```bash
./scripts/demo-traffic.sh
```

---

## Environment Variables

Create a root `.env` file for the FastAPI service. At minimum:

```text
OPENAI_API_KEY=your-key-here
```

Optional FastAPI variables:

| Variable | Purpose |
| -------- | ------- |
| `OPENAI_API_KEY` | Enables live LLM explanation and recommendations |
| `LLM_TIMEOUT_SECONDS` | OpenAI client timeout (default `30`) |
| `LLM_FORCE_FALLBACK` | Force rule-based fallback for demo/testing (`true` / `1` / `yes`) |

If the key is unavailable, the system still runs and returns rule-based fallback explanations.

This allows the deterministic decision pipeline to continue operating even when the external LLM dependency is unavailable.

---

## Repository Structure

```text
.
├── frontend/
│   ├── Dockerfile
│   ├── package.json
│   └── src/
│       ├── App.jsx
│       ├── App.css
│       └── main.jsx
├── services/
│   ├── ai-service/
│   │   ├── Dockerfile
│   │   ├── main.py
│   │   ├── llm.py
│   │   ├── middleware.py
│   │   ├── metrics.py
│   │   ├── logging_config.py
│   │   └── requirements.txt
│   └── decision-engine/
│       ├── Dockerfile
│       ├── pom.xml
│       └── src/main/java/com/example/demo/
│           ├── controller/
│           │   └── EligibilityController.java
│           ├── model/
│           │   ├── Applicant.java
│           │   ├── DecisionContext.java
│           │   ├── EligibilityResponse.java
│           │   └── EmploymentStatus.java
│           └── service/
│               ├── DecisionEngine.java
│               └── EligibilityService.java
├── observability/
│   ├── prometheus/
│   │   └── prometheus.yml
│   └── grafana/
│       └── dashboards/
│           └── azure-ai-decision-pipeline-observability.json
├── scripts/
│   ├── demo-traffic.sh
│   └── full-demo.sh
├── docker-compose.yml
└── README.md
```

---

## Azure Deployment Future State

The current implementation runs locally through Docker Compose.

The planned Azure productionization path is:

```text
GitHub Actions
   |
   v
Azure Container Registry
   |
   v
Azure Container Apps
   |
   +--> Frontend Container
   +--> Spring Boot Decision Engine Container
   +--> FastAPI AI Explanation Service Container

Secrets:
Azure Key Vault

Observability:
Azure Monitor
Log Analytics
Managed Prometheus / Grafana option

LLM Provider:
Azure OpenAI or OpenAI API
```

Planned Azure components:

* Azure Container Registry for image storage
* Azure Container Apps for container hosting
* Azure Key Vault for secrets
* Azure Monitor for cloud metrics
* Log Analytics for centralized logs
* GitHub Actions for CI/CD
* Optional Azure OpenAI integration
* Terraform skeleton under `infra/terraform/environments/dev`

The Azure deployment is not currently active. It is the next productionization step.

---

## Known Limitations

* The current implementation is local-first and runs through Docker Compose
* Azure deployment is planned but not yet implemented
* Authentication and authorization are not implemented
* Persistent decision audit storage is not implemented
* The decision model is intentionally simple for demo purposes
* The Grafana dashboard is exported as JSON; automatic provisioning can be improved
* The LLM provider is currently OpenAI-focused
* The project does not yet include CI/CD automation
* The frontend UI is functional but intentionally minimal
* Spring Boot metrics are not yet exported to Prometheus

---

## Future Work

Planned improvements:

* Add Azure Container Apps Terraform skeleton
* Add Azure Container Registry integration
* Add Azure Key Vault for secret management
* Add Azure Monitor and Log Analytics integration
* Add GitHub Actions CI/CD
* Add Azure OpenAI provider option
* Add model/provider abstraction for OpenAI vs Azure OpenAI
* Add persistent decision and explanation audit logs
* Add authentication and authorization
* Add stronger structured error handling
* Add automated Grafana dashboard provisioning
* Add additional test cases for deterministic decision outcomes
* Wire `AI_SERVICE_URL` in Spring Boot instead of hardcoding the FastAPI URL

---

## Demo Readiness Checklist

Before presenting:

```bash
docker compose down
docker compose up -d --build
./scripts/full-demo.sh
```

Confirm:

* Docker Desktop is running
* all five containers are healthy
* frontend opens in Chrome
* Prometheus opens in Chrome
* Grafana opens in Chrome
* frontend shows `LLM-generated explanation`
* Prometheus shows `app_requests_total`
* Grafana dashboard shows request, error, and latency data

Recommended backup screenshots:

* frontend result card
* `docker ps`
* Prometheus query results
* Grafana dashboard
