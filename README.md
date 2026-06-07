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
* Apply deterministic scoring rules
* Apply hard-stop rejection rules
* Produce:

  * `decision`
  * `riskScore`
  * `reasons`
* Call the FastAPI AI explanation service
* Return a combined response to the frontend
* Provide fallback output if the FastAPI service is unavailable

The Spring Boot service is the decision authority. The LLM does not make the decision.

---

### FastAPI AI Explanation Service

The FastAPI service owns the AI explanation layer.

Responsibilities:

* Expose the `/analyze` endpoint
* Accept structured decision data from Spring Boot
* Build a constrained prompt using:

  * decision
  * risk score
  * reasons
* Call the OpenAI API
* Validate that the LLM response is strict JSON
* Return:

  * explanation
  * suggestions
  * source
* Provide fallback behavior when the LLM call fails or returns invalid output

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
  "decision": "REVIEW",
  "riskScore": 82.0,
  "reasons": [
    "Credit score is in the moderate range (700-749)",
    "Income is below preferred threshold (60000-74999)"
  ],
  "explanation": "...",
  "suggestions": ["...", "...", "..."],
  "source": "llm"
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
  "riskScore": 72.0,
  "reasons": [
    "Credit score is in the moderate range",
    "Income is below preferred threshold",
    "Employment history is limited"
  ]
}
```

Expected response shape:

```json
{
  "explanation": "The decision was made because...",
  "suggestions": [
    "Improve credit profile strength...",
    "Increase income stability...",
    "Address the listed risk factors..."
  ],
  "source": "llm"
}
```

The service validates LLM output before returning it. If the response is invalid or unavailable, it returns a controlled fallback response.

Possible explanation sources:

| Source                    | Meaning                                                                  |
| ------------------------- | ------------------------------------------------------------------------ |
| `llm`                     | OpenAI successfully generated the explanation                            |
| `fallback`                | FastAPI used fallback logic                                              |
| `springboot-fallback`     | Spring Boot could not reach FastAPI and used fallback output             |
| `deterministic-hard-stop` | Spring Boot rejected the applicant through deterministic hard-stop rules |

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

* lists running containers
* checks FastAPI health
* tests the Spring Boot to FastAPI to LLM path
* generates demo traffic
* confirms Prometheus metrics exist
* opens the frontend, Prometheus, and Grafana in the browser

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
Explanation Source: LLM Explanation
```

The result card should display:

* decision
* risk score
* key factors
* explanation
* suggestions
* explanation source

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
  "decision": "REVIEW",
  "riskScore": 82.0,
  "reasons": [
    "Credit score is in the moderate range (700-749)",
    "Income is below preferred threshold (60000-74999)"
  ],
  "explanation": "...",
  "suggestions": ["...", "...", "..."],
  "source": "llm"
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
    "riskScore": 72.0,
    "reasons": [
      "Credit score is in the moderate range",
      "Income is below preferred threshold",
      "Employment history is limited"
    ]
  }'
```

Expected result:

* `HTTP/1.1 200 OK`
* `X-Request-ID` response header
* JSON response containing:

  * explanation
  * suggestions
  * source

Example response shape:

```json
{
  "explanation": "...",
  "suggestions": ["...", "...", "..."],
  "source": "llm"
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

### Rebuild Spring Boot JAR

The Spring Boot Docker image runs from the packaged JAR in `target/`.

After changing Java code, rebuild the JAR first:

```bash
cd services/decision-engine
./mvnw clean package -DskipTests
cd ../..
docker compose up -d --build
```

If `./mvnw` is unavailable:

```bash
cd services/decision-engine
mvn clean package -DskipTests
cd ../..
docker compose up -d --build
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

The FastAPI service uses:

```text
OPENAI_API_KEY
```

If the key is unavailable, the system still runs and returns fallback explanations.

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
│           │   ├── EligibilityResponse.java
│           │   └── EmploymentStatus.java
│           └── service/
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
├── infra/
│   └── terraform/
│       └── environments/
│           └── dev/
├── docs/
│   └── images/
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
* The system does not yet include advanced multi-step LLM workflows

---

## Future Work

Planned improvements:

* Use Cursor to accelerate additional feature development and refactoring
* Use Cursor to expand LLM capabilities, including multi-step explanation workflows
* Use Cursor to support Azure component buildout and Terraform iteration
* Use Cursor to help identify and resolve limitations in the current implementation
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
* Add multi-chain or multi-step prompting for richer AI explanation workflows

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
* frontend returns `Explanation Source: LLM Explanation`
* Prometheus shows `app_requests_total`
* Grafana dashboard shows request, error, and latency data

Recommended backup screenshots:

* frontend result card
* `docker ps`
* Prometheus query results
* Grafana dashboard

```
```
