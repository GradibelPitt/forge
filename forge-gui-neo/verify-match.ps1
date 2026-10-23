param(
    [Parameter(Mandatory = $true)][string]$RuntimeRoot,
    [string]$BuildRoot = (Join-Path $PSScriptRoot 'target\match')
)
$ErrorActionPreference = 'Stop'
$app = Join-Path (Resolve-Path -LiteralPath $RuntimeRoot).Path 'app'
$aggregate = @(Get-ChildItem -LiteralPath $app -Filter '*-jar-with-dependencies.jar')
if ($aggregate.Count -ne 1) { throw 'Expected one desktop aggregate JAR.' }
$tests = Join-Path $BuildRoot 'tests'
New-Item -ItemType Directory -Path $tests -Force | Out-Null
$cp = "$tests;$BuildRoot/overlays/*;$app/overlays/*;$($aggregate[0].FullName)"
$probe = Join-Path $PSScriptRoot 'src\test\java\forge\neo\diy\NeoSceneProbe.java'
$imageProbe = Join-Path $PSScriptRoot 'src\test\java\forge\neo\diy\NeoImageBridgeCheck.java'
& javac '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $cp -d $tests $probe $imageProbe
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed.' }
foreach ($mode in @('--images', 'scene', '--match')) {
    $log = Join-Path $BuildRoot ($mode.TrimStart('-') + '-verification.log')
    & java '-Djava.awt.headless=true' '-Dprism.order=sw' "-Dforge.test.app=$app" -cp $cp NeoSceneProbe $tests $mode *> $log
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 40; throw "Neo probe failed: $mode" }
    Get-Content -LiteralPath $log | Where-Object { $_ -match '_OK' }
}
Write-Output 'NEO_MATCH_VERIFICATION=OK'
