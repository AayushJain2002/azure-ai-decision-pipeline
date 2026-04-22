AI Decision Pipeline:
 - A fullstack microserivce-based decision support system that combines deterministic risk scoring with LLM-powered explainability.

Core Objectives
 This project simulates a production-grade deicsioning system where:
 - Core decisions are deterministic, auditable, and rule-based
 - Explanations are generated using an LLM (non-deterministic layer)
 - The system is fault-tolerant with fallback logic
 - Services are decoupled and communicate via APIs


Start Application:

docker compose up -d build (use this when starting fresh and dependencies change requirements.txt or Dockerfile, open docker app)
docker compose up -d (only changes to code - no dependency changes)
docker ps (verfiy containers are running [FastAPI service (port 8000), Spring Boot service (port 8080)])


Debugging: docker compose logs -f fastapi
Shutdown: docker compose down (closes down all containers)

API Testing:

Health Check: curl http://localhost:8000/health (expected: {status: "ok"})
Metrics Endpoint: curl http://localhost:8000/metrics (app_request_total, app_error_total, app_latency_seconds basically prometheus style output)

Analyze Endpoint: 

curl -i -X POST http://localhost:8000/analyze \
-H "Content-Type: application/json" \
-d '{
  "decision": "approve",
  "riskScore": 0.2,
  "reasons": ["low debt", "high income"]
}'

Verifications for Analyze Endpoint:

HTTP/1.1 200 OK
X-Request-ID header present
JSON response with explanation + suggestions

Architecture:
Frontend (React) --> Spring Boot (Decision Engine) --> FastAPI (LLM Explanation Service) --> OpenAI API

Service Responsibilities
1. Frontend (React)
   a) Collects applicant inputs
   b) Displays decision, reasoning, and suggestions

2. Spring Boot (Decision Engine)
   a) Implements deterministic scoring logic
   b) Produces: decision, riskScore, reasons
   b) Calls FastAPI for explanation
   c) Handles fallback if AI service falls

3. FastAPI (AI Service)
   a) Gets structured decision data
   b) Generates 
      i) Natural Language Explanation
      ii) Actionable suggestions
   c) Uses OpenAI API
   d) Includes strict JSON validation + fallback

 4. Features
   a) Deterministic risk scoring engine
   b) LLM-based explanation layer
   c) Microservice architecture (Java + Python)
   d) Dockerized multi-service deployment
   e) Fallback handling for AI failures
   f) End-to-End UI integration

Example 
Input:
{
  "income": 50000,
  "creditScore": 700,
  "employmentStatus": "EMPLOYED"
}
Output:
{
  "decision": "REVIEW",
  "riskScore": 72.0,
  "reasons": [
    "Credit score is in the moderate range",
    "Income is low"
  ],
  "explanation": "...",
  "suggestions": ["...", "..."]
}

