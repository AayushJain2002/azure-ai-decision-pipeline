# MITRE Round 2 — Technical Demo Runbook

## Project Thesis

This is an AI-assisted decision support system where Spring Boot makes deterministic eligibility decisions and FastAPI/OpenAI generates constrained explanations and suggestions.

---

## AI Pipeline (Multi-Step)

The LLM is a **post-decision explanation layer**, not the decision authority.

```text
Applicant input
  → DecisionEngine.evaluate()     [Spring Boot — deterministic APPROVE/REVIEW/REJECT]
  → DecisionContext artifact      [decision, reasonCodes, ruleFactors, nextStepCategory]
  → [if status != HARD_STOP] POST /analyze
       → Step 1: explanation LLM call + JSON validation
       → Step 2: recommendations LLM call + JSON validation
  → EligibilityResponse           [decision from engine; explanation/recommendations from AI or fallback]
```

**Boundary rules (enforced in code, not prompt-only):**

| Layer | Responsibility |
| ----- | -------------- |
| `DecisionEngine.java` | All approval/review/reject outcomes; hard-stop rules bypass the LLM entirely |
| `llm.py` — `generate_explanation_text` | Explains the already-made decision; must not recommend next steps |
| `llm.py` — `generate_recommendations` | Suggests exactly 3 next steps; must not re-explain or change the decision |
| `FORBIDDEN_RESPONSE_KEYS` | Rejects LLM JSON that echoes decision fields (`decision`, `score`, `reasonCodes`, etc.) |
| Fallback chain | OpenAI failure, invalid JSON, or forbidden fields → rule-based text; FastAPI down → Spring Boot fallback |

**Transparency fields for demos:** `source` (`llm`, `partial-fallback`, `fallback`, `springboot-fallback`, `deterministic-hard-stop`) and `llmStatus` (`success` or `fallback`).

**Key files:** `DecisionEngine.java`, `EligibilityService.java`, `services/ai-service/llm.py`, `services/ai-service/main.py`.

---

## Local Setup

### 1. Environment file

Create `.env` before your first `docker compose up`. The FastAPI service loads it via `env_file: .env` in `docker-compose.yml`.

```powershell
Copy-Item .env.example .env
```

**How `.env` affects startup and LLM behavior:**

| Situation | Effect |
| --------- | ------ |
| **Missing `.env` file** | Compose may fail to start (the referenced `env_file` is absent). Copy `.env.example` to `.env` first. |
| **`.env` present, `OPENAI_API_KEY` empty or unset** | Stack can start. FastAPI uses rule-based fallback (`source: "fallback"`, `llmStatus: "fallback"`). Deterministic decisions still work. |
| **`.env` present, valid `OPENAI_API_KEY`** | Stack starts. FastAPI can call OpenAI for explanations when the LLM path succeeds (`source: "llm"`, `llmStatus: "success"`). |

Add your OpenAI key for the LLM explanation path:

```text
OPENAI_API_KEY=sk-...
```

Optional controls (see `.env.example`):

- `LLM_TIMEOUT_SECONDS` — OpenAI client timeout (default `30`)
- `LLM_FORCE_FALLBACK=true` — skip OpenAI and use rule-based fallback

### 2. Start the stack

```bash
docker compose up -d --build
```

Five containers should start: `frontend`, `springboot`, `fastapi`, `prometheus`, `grafana`.

### 3. Health checks

```bash
# Containers
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# FastAPI
curl -s http://localhost:8000/health
# Expected: {"status":"ok"}

# Spring Boot (no dedicated /health; smoke-test the API)
curl -s -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{"income":65000,"creditScore":705,"employmentStatus":"EMPLOYED"}'

# Frontend
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:3000

# Prometheus / Grafana
curl -s -o /dev/null -w "Prometheus HTTP %{http_code}\n" http://localhost:9090
curl -s -o /dev/null -w "Grafana HTTP %{http_code}\n" http://localhost:3001
```

Automated validation (rebuilds stack, runs checks, opens browser tabs):

```bash
./scripts/full-demo.sh
```

---

## Demo URLs

| Service | URL |
| ------- | --- |
| Frontend | http://localhost:3000 |
| Spring Boot API | http://localhost:8080/api/evaluate |
| FastAPI (health) | http://localhost:8000/health |
| FastAPI (analyze) | http://localhost:8000/analyze |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 |

