# Export springdoc OpenAPI to backend/apidocs.json
#
# The API must already be running with SWAGGER_ENABLED=true (off in production by default).
# Typical local:  $env:SWAGGER_ENABLED='true'; mvn -f backend/pom.xml spring-boot:run
#
# Usage (from repo root or backend/):
#   ./scripts/export-openapi.ps1
#   ./scripts/export-openapi.ps1 -ApiDocsUrl http://localhost:8080/v3/api-docs

param(
    [string]$ApiDocsUrl = "http://localhost:8080/v3/api-docs"
)

$ErrorActionPreference = "Stop"
$out = Join-Path $PSScriptRoot "..\apidocs.json"
Write-Host "GET $ApiDocsUrl -> $out"
$json = Invoke-RestMethod -Uri $ApiDocsUrl
[System.IO.File]::WriteAllText((Resolve-Path $out).Path, (($json | ConvertTo-Json -Depth 100 -Compress) + "`n"))
Write-Host "Wrote $out"
