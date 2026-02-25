#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

HARNESS_DIR="${HARNESS_DIR:-/tmp/bisq2-ui-harness}"
DATA_DIR="${DATA_DIR:-${HARNESS_DIR}/data}"
ARTIFACTS_DIR="${ARTIFACTS_DIR:-${HARNESS_DIR}/artifacts}"
LOG_FILE="${LOG_FILE:-${HARNESS_DIR}/desktop.log}"
PID_FILE="${PID_FILE:-${HARNESS_DIR}/desktop.pid}"
TOKEN_FILE="${TOKEN_FILE:-${HARNESS_DIR}/automation.token}"
APP_NAME="${APP_NAME:-bisq2_gui_harness}"
AUTOMATION_HOST="${AUTOMATION_HOST:-127.0.0.1}"
AUTOMATION_PORT="${AUTOMATION_PORT:-18180}"
WINDOW_WIDTH="${WINDOW_WIDTH:-1440}"
WINDOW_HEIGHT="${WINDOW_HEIGHT:-900}"
P2P_PORT="${P2P_PORT:-}"
HARNESS_RESET_ON_START="${HARNESS_RESET_ON_START:-1}"

DESKTOP_BIN="${REPO_DIR}/apps/desktop/desktop-app/build/install/desktop-app/bin/desktop-app"

# Optional network override for deterministic local clusters.
# Example:
# HARNESS_NETWORK_OPTS="-Dapplication.network.supportedTransportTypes.0=CLEAR -Dapplication.network.configByTransportType.clear.defaultNodePort=18101 -Dapplication.network.seedAddressByTransportType.clear.0=127.0.0.1:18000 -Dapplication.network.seedAddressByTransportType.clear.1=127.0.0.1:18000"
HARNESS_NETWORK_OPTS="${HARNESS_NETWORK_OPTS:-}"
LOG_LINES="${LOG_LINES:-200}"

AUTOMATION_URL="http://${AUTOMATION_HOST}:${AUTOMATION_PORT}"
AUTOMATION_HEADER_NAME="X-Bisq-Automation-Token"

usage() {
  cat <<'EOF'
Usage:
  scripts/desktop-ui-harness.bash start
  scripts/desktop-ui-harness.bash stop
  scripts/desktop-ui-harness.bash restart
  scripts/desktop-ui-harness.bash status
  scripts/desktop-ui-harness.bash health
  scripts/desktop-ui-harness.bash logs
  scripts/desktop-ui-harness.bash tail
  scripts/desktop-ui-harness.bash nodes
  scripts/desktop-ui-harness.bash screenshot [name]
  scripts/desktop-ui-harness.bash click <id>
  scripts/desktop-ui-harness.bash type <id> <text>
  scripts/desktop-ui-harness.bash wait-node <id> [timeout_ms] [visible]
  scripts/desktop-ui-harness.bash press-key <key> [id]
  scripts/desktop-ui-harness.bash scenario <scenario-file>

Environment overrides:
  HARNESS_DIR, DATA_DIR, ARTIFACTS_DIR, APP_NAME
  AUTOMATION_HOST, AUTOMATION_PORT
  WINDOW_WIDTH, WINDOW_HEIGHT, P2P_PORT, HARNESS_RESET_ON_START
  HARNESS_NETWORK_OPTS, LOG_LINES
EOF
}

ensure_dirs() {
  mkdir -p "${HARNESS_DIR}" "${DATA_DIR}" "${ARTIFACTS_DIR}"
}

is_running() {
  [[ -f "${PID_FILE}" ]] || return 1
  local pid
  pid="$(cat "${PID_FILE}")"
  [[ -n "${pid}" ]] || return 1
  kill -0 "${pid}" >/dev/null 2>&1
}

require_token() {
  if [[ ! -f "${TOKEN_FILE}" ]]; then
    echo "Token file missing: ${TOKEN_FILE}"
    exit 1
  fi
}

generate_token() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 24
  elif [[ -r /dev/urandom ]]; then
    od -An -tx1 -N24 /dev/urandom | tr -d ' \n'
  else
    echo "No secure token generator available (missing openssl and /dev/urandom)." >&2
    return 1
  fi
}

is_port_bound() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
  elif command -v ss >/dev/null 2>&1; then
    ss -nlt "( sport = :${port} )" 2>/dev/null | grep -Eq "[\\.:]${port}[[:space:]]"
  elif command -v netstat >/dev/null 2>&1; then
    netstat -an 2>/dev/null | grep -E "LISTEN|LISTENING" | grep -Eq "[\\.:]${port}[[:space:]]"
  else
    echo "No port inspection tool available (need lsof, ss, or netstat)." >&2
    return 2
  fi
}

