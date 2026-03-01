$ErrorActionPreference = "Stop"

function Require-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Command '$name' was not found."
    }
}

if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "Docker is already installed."
    exit 0
}

Require-Command "winget"

Write-Host "Installing Docker Desktop via winget..."
winget install -e --id Docker.DockerDesktop --accept-source-agreements --accept-package-agreements

Write-Host "Docker Desktop installed. Please start Docker Desktop and wait until Engine is running."
