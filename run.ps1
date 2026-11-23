# SmartMed Standalone Priority Scheduling Engine - PowerShell Runner
Write-Host "Starting SmartMed Standalone Priority Scheduling Engine..." -ForegroundColor Cyan

if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd compile exec:java
} else {
    if (-not (Test-Path "bin")) { New-Item -ItemType Directory -Path "bin" | Out-Null }
    $sources = Get-ChildItem -Recurse -Filter *.java src | Select-Object -ExpandProperty FullName
    javac -d bin $sources
    java -cp bin Main
}
