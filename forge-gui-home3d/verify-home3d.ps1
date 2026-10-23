param(
    [Parameter(Mandatory = $true)][string]$RuntimeRoot,
    [string]$Payload = (Join-Path $PSScriptRoot 'target\home3d\overlays'),
    [string]$OutputRoot = (Join-Path $PSScriptRoot 'target\home3d\verification')
)
$ErrorActionPreference = 'Stop'
$sourceRoot = Split-Path $PSScriptRoot -Parent
$runtimeApp = Join-Path (Resolve-Path -LiteralPath $RuntimeRoot).Path 'app'
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
$aggregate = @(Get-ChildItem -LiteralPath $runtimeApp -Filter '*-jar-with-dependencies.jar')
if ($aggregate.Count -ne 1) { throw 'Expected one desktop aggregate JAR.' }
$javafx = Join-Path $sourceRoot 'forge-gui-neo\vendor\win'
$cp = "$Payload/forge-gui-home3d.jar;$Payload/forge-gui-desktop.jar;$javafx/*;$runtimeApp/overlays/*;$($aggregate[0].FullName)"
& javac '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $cp -d $OutputRoot (Join-Path $PSScriptRoot 'src\test\java\Home3DProbe.java')
if ($LASTEXITCODE -ne 0) { throw '3D lobby probe compilation failed.' }
& java '-Duser.language=en' -cp "$OutputRoot;$cp" Home3DProbe $OutputRoot
if ($LASTEXITCODE -ne 0) { throw '3D lobby verification failed.' }
