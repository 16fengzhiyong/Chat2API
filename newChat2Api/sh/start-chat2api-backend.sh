#!/usr/bin/env bash
set -euo pipefail

APP_HOME="${CHAT2API_BACKEND_HOME:-/home/chat2api-backend-boot-1.0.0}"
APP_BIN="${CHAT2API_BACKEND_BIN:-${APP_HOME}/bin/chat2api-backend}"
LOG_DIR="${CHAT2API_LOG_DIR:-${APP_HOME}/logs}"
LOG_FILE="${CHAT2API_LOG_FILE:-${LOG_DIR}/chat2api-backend.log}"
PID_FILE="${CHAT2API_PID_FILE:-${APP_HOME}/chat2api-backend.pid}"

export CHAT2API_SERVER_PORT="${CHAT2API_SERVER_PORT:-8080}"
export CHAT2API_DB_URL="${CHAT2API_DB_URL:-jdbc:mysql://127.0.0.1:3306/chat2api?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true}"
export CHAT2API_DB_USERNAME="${CHAT2API_DB_USERNAME:-root}"
if [ -z "${CHAT2API_DB_PASSWORD:-}" ]; then
  export CHAT2API_DB_PASSWORD='r3fF}8jFKN3mrEV4'
else
  export CHAT2API_DB_PASSWORD
fi
export CHAT2API_ENCRYPTION_KEY="${CHAT2API_ENCRYPTION_KEY:-uLRf3M2zQ6Np8eXK9vYc4HbT7SaW1DgE}"
export CHAT2API_JWT_SECRET="${CHAT2API_JWT_SECRET:-Yb7Qp2Lx9Vn4Rs8Dk5Gh3Tm6Zw1Ce0Fa}"
export CHAT2API_JWT_TTL_SECONDS="${CHAT2API_JWT_TTL_SECONDS:-86400}"
export CHAT2API_CORS_ALLOWED_ORIGINS="${CHAT2API_CORS_ALLOWED_ORIGINS:-http://localhost:*,http://127.0.0.1:*,https://www.likegpt.top,https://likegpt.top}"
export CHAT2API_MAX_REQUEST_BODY_BYTES="${CHAT2API_MAX_REQUEST_BODY_BYTES:-1048576}"
export CHAT2API_RATE_LIMIT_WINDOW_SECONDS="${CHAT2API_RATE_LIMIT_WINDOW_SECONDS:-60}"
export CHAT2API_AUTH_RATE_LIMIT="${CHAT2API_AUTH_RATE_LIMIT:-10}"
export CHAT2API_REPORTER_RATE_LIMIT="${CHAT2API_REPORTER_RATE_LIMIT:-60}"
export CHAT2API_REQUEST_TIMEOUT_MS="${CHAT2API_REQUEST_TIMEOUT_MS:-60000}"
export CHAT2API_RETRY_COUNT="${CHAT2API_RETRY_COUNT:-3}"

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: java command not found. Please install JDK/JRE 17 and ensure java is in PATH." >&2
  exit 1
fi

if [ ! -x "${APP_BIN}" ]; then
  echo "ERROR: backend executable not found or not executable: ${APP_BIN}" >&2
  echo "Please check deployment path or run: chmod +x ${APP_BIN}" >&2
  exit 1
fi

mkdir -p "${LOG_DIR}"

if [ -f "${PID_FILE}" ]; then
  OLD_PID="$(cat "${PID_FILE}")"
  if [ -n "${OLD_PID}" ] && kill -0 "${OLD_PID}" >/dev/null 2>&1; then
    echo "Chat2API backend is already running, pid=${OLD_PID}"
    echo "Log file: ${LOG_FILE}"
    exit 0
  fi
fi

nohup "${APP_BIN}" "$@" >> "${LOG_FILE}" 2>&1 &
APP_PID="$!"
echo "${APP_PID}" > "${PID_FILE}"

echo "Chat2API backend started, pid=${APP_PID}"
echo "Log file: ${LOG_FILE}"
