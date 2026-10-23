param(
    [string]$RuntimeRoot = (Join-Path $env:LOCALAPPDATA 'ForgeDIY/repo'),
    [string]$Frontend = (Join-Path $PSScriptRoot 'web'),
    [string]$OutputRoot = (Join-Path $PSScriptRoot '../../build')
)
$ErrorActionPreference='Stop'
$OutputRoot=[IO.Path]::GetFullPath($OutputRoot)
$app=Join-Path $RuntimeRoot 'app'
$aggregate=@(Get-ChildItem -LiteralPath $app -Filter '*-jar-with-dependencies.jar')
if($aggregate.Count -ne 1){throw 'Expected one desktop aggregate.'}
New-Item -ItemType Directory -Path "$OutputRoot/classes","$OutputRoot/overlays","$OutputRoot/classes/web" -Force | Out-Null
$expected='AC700DC802EA56F5B46569DD371EACCA988C40F1622A3AA92847F9AF76A7E454'
if((Get-FileHash -LiteralPath "$PSScriptRoot/vendor/javafx-web-21.0.5-win.jar").Hash -ne $expected){throw 'WebView dependency hash mismatch'}
$cp="$PSScriptRoot/vendor/*;$app/overlays/*;$($aggregate[0].FullName)"
& javac '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $cp -d "$OutputRoot/classes" "$PSScriptRoot/src/main/java/forge/arena/*.java" "$PSScriptRoot/../forge-gui-neo/src/main/java/forge/neo/diy/DiyNeoMatchUI.java"
if($LASTEXITCODE -ne 0){throw 'Java compilation failed.'}
Copy-Item -Path "$Frontend/dist/*" -Destination "$OutputRoot/classes/web" -Recurse -Force
& jar --create --file "$OutputRoot/overlays/forge-gui-arena.jar" -C "$OutputRoot/classes" forge/arena -C "$OutputRoot/classes" web
if($LASTEXITCODE -ne 0){throw 'Arena packaging failed.'}
Copy-Item -LiteralPath "$app/overlays/forge-gui-neo.jar" -Destination "$OutputRoot/overlays/forge-gui-neo.jar" -Force
& jar --update --file "$OutputRoot/overlays/forge-gui-neo.jar" -C "$OutputRoot/classes" forge/neo/diy/DiyNeoMatchUI.class
Copy-Item -LiteralPath "$PSScriptRoot/vendor/javafx-web-21.0.5-win.jar" -Destination "$OutputRoot/overlays" -Force
Write-Output 'ARENA_BUILD_OK'