---

## Demo Scenarios

Use the frontend at http://localhost:3000 or the `curl` commands below.

### Scenario 1 — Review (positive path with LLM explanation)

**Input**

| Field | Value |
| ----- | ----- |
| Income | `65000` |
| Credit Score | `705` |
| Employment Status | `EMPLOYED` |

**Expected decision**

- `decision`: `REVIEW`
- `riskScore`: `82`
- `decisionContext.status`: `EVALUATED`
- `decisionContext.nextStepCategory`: `MANUAL_REVIEW`
- `decisionContext.reasonCodes`: `CREDIT_MODERATE`, `INCOME_BELOW_PREFERRED`

**Expected source**

- With valid `OPENAI_API_KEY` in `.env`: `source: "llm"`, `llmStatus: "success"` → UI badge: **Explanation Source: LLM**
- With `.env` present but key empty, or on LLM failure: `source: "fallback"`, `llmStatus: "fallback"` → UI badge: **Explanation Source: fallback** (or `springboot-fallback` / `partial-fallback` when applicable)

**What it proves**

- Deterministic scoring produces the decision before any LLM call.
- FastAPI receives the decision context and returns explanation + recommendations.
- The full user path works: Frontend → Spring Boot → FastAPI → (OpenAI or fallback).

```bash
curl -s -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{"income":65000,"creditScore":705,"employmentStatus":"EMPLOYED"}'
```

**Alternative — Approval**

| Field | Value |
| ----- | ----- |
| Income | `95000` |
| Credit Score | `760` |
| Employment Status | `EMPLOYED` |

Expected: `decision: APPROVE`, `riskScore: 100`, `status: EVALUATED`, `nextStepCategory: PROCEED`.

---

### Scenario 2 — Hard-stop rejection

**Input**

| Field | Value |
| ----- | ----- |
| Income | `45000` |
| Credit Score | `580` |
| Employment Status | `EMPLOYED` |

**Expected decision**

- `decision`: `REJECT`
- `riskScore`: `50`
- `decisionContext.status`: `HARD_STOP`
- `decisionContext.reasonCodes`: `CREDIT_CRITICALLY_LOW`
- `decisionContext.nextStepCategory`: `IMPROVE_AND_REAPPLY`

**Expected source**

- `source`: `deterministic-hard-stop`
- `llmStatus`: `fallback`
- UI badge: **Explanation Source: deterministic-hard-stop**

**What it proves**

- Hard-stop rules in `DecisionEngine` reject before the LLM path runs.
- Spring Boot skips the FastAPI call for `HARD_STOP` cases.
- The decision authority stays in deterministic rules, not the LLM.

```bash
curl -s -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{"income":45000,"creditScore":580,"employmentStatus":"EMPLOYED"}'
```

---

### Scenario 3 — Invalid / missing input

**Frontend (empty field)**

Leave any field blank and click **Evaluate**.

- Expected: client-side error — `Please fill all fields`

**API — missing employment status**

```bash
curl -s -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{"income":80000,"creditScore":720}'
```

- Expected: HTTP `400`, body includes `"employmentStatus": "Employment status is required"`

**API — invalid credit score**

```bash
curl -s -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{"income":80000,"creditScore":250,"employmentStatus":"EMPLOYED"}'
```

- Expected: HTTP `400`, body includes `"creditScore": "Credit score must be at least 300"`

**API — invalid employment status**

```bash
curl -s -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{"income":80000,"creditScore":720,"employmentStatus":"RETIRED"}'
```

- Expected: HTTP `400`, body includes allowed values message on `employmentStatus`

**What it proves**

- Input validation runs in Spring Boot (`@Valid` on `Applicant`) before decision logic.
- Invalid requests for employment status, credit range, and income return structured field errors (HTTP 400).
- The frontend also blocks empty fields client-side before calling the API.

---

## Fallback Story

### When OpenAI is unavailable

FastAPI falls back to rule-based text when `.env` exists but:

- `OPENAI_API_KEY` is missing or empty
- `LLM_FORCE_FALLBACK=true`
- OpenAI API call fails or times out
- LLM output is invalid JSON or contains forbidden fields

Response fields: `source: "fallback"` (or `"partial-fallback"` if one step succeeded), `llmStatus: "fallback"`.

### When FastAPI is unavailable

