param([string]$Jdk = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$resource = Join-Path $PSScriptRoot '../../forge-gui/src/main/resources/forge/download'
. (Join-Path $resource 'diy-updater.ps1') -LibraryOnly
$root = Join-Path ([IO.Path]::GetTempPath()) ('diy-protection-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
Initialize-ProtectionTool $Jdk $root
$relative = 'forge-game/src/main/java/demo/Rules.java'
foreach ($tree in @('official', 'baseline', 'candidate')) {
    New-Item -ItemType Directory -Path (Split-Path (Join-Path $root "$tree/$relative")) -Force | Out-Null
}
$official = @'
package demo;
class Rules {
  int registry = 1;
  int helper() { return 2; }
  int choose(String query) { return helper(); }
  int choose(int query) { return query; }
  int officialOnly() { return 10; }
}
'@
$baseline = $official.Replace('return helper();', 'return registry + helper();')
Write-Utf8 (Join-Path $root "official/$relative") $official
Write-Utf8 (Join-Path $root "baseline/$relative") $baseline
Write-Utf8 (Join-Path $root "candidate/$relative") $baseline
$catalog = Join-Path $root 'catalog.tsv'
$bindings = Join-Path $root 'bindings.tsv'
Invoke-Protection $Jdk $root @('catalog', (Join-Path $root 'baseline'), (Join-Path $root 'official'), $catalog)
Invoke-Protection $Jdk $root @('bindings', (Join-Path $root 'baseline'), $bindings, $root)
$script:checks = 0
function Check-Candidate([string]$Label, [string]$Text, [bool]$Expected, [bool]$Bound = $false) {
    Write-Utf8 (Join-Path $root "candidate/$relative") $Text
    $passed = $true
    try {
        if ($Bound) { Invoke-Protection $Jdk $root @('verify-bindings', (Join-Path $root 'candidate'), $catalog, $bindings, $root) | Out-Null }
        else { Invoke-Protection $Jdk $root @('verify', (Join-Path $root 'candidate'), $catalog) | Out-Null }
    } catch { $passed = $false; if ($Expected) { throw "$Label unexpectedly failed: $_" } }
    if ($passed -ne $Expected) { throw "${Label}: unexpected protection result" }
    $script:checks++
    Write-Host "PASS: $Label"
}
Check-Candidate 'formatting and comments' ($baseline.Replace('int choose(String query)', "/* shifted */`n`nint choose( String query )")) $true $true
Check-Candidate 'unrelated official method' ($baseline.Replace('return 10;', 'return 11;')) $true $true
Check-Candidate 'protected call removed' ($baseline.Replace('return registry + helper();', 'return registry;')) $false
Check-Candidate 'renamed protected method' ($baseline.Replace('choose(String query)', 'renamed(String query)')) $false
Check-Candidate 'overload remains independently editable' ($baseline.Replace('return query;', 'return query + 1;')) $true $true
Check-Candidate 'dependency field deleted' ($baseline.Replace('int registry = 1;', '')) $false $true
Check-Candidate 'helper behavior changed outside DIY method' ($baseline.Replace('return 2;', 'return 3;')) $false $true
Check-Candidate 'local shadow changes binding' ($baseline.Replace('return registry + helper();', 'int registry = 5; return registry + helper();')) $false
Check-Candidate 'ambiguous invalid Java stops' ($baseline.Replace('return registry + helper();', 'return missingSymbol;')) $false $true
Check-Candidate 'new caller changes protected lifecycle' ($baseline.Replace('return 10;', 'return choose("new caller");')) $false $true
# Exact same method text with a different imported static field must fail binding verification.
$aliasOfficial = "package demo; import static demo.AliasOne.value; class Rules { int choose() { return 0; } }"
$aliasBaseline = $aliasOfficial.Replace('return 0;', 'return value;')
foreach ($tree in @('official', 'baseline', 'candidate')) {
    foreach ($name in @('AliasOne', 'AliasTwo')) {
        Write-Utf8 (Join-Path $root "$tree/forge-game/src/main/java/demo/$name.java") "package demo; class $name { static int value = 1; }"
    }
}
Write-Utf8 (Join-Path $root "official/$relative") $aliasOfficial
Write-Utf8 (Join-Path $root "baseline/$relative") $aliasBaseline
Invoke-Protection $Jdk $root @('catalog', (Join-Path $root 'baseline'), (Join-Path $root 'official'), $catalog)
Invoke-Protection $Jdk $root @('bindings', (Join-Path $root 'baseline'), $bindings, $root)
Check-Candidate 'same text rebound to different owner' ($aliasBaseline.Replace('import static demo.AliasOne', 'import static demo.AliasTwo')) $false $true
$dispatchOfficial = 'package demo; class Rules { int choose(Base b) { return 0; } } class Base { int helper() { return 1; } } class Child extends Base {}'
$dispatchBaseline = $dispatchOfficial.Replace('return 0;', 'return b.helper();')
Write-Utf8 (Join-Path $root "official/$relative") $dispatchOfficial
Write-Utf8 (Join-Path $root "baseline/$relative") $dispatchBaseline
Invoke-Protection $Jdk $root @('catalog', (Join-Path $root 'baseline'), (Join-Path $root 'official'), $catalog)
Invoke-Protection $Jdk $root @('bindings', (Join-Path $root 'baseline'), $bindings, $root)
Check-Candidate 'new inherited override changes virtual dispatch' ($dispatchBaseline.Replace('class Child extends Base {}', 'class Child extends Base { int helper() { return 2; } }')) $false $true
Write-Host "DIY_PROTECTION_TESTS=OK; cases=$checks; fixtures=$root"
