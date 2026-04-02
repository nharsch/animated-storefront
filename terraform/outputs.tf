output "chat_url" {
  description = "Lambda Function URL for the chat proxy"
  value       = aws_lambda_function_url.chat_url.function_url
}
