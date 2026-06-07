#!/usr/bin/env bash
set -euo pipefail

echo "Health check..."
curl -s http://localhost:8000/health
echo
echo

echo "Successful analyze request..."
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
echo
echo

echo "Generate repeated analyze traffic..."
for i in {1..10}; do
  curl -s -X POST http://localhost:8000/analyze \
    -H "Content-Type: application/json" \
    -d '{
      "decision": "APPROVE",
      "riskScore": 22.5,
      "reasons": [
        "Strong credit score",
        "Stable income",
        "Low debt-to-income ratio"
      ]
    }' > /dev/null
done

echo "Generate validation error traffic..."
curl -s -o /dev/null -w "Validation error status: %{http_code}\n" \
  -X POST http://localhost:8000/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "decision": "REVIEW",
    "riskScore": "bad-number",
    "reasons": ["invalid payload test"]
  }'

echo
echo "Metrics sample..."
curl -s http://localhost:8000/metrics | grep -E "app_requests_total|app_errors_total|app_latency_seconds_count" | head -20

echo
echo "Demo traffic complete. Check Prometheus and Grafana."