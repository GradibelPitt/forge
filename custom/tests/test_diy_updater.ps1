param([string]$Updater = (Join-Path $PSScriptRoot '../../forge-gui/src/main/resources/forge/download/diy-updater.ps1'))
$ErrorActionPreference = 'Stop'
. $Updater -LibraryOnly
function Assert($Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
Assert ((Get-UpdateDecision 'A' 'forge-gui/res/cardsfolder/n/new.txt') -eq 'merge') 'New cards must merge'
Assert ((Get-UpdateDecision 'M' 'forge-gui/res/cardsfolder/o/old.txt') -eq 'skip') 'Existing card scripts preserved'
Assert ((Get-UpdateDecision 'M' 'forge-gui/res/editions/Set.txt') -eq 'merge') 'Edition changes must merge'
Assert ((Get-UpdateDecision 'M' 'forge-game/src/main/java/forge/game/card/Card.java') -eq 'merge') 'Existing engine Java may change'
Assert ((Get-UpdateDecision 'D' 'forge-game/src/main/java/forge/game/keyword/Boarding.java') -eq 'block') 'Java deletion blocked'
Assert ((Get-UpdateDecision 'R100' 'forge-game/src/main/java/forge/game/Old.java') -eq 'block') 'Java rename blocked'
Assert ((Get-UpdateDecision 'M' 'forge-gui-desktop/src/main/java/forge/view/UI.java') -eq 'merge') 'Shared UI must pass symbol gates'
Assert ((Get-UpdateDecision 'M' 'forge-core/src/main/java/forge/util/CardTranslation.java') -eq 'merge') 'Translation Java must pass symbol gates'
Assert ((Get-UpdateDecision 'M' 'forge-core/src/main/java/forge/util/ComparableOp.java') -eq 'merge') 'Shared core dependency omitted'
Assert ((Get-UpdateDecision 'M' 'forge-gui-desktop/src/main/java/forge/gui/ListChooser.java') -eq 'skip') 'Fuzzy chooser must not be overwritten'
Assert ((Get-UpdateDecision 'A' 'forge-gui/src/main/java/forge/gui/CardNameSearchIndex.java') -eq 'skip') 'Official name collision must not replace fuzzy index'
Assert ((Get-UpdateDecision 'D' 'forge-gui/src/main/java/forge/gui/LatestSearchGeneration.java') -eq 'block') 'Fuzzy generation deletion must block'
Assert ((Get-UpdateDecision 'M' 'forge-gui/res/languages/cardnames-zh-CN.txt') -eq 'skip') 'Legacy Chinese data preserved'
Assert ((Resolve-UpstreamBase ([pscustomobject]@{sourceCommit='0d87c2c71c269d645188ece26413ef89f4b9519a'}) $null) -eq '4bee0abda5277ad8b8def2ed1229458bb7121fc0') 'Reviewed release fell back to obsolete upstream'
$explicit = '1111111111111111111111111111111111111111'
Assert ((Resolve-UpstreamBase ([pscustomobject]@{upstreamCommit=$explicit}) $null) -eq $explicit) 'Release upstream metadata ignored'
$missingBase = $false
try { Resolve-UpstreamBase ([pscustomobject]@{sourceCommit='unknown'}) $null } catch { $missingBase = $true }
Assert $missingBase 'Unknown baseline must stop'
Assert ((Get-UpdateDecision 'M' 'pom.xml') -eq 'block') 'Dependency/build changes require review'
Assert ((Get-UpdateDecision 'A' '../outside.java') -eq 'block') 'Traversal blocked'
Assert ((Get-UpdateDecision 'A' 'forge-gui/res/cardsfolder/a/deck.dck') -eq 'skip') 'Decks excluded'
Assert ((Get-UpdateDecision 'M' 'forge-gui-ios/pom.xml') -eq 'skip') 'Unrelated mobile dependencies ignored'
$temp = Join-Path ([IO.Path]::GetTempPath()) ('forge-update-test-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
$repo = Join-Path $temp 'repo'
Invoke-Native git @('init', $repo) | Out-Null
Invoke-Git $repo @('config', 'user.name', 'Updater Test') | Out-Null
Invoke-Git $repo @('config', 'user.email', 'test@example.invalid') | Out-Null
$path = 'forge-game/src/main/java/forge/game/Rules.java'
$file = Join-Path $repo $path
New-Item -ItemType Directory -Path (Split-Path $file) -Force | Out-Null
Write-Utf8 $file "first=old`nline2`nline3`nline4`nlast=old`n"
Invoke-Git $repo @('add', '--', $path) | Out-Null
Invoke-Git $repo @('commit', '-m', 'base') | Out-Null
$base = (Invoke-Git $repo @('rev-parse', 'HEAD')).Trim()
Write-Utf8 $file "first=official`nline2`nline3`nline4`nlast=old`n"
Invoke-Git $repo @('commit', '-am', 'official') | Out-Null
$upstream = (Invoke-Git $repo @('rev-parse', 'HEAD')).Trim()
Invoke-Git $repo @('checkout', '-b', 'diy', $base) | Out-Null
Write-Utf8 $file "first=old`nline2`nline3`nline4`nlast=DIY`n"
Invoke-Git $repo @('commit', '-am', 'diy') | Out-Null
Merge-UpdatePaths $repo $base $upstream @($path) $temp
$text = [IO.File]::ReadAllText($file)
Assert ($text.Contains('first=official') -and $text.Contains('last=DIY')) 'Three-way merge lost official or DIY edits'
Invoke-Git $repo @('commit', '-am', 'merged') | Out-Null
$before = (Invoke-Git $repo @('rev-parse', 'HEAD')).Trim()
Invoke-Git $repo @('checkout', '-b', 'conflicting', $base) | Out-Null
Write-Utf8 $file "first=conflict`nline2`nline3`nline4`nlast=old`n"
Invoke-Git $repo @('commit', '-am', 'conflict') | Out-Null
$conflicted = $false
try { Merge-UpdatePaths $repo $base $upstream @($path) $temp } catch { $conflicted = $true }
Assert $conflicted 'Overlapping Java changes must stop'
Assert ((Invoke-Git $repo @('rev-parse', 'diy')).Trim() -eq $before) 'Conflict changed the accepted source'
$outsideBlocked = $false
try { Assert-ChildPath $temp (Join-Path $temp '../escape') } catch { $outsideBlocked = $true }
Assert $outsideBlocked 'Path containment guard failed'
$pointerRoot = Join-Path $temp 'pointers'
New-Item -ItemType Directory -Path $pointerRoot | Out-Null
Write-ActivePointer $pointerRoot @{generation='first'}
Write-ActivePointer $pointerRoot @{generation='second'}
$active = Get-Content -LiteralPath (Join-Path $pointerRoot 'active.json') -Raw | ConvertFrom-Json
Assert ($active.generation -eq 'second') 'Atomic activation failed'
$backup = @(Get-ChildItem -LiteralPath $pointerRoot -Filter 'previous-*.json')
Assert ($backup.Count -eq 1) 'Previous active version was not retained'
Assert (((Get-Content $backup[0].FullName -Raw | ConvertFrom-Json).generation) -eq 'first') 'Wrong previous version saved'
'DIY_UPDATER_TESTS=OK (policy, real Git merge, conflict isolation, path containment)'
