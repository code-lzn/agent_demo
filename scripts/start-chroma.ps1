param(
    [string]$HostName = "localhost",
    [int]$Port = 8000,
    [switch]$Upgrade
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$VenvPath = Join-Path $ProjectRoot ".chroma-venv"
$DataPath = Join-Path $ProjectRoot "chroma-data"

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    throw "Python is not available. Please install Python 3.10+ and add it to PATH."
}

$CreatedVenv = $false
if (-not (Test-Path $VenvPath)) {
    python -m venv $VenvPath
    $CreatedVenv = $true
}

$PythonExe = Join-Path $VenvPath "Scripts\python.exe"
$ChromaExe = Join-Path $VenvPath "Scripts\chroma.exe"

if ($CreatedVenv -or $Upgrade -or -not (Test-Path $ChromaExe)) {
    & $PythonExe -m pip install --upgrade pip
    & $PythonExe -m pip install --upgrade chromadb
}

if (-not (Test-Path $DataPath)) {
    New-Item -ItemType Directory -Path $DataPath | Out-Null
}

& $ChromaExe run --host $HostName --port $Port --path $DataPath
