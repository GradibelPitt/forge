param(
    [Parameter(Mandatory = $true)][string]$RuntimeRoot,
    [string]$BuildRoot = (Join-Path $PSScriptRoot 'target\match')
)
$ErrorActionPreference = 'Stop'
$app = Join-Path (Resolve-Path -LiteralPath $RuntimeRoot).Path 'app'
$aggregate = @(Get-ChildItem -LiteralPath $app -Filter '*-jar-with-dependencies.jar')
if ($aggregate.Count -ne 1) { throw 'Expected one desktop aggregate JAR.' }
$tests = Join-Path $BuildRoot 'priority-tests'
New-Item -ItemType Directory -Path $tests -Force | Out-Null
$cp = "$tests;$BuildRoot/overlays/*;$app/overlays/*;$($aggregate[0].FullName)"
$sources = @('PriorityPassProbe.java', 'PriorityShortcutProbe.java') | ForEach-Object {
    Join-Path $PSScriptRoot "src\test\java\forge\neo\diy\$_"
}
& javac '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $cp -d $tests @sources
if ($LASTEXITCODE -ne 0) { throw 'Priority shortcut probe compilation failed.' }
foreach ($probe in @('PriorityPassProbe', 'forge.neo.PriorityShortcutProbe')) {
    $log = Join-Path $BuildRoot ($probe + '.log')
    & java '-Djava.awt.headless=true' '-Dprism.order=sw' "-Dforge.test.app=$app" -cp $cp $probe *> $log
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 40; throw "Priority shortcut probe failed: $probe" }
    Get-Content -LiteralPath $log | Where-Object { $_ -match '_OK' }
}
