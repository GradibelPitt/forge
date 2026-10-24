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
$classes=Join-Path $OutputRoot ('classes-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes,"$OutputRoot/overlays","$classes/web" -Force | Out-Null
$cp="$app/overlays/*;$($aggregate[0].FullName)"
$neo="$PSScriptRoot/../forge-gui-neo/src/main/java/forge/neo"
& javac '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $cp -d $classes "$PSScriptRoot/src/main/java/forge/arena/*.java" "$neo/diy/DiyNeoMatchUI.java" "$neo/diy/NativeMatchBinder.java" "$neo/diy/DiyMatchControls.java" "$neo/ui/TableScreen.java" "$neo/match/NeoMatchUI.java"
if($LASTEXITCODE -ne 0){throw 'Java compilation failed.'}
Copy-Item -Path "$Frontend/dist/*" -Destination "$classes/web" -Recurse -Force
& jar --create --file "$OutputRoot/overlays/forge-gui-arena.jar" -C $classes forge/arena -C $classes web
if($LASTEXITCODE -ne 0){throw 'Arena packaging failed.'}
Copy-Item -LiteralPath "$app/overlays/forge-gui-neo.jar" -Destination "$OutputRoot/overlays/forge-gui-neo.jar" -Force
& jar --update --file "$OutputRoot/overlays/forge-gui-neo.jar" -C $classes forge/neo
if($LASTEXITCODE -ne 0){throw 'Native dialog host packaging failed.'}
Write-Output 'ARENA_BUILD_OK'
