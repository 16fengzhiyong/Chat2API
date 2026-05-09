#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${APP_ROOT}/backend-java"

export CHAT2API_SERVER_PORT="${CHAT2API_SERVER_PORT:-8080}"
export CHAT2API_DB_URL="${CHAT2API_DB_URL:-jdbc:mysql://127.0.0.1:3306/chat2api?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true}"
export CHAT2API_DB_USERNAME="${CHAT2API_DB_USERNAME:-root}"
if [ -z "${CHAT2API_DB_PASSWORD:-}" ]; then
  export CHAT2API_DB_PASSWORD='r3fF}8jFKN3mrEV4'
else
  export CHAT2API_DB_PASSWORD
fi
export CHAT2API_ENCRYPTION_KEY="${CHAT2API_ENCRYPTION_KEY:-uLRf3M2zQ6Np8eXK9vYc4HbT7SaW1DgE}"
export CHAT2API_ADMIN_USERNAME="${CHAT2API_ADMIN_USERNAME:-admin}"
export CHAT2API_ADMIN_PASSWORD="${CHAT2API_ADMIN_PASSWORD:-admin123456}"
export CHAT2API_JWT_SECRET="${CHAT2API_JWT_SECRET:-Yb7Qp2Lx9Vn4Rs8Dk5Gh3Tm6Zw1Ce0Fa}"
export CHAT2API_JWT_TTL_SECONDS="${CHAT2API_JWT_TTL_SECONDS:-86400}"
export CHAT2API_REPORTER_REGISTRATION_CODE="${CHAT2API_REPORTER_REGISTRATION_CODE:-c2a-reporter-7Kp9xQ4mR6vN2sH8}"
export CHAT2API_CORS_ALLOWED_ORIGINS="${CHAT2API_CORS_ALLOWED_ORIGINS:-http://localhost:*,http://127.0.0.1:*}"
export CHAT2API_MAX_REQUEST_BODY_BYTES="${CHAT2API_MAX_REQUEST_BODY_BYTES:-1048576}"
export CHAT2API_RATE_LIMIT_WINDOW_SECONDS="${CHAT2API_RATE_LIMIT_WINDOW_SECONDS:-60}"
export CHAT2API_AUTH_RATE_LIMIT="${CHAT2API_AUTH_RATE_LIMIT:-10}"
export CHAT2API_REPORTER_RATE_LIMIT="${CHAT2API_REPORTER_RATE_LIMIT:-60}"
export CHAT2API_REQUEST_TIMEOUT_MS="${CHAT2API_REQUEST_TIMEOUT_MS:-60000}"
export CHAT2API_RETRY_COUNT="${CHAT2API_RETRY_COUNT:-3}"

if command -v chat2api-backend >/dev/null 2>&1; then
  exec chat2api-backend "$@"
fi

if [ -x "${APP_ROOT}/chat2api-backend" ]; then
  exec "${APP_ROOT}/chat2api-backend" "$@"
fi

if [ -x "${BACKEND_DIR}/chat2api-backend" ]; then
  exec "${BACKEND_DIR}/chat2api-backend" "$@"
fi

JAR_FILE="$(find "${BACKEND_DIR}/build/libs" -maxdepth 1 -type f -name '*.jar' ! -name '*plain.jar' 2>/dev/null | head -n 1 || true)"
if [ -n "${JAR_FILE}" ]; then
  exec java -jar "${JAR_FILE}" "$@"
fi

exec "${BACKEND_DIR}/gradlew" bootRun --no-daemon --args="$*"
