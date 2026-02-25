#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="/tmp/bisq2-local-3node"
SESSION="bisq-local-3node"
APP_PATTERN='bisq2_seed1|bisq2_gui1|bisq2_api1'
API_URL="http://127.0.0.1:8090/api/v1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SEED_BIN="${REPO_DIR}/apps/seed-node-app/build/install/seed-node-app/bin/seed-node-app"
DESKTOP_BIN="${REPO_DIR}/apps/desktop/desktop-app/build/install/desktop-app/bin/desktop-app"
API_BIN="${REPO_DIR}/apps/api-app/build/install/api-app/bin/api-app"
SEED_PGREP='bisq\.seed_node\.SeedNodeApp.*--app-name=bisq2_seed1'
API_PGREP='bisq\.api_app\.ApiApp.*--app-name=bisq2_api1'
DESKTOP_PGREP='bisq\.desktop_app\.DesktopApp.*--app-name=bisq2_gui1'

seed_java_opts() {
  printf '%s' "-Dapplication.network.supportedTransportTypes.0=CLEAR -Dapplication.network.configByTransportType.clear.defaultNodePort=18000 -Dapplication.network.seedAddressByTransportType.clear.0=127.0.0.1:18000 -Dapplication.network.seedAddressByTransportType.clear.1=127.0.0.1:18000"
}

desktop_java_opts() {
  printf '%s' "-Dapplication.network.supportedTransportTypes.0=CLEAR -Dapplication.network.configByTransportType.clear.defaultNodePort=18001 -Dapplication.network.seedAddressByTransportType.clear.0=127.0.0.1:18000 -Dapplication.network.seedAddressByTransportType.clear.1=127.0.0.1:18000"
}

api_java_opts() {
  local rate_limit_enabled="${BISQ_USER_RATE_LIMIT_ENABLED:-${BISQ_CHAT_RATE_LIMIT_ENABLED:-true}}"
  printf '%s' "-Dapplication.network.supportedTransportTypes.0=CLEAR -Dapplication.network.configByTransportType.clear.defaultNodePort=18002 -Dapplication.network.seedAddressByTransportType.clear.0=127.0.0.1:18000 -Dapplication.network.seedAddressByTransportType.clear.1=127.0.0.1:18000 -Dapplication.api.server.restEnabled=true -Dapplication.api.server.websocketEnabled=true -Dapplication.api.server.bind.host=127.0.0.1 -Dapplication.api.server.bind.port=8090 -Dapplication.user.rateLimitEnabled=${rate_limit_enabled}"
}

backend_running() {
  pgrep -f "${SEED_PGREP}" >/dev/null 2>&1 && pgrep -f "${API_PGREP}" >/dev/null 2>&1
}

stop_screen_desktop() {
  screen -S "${SESSION}" -p desktop -X kill >/dev/null 2>&1 || true
  pkill -f "${DESKTOP_PGREP}" >/dev/null 2>&1 || true
}

status() {
  mkdir -p "${BASE_DIR}"
  echo "Session:"
  local screen_list
  screen_list="$(screen -ls 2>/dev/null || true)"
  if printf '%s\n' "${screen_list}" | rg -q "${SESSION}" || ps -ax -o command= 2>/dev/null | rg -q "SCREEN .*${SESSION}"; then
    echo "  ${SESSION} (running)"
  else
    echo "  ${SESSION} (not running)"
  fi

  echo "Processes:"
  local procs
  procs="$(ps -ax -o pid= -o command= 2>/dev/null | rg "${APP_PATTERN}" | rg -v 'rg |ps -ax|/bin/zsh -c' || true)"
  if [[ -n "${procs}" ]]; then
    echo "${procs}" | sed 's/^/  /'
  else
    echo "  none"
  fi

  echo "API:"
  local code
  code="$(curl -sS -o "${BASE_DIR}/status-api-body.txt" -w '%{http_code}' "${API_URL}" 2>/dev/null || true)"
  echo "  ${API_URL} -> ${code}"
}

stop() {
  screen -S "${SESSION}" -X quit >/dev/null 2>&1 || true
  pkill -f "bisq2_seed1" >/dev/null 2>&1 || true
  pkill -f "bisq2_gui1" >/dev/null 2>&1 || true
  pkill -f "bisq2_api1" >/dev/null 2>&1 || true
  echo "Stopped local 3-node setup (seed/gui/api)."
}

start() {
  require_binaries
  start_impl "persistent-users"
}

start_fresh() {
  require_binaries
  start_impl "fresh"
}

start_persistent() {
  start
}

require_binaries() {
  if [[ ! -x "${SEED_BIN}" || ! -x "${DESKTOP_BIN}" || ! -x "${API_BIN}" ]]; then
    echo "Missing app binaries. Build them first:"
    echo "  ./gradlew :apps:seed-node-app:installDist :apps:desktop:desktop-app:installDist :apps:api-app:installDist"
    exit 1
  fi
}

