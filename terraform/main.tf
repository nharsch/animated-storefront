terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# --- IAM ---

resource "aws_iam_role" "lambda_role" {
  name = "animated-storefront-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# --- Secrets Manager ---

resource "aws_secretsmanager_secret" "anthropic_api_key" {
  name = "animated-storefront/anthropic-api-key"
}

resource "aws_secretsmanager_secret_version" "anthropic_api_key" {
  secret_id     = aws_secretsmanager_secret.anthropic_api_key.id
  secret_string = "placeholder"

  lifecycle {
    ignore_changes = [secret_string]
  }
}

resource "aws_iam_role_policy" "lambda_secrets" {
  name = "animated-storefront-lambda-secrets"
  role = aws_iam_role.lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = aws_secretsmanager_secret.anthropic_api_key.arn
    }]
  })
}

# --- Lambda ---

data "archive_file" "lambda_zip" {
  type        = "zip"
  source_dir  = "${path.module}/../lambda/chat"
  output_path = "${path.module}/.build/lambda.zip"
}

resource "aws_lambda_function" "chat_proxy" {
  filename         = data.archive_file.lambda_zip.output_path
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256
  function_name    = "animated-storefront-chat"
  role             = aws_iam_role.lambda_role.arn
  handler          = "index.handler"
  runtime          = "nodejs20.x"
  timeout = 30

  environment {
    variables = {
      SECRET_ARN = aws_secretsmanager_secret.anthropic_api_key.arn
    }
  }
}

# --- Function URL (no API Gateway needed) ---

resource "aws_lambda_function_url" "chat_url" {
  function_name      = aws_lambda_function.chat_proxy.function_name
  authorization_type = "NONE"

  cors {
    allow_origins = ["*"]
    allow_methods = ["POST"]
    allow_headers = ["content-type"]
    max_age       = 300
  }
}