Spring Boot still returns a complete response. It keeps the deterministic decision and uses built-in fallback explanation/recommendations. `source` stays `springboot-fallback`; `llmStatus` is `fallback`.

### Why the decision still works

`DecisionEngine.evaluate()` runs first in Spring Boot. The LLM never sets `APPROVE`, `REVIEW`, or `REJECT`. FastAPI only explains and recommends from the decision context it receives. If AI services fail, the user still gets the same decision and score.

---

## Observability

### Prometheus metrics

- Endpoint: http://localhost:8000/metrics
- Scraped by Prometheus at `fastapi:8000` (see `observability/prometheus/prometheus.yml`)
- Key metrics: `app_requests_total`, `app_errors_total`, `app_latency_seconds_count`

Generate sample traffic:

```bash
./scripts/demo-traffic.sh
```

Useful PromQL at http://localhost:9090:

```promql
app_requests_total
app_errors_total
app_latency_seconds_count
```

### Grafana dashboard (manual import)

Grafana starts empty — there is no automatic provisioning in `docker-compose.yml`.

1. Open http://localhost:3001 (default login `admin` / `admin` on first visit).
2. Add a Prometheus data source pointing to `http://prometheus:9090` (same Docker network).
3. Import `observability/grafana/dashboards/azure-ai-decision-pipeline-observability.json`.
4. Dashboard name: **Azure AI Decision Pipeline Observability**.

Shows FastAPI request volume, errors, status distribution, and latency. Spring Boot metrics are not exported to Prometheus in the current build.

---

## Known Limitations

- **No production auth** — endpoints are open on localhost.
- **Local Docker demo only** — intended for `docker compose` on a developer machine.
- **Azure path is future work** — Terraform skeleton exists under `infra/terraform/environments/dev`; no active Azure deployment.
- **FastAPI-only observability** — Prometheus scrapes FastAPI only; Spring Boot and frontend have no metrics export.
- **Hardcoded FastAPI URL** — Spring Boot calls `http://fastapi:8000/analyze` internally; `AI_SERVICE_URL` in compose is reserved for future wiring.
- **Simple decision model** — demo scoring rules, not a production credit model.
- **Minimal frontend** — functional UI, not a polished product shell.
- **Incomplete null checks on income/creditScore** — omitting these fields via direct API (not the UI) can return HTTP 500 instead of a field error; use the UI or the documented curl examples for validation demos.

---

## Troubleshooting

| Problem | What to check | Fix |
| ------- | ------------- | --- |
| **Docker build fails** | `docker compose logs` for the failing service | Ensure Docker Desktop is running. Re-run `docker compose up -d --build`. For Spring Boot, the Dockerfile runs Maven inside the image — no host `mvn` needed. |
| **Missing `.env` file** | `docker compose up` fails or FastAPI service does not start; error references missing `env_file` | Copy `.env.example` to `.env`, then run `docker compose up -d --build`. |
| **`.env` present, OpenAI key empty** | UI badge shows **Explanation Source: fallback**; `llmStatus: "fallback"` | Expected behavior. Add `OPENAI_API_KEY` to `.env` and restart FastAPI (`docker compose restart fastapi`), or demo fallback intentionally with `LLM_FORCE_FALLBACK=true`. |
| **OpenAI key set but LLM fails** | UI badge shows **Explanation Source: fallback**; `source: "fallback"` | Check `docker compose logs fastapi`. Decision is unchanged; only the explanation layer falls back. |
| **Frontend cannot reach backend** | Browser console network error; UI shows `Failed to connect to backend` | Confirm `springboot` is running on port `8080`. Frontend calls `http://localhost:8080/api/evaluate` directly — both must be on the same host. |
| **FastAPI unavailable** | Spring Boot logs: `FastAPI explanation service call failed` | Check `docker compose logs fastapi`. Decision still returns with `springboot-fallback`. Restart: `docker compose restart fastapi`. |
| **Grafana shows no data** | Empty panels after import | Add Prometheus data source (`http://prometheus:9090`), run `./scripts/demo-traffic.sh`, confirm targets at http://localhost:9090/targets. |
| **Prometheus target down** | Target `fastapi:8000` not UP | Ensure `fastapi` container is healthy: `curl http://localhost:8000/health`. |

Quick reset before a live demo (requires `.env` to exist):

```bash
docker compose down
docker compose up -d --build
./scripts/full-demo.sh
```
