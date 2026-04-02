AI Decision Pipeline:
 - A fullstack microserivce-based decision support system that combines deterministic risk scoring with LLM-powered explainability.

Core Objectives
 This project simulates a production-grade deicsioning system where:
 - Core decisions are deterministic, auditable, and rule-based
 - Explanations are generated using an LLM (non-deterministic layer)
 - The system is fault-tolerant with fallback logic
 - Services are decoupled and communicate via APIs

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

Debugging & Troubleshooting
This project required debugging across multiple layers of a distributed system:
1. API Contract Mismatch

Issue:

FastAPI expected decision outputs instead of raw inputs

Fix:

Clearly defined service roles:
Spring Boot → decision engine
FastAPI → explanation service
2. Service-to-Service Communication Failure

Issue:

explanation and suggestions returned as null

Root Cause:

FastAPI call failing silently in Spring Boot

Fix:

Added structured logging:
request payload
raw response
error stack traces
Switched to explicit JSON parsing
3. Docker Networking Issues

Issue:

Used localhost inside container

Fix:

Updated service URL:
http://fastapi:8000/analyze
4. Build Pipeline Failure (Critical)

Issue:

Code changes not reflected in running container

Root Causes:

Docker layer caching
Stale JAR artifact
Incorrect artifact copying

Fixes:

Forced clean rebuild:
docker compose down -v --rmi all
docker builder prune -a -f
docker compose build --no-cache
5. Incorrect JAR Artifact

Issue:

Used non-executable .jar.original

Error:

no main manifest attribute in app.jar

Fix:

Use Spring Boot executable JAR containing:
BOOT-INF/
6. Docker Artifact Ambiguity

Issue:

COPY target/*.jar selected wrong artifact

Fix:

COPY target/demo-0.0.1-SNAPSHOT.jar app.jar
7. JSON Type Safety

Issue:

Unchecked conversion using Map.class

Fix:

new TypeReference<Map<String, Object>>() {}
🐳 Running the Project
docker compose up --build
🌐 Services
Service	URL
Frontend	http://localhost:3000

Spring Boot API	http://localhost:8080

FastAPI Docs	http://localhost:8000/docs
📈 Future Improvements
🔹 Prometheus metrics (latency, success rate, fallback rate)
🔹 Grafana dashboards
🔹 Distributed tracing across services
🔹 Load testing & performance benchmarking
🔹 Authentication & rate limiting
🧠 Key Takeaways
Separation of deterministic and AI logic is critical
Build pipelines are often harder than application logic
Observability is essential for production systems
Clear API contracts prevent system-level bugs
🎯 What This Project Demonstrates
Full-stack system design
Microservices communication
AI integration with fallback safety
Debugging across distributed systems
Production-oriented engineering mindset
🚀 Getting Started (Quick Commands)
docker compose down -v --rmi all
docker builder prune -a -f
docker compose build --no-cache
docker compose up
📌 Author Notes

This project emphasizes system reliability over just functionality, focusing on:

correctness
observability
reproducibility
clear service boundaries