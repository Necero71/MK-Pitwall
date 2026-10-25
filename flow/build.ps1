# Builds Flow and copies the APKs into .\dist
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$gradle = if (Test-Path .\gradlew.bat) { '.\gradlew.bat' } else {
    Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.9-bin\*\gradle-8.9\bin\gradle.bat" | Select-Object -First 1 -ExpandProperty FullName
}
if (-not (Test-Path .\gradlew.bat)) { & $gradle wrapper --gradle-version 8.9 -q }

& $gradle assembleDebug assembleRelease --console=plain *> build.log
$code = $LASTEXITCODE
Get-Content build.log -Tail 40
if ($code -ne 0) { Write-Host "BUILD FAILED ($code)"; exit $code }

New-Item -ItemType Directory -Force dist | Out-Null
$out = Get-ChildItem -Recurse -Filter *.apk -Path "$env:TEMP\flowtv-build", .\app\build -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match 'outputs\\apk' }
foreach ($apk in $out) { Copy-Item $apk.FullName ("dist\Flow-" + $apk.Directory.Name + ".apk") -Force }
Get-ChildItem dist | Format-Table Name, Length
Write-Host "BUILD OK"
