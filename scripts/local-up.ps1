param(
    [switch]$Rebuild = $true
)

$ErrorActionPreference = "Stop"

function Require-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Command '$name' was not found. Install it and run again."
    }
}

Require-Command "docker"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if ($Rebuild) {
    Write-Host "Building plugin in Maven container (Java 21)..."
    docker run --rm `
      -v "${repoRoot}:/workspace" `
      -w /workspace `
      maven:3.9.9-eclipse-temurin-21 `
      mvn -q -DskipTests package
}

$jar = Get-ChildItem "$repoRoot\target\*.jar" `
    | Where-Object { $_.Name -notlike "original-*" } `
    | Sort-Object LastWriteTime -Descending `
    | Select-Object -First 1

if (-not $jar) {
    throw "Built jar was not found in target/. Run build and try again."
}

Write-Host "Using plugin: $($jar.Name)"

$targets = @(
    "$repoRoot\local\master\plugins\DynamicWorldFusion.jar",
    "$repoRoot\local\node-a\plugins\DynamicWorldFusion.jar",
    "$repoRoot\local\node-b\plugins\DynamicWorldFusion.jar"
)

foreach ($target in $targets) {
    Copy-Item $jar.FullName $target -Force
}

Write-Host "Starting local cluster (master + 2 nodes)..."
docker compose -f "$repoRoot\local\docker-compose.yml" up -d --force-recreate

Write-Host ""
Write-Host "Cluster is up."
Write-Host "Game ports:"
Write-Host "  node-a: localhost:25565"
Write-Host "  node-b: localhost:25566"
Write-Host "  master: localhost:25567"
Write-Host "Master observability:"
Write-Host "  UI/API: http://localhost:8099/"
Write-Host "  WS:     ws://localhost:8100/live"
Write-Host ""
Write-Host "Logs: docker compose -f .\local\docker-compose.yml logs -f"
