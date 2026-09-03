param([ValidateSet('start', 'build', 'test', 'install')][string]$Task = 'start')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$portableNode = Join-Path $projectRoot '.tools/node-v24.15.0-win-x64'
$originalPath = $env:PATH
$originalLocation = Get-Location
try {
    if (Test-Path -LiteralPath (Join-Path $portableNode 'node.exe')) { $env:PATH = $portableNode + ';' + $env:PATH }
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Instale Node.js 24.15 ou superior da serie 24 para usar Angular 22.' }
    $nodeVersion = [Version](& node -p 'process.versions.node')
    $nodeSupported = ($nodeVersion.Major -eq 24 -and $nodeVersion.Minor -ge 15) -or
        ($nodeVersion.Major -eq 22 -and $nodeVersion -ge [Version]'22.22.3') -or
        ($nodeVersion.Major -eq 26)
    if (-not $nodeSupported) { throw 'Node incompativel. Use Node 24.15+ (serie 24), 22.22.3+ (serie 22) ou 26.' }
    Set-Location -LiteralPath $projectRoot
    if ($Task -eq 'install') { & npm.cmd ci } else { & npm.cmd run $Task }
    $frontendExitCode = $LASTEXITCODE
} finally { $env:PATH = $originalPath; Set-Location -LiteralPath $originalLocation }
exit $frontendExitCode