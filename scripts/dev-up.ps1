# Arranca el backend de WalkSecurity en Windows con el MENOR consumo de memoria posible.
#
#   powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1           (recomendado: sin Docker)
#   powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1 -Docker   (PostgreSQL en Docker)
#
# Sin Docker usa el PostgreSQL ya instalado en Windows (servicio postgresql-x64-*). La primera vez
# crea la base de datos "walksecurity" y pide la contrasena del usuario "postgres".
# El backend corre como .jar con 256 MB maximos (sin dejar Gradle ocupando memoria).
# (Archivo sin tildes a proposito: PowerShell 5.1 lee los .ps1 sin BOM como ANSI.)

param([switch]$Docker)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'

$jdk = "$env:ProgramFiles\Java\jdk-26"
if (Test-Path $jdk) {
    $env:JAVA_HOME = $jdk
    $env:Path = "$jdk\bin;$env:Path"
}

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }

function Test-Docker {
    try { docker info --format '{{.ServerVersion}}' 2>$null | Out-Null; return ($LASTEXITCODE -eq 0) } catch { return $false }
}

# 1. Base de datos -----------------------------------------------------------------
$nativePg = Get-Service -Name 'postgresql-x64-*' -ErrorAction SilentlyContinue |
    Where-Object { $_.Status -eq 'Running' } | Select-Object -First 1

if (-not $Docker -and $nativePg) {
    Step "PostgreSQL de Windows ($($nativePg.Name)) - sin Docker"
    $psql = Get-ChildItem "$env:ProgramFiles\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if (-not $psql) { throw 'No se encontro psql.exe en C:\Program Files\PostgreSQL.' }

    $env:PGPASSWORD = 'walksecurity'
    & $psql.FullName -h localhost -p 5432 -U walksecurity -d walksecurity -c 'select 1' *> $null
    $dbReady = ($LASTEXITCODE -eq 0)
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue

    if (-not $dbReady) {
        Write-Host '  Primera vez: se crea el usuario y la base de datos "walksecurity".' -ForegroundColor Yellow
        Write-Host '  Escribe la contrasena del usuario "postgres" (la que elegiste al instalar PostgreSQL):'
        & $psql.FullName -h localhost -p 5432 -U postgres -f (Join-Path $backend 'db\crear-bd-local.sql')
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base de datos. Revisa la contrasena de postgres.' }
    }
    Write-Host '  Base de datos lista.' -ForegroundColor Green
    $env:DB_URL = 'jdbc:postgresql://localhost:5432/walksecurity'
} else {
    Step 'PostgreSQL en Docker (localhost:5433)'
    if (-not (Test-Docker)) {
        $running = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -like '*docker*' }
        if (-not $running) {
            # Si Docker se cerro mal deja sockets huerfanos que impiden el siguiente arranque. Se apartan.
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
    docker compose -f (Join-Path $backend 'docker-compose.yml') up -d --wait
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo levantar PostgreSQL en Docker.' }
    $env:DB_URL = 'jdbc:postgresql://localhost:5433/walksecurity'
}

# 2. Telefono ----------------------------------------------------------------------
Step 'Telefono (adb reverse)'
$devices = @(adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' })
if ($devices.Count -gt 0) {
    adb reverse tcp:8080 tcp:8080 | Out-Null
    Write-Host '  127.0.0.1:8080 del telefono -> backend del PC.' -ForegroundColor Green
} else {
    Write-Host '  No hay telefono conectado. Conectalo y ejecuta: adb reverse tcp:8080 tcp:8080' -ForegroundColor Yellow
    Write-Host '  (La app tambien funciona sin servidor en "modo local".)'
}

# 3. Backend -----------------------------------------------------------------------
Step 'Compilando el backend (una vez)...'
Push-Location $backend
try {
    # --no-daemon: Gradle termina al compilar y libera su memoria
    & .\gradlew.bat bootJar --no-daemon -q
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion del backend.' }
    $jar = Get-ChildItem 'build\libs\*.jar' | Where-Object { $_.Name -notlike '*-plain.jar' } | Select-Object -First 1
} finally {
    Pop-Location
}

Step 'Backend en http://localhost:8080  (Ctrl+C para detenerlo)'
& java -Xmx256m -XX:+UseSerialGC -jar $jar.FullName
