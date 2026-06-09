#!/usr/bin/env bash
set -euo pipefail

FRONTEND_URL="http://localhost:3000"
PROMETHEUS_URL="http://localhost:9090"
GRAFANA_URL="http://localhost:3001"

echo "======================================"
echo "Azure AI Decision Pipeline Demo Startup"
echo "======================================"
echo

echo "0. Rebuilding and starting Docker Compose stack..."
docker compose down
docker compose up -d --build
echo

echo "Waiting for services to initialize..."
sleep 8
echo

echo "1. Checking running containers..."
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo

echo "2. Checking required containers..."
REQUIRED_CONTAINERS=("frontend" "springboot" "fastapi" "prometheus" "grafana")

for container in "${REQUIRED_CONTAINERS[@]}"; do
  if docker ps --format "{{.Names}}" | grep -q "^${container}$"; then
    echo "OK: ${container} is running"
  else
    echo "ERROR: ${container} is not running"
    echo
    echo "Current containers:"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    exit 1
  fi
done

echo

echo "3. Checking FastAPI health..."
FASTAPI_HEALTH="$(curl -s http://localhost:8000/health || true)"

if [[ "${FASTAPI_HEALTH}" == *"ok"* ]]; then
  echo "${FASTAPI_HEALTH}"
else
  echo "ERROR: FastAPI health check failed"
  echo "Response: ${FASTAPI_HEALTH}"
  echo
  echo "FastAPI logs:"
  docker compose logs --tail=50 fastapi
  exit 1
fi

echo
echo

echo "4. Testing Spring Boot -> FastAPI -> LLM path..."
SPRINGBOOT_RESPONSE="$(curl -s -X POST http://localhost:8080/api/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "income": 65000,
    "creditScore": 705,
    "employmentStatus": "EMPLOYED"
  }' || true)"

echo "${SPRINGBOOT_RESPONSE}" | python -m json.tool 2>/dev/null || echo "${SPRINGBOOT_RESPONSE}"
echo

if [[ "${SPRINGBOOT_RESPONSE}" == *'"decision"'* && "${SPRINGBOOT_RESPONSE}" == *'"source"'* ]]; then
  echo "OK: Spring Boot end-to-end evaluation returned decision and source"
else
  echo "ERROR: Spring Boot end-to-end evaluation did not return expected fields"
  echo
  echo "Spring Boot logs:"
  docker compose logs --tail=75 springboot
  echo
  echo "FastAPI logs:"
  docker compose logs --tail=75 fastapi
  exit 1
fi

echo

echo "5. Generating demo traffic for observability..."
./scripts/demo-traffic.sh
echo

echo "6. Confirming Prometheus metrics exist..."
METRICS_SAMPLE="$(curl -s http://localhost:8000/metrics | grep -E "app_requests_total|app_errors_total|app_latency_seconds_count" | head -20 || true)"

if [[ -n "${METRICS_SAMPLE}" ]]; then
  echo "${METRICS_SAMPLE}"
else
  echo "ERROR: Expected metrics were not found at http://localhost:8000/metrics"
  echo
  echo "FastAPI logs:"
  docker compose logs --tail=75 fastapi
  exit 1
fi

echo

echo "7. Checking Prometheus target page availability..."
PROMETHEUS_CHECK="$(curl -s -o /dev/null -w "%{http_code}" "${PROMETHEUS_URL}" || true)"

if [[ "${PROMETHEUS_CHECK}" == "200" || "${PROMETHEUS_CHECK}" == "302" ]]; then
  echo "OK: Prometheus is reachable at ${PROMETHEUS_URL}"
else
  echo "WARNING: Prometheus returned HTTP ${PROMETHEUS_CHECK}"
fi

echo

echo "8. Checking Grafana availability..."
GRAFANA_CHECK="$(curl -s -o /dev/null -w "%{http_code}" "${GRAFANA_URL}" || true)"

if [[ "${GRAFANA_CHECK}" == "200" || "${GRAFANA_CHECK}" == "302" ]]; then
  echo "OK: Grafana is reachable at ${GRAFANA_URL}"
else
  echo "WARNING: Grafana returned HTTP ${GRAFANA_CHECK}"
fi

echo

echo "9. Opening demo URLs..."
echo "Frontend:   ${FRONTEND_URL}"
echo "Prometheus: ${PROMETHEUS_URL}"
echo "Grafana:    ${GRAFANA_URL}"
echo

# Always open demo URLs in Google Chrome when available
if command -v powershell.exe >/dev/null 2>&1; then
  powershell.exe -NoProfile -Command "
    \$paths = @(
      \"\$env:ProgramFiles\Google\Chrome\Application\chrome.exe\",
      \"\${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe\",
      \"\$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe\"
    )
    \$chrome = \$paths | Where-Object { Test-Path \$_ } | Select-Object -First 1
    \$urls = @('${FRONTEND_URL}', '${PROMETHEUS_URL}', '${GRAFANA_URL}')
    if (\$chrome) {
      Start-Process \$chrome -ArgumentList \$urls
    } else {
      Write-Warning 'Google Chrome not found. Falling back to the system default browser.'
      foreach (\$url in \$urls) { Start-Process \$url }
    }
  " >/dev/null 2>&1

elif command -v open >/dev/null 2>&1; then
  open -a "Google Chrome" "${FRONTEND_URL}" "${PROMETHEUS_URL}" "${GRAFANA_URL}"

elif command -v google-chrome >/dev/null 2>&1; then
  google-chrome "${FRONTEND_URL}" "${PROMETHEUS_URL}" "${GRAFANA_URL}" >/dev/null 2>&1 &

elif command -v google-chrome-stable >/dev/null 2>&1; then
  google-chrome-stable "${FRONTEND_URL}" "${PROMETHEUS_URL}" "${GRAFANA_URL}" >/dev/null 2>&1 &

else
  echo "Google Chrome not found. Open these URLs manually:"
  echo "${FRONTEND_URL}"
  echo "${PROMETHEUS_URL}"
  echo "${GRAFANA_URL}"
fi

echo
echo "======================================"
echo "Demo environment ready."
echo "======================================"
echo
echo "Main walkthrough:"
echo "1. Use frontend for the product demo."
echo "2. Use Prometheus to prove raw metrics."
echo "3. Use Grafana to show operator-friendly observability."
echo
echo "Recommended frontend demo input:"
echo "Income: 65000"
echo "Credit Score: 705"
echo "Employment Status: EMPLOYED"
echo
echo "Expected UI result:"
echo "Decision: REVIEW"
echo "Explanation source: LLM-generated explanation"