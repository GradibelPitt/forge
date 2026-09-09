param([string]$Jdk = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$resource = Join-Path $PSScriptRoot '../../forge-gui/src/main/resources/forge/download'
. (Join-Path $resource 'diy-updater.ps1') -LibraryOnly
$root = Join-Path ([IO.Path]::GetTempPath()) ('diy-update-history-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
Initialize-ProtectionTool $Jdk $root
$relative = 'forge-gui/src/main/java/forge/download/DiyUpdateBridge.java'
$id = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($relative))
$row = @([IO.File]::ReadAllLines((Join-Path $resource 'diy-protection-history.tsv')) | Where-Object { $_.StartsWith("file`t$id`t") })
if ($row.Count -ne 1) { throw 'The released updater must have exactly one historical protection rule' }
$fixture = Join-Path $root $relative
New-Item -ItemType Directory -Path (Split-Path $fixture) -Force | Out-Null
# Match the Windows checkout required by this updater; do not hash a mixed-EOL editor buffer.
$source = [IO.File]::ReadAllText((Join-Path $PSScriptRoot "../../$relative")).Replace("`r`n", "`n").Replace("`n", "`r`n")
Write-Utf8 $fixture $source
$catalog = Join-Path $root 'history.tsv'
Write-Utf8 $catalog ("DIY-PROTECTION-2`n" + $row[0] + "`n")
Invoke-Protection $Jdk $root @('verify', $root, $catalog)
Write-Utf8 $fixture ($source.Replace('new AtomicBoolean()', 'new AtomicBoolean(true)'))
$rejected = $false
try { Invoke-Protection $Jdk $root @('verify', $root, $catalog) | Out-Null } catch { $rejected = $true }
if (-not $rejected) { throw 'A subsequent unreviewed updater change must remain blocked' }
'DIY_UPDATE_HISTORY_TESTS=OK (released updater accepted; later modification rejected)'