find_free_port() {
  local start="${1:-19101}"
  local end="${2:-19250}"
  local port status
  for port in $(seq "${start}" "${end}"); do
    is_port_bound "${port}"
    status=$?
    if (( status == 0 )); then
      continue
    elif (( status == 1 )); then
      echo "${port}"
      return 0
    else
      return "${status}"
    fi
  done
  return 1
}

auth_header() {
  local token
  token="$(cat "${TOKEN_FILE}")"
  printf '%s: %s' "${AUTOMATION_HEADER_NAME}" "${token}"
}

api_request() {
  local response_file
  response_file="$(mktemp "${HARNESS_DIR}/api-response.XXXXXX")"
  local code
  code="$(curl -sS -o "${response_file}" -w '%{http_code}' "$@")" || {
    rm -f "${response_file}" >/dev/null 2>&1 || true
    return 1
  }
  cat "${response_file}"
  rm -f "${response_file}" >/dev/null 2>&1 || true
  if (( code < 200 || code >= 300 )); then
    return 1
  fi
}

require_ok_status_json() {
  local response="${1:-}"
  if command -v jq >/dev/null 2>&1; then
    if ! printf '%s' "${response}" | jq -e '.status == "ok"' >/dev/null 2>&1; then
      echo "Automation response did not contain status=ok: ${response}" >&2
      return 1
    fi
  elif [[ ! "${response}" =~ \"status\"[[:space:]]*:[[:space:]]*\"ok\" ]]; then
    echo "Automation response did not contain status=ok: ${response}" >&2
    return 1
  fi
}

health_status_code() {
  require_token
  curl -sS -o /dev/null -w '%{http_code}' \
    -H "$(auth_header)" \
    "${AUTOMATION_URL}/health" || true
}

wait_for_health() {
  local timeout_sec="${1:-40}"
  local start now code
  start="$(date +%s)"
  while true; do
    code="$(health_status_code)"
    if [[ "${code}" == "200" ]]; then
      return 0
    fi
    now="$(date +%s)"
    if (( now - start >= timeout_sec )); then
      return 1
    fi
    sleep 1
  done
}

require_binary() {
  if [[ ! -x "${DESKTOP_BIN}" ]]; then
    echo "Missing desktop binary. Build first:"
    echo "  ./gradlew :apps:desktop:desktop-app:installDist"
    exit 1
  fi
}

start() {
  require_binary
  ensure_dirs

  if is_running; then
    echo "Harness already running (pid=$(cat "${PID_FILE}"))."
    status
    return 0
  fi

  if [[ "${HARNESS_RESET_ON_START}" == "1" ]]; then
    rm -rf "${DATA_DIR}"
    mkdir -p "${DATA_DIR}"
  fi

  local token
  token="$(generate_token)"
  printf '%s\n' "${token}" > "${TOKEN_FILE}"
  chmod 600 "${TOKEN_FILE}"

  local automation_opts
  automation_opts="-Dapplication.desktop.automation.enabled=true -Dapplication.desktop.automation.bind.host=${AUTOMATION_HOST} -Dapplication.desktop.automation.bind.port=${AUTOMATION_PORT} -Dapplication.desktop.automation.token=${token} -Dapplication.desktop.automation.artifacts.dir=${ARTIFACTS_DIR} -Dapplication.desktop.automation.window.width=${WINDOW_WIDTH} -Dapplication.desktop.automation.window.height=${WINDOW_HEIGHT}"

  local network_opts
  network_opts="${HARNESS_NETWORK_OPTS}"
  if [[ -z "${network_opts}" ]]; then
    local selected_port
    selected_port="${P2P_PORT}"
    if [[ -z "${selected_port}" ]]; then
      selected_port="$(find_free_port 19101 19250 || true)"
    fi
    if [[ -z "${selected_port}" ]]; then
      echo "No free P2P port found in 19101-19250; set P2P_PORT or HARNESS_NETWORK_OPTS."
      exit 1
    fi
    printf '%s\n' "${selected_port}" > "${HARNESS_DIR}/p2p-port"
    network_opts="-Dapplication.network.supportedTransportTypes.0=CLEAR -Dapplication.network.configByTransportType.clear.defaultNodePort=${selected_port} -Dapplication.network.seedAddressByTransportType.clear.0=127.0.0.1:${selected_port} -Dapplication.network.seedAddressByTransportType.clear.1=127.0.0.1:${selected_port}"
  fi

  local combined_opts
  combined_opts="${JAVA_OPTS:-} ${network_opts} ${automation_opts}"

  : > "${LOG_FILE}"
  (
    cd "${REPO_DIR}"
    exec env JAVA_OPTS="${combined_opts}" \
      "${DESKTOP_BIN}" \
      --app-name="${APP_NAME}" \
      --data-dir="${DATA_DIR}" >> "${LOG_FILE}" 2>&1
  ) &
  local pid=$!
  printf '%s\n' "${pid}" > "${PID_FILE}"

  if wait_for_health 45; then
    echo "Desktop UI harness started."
    status
  else
    echo "Harness failed to become healthy. Last log lines:"
    tail -n "${LOG_LINES}" "${LOG_FILE}" || true
    if is_running; then
      stop >/dev/null 2>&1 || true
    else
      rm -f "${PID_FILE}" "${TOKEN_FILE}" >/dev/null 2>&1 || true
    fi
    exit 1
  fi
}

stop() {
  if ! is_running; then
    rm -f "${PID_FILE}" "${TOKEN_FILE}" >/dev/null 2>&1 || true
    echo "Harness not running."
    return 0
  fi

  local pid
  pid="$(cat "${PID_FILE}")"
  kill "${pid}" >/dev/null 2>&1 || true

  for _ in $(seq 1 20); do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
      break
    fi
    sleep 0.2
  done

  if kill -0 "${pid}" >/dev/null 2>&1; then
    kill -9 "${pid}" >/dev/null 2>&1 || true
  fi

  rm -f "${PID_FILE}" "${TOKEN_FILE}" >/dev/null 2>&1 || true
  echo "Desktop UI harness stopped."
}

status() {
  echo "Harness paths:"
  echo "  dir: ${HARNESS_DIR}"
  echo "  data: ${DATA_DIR}"
  echo "  artifacts: ${ARTIFACTS_DIR}"
  echo "  log: ${LOG_FILE}"
  if [[ -f "${HARNESS_DIR}/p2p-port" ]]; then
    echo "  p2p-port: $(cat "${HARNESS_DIR}/p2p-port")"
  fi
  if is_running; then
    echo "Process:"
    echo "  pid=$(cat "${PID_FILE}") running"
  else
    echo "Process:"
    echo "  not running"
  fi
  if [[ -f "${TOKEN_FILE}" ]]; then
    echo "Automation:"
    echo "  ${AUTOMATION_URL} -> $(health_status_code)"
  else
    echo "Automation:"
    echo "  token not present"
  fi
}

health() {
  require_token
  local response
  response="$(api_request -H "$(auth_header)" "${AUTOMATION_URL}/health")"
  echo "${response}"
  require_ok_status_json "${response}"
}

nodes() {
  require_token
  api_request -H "$(auth_header)" "${AUTOMATION_URL}/nodes"
  echo
}

screenshot() {
  require_token
  local name="${1:-shot}"
  local response
  response="$(api_request --request POST --get \
    --data-urlencode "name=${name}" \
    -H "$(auth_header)" \
    "${AUTOMATION_URL}/screenshot")"
  echo "${response}"
  require_ok_status_json "${response}"
}

click() {
  require_token
  local id="${1:-}"
  if [[ -z "${id}" ]]; then
    echo "Usage: scripts/desktop-ui-harness.bash click <id>"
    exit 1
  fi
  local response
  response="$(api_request --request POST --get \
    --data-urlencode "id=${id}" \
    -H "$(auth_header)" \
    "${AUTOMATION_URL}/action/click")"
  echo "${response}"
  require_ok_status_json "${response}"
}

type_text() {
  require_token
  local id="${1:-}"
  shift || true
  local text="${*:-}"
  if [[ -z "${id}" || -z "${text}" ]]; then
    echo "Usage: scripts/desktop-ui-harness.bash type <id> <text>"
    exit 1
  fi
  local response
  response="$(api_request --request POST --get \
    --data-urlencode "id=${id}" \
    --data-urlencode "text=${text}" \
    -H "$(auth_header)" \
    "${AUTOMATION_URL}/action/type")"
  echo "${response}"
  require_ok_status_json "${response}"
}

wait_node() {
  require_token
  local id="${1:-}"
  local timeout_ms="${2:-5000}"
  local visible="${3:-false}"
  if [[ -z "${id}" ]]; then
    echo "Usage: scripts/desktop-ui-harness.bash wait-node <id> [timeout_ms] [visible]"
    exit 1
  fi
  local response
  response="$(api_request --request POST --get \
    --data-urlencode "id=${id}" \
    --data-urlencode "timeoutMs=${timeout_ms}" \
    --data-urlencode "visible=${visible}" \
    -H "$(auth_header)" \
    "${AUTOMATION_URL}/wait/node")"
  echo "${response}"
  require_ok_status_json "${response}"
}

press_key() {
  require_token
  local key="${1:-}"
  local id="${2:-}"
  if [[ -z "${key}" ]]; then
    echo "Usage: scripts/desktop-ui-harness.bash press-key <key> [id]"
    exit 1
  fi
  local response
  response="$(api_request --request POST --get \
    --data-urlencode "key=${key}" \
    --data-urlencode "id=${id}" \
    -H "$(auth_header)" \
    "${AUTOMATION_URL}/action/pressKey")"
  echo "${response}"
  require_ok_status_json "${response}"
}

run_scenario() {
  local scenario_file="${1:-}"
  if [[ -z "${scenario_file}" ]]; then
    echo "Usage: scripts/desktop-ui-harness.bash scenario <scenario-file>"
    exit 1
  fi
  if [[ ! -f "${scenario_file}" ]]; then
    echo "Scenario file not found: ${scenario_file}"
    exit 1
  fi

  local line
  local line_no=0
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line_no=$((line_no + 1))
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    if [[ -z "${line}" || "${line:0:1}" == "#" ]]; then
      continue
    fi

    local cmd="${line%%[[:space:]]*}"
    local args=""
    if [[ "${line}" == *[[:space:]]* ]]; then
      args="${line#${cmd}}"
      args="${args#"${args%%[![:space:]]*}"}"
    fi

    case "${cmd}" in
      health)
        health
        ;;
      nodes)
        nodes
        ;;
      wait-node)
        local wait_node_id=""
        local wait_timeout_ms="5000"
        local wait_visible="false"
        read -r wait_node_id wait_timeout_ms wait_visible _ <<< "${args}"
        wait_node "${wait_node_id}" "${wait_timeout_ms:-5000}" "${wait_visible:-false}"
        ;;
      click)
        local click_id=""
        read -r click_id _ <<< "${args}"
        click "${click_id}"
        ;;
      type)
        if [[ ! "${args}" =~ ^([^[:space:]]+)[[:space:]]+(.+)$ ]]; then
          echo "Invalid type command at line ${line_no}: ${line}"
          return 1
        fi
        local target_id="${BASH_REMATCH[1]}"
        local text="${BASH_REMATCH[2]}"
        if [[ "${text}" =~ ^\"(.*)\"$ ]]; then
          text="${BASH_REMATCH[1]}"
        elif [[ "${text}" =~ ^\'(.*)\'$ ]]; then
          text="${BASH_REMATCH[1]}"
        fi
        type_text "${target_id}" "${text}"
        ;;
      press-key)
        local key=""
        local press_id=""
        read -r key press_id _ <<< "${args}"
        press_key "${key}" "${press_id}"
        ;;
      screenshot)
        local shot_name="shot"
        read -r shot_name _ <<< "${args}"
        screenshot "${shot_name:-shot}"
        ;;
      sleep)
        local ms="${args%%[[:space:]]*}"
        if [[ ! "${ms}" =~ ^[0-9]+$ ]]; then
          echo "Invalid sleep value at line ${line_no}: ${line}"
          return 1
        fi
        local sec
        sec="$(awk "BEGIN {printf \"%.3f\", ${ms} / 1000}")"
        sleep "${sec}"
        ;;
      *)
        echo "Unknown scenario command at line ${line_no}: ${cmd}"
        return 1
        ;;
    esac
  done < "${scenario_file}"

  echo "Scenario completed: ${scenario_file}"
}

logs() {
  sed -n "1,${LOG_LINES}p" "${LOG_FILE}"
}

tail_logs() {
  tail -n "${LOG_LINES}" "${LOG_FILE}"
}

restart() {
  stop
  start
}

main() {
  local cmd="${1:-}"
  shift || true
  case "${cmd}" in
    start) start ;;
    stop) stop ;;
    restart) restart ;;
    status) status ;;
    health) health ;;
    nodes) nodes ;;
    screenshot) screenshot "$@" ;;
    click) click "$@" ;;
    type) type_text "$@" ;;
    wait-node) wait_node "$@" ;;
    press-key) press_key "$@" ;;
    scenario) run_scenario "$@" ;;
    logs) logs ;;
    tail) tail_logs ;;
    *) usage; exit 1 ;;
  esac
}

main "$@"
