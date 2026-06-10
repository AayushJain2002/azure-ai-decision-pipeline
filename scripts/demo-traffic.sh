#!/usr/bin/env bash
set -euo pipefail

echo "Health check..."
curl -s http://localhost:8000/health
echo
echo

echo "Successful analyze request (REVIEW)..."
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
echo
echo

echo "Successful analyze request (APPROVE, varied case)..."
curl -s -o /dev/null -w "APPROVE analyze status: %{http_code}\n" \
  -X POST http://localhost:8000/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "APPROVE",
    "status": "EVALUATED",
    "reasonCodes": ["STRONG_FACTORS"],
    "ruleFactors": [
      "Strong performance across all evaluation factors"
    ],
    "score": 100.0,
    "nextStepCategory": "PROCEED"
  }'
echo

echo "Generate repeated successful analyze traffic..."
for i in {1..5}; do
  curl -s -o /dev/null -w "  request ${i}/5 status: %{http_code}\n" \
    -X POST http://localhost:8000/analyze \
    -H "Content-Type: application/json" \
    -d '{
      "decision": "APPROVE",
      "status": "EVALUATED",
      "reasonCodes": ["STRONG_FACTORS"],
      "ruleFactors": [
        "Strong performance across all evaluation factors"
      ],
      "score": 100.0,
      "nextStepCategory": "PROCEED"
    }'
done
echo

echo "Labeled validation error (missing required status field)..."
curl -s -o /dev/null -w "Labeled validation error status: %{http_code}\n" \
  -X POST http://localhost:8000/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "REVIEW",
    "reasonCodes": ["CREDIT_MODERATE"],
    "ruleFactors": ["Missing status field for demo validation test"]
  }'

echo
echo "Metrics sample..."
curl -s http://localhost:8000/metrics | grep -E "app_requests_total|app_errors_total|app_latency_seconds_count" | head -20

echo
echo "Demo traffic complete. Check Prometheus and Grafana."
