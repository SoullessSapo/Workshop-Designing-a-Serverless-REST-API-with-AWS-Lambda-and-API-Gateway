#!/usr/bin/env bash
# Calls every documented case of the API with curl and prints status line, headers and body.
#
#   ./scripts/smoke-test.sh proxy     # Architecture B (default)
#   ./scripts/smoke-test.sh mapping   # Architecture A
#   ./scripts/smoke-test.sh https://abc123.execute-api.us-east-1.amazonaws.com/prod
set -euo pipefail

STACK_NAME="${STACK_NAME:-orders-api}"
REGION="${AWS_REGION:-$(aws configure get region 2>/dev/null || true)}"
REGION="${REGION:-us-east-1}"
TARGET="${1:-proxy}"

case "$TARGET" in
  http*) BASE_URL="$TARGET" ;;
  mapping) KEY=MappingApiUrl ;;
  proxy) KEY=ProxyApiUrl ;;
  *) echo "usage: $0 [proxy|mapping|<base-url>]" >&2; exit 1 ;;
esac
if [ -z "${BASE_URL:-}" ]; then
  BASE_URL="$(aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK_NAME" \
    --query "Stacks[0].Outputs[?OutputKey=='${KEY}'].OutputValue" --output text)"
fi

call() {
  local title="$1"; shift
  echo
  echo "### ${title}"
  echo "\$ curl $*"
  curl -sS -i "$@"
  echo
}

echo "Base URL: ${BASE_URL}"

call "POST /orders (valid) -> 201 + Location" \
  -X POST "${BASE_URL}/orders" -H 'Content-Type: application/json' \
  -d '{"customerId":"CUST-42","product":"MacBook Air","quantity":1}'

call "POST /orders (business rules broken) -> 400" \
  -X POST "${BASE_URL}/orders" -H 'Content-Type: application/json' \
  -d '{"customerId":"CUST-42","quantity":0}'

call "POST /orders (malformed JSON) -> 400" \
  -X POST "${BASE_URL}/orders" -H 'Content-Type: application/json' \
  -d '{"customerId": '

call "GET /orders/ORD-42 -> 200" "${BASE_URL}/orders/ORD-42"

call "GET /orders/ORD-999 -> 404" "${BASE_URL}/orders/ORD-999"

call "GET /orders?customerId=CUST-42 -> 200 (two seeded orders, plus any created in this environment)" \
  "${BASE_URL}/orders?customerId=CUST-42"

call "GET /orders?customerId=CUST-100 -> 200 []" "${BASE_URL}/orders?customerId=CUST-100"

call "GET /orders (no customerId) -> 400" "${BASE_URL}/orders"

# Last, because it removes a seeded order from this execution environment.
call "DELETE /orders/ORD-42 -> 204" -X DELETE "${BASE_URL}/orders/ORD-42"

call "DELETE /orders/ORD-42 again -> 404 (if served by the same warm environment)" -X DELETE "${BASE_URL}/orders/ORD-42"

call "DELETE /orders/ORD-999 -> 404" -X DELETE "${BASE_URL}/orders/ORD-999"
