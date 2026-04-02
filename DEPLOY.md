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
terraform apply -var="anthropic_api_key=sk-..."
```

This creates:
- AWS Secrets Manager secret (`animated-storefront/anthropic-api-key`)
- Lambda function (`animated-storefront-chat`)
- Lambda Function URL (with CORS)
- IAM role scoped to read the secret and write logs

### 2. Build frontend

```bash
cd ..
CHAT_API_URL=$(cd terraform && terraform output -raw chat_url) npx shadow-cljs release app
```

The Lambda Function URL is baked into the compiled JS at build time.

### 3. Push to GitHub Pages

```bash
cd public
git init
git checkout -b gh-pages
git add -A
git commit -m "Deploy $(date -u +%Y-%m-%dT%H:%M:%SZ)"
git remote add origin git@github.com:nharsch/animated-storefront.git
git push --force origin gh-pages
rm -rf .git
cd ..
```

Site will be live at: https://nharsch.github.io/animated-storefront/

## Updating the Lambda

Re-running `terraform apply` zips and redeploys `lambda/chat/` automatically if the source has changed (`source_code_hash` detects it).

## Rotating the API key

```bash
cd terraform
terraform apply -var="anthropic_api_key=sk-new-key..."
```

This updates the Secrets Manager secret version. The Lambda picks it up on next cold start (cached per warm container).
