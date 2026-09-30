# Emulador del reloj de WalkSecurity en el computador (no necesita telefono ni reloj).
#
#   powershell -ExecutionPolicy Bypass -File scripts\reloj-emulador.ps1             abre la ventana
#   powershell -ExecutionPolicy Bypass -File scripts\reloj-emulador.ps1 -Capturas   genera PNG en docs\reloj
#   powershell -ExecutionPolicy Bypass -File scripts\reloj-emulador.ps1 -Exe        genera WalkSecurityReloj.exe
#
# El .exe incluye su propio Java: se puede copiar la carpeta a otro PC con Windows y abrirlo con doble clic.
# (Archivo sin tildes a proposito: PowerShell 5.1 lee los .ps1 sin BOM como ANSI.)

param([switch]$Capturas, [switch]$Exe)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
# jpackage (necesario para el .exe) no viene en el JDK de Android Studio: se usa el JDK 26 instalado
$jdk = "$env:ProgramFiles\Java\jdk-26"
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }

Push-Location $root
try {
    if ($Capturas) {
        $out = Join-Path $root 'docs\reloj'
        & .\gradlew.bat :watch-emulator:run "--args=--export $out" --console=plain
    } elseif ($Exe) {
        & .\gradlew.bat :watch-emulator:createDistributable --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar el ejecutable.' }
        $dir = Join-Path $root 'watch-emulator\build\compose\binaries\main\app\WalkSecurityReloj'
        Write-Host "`nEjecutable listo: $dir\WalkSecurityReloj.exe" -ForegroundColor Green
        Write-Host 'Copia esa carpeta completa al PC donde vas a presentar.'
        Start-Process explorer.exe $dir
    } else {
        & .\gradlew.bat :watch-emulator:run --console=plain
    }
} finally {
    Pop-Location
}
