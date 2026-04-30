param(
    [string]$MysqlUrl = "jdbc:mysql://host.docker.internal:3306/customer_agent?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
    [string]$MysqlUsername = "root",
    [string]$MysqlPassword = "password",
    [string]$RedisHost = "host.docker.internal",
    [string]$RedisPort = "6379",
    [string]$RabbitMqHost = "host.docker.internal",
    [string]$RabbitMqPort = "5672",
    [string]$RabbitMqUsername = "guest",
    [string]$RabbitMqPassword = "guest",
    [string]$EsHost = "http://host.docker.internal:9200",
    [string]$EsIndex = "customer_service_agent_ollama",
    [string]$EsEmbeddingDimension = "768",
    [string]$LlmBaseUrl = "http://host.docker.internal:11434/v1",
    [string]$LlmApiKey = "ollama",
    [string]$LlmModel = "qwen2.5:7b",
    [string]$LlmEmbeddingModel = "nomic-embed-text:latest",
    [string]$Port = "8092",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$mavenCacheDir = Join-Path $projectRoot ".m2-cache"
$mavenCommand = if ($SkipTests) { "./mvnw spring-boot:run -DskipTests" } else { "./mvnw spring-boot:run" }

if (-not (Test-Path $mavenCacheDir)) {
    New-Item -ItemType Directory -Path $mavenCacheDir | Out-Null
}

Write-Host "Starting customer-service-multi-agent-langchain4j with Docker Maven..." -ForegroundColor Cyan
Write-Host "Project root: $projectRoot"
Write-Host "Maven cache: $mavenCacheDir"
Write-Host "App port: $Port"
Write-Host "LLM model: $LlmModel"
Write-Host "Embedding model: $LlmEmbeddingModel"

docker run --rm -it `
  -p "${Port}:8092" `
  -e "MYSQL_URL=$MysqlUrl" `
  -e "MYSQL_USERNAME=$MysqlUsername" `
  -e "MYSQL_PASSWORD=$MysqlPassword" `
  -e "REDIS_HOST=$RedisHost" `
  -e "REDIS_PORT=$RedisPort" `
  -e "RABBITMQ_HOST=$RabbitMqHost" `
  -e "RABBITMQ_PORT=$RabbitMqPort" `
  -e "RABBITMQ_USERNAME=$RabbitMqUsername" `
  -e "RABBITMQ_PASSWORD=$RabbitMqPassword" `
  -e "ES_HOST=$EsHost" `
  -e "ES_INDEX=$EsIndex" `
  -e "ES_EMBEDDING_DIMENSION=$EsEmbeddingDimension" `
  -e "LLM_BASE_URL=$LlmBaseUrl" `
  -e "LLM_API_KEY=$LlmApiKey" `
  -e "LLM_MODEL=$LlmModel" `
  -e "LLM_EMBEDDING_MODEL=$LlmEmbeddingModel" `
  -v "${projectRoot}:/app" `
  -v "${mavenCacheDir}:/root/.m2" `
  -w /app `
  maven:3.9.9-eclipse-temurin-17 `
  sh -lc $mavenCommand
