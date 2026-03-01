$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
docker compose -f "$repoRoot\local\docker-compose.yml" down
Write-Host "Local cluster stopped."