start_impl() {
  local mode="${1}"
  stop >/dev/null

  if [[ "${mode}" == "fresh" ]]; then
    rm -rf "${BASE_DIR}"
    mkdir -p "${BASE_DIR}/seed" "${BASE_DIR}/desktop" "${BASE_DIR}/api"
  elif [[ "${mode}" == "persistent-users" ]]; then
    # Keep desktop/api data so GUI+API identities survive restarts.
    rm -rf "${BASE_DIR}/seed"
    mkdir -p "${BASE_DIR}/seed" "${BASE_DIR}/desktop" "${BASE_DIR}/api"
  else
    echo "Unknown mode: ${mode}"
    exit 1
  fi

  : > "${BASE_DIR}/seed.log"
  : > "${BASE_DIR}/desktop.log"
  : > "${BASE_DIR}/api.log"

  local seed_opts
  local desktop_opts
  local api_opts
  seed_opts="$(seed_java_opts)"
  desktop_opts="$(desktop_java_opts)"
  api_opts="$(api_java_opts)"

  screen -S "${SESSION}" -X quit >/dev/null 2>&1 || true
  # Keep a stable bootstrap tab so session creation does not race with app startup failures.
  screen -dmS "${SESSION}" -t bootstrap sh -lc "while :; do sleep 3600; done"
  screen -S "${SESSION}" -X screen -t seed sh -lc "cd '${REPO_DIR}'; env JAVA_OPTS='${seed_opts}' '${SEED_BIN}' --app-name=bisq2_seed1 --data-dir='${BASE_DIR}/seed' 2>&1 | tee -a '${BASE_DIR}/seed.log'"
  screen -S "${SESSION}" -X screen -t desktop sh -lc "cd '${REPO_DIR}'; env JAVA_OPTS='${desktop_opts}' '${DESKTOP_BIN}' --app-name=bisq2_gui1 --data-dir='${BASE_DIR}/desktop' 2>&1 | tee -a '${BASE_DIR}/desktop.log'"
  screen -S "${SESSION}" -X screen -t api sh -lc "cd '${REPO_DIR}'; env JAVA_OPTS='${api_opts}' '${API_BIN}' --app-name=bisq2_api1 --data-dir='${BASE_DIR}/api' 2>&1 | tee -a '${BASE_DIR}/api.log'"
  screen -S "${SESSION}" -p bootstrap -X kill >/dev/null 2>&1 || true

  sleep 6
  if [[ "${mode}" == "persistent-users" ]]; then
    echo "Started local 3-node setup (persistent desktop/api users) in screen session '${SESSION}'."
  else
    echo "Started local 3-node setup in screen session '${SESSION}'."
  fi
  status
}

gui() {
  require_binaries
  if ! backend_running; then
    echo "Seed/API not detected; starting persistent local 3-node backend first."
    start >/dev/null
  fi

  mkdir -p "${BASE_DIR}/desktop"
  stop_screen_desktop

  local desktop_opts
  desktop_opts="$(desktop_java_opts)"

  echo "Launching desktop GUI in foreground with persistent user data."
  echo "Press Ctrl+C in this terminal to close the GUI window."
  (
    cd "${REPO_DIR}"
    env JAVA_OPTS="${desktop_opts}" \
      "${DESKTOP_BIN}" --app-name=bisq2_gui1 --data-dir="${BASE_DIR}/desktop"
  )
}

restart() {
  stop >/dev/null
  start
}

restart_persistent() {
  restart
}

restart_fresh() {
  stop >/dev/null
  start_fresh
}

start_visible() {
  start
  gui
}

restart_visible() {
  restart
  gui
}

usage() {
  cat <<'EOF'
Usage:
  scripts/local-3node.sh start
  scripts/local-3node.sh start-visible
  scripts/local-3node.sh start-fresh
  scripts/local-3node.sh start-persistent
  scripts/local-3node.sh restart
  scripts/local-3node.sh restart-visible
  scripts/local-3node.sh restart-fresh
  scripts/local-3node.sh restart-persistent
  scripts/local-3node.sh gui
  scripts/local-3node.sh status
  scripts/local-3node.sh stop
EOF
}

main() {
  local cmd="${1:-}"
  case "${cmd}" in
    start)
      start
      ;;
    start-visible)
      start_visible
      ;;
    start-fresh)
      start_fresh
      ;;
    start-persistent)
      start_persistent
      ;;
    restart)
      restart
      ;;
    restart-visible)
      restart_visible
      ;;
    restart-fresh)
      restart_fresh
      ;;
    restart-persistent)
      restart_persistent
      ;;
    status)
      status
      ;;
    stop)
      stop
      ;;
    gui)
      gui
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
