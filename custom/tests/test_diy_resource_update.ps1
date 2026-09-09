param([string]$Updater = (Join-Path $PSScriptRoot '../../forge-gui/src/main/resources/forge/download/diy-updater.ps1'))
$ErrorActionPreference = 'Stop'
. $Updater -LibraryOnly
function Assert($Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Put([string]$Root, [string]$Path, [string]$Text) {
    $file = Join-Path $Root $Path
    New-Item -ItemType Directory -Path (Split-Path $file) -Force | Out-Null
    Write-Utf8 $file $Text
}
Assert ([bool](Get-Command Get-CardResourceAudit -ErrorAction SilentlyContinue)) 'Complete resource audit is missing'
$temp = Join-Path ([IO.Path]::GetTempPath()) ('forge-resource-test-' + [guid]::NewGuid().ToString('N'))
$repo = Join-Path $temp 'repo'; $active = Join-Path $temp 'active'; $candidate = Join-Path $temp 'candidate'
New-Item -ItemType Directory -Path $temp, $active, $candidate -Force | Out-Null
Invoke-Native git @('init', $repo) | Out-Null
Invoke-Git $repo @('config', 'user.name', 'Resource Update Test') | Out-Null
Invoke-Git $repo @('config', 'user.email', 'resource-test@example.invalid') | Out-Null
$old = 'forge-gui/res/cardsfolder/o/old.txt'
$added = 'forge-gui/res/cardsfolder/n/new.txt'
$edition = 'forge-gui/res/editions/Fixture Set.txt'
$token = 'forge-gui/res/tokenscripts/new_token.txt'
$constant = 'forge-gui/res/cardsfolder/c/constant.txt'
Put $repo $old "Name:Old`nOracle:Old official`n"
Put $repo $edition "[metadata]`nCode=TST`n[cards]`n1 C Old`n"
Put $repo $constant "Name:Constant`n"
Invoke-Git $repo @('add', '--', 'forge-gui/res') | Out-Null
Invoke-Git $repo @('commit', '-m', 'official resource base') | Out-Null
$base = (Invoke-Git $repo @('rev-parse', 'HEAD')).Trim()
foreach ($path in @($old, $edition, $constant)) {
    # A Windows checkout must compare equal to the official LF blob.
    Put $active $path.Substring('forge-gui/'.Length) ([IO.File]::ReadAllText((Join-Path $repo $path)).Replace("`n", "`r`n"))
}
Put $repo $old "Name:Old`nOracle:New official fix`n"
Put $repo $edition "[metadata]`nCode=TST`n[cards]`n1 C Old`n2 R New`n"
Put $repo $added "Name:New`n"
Put $repo $token "Name:New Token`n"
Invoke-Git $repo @('add', '--', 'forge-gui/res') | Out-Null
Invoke-Git $repo @('commit', '-m', 'new official card scripts editions and tokens') | Out-Null
$target = (Invoke-Git $repo @('rev-parse', 'HEAD')).Trim()
Invoke-Git $repo @('checkout', '-b', 'diy-old', $base) | Out-Null
Put $repo 'forge-gui/res/cardsfolder/d/diy_extra.txt' "Name:My DIY`n"
Put $active 'res/cardsfolder/d/diy_extra.txt' "Name:My runtime DIY`n"

# Reproduce the production bug: engine baseline already equals target, resources still old.
Assert (@(Get-UpdatePlan $repo $target $target).Count -eq 0) 'Fixture must have no engine delta'
$audit = Get-CardResourceAudit $repo $active $target @($target, $base)
Assert ($audit.officialFileCount -eq 5) 'Complete official resource inventory not read'
Assert (@($audit.files | Where-Object decision -eq 'repair').Count -eq 4) 'No-delta stale source/runtime was not repaired'
Assert (@($audit.files | Where-Object decision -eq 'block').Count -eq 0) 'Known old official content incorrectly blocked'
Assert (($audit.files | Where-Object path -eq $constant).decision -eq 'current') 'CRLF checkout mismatched official blob'
Assert (($audit.files | Where-Object path -eq $old).decision -eq 'repair') 'Modified official card omitted'
Assert (($audit.files | Where-Object path -eq $token).decision -eq 'repair') 'New token omitted'
$rules = Get-ProtectedFileManifest $repo $target $audit
Assert (-not $rules.ContainsKey($old)) 'Old official resource misclassified as protected DIY'
Repair-CardResourceSource $repo $target $audit
Copy-Item -LiteralPath (Join-Path $active 'res') -Destination $candidate -Recurse
Copy-AuditedCardResources $repo $active $candidate $audit
Assert-CardResourceCandidate $repo $candidate $audit
Assert ([IO.File]::ReadAllText((Join-Path $repo 'forge-gui/res/cardsfolder/d/diy_extra.txt')) -eq "Name:My DIY`n") 'Extra source DIY changed'
Assert ([IO.File]::ReadAllText((Join-Path $candidate 'res/cardsfolder/d/diy_extra.txt')) -eq "Name:My runtime DIY`n") 'Extra runtime DIY changed'
$current = Get-CardResourceAudit $repo $candidate $target @($target)
Assert (@($current.files | Where-Object decision -ne 'current').Count -eq 0) 'Candidate is not fully current'

# Source and installed local changes must independently block an official modification.
Put $repo $old "Name:Old`nOracle:My source DIY change`n"
$conflict = Get-CardResourceAudit $repo $active $target @($base)
Assert (($conflict.files | Where-Object path -eq $old).decision -eq 'block') 'Source DIY overwritten by new official content'
Put $repo $old "Name:Old`nOracle:New official fix`n"
Put $active 'res/cardsfolder/o/old.txt' "Name:Old`nOracle:My installed DIY change`n"
$conflict = Get-CardResourceAudit $repo $active $target @($base)
Assert (($conflict.files | Where-Object path -eq $old).decision -eq 'block') 'Installed DIY overwritten by new official content'
$preserved = Get-CardResourceAudit $repo $active $target @($target)
Assert (($preserved.files | Where-Object path -eq $old).decision -eq 'preserve-diy') 'Unchanged official file must preserve its local override'

# A file altered after the audit must never be silently replaced.
$raceBlocked = $false
try { Copy-AuditedCardResources $repo $active $candidate $audit } catch { $raceBlocked = $_.Exception.Message -like '*changed after audit*' }
Assert $raceBlocked 'Concurrent runtime change was overwritten'
$tamperBlocked = $false
Put $candidate 'res/tokenscripts/new_token.txt' "Name:Broken staged token`n"
try { Assert-CardResourceCandidate $repo $candidate $audit } catch { $tamperBlocked = $true }
Assert $tamperBlocked 'Final staged resource corruption was accepted'

Assert (-not (Test-OfficialCardResourcePath 'forge-gui/res/cardsfolder/x.dck')) 'Deck admitted to resource audit'
Assert (-not (Test-OfficialCardResourcePath 'forge-gui/res/cardsfolder/../outside.txt')) 'Traversal admitted to resource audit'
Assert ((Get-UpdateDecision 'M' $token) -eq 'resource-audit') 'Modified tokens skipped'
Assert ((Get-CardResourceBases ([pscustomobject]@{cardResourceCommit=$base}) $null $target) -eq $base) 'Independent resource baseline ignored'
Assert ((Get-CardResourceBases ([pscustomobject]@{cardResourceCommit=$base}) ([pscustomobject]@{cardResourceCommit=$target}) $base) -eq $target) 'Previous resource state ignored'

# An official edition rename must remove the old known official file from both trees.
$renameRepo = Join-Path $temp 'rename-repo'; $renameActive = Join-Path $temp 'rename-active'; $renameCandidate = Join-Path $temp 'rename-candidate'
Invoke-Native git @('init', $renameRepo) | Out-Null
Invoke-Git $renameRepo @('config', 'user.name', 'Resource Update Test') | Out-Null
Invoke-Git $renameRepo @('config', 'user.email', 'resource-test@example.invalid') | Out-Null
$retiredEdition = 'forge-gui/res/editions/Alchemy Karlov Manor.txt'
$replacementEdition = 'forge-gui/res/editions/Alchemy Murders at Karlov Manor.txt'
Put $renameRepo $retiredEdition "[metadata]`nCode=YMKM`n[cards]`n1 R Existing`n"
Put $renameActive 'res/editions/Alchemy Karlov Manor.txt' "[metadata]`nCode=YMKM`n[cards]`n1 R Existing`n"
Invoke-Git $renameRepo @('add', '--', 'forge-gui/res') | Out-Null
Invoke-Git $renameRepo @('commit', '-m', 'old edition name') | Out-Null
$renameBase = (Invoke-Git $renameRepo @('rev-parse', 'HEAD')).Trim()
Invoke-Git $renameRepo @('mv', '--', $retiredEdition, $replacementEdition) | Out-Null
Put $renameRepo $replacementEdition "[metadata]`nCode=YMKM`n[cards]`n1 R Existing`n2 R New`n"
Invoke-Git $renameRepo @('commit', '-am', 'renamed official edition') | Out-Null
$renameTarget = (Invoke-Git $renameRepo @('rev-parse', 'HEAD')).Trim()
Invoke-Git $renameRepo @('checkout', '-b', 'old-runtime', $renameBase) | Out-Null
$renameAudit = Get-CardResourceAudit $renameRepo $renameActive $renameTarget @($renameTarget, $renameBase)
Assert (($renameAudit.files | Where-Object path -eq $retiredEdition).decision -eq 'retire') 'Known old official edition was not retired'
Assert (($renameAudit.files | Where-Object path -eq $replacementEdition).decision -eq 'repair') 'Replacement edition was not copied'
Repair-CardResourceSource $renameRepo $renameTarget $renameAudit
New-Item -ItemType Directory -Path $renameCandidate -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $renameActive 'res') -Destination $renameCandidate -Recurse
Copy-AuditedCardResources $renameRepo $renameActive $renameCandidate $renameAudit
Assert-CardResourceCandidate $renameRepo $renameCandidate $renameAudit
Assert (-not (Test-Path -LiteralPath (Join-Path $renameCandidate 'res/editions/Alchemy Karlov Manor.txt'))) 'Old edition remained beside replacement'
Put $renameCandidate 'res/editions/DIY Duplicate.txt' "[metadata]`nCode=YMKM`n"
$duplicateBlocked = $false
try { Assert-CardResourceCandidate $renameRepo $renameCandidate $renameAudit } catch { $duplicateBlocked = $_.Exception.Message -like '*YMKM*' }
Assert $duplicateBlocked 'Duplicate edition Code accepted'
Put $renameActive 'res/editions/Alchemy Karlov Manor.txt' "[metadata]`nCode=YMKM`n[cards]`n1 M Local DIY`n"
$renameConflict = Get-CardResourceAudit $renameRepo $renameActive $renameTarget @($renameBase)
Assert (($renameConflict.files | Where-Object path -eq $retiredEdition).decision -eq 'block') 'Modified local edition retired without review'
'DIY_RESOURCE_UPDATE_TESTS=OK (zero engine delta, full tree, official modifications, tokens, CRLF, both DIY conflict paths, retained extras, candidate integrity, concurrent changes, official edition rename, duplicate Code, changed local edition retained)'
