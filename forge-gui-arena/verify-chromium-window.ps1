param(
    [string]$RuntimeRoot = (Join-Path $env:LOCALAPPDATA 'ForgeDIY/repo'),
    [string]$OutputRoot = (Join-Path $PSScriptRoot '../../build/chromium-window-check'),
    [string]$BrowserExecutable
)
$ErrorActionPreference = 'Stop'
$OutputRoot = [IO.Path]::GetFullPath($OutputRoot)
$app = Join-Path $RuntimeRoot 'app'
$aggregate = @(Get-ChildItem -LiteralPath $app -Filter '*-jar-with-dependencies.jar')
if ($aggregate.Count -ne 1) { throw 'Expected one desktop aggregate.' }
New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null
& javac --release 17 -encoding UTF-8 -cp $aggregate[0].FullName -d $OutputRoot `
    (Join-Path $PSScriptRoot 'src/main/java/forge/arena/ChromiumWindow.java') `
    (Join-Path $PSScriptRoot 'src/test/java/forge/arena/ChromiumWindowCheck.java')
if ($LASTEXITCODE -ne 0) { throw 'Chromium window check compilation failed.' }
$arguments = @('-Dfile.encoding=UTF-8', "-Dforge.arena.sessionDir=$OutputRoot/sessions")
if ($BrowserExecutable) { $arguments += "-Dforge.arena.browser.executable=$BrowserExecutable" }
$arguments += @('-cp', "$OutputRoot;$($aggregate[0].FullName)", 'forge.arena.ChromiumWindowCheck')
& java @arguments
if ($LASTEXITCODE -ne 0) { throw 'Chromium window lifecycle checks failed.' }
