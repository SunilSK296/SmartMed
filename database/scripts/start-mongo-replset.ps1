<#
 Starts a local single-node MongoDB replica set WITHOUT Docker (Windows 11 / PowerShell).
 Prerequisite: MongoDB Community Server and mongosh installed, e.g.
     winget install MongoDB.Server
     winget install MongoDB.Shell
     winget install MongoDB.Compass.Full      # optional GUI
 Usage:  .\database\scripts\start-mongo-replset.ps1            (leave this window open)
#>
param(
  [string]$DbPath = "$env:USERPROFILE\smartmed-mongo-data",
  [int]$Port = 27017
)
$ErrorActionPreference = "Stop"

$mongod = (Get-Command mongod -ErrorAction SilentlyContinue).Source
if (-not $mongod) {
  $mongod = Get-ChildItem "C:\Program Files\MongoDB\Server\*\bin\mongod.exe" -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mongod) { throw "mongod.exe not found. Install it with: winget install MongoDB.Server" }

# a Windows service installed by the MSI may already occupy the port
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
  Write-Warning "Port $Port is already in use (maybe the 'MongoDB' Windows service). Stop it first:  Stop-Service MongoDB   (needs an Administrator shell)"
  exit 1
}

New-Item -ItemType Directory -Force -Path $DbPath | Out-Null
Write-Host "Starting mongod on port $Port with data in $DbPath ..."
$proc = Start-Process -FilePath $mongod -ArgumentList @("--replSet", "rs0", "--dbpath", "`"$DbPath`"", "--port", $Port, "--bind_ip", "127.0.0.1") -PassThru -WindowStyle Minimized

Start-Sleep -Seconds 4
$script = Join-Path $PSScriptRoot "init-replica-set.js"
mongosh --port $Port --quiet --file $script
Write-Host ""
Write-Host "MongoDB replica set rs0 is running (PID $($proc.Id)). Connection string:"
Write-Host "  mongodb://localhost:$Port/smartmed?directConnection=true"
Write-Host "Press Ctrl+C to leave it running in the background, or run:  Stop-Process -Id $($proc.Id)"
