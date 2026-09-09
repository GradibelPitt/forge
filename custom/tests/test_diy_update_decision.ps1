param([string]$Updater = (Join-Path $PSScriptRoot '../../forge-gui/src/main/resources/forge/download/diy-updater.ps1'))
$ErrorActionPreference = 'Stop'
. $Updater -LibraryOnly
$root = Join-Path ([IO.Path]::GetTempPath()) ('diy-decision-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
function Quote([string]$Value) { return "'" + $Value.Replace("'","''") + "'" }
function Case([string]$Name, [string]$Answer, [bool]$ExpectSuccess, [bool]$ExpectRequest, [bool]$InitialSuccess = $false, [bool]$RetryFails = $false, [string]$NextAnswer = '') {
    $job = Join-Path $root $Name
    $source = Join-Path $job 'source'
    $reports = Join-Path $source 'forge-game/target/surefire-reports'
    New-Item -ItemType Directory -Path $reports -Force | Out-Null
    Write-Utf8 (Join-Path $reports 'TEST-fixture.xml') '<testsuite tests="2" failures="1" errors="1"><testcase classname="demo.One" name="failure"><failure message="expected 2, got 1"/></testcase><testcase classname="demo.Two" name="error"><error message="test exception"/></testcase></testsuite>'
    $maven = Join-Path $job 'maven.cmd'
    $failure = if($Name -eq 'compile') { '[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.13.0:compile on project forge-game: Compilation failure' }
        elseif($Name -eq 'crash') { '[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test on project forge-game: The forked VM terminated without properly saying goodbye' }
        else { '[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.1.2:test on project forge-game: There are test failures.' }
    $retryCode = if($RetryFails) { 2 } else { 0 }
    $initialCode = if($InitialSuccess) { 0 } else { 1 }
    $retryAction = ''
    if ($NextAnswer) {
        $more = [IO.File]::ReadAllText((Join-Path $reports 'TEST-fixture.xml')).Replace('</testsuite>', '<testcase classname="demo.Three" name="later"><failure message="new failure in later module"/></testcase></testsuite>')
        Write-Utf8 (Join-Path $job 'more.xml') $more
        $retryAction = 'copy /y "%~dp0more.xml" "%~dp0source\forge-game\target\surefire-reports\TEST-fixture.xml" >nul'
    } elseif ($Name -eq 'post-ack-vm-error') {
        $retryAction = 'echo [ERROR] There was an error in the forked process'
    }
    $cmd = @"
@echo off
echo %*>>"%~dp0calls.txt"
echo %* | findstr /C:"-Dmaven.test.failure.ignore=true" >nul
if not errorlevel 1 goto retry
echo $failure
exit /b $initialCode
:retry
$retryAction
exit /b $retryCode
"@
    [IO.File]::WriteAllText($maven,$cmd.Replace("`r`n","`n").Replace("`n","`r`n"),[Text.Encoding]::ASCII)
    $wrapper = Join-Path $job 'run.ps1'
    $body = @"
`$ErrorActionPreference='Stop'
. $(Quote ([IO.Path]::GetFullPath($Updater))) -LibraryOnly
try {
  `$result=Invoke-CheckedPackage $(Quote $maven) $(Quote $source) $(Quote (Join-Path $job 'cache')) $(Quote $job)
  Write-Utf8 $(Quote (Join-Path $job 'result.json')) (`$result | ConvertTo-Json -Depth 8)
  exit 0
} catch { Write-Utf8 $(Quote (Join-Path $job 'error.txt')) `$_.Exception.Message; exit 3 }
"@
    [IO.File]::WriteAllText($wrapper,$body,(New-Object Text.UTF8Encoding($true)))
    $proc = Start-Process powershell.exe -ArgumentList @('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',('"'+$wrapper+'"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $job 'output.log') -RedirectStandardError (Join-Path $job 'stderr.log')
    $null = $proc.Handle
    try {
        $request = Join-Path $job 'test-decision.request'
        $deadline = [DateTime]::UtcNow.AddSeconds(15)
        while (-not $proc.HasExited -and -not (Test-Path -LiteralPath $request) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
        if ((Test-Path -LiteralPath $request) -ne $ExpectRequest) { throw "${Name}: wrong decision classification; inspect $job" }
        if ($ExpectRequest) {
            Start-Sleep -Milliseconds 500
            if ($proc.HasExited) { throw "${Name}: updater did not wait for the user" }
            $calls = [IO.File]::ReadAllText((Join-Path $job 'calls.txt'))
            if ($calls.Contains('-Dmaven.test.failure.ignore=true')) { throw 'Test failures were ignored without a decision' }
            $id = ([IO.File]::ReadAllLines($request))[0]
            Write-Utf8 (Join-Path $job "test-decision-$id.response") $Answer
            if ($NextAnswer) {
                $deadline = [DateTime]::UtcNow.AddSeconds(15)
                do {
                    Start-Sleep -Milliseconds 100
                    $nextId = ([IO.File]::ReadAllLines($request))[0]
                } while ($nextId -eq $id -and -not $proc.HasExited -and [DateTime]::UtcNow -lt $deadline)
                if ($nextId -eq $id -or $proc.HasExited) { throw "${Name}: new failures were not offered separately; inspect $job" }
                Start-Sleep -Milliseconds 500
                if ($proc.HasExited) { throw 'New failures were automatically accepted' }
                $pending = @(Get-Content (Join-Path $job 'test-failures.json') -Raw -Encoding UTF8 | ConvertFrom-Json)
                if ($pending.Count -ne 1 -or $pending[0].name -ne 'demo.Three.later') { throw 'New failure list is incorrect' }
                Write-Utf8 (Join-Path $job "test-decision-$nextId.response") $NextAnswer
            }
        }
        if (-not $proc.WaitForExit(15000)) { throw "${Name}: updater did not finish" }
        if (($proc.ExitCode -eq 0) -ne $ExpectSuccess) { throw "${Name}: wrong build result; inspect $job" }
        $calls = [IO.File]::ReadAllText((Join-Path $job 'calls.txt'))
        if ($calls.Contains('-DskipTests')) { throw 'The remaining tests must still execute' }
        if ($calls.Contains('-Dmaven.test.failure.ignore=true') -ne ($ExpectRequest -and $Answer -eq 'continue')) { throw "${Name}: unexpected retry" }
        if ($ExpectSuccess) {
            $result = Get-Content (Join-Path $job 'result.json') -Raw -Encoding UTF8 | ConvertFrom-Json
            $expectedStatus = if($InitialSuccess) {'passed'} else {'failures-acknowledged'}
            if ($result.status -ne $expectedStatus) { throw 'Incorrect verification status' }
            $expectedFailures = if ($NextAnswer) {3} else {2}
            $expectedDecisions = if ($NextAnswer) {2} else {1}
            if (-not $InitialSuccess -and ($result.failures.Count -ne $expectedFailures -or $result.acknowledgement.Count -ne $expectedDecisions)) { throw 'Acknowledged failures or decisions lost' }
        }
        Write-Host "PASS: $Name"
    } finally { if(-not $proc.HasExited) {$proc.Kill()}; $proc.Dispose() }
}
Case 'continue' 'continue' $true $true
Case 'stop' 'stop' $false $true
Case 'compile' '' $false $false
Case 'crash' '' $false $false
Case 'post-ack-build-failure' 'continue' $false $true $false $true
Case 'post-ack-vm-error' 'continue' $false $true
Case 'new-failures-continue' 'continue' $true $true $false $false 'continue'
Case 'new-failures-stop' 'continue' $false $true $false $false 'stop'
Case 'passed' '' $true $false $true
'DIY_TEST_DECISION_TESTS=OK (9 cases; waits, continue/stop, new failure prompts, compile/VM/retry failures, passed status)'
