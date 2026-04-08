output "chat_url" {
  description = "API Gateway URL for the chat proxy"
  value       = aws_apigatewayv2_stage.default.invoke_url
}
