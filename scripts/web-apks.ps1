# Compila las dos apps y copia los APK a web\downloads para publicarlos en la pagina (Vercel).
#   powershell -ExecutionPolicy Bypass -File scripts\web-apks.ps1
# (Archivo sin tildes a proposito: PowerShell 5.1 lee los .ps1 sin BOM como ANSI.)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jdk = "$env:ProgramFiles\Java\jdk-26"
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk }

Push-Location $root
try {
    & .\gradlew.bat :app:assembleDebug :wear:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion.' }
    New-Item -ItemType Directory -Force 'web\downloads' | Out-Null
    Copy-Item 'app\build\outputs\apk\debug\app-debug.apk' 'web\downloads\walksecurity-telefono.apk' -Force
    Copy-Item 'wear\build\outputs\apk\debug\wear-debug.apk' 'web\downloads\walksecurity-reloj.apk' -Force
    Get-ChildItem 'web\downloads\*.apk' | ForEach-Object { '{0}  {1:N1} MB' -f $_.Name, ($_.Length / 1MB) }
} finally {
    Pop-Location
}
