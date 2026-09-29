# Arranca todo el entorno de desarrollo de WalkSecurity en Windows:
#   Docker Desktop -> PostgreSQL (contenedor) -> adb reverse -> backend Spring Boot
# Uso (desde la raiz del proyecto):
#   powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1
# (Archivo sin tildes a proposito: PowerShell 5.1 lee los .ps1 sin BOM como ANSI.)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

function Test-Docker {
    try { docker info --format '{{.ServerVersion}}' 2>$null | Out-Null; return ($LASTEXITCODE -eq 0) } catch { return $false }
}

# 1. Docker Desktop -----------------------------------------------------------
Step 'Docker Desktop'
if (-not (Test-Docker)) {
    $running = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -like '*docker*' }
    if (-not $running) {
        # Si Docker se cerro mal (apagado del PC, etc.) deja sockets huerfanos que impiden
        # el siguiente arranque ("The file cannot be accessed by the system"). Se apartan.
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        foreach ($dir in @("$env:LOCALAPPDATA\Docker\run", "$env:LOCALAPPDATA\docker-secrets-engine")) {
            if (Test-Path $dir) {
                $sockets = Get-ChildItem -Force $dir | Where-Object { $_.Attributes -match 'ReparsePoint' }
                if ($sockets) {
                    Rename-Item $dir ((Split-Path $dir -Leaf) + ".stale-$stamp")
                    Write-Host "  Sockets huerfanos apartados: $dir"
                }
            }
        }
    }
    $exe = @("$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe",
             "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $exe) { throw 'No se encontro Docker Desktop instalado.' }
    Start-Process $exe
    Write-Host '  Esperando a Docker (hasta 3 min)...'
    $deadline = (Get-Date).AddMinutes(3)
    while (-not (Test-Docker)) {
        if ((Get-Date) -gt $deadline) { throw 'Docker no arranco. Abre Docker Desktop y revisa el error que muestra.' }
        Start-Sleep -Seconds 3
    }
}
Write-Host '  Docker listo.' -ForegroundColor Green

# 2. PostgreSQL ------------------------------------------------------------------
Step 'PostgreSQL (localhost:5433)'
docker compose -f (Join-Path $backend 'docker-compose.yml') up -d --wait
if ($LASTEXITCODE -ne 0) { throw 'No se pudo levantar PostgreSQL.' }

# 3. Telefono -------------------------------------------------------------------
Step 'Telefono (adb reverse)'
$devices = @(adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' })
if ($devices.Count -gt 0) {
    adb reverse tcp:8080 tcp:8080 | Out-Null
    Write-Host '  127.0.0.1:8080 del telefono -> backend del PC.' -ForegroundColor Green
} else {
    Write-Host '  No hay telefono conectado. Conectalo y ejecuta: adb reverse tcp:8080 tcp:8080' -ForegroundColor Yellow
}

# 4. Backend ---------------------------------------------------------------------
Step 'Backend Spring Boot (http://localhost:8080) - Ctrl+C para detenerlo'
$jdk = "$env:ProgramFiles\Java\jdk-26"
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }
Push-Location $backend
try { & .\gradlew.bat bootRun --console=plain } finally { Pop-Location }
