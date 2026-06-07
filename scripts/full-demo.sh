#!/usr/bin/env bash
set -euo pipefail

FRONTEND_URL="http://localhost:3000"
PROMETHEUS_URL="http://localhost:9090"
GRAFANA_URL="http://localhost:3001"

echo "======================================"
echo "Azure AI Decision Pipeline Demo Startup"
echo "======================================"
echo

echo "1. Checking running containers..."
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo

echo "2. Checking FastAPI health..."
curl -s http://localhost:8000/health
echo
echo

echo "3. Testing Spring Boot -> FastAPI -> LLM path..."
curl -s -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "income": 65000,
    "creditScore": 705,
    "employmentStatus": "EMPLOYED"
  }' | python -m json.tool
echo

echo "4. Generating demo traffic for observability..."
./scripts/demo-traffic.sh
echo

echo "5. Confirming Prometheus metrics exist..."
curl -s http://localhost:8000/metrics | grep -E "app_requests_total|app_errors_total|app_latency_seconds_count" | head -20
echo

echo "6. Opening demo URLs..."
echo "Frontend:   ${FRONTEND_URL}"
echo "Prometheus: ${PROMETHEUS_URL}"
echo "Grafana:    ${GRAFANA_URL}"
echo

# Windows Git Bash / WSL-safe browser launch
if command -v powershell.exe >/dev/null 2>&1; then
  powershell.exe -NoProfile -Command "Start-Process '${FRONTEND_URL}'" >/dev/null 2>&1
  powershell.exe -NoProfile -Command "Start-Process '${PROMETHEUS_URL}'" >/dev/null 2>&1
  powershell.exe -NoProfile -Command "Start-Process '${GRAFANA_URL}'" >/dev/null 2>&1

# macOS
elif command -v open >/dev/null 2>&1; then
  open "${FRONTEND_URL}"
  open "${PROMETHEUS_URL}"
  open "${GRAFANA_URL}"

# Linux
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "${FRONTEND_URL}" >/dev/null 2>&1 &
  xdg-open "${PROMETHEUS_URL}" >/dev/null 2>&1 &
  xdg-open "${GRAFANA_URL}" >/dev/null 2>&1 &
else
  echo "Could not auto-open browser. Open these manually:"
  echo "${FRONTEND_URL}"
  echo "${PROMETHEUS_URL}"
  echo "${GRAFANA_URL}"
fi

echo
echo "Demo environment ready."
echo "Use the frontend for the main walkthrough."
echo "Use Prometheus to prove raw metrics."
echo "Use Grafana to show operator-friendly observability."