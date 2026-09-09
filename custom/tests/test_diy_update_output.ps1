param([string]$Updater = (Join-Path $PSScriptRoot '../../forge-gui/src/main/resources/forge/download/diy-updater.ps1'))
$ErrorActionPreference = 'Stop'
$temp = Join-Path ([IO.Path]::GetTempPath()) ('forge-live-log-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
$utf8 = New-Object Text.UTF8Encoding($true)
$child = Join-Path $temp 'child.ps1'
$gate = Join-Path $temp 'continue'
$wrapper = Join-Path $temp 'wrapper.ps1'
$log = Join-Path $temp 'live.log'
function Read-Log {
    $stream = [IO.File]::Open($log, 'Open', 'Read', 'ReadWrite')
    $reader = New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
[IO.File]::WriteAllText($child, @'
param([string]$Gate)
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
[Console]::Out.Write("下载中 10%`r"); [Console]::Out.Flush()
[Console]::Error.Write("stderr-before-exit`n"); [Console]::Error.Flush()
$deadline = [DateTime]::UtcNow.AddSeconds(20)
while (-not (Test-Path -LiteralPath $Gate) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 40 }
[Console]::Out.Write(('x' * 70000) + "`n下载完成`n"); [Console]::Out.Flush()
[Console]::Error.Write(('y' * 70000) + "`nerror-tail`n"); [Console]::Error.Flush()
exit 7
'@, $utf8)
$quote = { param($s) "'" + $s.Replace("'", "''") + "'" }
[IO.File]::WriteAllText($wrapper, @"
[Console]::OutputEncoding = New-Object Text.UTF8Encoding(`$false)
. $(& $quote ([IO.Path]::GetFullPath($Updater))) -LibraryOnly
try { Invoke-Native powershell.exe @('-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $(& $quote $child), '-Gate', $(& $quote $gate)) -LiveOutput | Out-Null; exit 0 }
catch { [Console]::Out.WriteLine(`$_.Exception.Message); exit 1 }
"@, $utf8)
$proc = Start-Process powershell.exe -ArgumentList @('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',('"' + $wrapper + '"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError (Join-Path $temp 'errors.log')
try {
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    do {
        Start-Sleep -Milliseconds 80
        $text = Read-Log
    } while ((-not $text.Contains('stderr-before-exit') -or -not $text.Contains('下载中 10%')) -and -not $proc.HasExited -and [DateTime]::UtcNow -lt $deadline)
    if ($proc.HasExited -or -not $text.Contains('stderr-before-exit') -or -not $text.Contains('下载中 10%')) { throw 'stdout/stderr were not visible while the child was still running.' }
    [IO.File]::WriteAllText($gate, 'continue')
    if (-not $proc.WaitForExit(15000)) { throw 'Large stdout/stderr deadlocked.' }
    $text = Read-Log
    if ($text -notmatch '下载完成' -or $text -notmatch 'error-tail' -or $text -notmatch 'exited 7') { throw 'Unicode, final output or failure status was lost.' }
    'DIY_LIVE_OUTPUT_TESTS=OK (before-exit stdout/stderr, CR progress, UTF-8, 140 KB output, failed exit)'
} finally {
    [IO.File]::WriteAllText($gate, 'continue')
    if (-not $proc.HasExited) { [void]$proc.WaitForExit(15000) }
    $proc.Dispose()
}
