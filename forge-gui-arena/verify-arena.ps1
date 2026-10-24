param([string]$RuntimeRoot=(Join-Path $env:LOCALAPPDATA 'ForgeDIY/repo'),[string]$BuildRoot=(Join-Path $PSScriptRoot '../../build'),[switch]$RuntimeOnly)
$ErrorActionPreference='Stop'
$BuildRoot=[IO.Path]::GetFullPath($BuildRoot)
$app=Join-Path $RuntimeRoot 'app'
$aggregate=@(Get-ChildItem -LiteralPath $app -Filter '*-jar-with-dependencies.jar')[0].FullName
$savedLocal=$env:LOCALAPPDATA
$savedRoaming=$env:APPDATA
try {
    $env:LOCALAPPDATA=Join-Path $BuildRoot 'test-local'
    $env:APPDATA=Join-Path $BuildRoot 'test-roaming'
    New-Item -ItemType Directory -Force -Path "$BuildRoot/tests" | Out-Null
    $cp="$BuildRoot/tests;$BuildRoot/overlays/*;$app/overlays/*;$aggregate"
    if($RuntimeOnly){$cp="$BuildRoot/tests;$app/overlays/*;$aggregate"}
    $sources=@("$PSScriptRoot/src/test/java/ArenaSceneProbe.java","$PSScriptRoot/src/test/java/ArenaBridgeCheck.java","$PSScriptRoot/src/test/java/ArenaPerformanceCheck.java","$PSScriptRoot/../forge-gui-neo/src/test/java/forge/neo/diy/NativeMatchHostCheck.java")
    & javac '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $cp -d "$BuildRoot/tests" @sources 2> "$BuildRoot/test-compile.log"
    if($LASTEXITCODE -ne 0){Get-Content "$BuildRoot/test-compile.log";throw 'Test compile failed'}
    $javaArgs=@('-Dforge.arena.noLaunch=true','-Dprism.order=sw','-Duser.language=en',"-Duser.home=$BuildRoot/test-home","-Dforge.test.app=$app",'-cp',$cp)
    & java @javaArgs forge.neo.diy.NativeMatchHostCheck *> "$BuildRoot/native-host.log"
    Get-Content "$BuildRoot/native-host.log" -Tail 8
    if($LASTEXITCODE -ne 0){throw 'Native host separation check failed'}
    & java @javaArgs '-Dforge.test.performance=true' ArenaSceneProbe "$BuildRoot/tests" *> "$BuildRoot/bridge.log"
    Get-Content "$BuildRoot/bridge.log" -Tail 30
    if($LASTEXITCODE -ne 0){throw 'Arena bridge regression failed'}
    & java @javaArgs '-Djava.awt.headless=true' ArenaSceneProbe "$BuildRoot/tests" --match *> "$BuildRoot/match.log"
    Get-Content "$BuildRoot/match.log" -Tail 10
    if($LASTEXITCODE -ne 0){throw 'Real hosted match failed'}
    Write-Output 'ARENA_NATIVE_AND_PROTOCOL_VERIFIED; Chromium visible-frame acceptance is a separate check.'
} finally {
    $env:LOCALAPPDATA=$savedLocal
    $env:APPDATA=$savedRoaming
}
