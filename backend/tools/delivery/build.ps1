param(
    [string]$FrontendRoot = '',
    [string]$MavenCommand = 'mvn.cmd',
    [string]$NodeCommand = 'node.exe',
    [string]$Settings = '',
    [string]$Repository = '',
    [switch]$SkipFrontendInstall
)
$ErrorActionPreference = 'Stop'
$backendRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$Settings) { $Settings = Join-Path $PSScriptRoot 'maven-settings.xml' }
if (!$FrontendRoot) { $FrontendRoot = Join-Path (Split-Path $backendRoot -Parent) 'frontend' }
$FrontendRoot = (Resolve-Path -LiteralPath $FrontendRoot).Path
foreach ($path in @($backendRoot, $FrontendRoot)) {
    if ($path -notmatch '^[Dd]:[\\/].+') { throw 'Build both source trees on D: to avoid writing C:' }
}
$stateRoot = Join-Path $backendRoot '.local'
$env:TEMP = Join-Path $stateRoot 'tmp'
$env:TMP = $env:TEMP
$env:npm_config_cache = Join-Path $stateRoot 'npm-cache'
$env:MAVEN_OPTS = ''
# Java parses this environment variable itself, avoiding cmd.exe nested-quote issues.
$env:JAVA_TOOL_OPTIONS = '"-Duser.home='+($stateRoot+'/test-user')+'" "-Djava.io.tmpdir='+($stateRoot+'/tmp')+'" "-Dpdfbox.fontcache='+($stateRoot+'/fontcache')+'" -Dfile.encoding=UTF-8'
New-Item -ItemType Directory -Path $env:TEMP, ($stateRoot+'/fontcache'), ($stateRoot+'/test-user') -Force | Out-Null
. (Join-Path $PSScriptRoot 'private-env.ps1')
Initialize-FlashcodeLocalSecret -RuntimeRoot $stateRoot
if (!$Repository) { $Repository = Join-Path $stateRoot 'maven-repository' }
if ([System.IO.Path]::GetFullPath($Repository) -notmatch '^[Dd]:[\\/].+') { throw 'Maven cache must be on D:' }
$mavenArgs = @('-B', ('-Dmaven.repo.local='+$Repository), '-Ddocker.skip=true', 'package')
if ($Settings) { $mavenArgs = @('-s', $Settings) + $mavenArgs }
Push-Location -LiteralPath $backendRoot
try {
    & $MavenCommand @mavenArgs *> (Join-Path $stateRoot 'backend-build.log')
    if ($LASTEXITCODE -ne 0) { throw ('Backend tests/build failed; see '+$stateRoot+'/backend-build.log') }
} finally { Pop-Location }
$nodeExe = (Get-Command $NodeCommand -ErrorAction Stop).Source
$npmCommand = Join-Path (Split-Path $nodeExe -Parent) 'npm.cmd'
$env:PATH = (Split-Path $nodeExe -Parent) + ';' + $env:PATH
Push-Location -LiteralPath $FrontendRoot
try {
    if (!$SkipFrontendInstall) {
        & $npmCommand ci --no-audit --no-fund *> (Join-Path $stateRoot 'frontend-install.log')
        if ($LASTEXITCODE -ne 0) { throw ('npm ci failed; see '+$stateRoot+'/frontend-install.log') }
    }
    & $nodeExe 'node_modules/vite/bin/vite.js' build *> (Join-Path $stateRoot 'frontend-build.log')
    if ($LASTEXITCODE -ne 0) { throw ('Frontend build failed; see '+$stateRoot+'/frontend-build.log') }
} finally { Pop-Location }
Write-Output ('BUILD_AND_TESTS_PASSED logs='+$stateRoot)
Write-Output 'This builds source only; runtime infrastructure/configuration is a separate acceptance step.'
