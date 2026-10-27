#!/usr/bin/env bash
# Deletes the CloudFormation stack (Lambdas, APIs, role). Pass --bucket to also empty and remove
# the artifact bucket created by deploy.sh.
set -euo pipefail

STACK_NAME="${STACK_NAME:-orders-api}"
REGION="${AWS_REGION:-$(aws configure get region || true)}"
REGION="${REGION:-us-east-1}"

aws cloudformation delete-stack --region "$REGION" --stack-name "$STACK_NAME"
aws cloudformation wait stack-delete-complete --region "$REGION" --stack-name "$STACK_NAME"
echo "Stack ${STACK_NAME} deleted."

if [ "${1:-}" = "--bucket" ]; then
  ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
  BUCKET="${ARTIFACT_BUCKET:-${STACK_NAME}-artifacts-${ACCOUNT_ID}-${REGION}}"
  aws s3 rb "s3://${BUCKET}" --force --region "$REGION"
fi
