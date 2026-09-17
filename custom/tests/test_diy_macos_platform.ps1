param([string]$Updater = (Join-Path $PSScriptRoot '../../forge-gui/src/main/resources/forge/download/diy-updater.ps1'))
$ErrorActionPreference = 'Stop'
. $Updater -LibraryOnly
function Assert($Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
if (Test-WindowsPlatform) { throw 'Run this regression on macOS.' }
$temp = Join-Path ([IO.Path]::GetTempPath()) ('forge mac platform ' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $temp | Out-Null
    Assert ((Get-JavaTool '/test/jdk' 'javac') -eq '/test/jdk/bin/javac') 'Compiler suffix is not portable'
    Assert ((Get-ReleaseFile $temp $null) -eq (Join-Path $temp 'repo-macos/release.json')) 'Wrong macOS release path'
    $arg = 'space "quote" and $literal'
    Assert ((Invoke-Native '/usr/bin/printf' @('%s', $arg)) -ceq $arg) 'Native argv changed'
    $old = Join-Path $temp 'old'
    $new = Join-Path $temp 'new'
    foreach ($relative in @('res/adventure/world/map.tmx','res/skins/default/sprite_adventure.png','res/cardsfolder/a/adventure_awaits.txt','res/skins/default/bg.png','res/test.DCK')) {
        $file = Join-Path $old $relative
        New-Item -ItemType Directory -Path (Split-Path $file) -Force | Out-Null
        Write-Utf8 $file 'fixture'
    }
    Copy-DesktopResources $old $new
    foreach ($relative in @('res/adventure','res/skins/default/sprite_adventure.png','res/test.DCK')) { Assert (-not (Test-Path (Join-Path $new $relative))) "Excluded path copied: $relative" }
    foreach ($relative in @('res/cardsfolder/a/adventure_awaits.txt','res/skins/default/bg.png')) { Assert (Test-Path (Join-Path $new $relative)) "Retained path lost: $relative" }
    New-Item -ItemType Directory -Path (Join-Path $old 'overlays') | Out-Null
    foreach ($name in @('001-forge-diy-updater-resources.jar','002-forge-diy-updater-platform.jar','000-forge-macos-native-audio.jar')) { Write-Utf8 (Join-Path $old "overlays/$name") 'fixture' }
    Write-Utf8 (Join-Path $old 'forge-test-jar-with-dependencies.jar') 'fixture'
    $release = [pscustomobject]@{ moduleOverlays=@('001-forge-diy-updater-resources.jar','002-forge-diy-updater-platform.jar') }
    $classpath = Get-ApplicationClasspath $old $release
    Assert (($classpath -split ':').Count -eq 4) 'Classpath separator or registered overlays wrong'
    Assert ($classpath.StartsWith((Join-Path $old 'overlays/000-forge-macos-native-audio.jar'))) 'Existing local audio overlay lost'
    Write-Utf8 (Join-Path $old 'overlays/unregistered.jar') 'fixture'
    $blocked = $false
    try { Get-ApplicationClasspath $old $release } catch { $blocked = $true }
    Assert $blocked 'Unknown overlays must still be rejected'
    $desktop = Join-Path $temp 'forge-gui-desktop'
    New-Item -ItemType Directory -Path $desktop | Out-Null
    $pom = Join-Path $desktop 'pom.xml'
    $originalPom = '<project xmlns="http://maven.apache.org/POM/4.0.0"><build><plugins><plugin><artifactId>launch4j-maven-plugin</artifactId></plugin></plugins></build></project>'
    Write-Utf8 $pom $originalPom
    function Invoke-MavenPass {
        param($Maven, $Arguments, $Log)
        if (-not ([IO.File]::ReadAllText($pom).Contains('<skip>true</skip>'))) { throw 'macOS attempted Windows EXE packaging' }
        if ($Arguments -notcontains 'package' -or $Arguments -contains '-DskipTests') { throw 'Shared build/test gates were bypassed' }
        return 0
    }
    $result = Invoke-CheckedPackage 'fixture-maven' $temp $temp $temp
    Assert ($result.status -eq 'passed') 'Checked packaging result lost'
    Assert ([IO.File]::ReadAllText($pom) -ceq $originalPom) 'Temporary platform packaging changed protected source'
    function Invoke-MavenPass { throw 'simulated build infrastructure failure' }
    try { Invoke-CheckedPackage 'fixture-maven' $temp $temp $temp } catch { }
    Assert ([IO.File]::ReadAllText($pom) -ceq $originalPom) 'Build failure did not restore original POM'
    'DIY_MACOS_PLATFORM=OK (argv, release path, native rsync, exclusions, overlay policy)'
} finally { if (Test-Path $temp) { Remove-Item -LiteralPath $temp -Recurse -Force } }
