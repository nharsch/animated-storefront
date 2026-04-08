# Deploy Playbook

## Prerequisites

- AWS CLI configured (`aws configure`)
- Terraform installed
- Node.js / npm installed

## One-time setup

### GitHub Pages branch

```bash
git checkout --orphan gh-pages
git rm -rf .
git commit --allow-empty -m "init"
git push origin gh-pages
git checkout master
```

Enable GitHub Pages in the repo settings, pointing at the `gh-pages` branch.

## Deploy

### 1. Provision infrastructure

```bash
cd terraform
terraform init
AWS_PROFILE=nharsch terraform apply
```

This creates:
- AWS Secrets Manager secret (`animated-storefront/anthropic-api-key`) — value must be set manually (see below)
- Lambda function (`animated-storefront-chat`)
- Lambda Function URL (with CORS)
- IAM role scoped to read the secret and write logs

**Set the secret value** (first deploy only):
```bash
aws secretsmanager put-secret-value \
  --secret-id animated-storefront/anthropic-api-key \
  --secret-string "sk-..." \
  --profile nharsch
```

### 2. Build frontend

```bash
cd ..
CHAT_API_URL=$(cd terraform && terraform output -raw chat_url) npx shadow-cljs release app
```

The Lambda Function URL is baked into the compiled JS at build time.

### 3. Push to GitHub Pages

```bash
./scripts/deploy-gh-pages.sh
```

Site will be live at: https://nharsch.github.io/animated-storefront/

## Updating the Lambda

Re-running `terraform apply` zips and redeploys `lambda/chat/` automatically if the source has changed (`source_code_hash` detects it).

## Rotating the API key

```bash
aws secretsmanager put-secret-value \
  --secret-id animated-storefront/anthropic-api-key \
  --secret-string "sk-new-key..." \
  --profile nharsch
```

The Lambda picks it up on next cold start (cached per warm container). Terraform is not involved.
