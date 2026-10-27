#!/usr/bin/env bash
# Builds the JAR and deploys both architectures (A: mapping templates, B: proxy) with CloudFormation.
#
#   STACK_NAME=orders-api AWS_REGION=us-east-1 ./scripts/deploy.sh
set -euo pipefail

cd "$(dirname "$0")/.."

STACK_NAME="${STACK_NAME:-orders-api}"
REGION="${AWS_REGION:-$(aws configure get region || true)}"
REGION="${REGION:-us-east-1}"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
BUCKET="${ARTIFACT_BUCKET:-${STACK_NAME}-artifacts-${ACCOUNT_ID}-${REGION}}"

echo "==> Building"
mvn -q clean package

echo "==> Uploading JAR to s3://${BUCKET}"
if ! aws s3api head-bucket --bucket "$BUCKET" --region "$REGION" 2>/dev/null; then
  aws s3 mb "s3://${BUCKET}" --region "$REGION"
fi
aws cloudformation package \
  --region "$REGION" \
  --template-file infra/template.yaml \
  --s3-bucket "$BUCKET" \
  --s3-prefix "$STACK_NAME" \
  --output-template-file target/packaged.yaml

echo "==> Deploying stack ${STACK_NAME} in ${REGION}"
aws cloudformation deploy \
  --region "$REGION" \
  --template-file target/packaged.yaml \
  --stack-name "$STACK_NAME" \
  --capabilities CAPABILITY_IAM \
  --no-fail-on-empty-changeset

output() {
  aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK_NAME" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}

# CloudFormation does not create a new API deployment when only the RestApi Body changes,
# so publish the current definition of both APIs to the stage explicitly.
echo "==> Publishing API stages"
for key in MappingApiId ProxyApiId; do
  aws apigateway create-deployment --region "$REGION" --rest-api-id "$(output "$key")" \
    --stage-name prod --description "deploy.sh $(date -u +%Y-%m-%dT%H:%M:%SZ)" > /dev/null
done

aws cloudformation describe-stacks --region "$REGION" --stack-name "$STACK_NAME" \
  --query "Stacks[0].Outputs" --output table
