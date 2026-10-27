#!/usr/bin/env bash
# Part II: invokes the create-order Lambda directly (no API Gateway) with the JSON files in events/direct.
#
#   ./scripts/invoke-lambda.sh                       # all create-order*.json payloads
#   ./scripts/invoke-lambda.sh get-order find-orders # other functions: name suffix = payload file
set -euo pipefail

cd "$(dirname "$0")/.."

STACK_NAME="${STACK_NAME:-orders-api}"
REGION="${AWS_REGION:-$(aws configure get region || true)}"
REGION="${REGION:-us-east-1}"
mkdir -p target

invoke() {
  local function_name="$1" payload="$2"
  echo "==> ${function_name} <- ${payload}"
  cat "$payload"
  # --cli-binary-format is required by AWS CLI v2 to send the payload as raw JSON.
  aws lambda invoke --region "$REGION" \
    --function-name "$function_name" \
    --cli-binary-format raw-in-base64-out \
    --payload "file://${payload}" \
    --query '{StatusCode: StatusCode, FunctionError: FunctionError}' \
    --output text \
    target/lambda-response.json
  echo "--- response:"
  cat target/lambda-response.json
  echo
  echo
}

if [ "$#" -eq 0 ]; then
  for payload in events/direct/create-order*.json; do
    invoke "${STACK_NAME}-create-order" "$payload"
  done
else
  for name in "$@"; do
    target="$name"
    [ "$name" = "find-orders" ] && target="find-orders-by-customer"
    invoke "${STACK_NAME}-${target}" "events/direct/${name}.json"
  done
fi
