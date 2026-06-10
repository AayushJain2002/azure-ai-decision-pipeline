#!/usr/bin/env bash
set -euo pipefail

FRONTEND_URL="http://localhost:3000"
PROMETHEUS_URL="http://localhost:9090"
GRAFANA_URL="http://localhost:3001"

DOCKER_READY_TIMEOUT_SECONDS="${DOCKER_READY_TIMEOUT_SECONDS:-120}"
DOCKER_READY_POLL_SECONDS="${DOCKER_READY_POLL_SECONDS:-2}"

docker_is_ready() {
  docker info >/dev/null 2>&1
}

wait_for_docker() {
  local elapsed=0

  while ! docker_is_ready; do
    if (( elapsed >= DOCKER_READY_TIMEOUT_SECONDS )); then
      echo "ERROR: Docker did not become ready within ${DOCKER_READY_TIMEOUT_SECONDS}s"
      echo "Start Docker Desktop manually, then re-run this script."
      exit 1
    fi

    echo "Waiting for Docker to be ready... (${elapsed}s / ${DOCKER_READY_TIMEOUT_SECONDS}s)"
    sleep "${DOCKER_READY_POLL_SECONDS}"
    elapsed=$((elapsed + DOCKER_READY_POLL_SECONDS))
  done
}

start_docker_desktop() {
  if command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -Command "
      \$dockerDesktop = Join-Path \$env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
      if (Test-Path \$dockerDesktop) {
        Start-Process \$dockerDesktop | Out-Null
        exit 0
      }
      exit 1
    "

    if [[ $? -eq 0 ]]; then
      return 0
    fi

    echo "ERROR: Docker Desktop was not found at the default install path."
    echo "Install Docker Desktop or start it manually, then re-run this script."
    exit 1
  fi

  if command -v open >/dev/null 2>&1; then
    open -a Docker >/dev/null 2>&1 || true
    return 0
  fi

  echo "ERROR: Docker is not running and could not be started automatically on this platform."
  echo "Start Docker manually, then re-run this script."
  exit 1
}

ensure_docker_ready() {
  if docker_is_ready; then
    echo "OK: Docker is running"
    return 0
  fi

  echo "Docker is not running. Starting Docker Desktop..."
  start_docker_desktop
  wait_for_docker
  echo "OK: Docker is ready"
}

parse_json_field() {
  local json="$1"
  local field="$2"

  echo "${json}" | python -c "import sys, json; data=json.load(sys.stdin); value=data.get('${field}'); print('' if value is None else value)" 2>/dev/null || true
}

describe_explanation_result() {
  local source="$1"
  local llm_status="$2"

  if [[ "${source}" == "deterministic-hard-stop" ]]; then
    echo "Deterministic hard-stop bypassed the LLM; rule-based fallback explanation was used."
  elif [[ "${source}" == "springboot-fallback" ]]; then
    echo "Deterministic decision succeeded; Spring Boot fallback explanation was used (FastAPI unavailable)."
  elif [[ "${source}" == "llm" && "${llm_status}" == "success" ]]; then
    echo "LLM-generated explanation (source=llm, llmStatus=success)."
  elif [[ "${source}" == "partial-fallback" ]]; then
    echo "Deterministic decision succeeded; partial LLM fallback explanation was used (source=partial-fallback)."
  elif [[ "${source}" == "fallback" || "${llm_status}" == "fallback" ]]; then
    echo "Deterministic decision succeeded; rule-based fallback explanation was used (source=${source})."
  else
    echo "Deterministic decision succeeded; explanation source=${source:-unknown}, llmStatus=${llm_status:-unknown}."
  fi
}

describe_ui_badge() {
  local llm_status="$1"

  if [[ "${llm_status}" == "success" ]]; then
    echo "LLM-generated explanation"
  else
    echo "Rule-based fallback explanation"
  fi
}

echo "======================================"
echo "Azure AI Decision Pipeline Demo Startup"
echo "======================================"
echo

echo "0. Ensuring Docker is ready, then rebuilding and starting Docker Compose stack..."
ensure_docker_ready
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
  E2E_DECISION="$(parse_json_field "${SPRINGBOOT_RESPONSE}" "decision")"
  E2E_SOURCE="$(parse_json_field "${SPRINGBOOT_RESPONSE}" "source")"
  E2E_LLM_STATUS="$(parse_json_field "${SPRINGBOOT_RESPONSE}" "llmStatus")"
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
echo "End-to-end test result:"
echo "Decision: ${E2E_DECISION:-REVIEW}"
echo "Explanation: $(describe_explanation_result "${E2E_SOURCE:-unknown}" "${E2E_LLM_STATUS:-unknown}")"
echo "UI badge: $(describe_ui_badge "${E2E_LLM_STATUS:-fallback}")"