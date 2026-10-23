param([string]$RuntimeRoot=(Join-Path $env:LOCALAPPDATA 'ForgeDIY/repo'),[string]$BuildRoot=(Join-Path $PSScriptRoot '../../build'),[switch]$RuntimeOnly)
$ErrorActionPreference='Stop'
$BuildRoot=[IO.Path]::GetFullPath($BuildRoot)
$app=Join-Path $RuntimeRoot 'app'
$aggregate=@(Get-ChildItem -LiteralPath $app -Filter '*-jar-with-dependencies.jar')[0].FullName
$env:LOCALAPPDATA=Join-Path $BuildRoot 'test-local'
$env:APPDATA=Join-Path $BuildRoot 'test-roaming'
$cp="$BuildRoot/tests;$BuildRoot/overlays/*;$app/overlays/*;$aggregate"
if($RuntimeOnly){$cp="$BuildRoot/tests;$app/overlays/*;$aggregate"}
& javac '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $cp -d "$BuildRoot/tests" "$PSScriptRoot/src/test/java/ArenaSceneProbe.java" "$PSScriptRoot/src/test/java/ArenaBridgeCheck.java" 2> "$BuildRoot/test-compile.log"
if($LASTEXITCODE -ne 0){Get-Content "$BuildRoot/test-compile.log";throw 'Test compile failed'}
& java '-Djava.awt.headless=true' '-Dprism.order=d3d,sw' '-Duser.language=en' "-Duser.home=$BuildRoot/test-home" "-Dforge.test.app=$app" -cp $cp ArenaSceneProbe "$BuildRoot/tests" *> "$BuildRoot/scene.log"
Get-Content "$BuildRoot/scene.log" -Tail 35
if($LASTEXITCODE -ne 0){throw 'Arena scene test failed'}

& java '-Dprism.order=d3d,sw' '-Dprism.verbose=true' '-Dforge.test.embedded=true' '-Duser.language=en' "-Duser.home=$BuildRoot/test-home" "-Dforge.test.app=$app" -cp $cp ArenaSceneProbe "$BuildRoot/tests" *> "$BuildRoot/embedded.log"
Get-Content "$BuildRoot/embedded.log" -Tail 8
if($LASTEXITCODE -ne 0){throw 'Embedded renderer test failed'}
Select-String -LiteralPath "$BuildRoot/embedded.log" -Pattern 'Initialized prism pipeline' | ForEach-Object { $_.Line }
foreach($log in @('scene.log','embedded.log')){
    if(Select-String -LiteralPath "$BuildRoot/$log" -Pattern 'java\.lang\.NullPointerException|com\.sun\.javafx\.tk\.RenderJob\.run|RenderJob error|RenderJob.run: internal exception|Texture must be non-null|Operation requires resource lock|Register not found for:'){
        throw "Graphics error in $log"
    }
}

& java '-Djava.awt.headless=true' '-Dprism.order=sw' '-Duser.language=en' "-Duser.home=$BuildRoot/test-home" "-Dforge.test.app=$app" -cp $cp ArenaSceneProbe "$BuildRoot/tests" --match *> "$BuildRoot/match.log"
Get-Content "$BuildRoot/match.log" -Tail 8
if($LASTEXITCODE -ne 0){throw 'Real match test failed'}
