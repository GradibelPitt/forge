param(
    [string]$Request,
    [switch]$LibraryOnly,
    [switch]$PlanOnly,
    [switch]$NoActivate,
    [string]$SourceRoot,
    [string]$UpstreamTarget = 'master',
    [string]$UpstreamBase = ''
)
$ErrorActionPreference = 'Stop'

function Write-Utf8([string]$Path, [string]$Text) {
    [IO.File]::WriteAllText($Path, $Text, (New-Object Text.UTF8Encoding($false)))
}
function Assert-ChildPath([string]$Root, [string]$Path) {
    $prefix = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $full = [IO.Path]::GetFullPath($Path)
    if (-not $full.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside update directory: $full"
    }
    # Refuse junctions inside the managed update tree (the install root itself may be migrated).
    $part = $full
    while ($part.Length -ge $prefix.Length) {
        $item = Get-Item -LiteralPath $part -Force -ErrorAction SilentlyContinue
        if ($item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Update path contains a junction or symlink: $part"
        }
        $part = [IO.Path]::GetDirectoryName($part)
    }
}
function Quote-Native([string]$Value) {
    # CommandLineToArgvW quoting, including backslashes immediately before quotes/end.
    return '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}
function Invoke-Native([string]$Exe, [string[]]$Arguments, [string]$OutputFile = '') {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Exe
    $info.Arguments = ($Arguments | ForEach-Object { Quote-Native $_ }) -join ' '
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
    $info.StandardErrorEncoding = New-Object Text.UTF8Encoding($false)
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $output = $stdout.GetAwaiter().GetResult()
    $errorText = $stderr.GetAwaiter().GetResult()
    $code = $process.ExitCode
    $process.Dispose()
    if ($code -ne 0) { throw "$Exe exited $code : $errorText $output" }
    if ($OutputFile) { Write-Utf8 $OutputFile $output } else { return $output }
}
function Invoke-Git([string]$Root, [string[]]$Arguments, [string]$OutputFile = '') {
    Invoke-Native 'git' (@('-c', "safe.directory=$Root", '-c', 'core.quotepath=false', '-c', 'core.longpaths=true',
        '-c', 'core.autocrlf=false', '--literal-pathspecs', '-C', $Root) + $Arguments) $OutputFile
}
function Get-UpdateDecision([string]$Status, [string]$Path) {
    if ($Path -match '(^|/)\.\.(/|$)|[:\\\x00-\x1f]' -or $Path.StartsWith('/')) { return 'block' }
    # Official deck examples are outside the allowed update surface, not a reason to block new cards.
    if ($Path -match '(?i)\.dck$') { return 'skip' }
    if ($Path -match '^forge-gui/src/main/java/forge/gui/(CardNameSearchIndex|LatestSearchGeneration)\.java$' -or
        $Path -match '^forge-gui-desktop/src/main/java/forge/gui/(ListChooser|GuiChoose)\.java$' -or
        $Path -eq 'forge-game/src/main/java/forge/game/card/CardFaceView.java') {
        if ($Status -notin @('A', 'M')) { return 'block' }
        return 'skip'
    }
    if ($Path -match '^(pom\.xml|forge-(core|game|ai|gui|gui-desktop)/pom\.xml)$') { return 'block' }
    # Updater implementation is owned by the DIY release, never by an official update.
    if ($Path -match '^forge-gui/src/.*/forge/download/(Diy|AutoUpdater)') { return 'skip' }
    $engine = $Path -match '^forge-(core|game|ai|gui|gui-desktop)/src/(main|test)/java/.+\.java$'
    if ($engine) {
        if ($Status -eq 'A' -or $Status -eq 'M') { return 'merge' }
        return 'block'
    }
    # Shared UI/translation Java goes through the member AND bound dependency gates.
    # Skin, language data, custom/, launchers and build machinery stay DIY-owned.
    if ($Path -match '^forge-gui/res/cardsfolder/.+\.txt$' -and $Status -eq 'A') { return 'merge' }
    if ($Path -match '^forge-gui/res/editions/[^/]+\.txt$' -and $Status -in @('A', 'M')) { return 'merge' }
    return 'skip'
}
function Get-UpdatePlan([string]$Root, [string]$Base, [string]$Target) {
    $rows = Invoke-Git $Root @('diff', '--name-status', '--no-renames', $Base, $Target)
    $plan = @()
    foreach ($row in ($rows -split "`n")) {
        if (-not $row.Trim()) { continue }
        $fields = $row.TrimEnd("`r") -split "`t", 2
        $decision = Get-UpdateDecision $fields[0] $fields[1]
        if ($fields[1] -eq 'pom.xml' -and $fields[0] -eq 'M') {
            # A release label alone does not require replacing DIY build configuration.
            $oldPom = Invoke-Git $Root @('show', "${Base}:pom.xml")
            $newPom = Invoke-Git $Root @('show', "${Target}:pom.xml")
            if (($oldPom -replace '<versionCode>[^<]+</versionCode>', '<versionCode/>') -eq
                ($newPom -replace '<versionCode>[^<]+</versionCode>', '<versionCode/>')) { $decision = 'skip' }
        }
        $plan += [pscustomobject]@{status=$fields[0]; path=$fields[1]; decision=$decision}
    }
    return $plan
}
function Merge-UpdatePaths([string]$Root, [string]$Base, [string]$Target, [string[]]$Paths, [string]$Job) {
    for ($offset = 0; $offset -lt $Paths.Count; $offset += 30) {
        $batch = @($Paths | Select-Object -Skip $offset -First 30)
        $patch = Join-Path $Job "upstream-$offset.patch"
        Invoke-Git $Root (@('diff', '--binary', '--full-index', '--no-renames', '--diff-filter=AM', $Base, $Target, '--') + $batch) $patch
        # apply --3way uses the original blob IDs and keeps non-overlapping DIY modifications.
        # Any conflict occurs only in this disposable source tree; no runtime is touched.
        Invoke-Git $Root @('apply', '--3way', '--index', '--whitespace=nowarn', $patch) | Out-Null
    }
}
function Get-JavaHome([string]$Preferred, [string]$Tools) {
    $choices = @($Preferred, $env:JAVA_HOME)
    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javac) { $choices += Split-Path (Split-Path $javac.Source) }
    foreach ($dir in @((Join-Path $env:LOCALAPPDATA 'Programs/Eclipse Adoptium'), (Join-Path $env:ProgramFiles 'Eclipse Adoptium'), (Join-Path $Tools 'jdk'))) {
        if (Test-Path -LiteralPath $dir) { $choices += @(Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) }
    }
    foreach ($candidate in $choices) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe'))) {
            $version = Invoke-Native (Join-Path $candidate 'bin/javac.exe') @('-version')
            if ($version -match 'javac 17\.') { return $candidate }
        }
    }
    Write-Host 'Downloading a verified Java 17 compiler...'
    $assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = @($assets)[0].binary.package
    if ($package.link -notmatch '^https://github\.com/adoptium/' -or $package.checksum -notmatch '^[a-fA-F0-9]{64}$') { throw 'Unexpected JDK download metadata.' }
    $zip = Join-Path $Tools 'jdk.zip'
    Invoke-WebRequest -UseBasicParsing $package.link -OutFile $zip
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $package.checksum) { throw 'JDK checksum mismatch.' }
    $jdkRoot = Join-Path $Tools 'jdk'
    Expand-Archive -LiteralPath $zip -DestinationPath $jdkRoot -Force
    return Get-JavaHome '' $Tools
}
function Get-Maven([string]$Tools) {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $maven = Join-Path $Tools 'apache-maven-3.9.16/bin/mvn.cmd'
    if (-not (Test-Path -LiteralPath $maven)) {
        Write-Host 'Downloading verified Maven build tools...'
        $url = 'https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip'
        $zip = Join-Path $Tools 'maven.zip'
        $checksum = ((Invoke-WebRequest -UseBasicParsing ($url + '.sha512')).Content -split '\s+')[0]
        if ($checksum -notmatch '^[a-fA-F0-9]{128}$') { throw 'Invalid Maven checksum metadata.' }
        Invoke-WebRequest -UseBasicParsing $url -OutFile $zip
        if ((Get-FileHash -LiteralPath $zip -Algorithm SHA512).Hash -ne $checksum) { throw 'Maven checksum mismatch.' }
        Expand-Archive -LiteralPath $zip -DestinationPath $Tools -Force
    }
    return $maven
}
function Assert-DiyClasses([string]$Jar) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        foreach ($name in @('forge/game/ability/effects/CardDiscoverEffect.class',
            'forge/game/ability/effects/CardDiscoverCandidateFilter.class', 'forge/game/keyword/Boarding.class',
            'forge/game/keyword/HarmonyKeyword.class', 'forge/game/player/PlayerSpellRuleRegistry.class',
            'forge/gui/CardNameSearchIndex.class', 'forge/gui/LatestSearchGeneration.class',
            'forge/gui/ListChooser.class', 'forge/gui/GuiChoose.class', 'forge/game/card/CardFaceView.class',
            'forge/download/DiyUpdateBridge.class', 'forge/download/diy-updater.ps1', 'forge/download/DiyProtection.java',
            'forge/download/diy-protection-history.tsv')) {
            if (-not $zip.GetEntry($name)) { throw "Compiled DIY component missing: $name" }
        }
    } finally { $zip.Dispose() }
}
function Write-ActivePointer([string]$Root, $Pointer) {
    $active = Join-Path $Root 'active.json'
    $next = Join-Path $Root ('active-' + [guid]::NewGuid().ToString('N') + '.json')
    Assert-ChildPath $Root $next
    Write-Utf8 $next ($Pointer | ConvertTo-Json -Depth 8)
    if (Test-Path -LiteralPath $active) {
        [IO.File]::Replace($next, $active, (Join-Path $Root ('previous-' + [guid]::NewGuid().ToString('N') + '.json')))
    } else { [IO.File]::Move($next, $active) }
}

function Resolve-UpstreamBase($Release, $PreviousState, [string]$ExplicitBase = '') {
    $base = $ExplicitBase
    if ($PreviousState) { $base = $PreviousState.upstreamCommit }
    elseif (-not $base) { $base = $Release.upstreamCommit }
    if (-not $base -and $Release.sourceCommit -eq '0d87c2c71c269d645188ece26413ef89f4b9519a') {
        # Compatibility for the already published, reviewed release without upstream metadata.
        $base = '4bee0abda5277ad8b8def2ed1229458bb7121fc0'
    }
    if ($base -notmatch '^[a-f0-9]{40}$') { throw 'Missing/invalid reviewed official baseline; update release.json upstreamCommit before updating.' }
    return $base
}
function Set-DesktopSparseCheckout([string]$Root) {
    $patterns = @('/pom.xml', '/.mvn/', '/forge-core/', '/forge-game/', '/forge-ai/',
        '/forge-gui/', '/forge-gui-desktop/', '/custom/', '/checkstyle*', '/LICENSE*',
        '/COPYING*', '!*.dck')
    Invoke-Git $Root (@('sparse-checkout', 'set', '--no-cone', '--') + $patterns) | Out-Null
}
function Initialize-DesktopReactor([string]$Root) {
    $pom = Join-Path $Root 'pom.xml'
    $xml = New-Object Xml.XmlDocument
    $xml.PreserveWhitespace = $true
    $xml.Load($pom)
    $allowed = @('forge-core', 'forge-game', 'forge-ai', 'forge-gui', 'forge-gui-desktop')
    $modules = @($xml.SelectNodes('/*[local-name()="project"]/*[local-name()="modules"]/*[local-name()="module"]'))
    foreach ($module in $modules) {
        if ($module.InnerText -notin $allowed) { [void]$module.ParentNode.RemoveChild($module) }
    }
    $actual = @($xml.SelectNodes('/*[local-name()="project"]/*[local-name()="modules"]/*[local-name()="module"]') | ForEach-Object { $_.InnerText })
    if (@(Compare-Object $allowed $actual).Count) { throw 'Desktop reactor dependency set changed; review required.' }
    $xml.Save($pom)
}
function Invoke-Protection([string]$Jdk, [string]$Job, [string[]]$Arguments) {
    Invoke-Native (Join-Path $Jdk 'bin/java.exe') (@('-Xmx2g', '-cp', (Join-Path $Job 'guard-classes'), 'DiyProtection') + $Arguments)
}
function Initialize-ProtectionTool([string]$Jdk, [string]$Job, [string]$Classpath = '') {
    foreach ($resourceName in @('DiyProtection.java', 'diy-protection-history.tsv')) {
      $resourcePath = Join-Path $PSScriptRoot $resourceName
      if (-not (Test-Path -LiteralPath $resourcePath)) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        foreach ($jar in ($Classpath -split ';')) {
            if (-not $jar) { continue }
            $zip = [IO.Compression.ZipFile]::OpenRead($jar)
            try {
                $entry = $zip.GetEntry("forge/download/$resourceName")
                if ($entry) { [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $resourcePath); break }
            } finally { $zip.Dispose() }
        }
      }
      if (-not (Test-Path -LiteralPath $resourcePath)) { throw "Bundled protection resource missing: $resourceName" }
    }
    $guard = Join-Path $PSScriptRoot 'DiyProtection.java'
    if (-not (Test-Path -LiteralPath $guard)) { throw 'Bundled DIY protection tool missing; refusing an unguarded update.' }
    $classes = Join-Path $Job 'guard-classes'
    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    Invoke-Native (Join-Path $Jdk 'bin/javac.exe') @('-encoding', 'UTF-8', '-d', $classes, $guard) | Out-Null
}
function Get-ApplicationClasspath([string]$App, $Release) {
    $jars = @()
    # The launcher enumerates every overlay and sorts it by filename.
    $overlayRoot = Join-Path $App 'overlays'
    $actualNames = @()
    if (Test-Path -LiteralPath $overlayRoot) { $actualNames = @(Get-ChildItem -LiteralPath $overlayRoot -File -Filter '*.jar' | Sort-Object Name | Select-Object -ExpandProperty Name) }
    $expectedNames = @($Release.moduleOverlays | Where-Object { $_ } | Sort-Object)
    if (($expectedNames -join '|') -cne ($actualNames -join '|')) { throw 'Active overlays differ from release metadata.' }
    foreach ($name in $actualNames) {
        if ($name -notmatch '^forge-(core|game|ai|gui|gui-desktop)\.jar$') { throw "Invalid overlay: $name" }
        $jar = Join-Path $App "overlays/$name"
        if (-not (Test-Path -LiteralPath $jar)) { throw "Active overlay missing: $name" }
        $jars += $jar
    }
    $main = @(Get-ChildItem -LiteralPath $App -File -Filter '*-jar-with-dependencies.jar')
    if ($main.Count -ne 1) { throw 'Expected one active desktop aggregate JAR.' }
    return (@($jars) + $main[0].FullName) -join ';'
}
function New-ProtectionCatalog([string]$Root, [string]$Base, [string]$Jdk, [string]$Job, [string]$Catalog) {
    $official = Join-Path $Job 'official-java'
    $archive = Join-Path $Job 'official-java.zip'
    $paths = @('forge-core', 'forge-game', 'forge-ai', 'forge-gui', 'forge-gui-desktop') | ForEach-Object { "$_/src/main/java" }
    # Only Java source blobs are requested here, never mobile files or decks.
    Invoke-Git $Root (@('archive', '--format=zip', "--output=$archive", $Base, '--') + $paths) | Out-Null
    Expand-Archive -LiteralPath $archive -DestinationPath $official
    Invoke-Protection $Jdk $Job @('catalog', $Root, $official, $Catalog, '--require-card-name-search', (Join-Path $PSScriptRoot 'diy-protection-history.tsv'))
}
function Get-ProtectedFileManifest([string]$Root, [string]$Base) {
    $rules = @{}
    # All tracked non-Java DIY files are immutable during an official update. This also
    # protects registrations/resources/tests, in addition to the Java member catalog.
    $changed = Invoke-Git $Root @('diff', '--name-only', '--no-renames', $Base, 'HEAD', '--',
        'custom', 'forge-core', 'forge-game', 'forge-ai', 'forge-gui', 'forge-gui-desktop')
    $owned = Invoke-Git $Root @('ls-files', '--', 'custom', 'forge-gui/res/skins/warmwood', 'forge-gui/src/main/resources/forge/download')
    foreach ($path in @((($changed + "`n" + $owned) -split "`n") | Sort-Object -Unique)) {
        $path = $path.TrimEnd("`r")
        if (-not $path -or $path -match '(?i)\.dck$') { continue }
        if ($path -match '/src/main/java/.*\.java$') { continue }
        # Excluded modules are absent by design, not files to inspect, copy or delete.
        if ($path -notmatch '^(custom/|forge-(core|game|ai|gui|gui-desktop)/)') { continue }
        $file = Join-Path $Root $path
        Assert-ChildPath $Root $file
        $rules[$path] = if (Test-Path -LiteralPath $file -PathType Leaf) { (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash } else { 'ABSENT' }
    }
    return $rules
}
function Assert-ProtectedFiles([string]$Root, $Rules) {
    foreach ($path in $Rules.Keys) {
        if ($path -match '(?i)\.dck$') { throw 'Deck path is invalid in a protection manifest.' }
        $file = Join-Path $Root $path
        Assert-ChildPath $Root $file
        $actual = if (Test-Path -LiteralPath $file -PathType Leaf) { (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash } else { 'ABSENT' }
        if ($actual -ne $Rules[$path]) { throw "Protected DIY file changed: $path" }
    }
}
function Assert-BundledProtection([string]$Jar, [string]$Job) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        foreach ($name in @('diy-updater.ps1', 'DiyProtection.java', 'diy-protection-history.tsv')) {
            $entry = $zip.GetEntry("forge/download/$name")
            if (-not $entry) { throw "Built updater resource missing: $name" }
            $stream = $entry.Open(); $reader = New-Object IO.StreamReader($stream)
            try { $actual = $reader.ReadToEnd().Replace("`r`n", "`n") } finally { $reader.Dispose() }
            $expected = [IO.File]::ReadAllText((Join-Path $PSScriptRoot $name)).Replace("`r`n", "`n")
            if ($actual -cne $expected) { throw "Built updater policy differs from the running protected policy: $name" }
        }
    } finally { $zip.Dispose() }
}

if ($LibraryOnly) { return }
$job = Split-Path -Parent ([IO.Path]::GetFullPath($Request))
$lock = $null
try {
    $config = Get-Content -LiteralPath $Request -Raw -Encoding UTF8 | ConvertFrom-Json
    $install = [IO.Path]::GetFullPath($config.installRoot)
    $updates = Join-Path $install 'updates'
    Assert-ChildPath $updates $job
    $repo = Join-Path $install 'repo'
    $releaseFile = Join-Path $repo 'release.json'
    $release = Get-Content -LiteralPath $releaseFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $releaseHash = (Get-FileHash -LiteralPath $releaseFile -Algorithm SHA256).Hash
    if ($release.sourceCommit -notmatch '^[a-f0-9]{40}$') { throw 'Invalid DIY source commit.' }
    $activeApp = [IO.Path]::GetFullPath($config.appRoot)
    if ($activeApp -ne [IO.Path]::GetFullPath((Join-Path $repo 'app'))) { Assert-ChildPath (Join-Path $updates 'versions') $activeApp }
    # An OS file lock is released on crash, so it cannot leave a stale PID lock.
    $lock = [IO.File]::Open((Join-Path $updates 'update.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) { throw 'Git is required; run the ForgeDIY launcher once to prepare it.' }
    $generation = [guid]::NewGuid().ToString('N')
    $versionRoot = Join-Path $updates "versions/$generation"
    $source = Join-Path $versionRoot 'source'
    Assert-ChildPath $updates $source
    New-Item -ItemType Directory -Path $versionRoot -Force | Out-Null
    $previous = Join-Path (Split-Path $activeApp) 'update-state.json'
    $previousState = $null
    if (Test-Path -LiteralPath $previous) {
        $state = Get-Content -LiteralPath $previous -Raw -Encoding UTF8 | ConvertFrom-Json
        $previousState = $state
        if ($state.baseReleaseHash -ne $releaseHash) { throw 'DIY baseline changed; restart through the launcher before updating.' }
        $previousSource = Join-Path (Split-Path $activeApp) 'source'
        Assert-ChildPath (Join-Path $updates 'versions') $previousSource
        Invoke-Native git @('-c', 'core.autocrlf=false', '-c', 'core.longpaths=true', 'clone', '--no-checkout', '--no-hardlinks', $previousSource, $source) | Out-Null
        Set-DesktopSparseCheckout $source
        Invoke-Git $source @('checkout') | Out-Null
    } elseif ($SourceRoot) {
        # Developer/test input is cloned, never modified in place.
        Invoke-Native git @('-c', 'core.autocrlf=false', '-c', 'core.longpaths=true', 'clone', '--no-checkout', '--no-hardlinks', $SourceRoot, $source) | Out-Null
        Set-DesktopSparseCheckout $source
        Invoke-Git $source @('checkout') | Out-Null
    } else {
        Write-Host 'Fetching the exact DIY source baseline...'
        Invoke-Native git @('init', $source) | Out-Null
        Invoke-Git $source @('remote', 'add', 'origin', 'https://github.com/GradibelPitt/forge.git') | Out-Null
        Invoke-Git $source @('config', 'remote.origin.promisor', 'true') | Out-Null
        Invoke-Git $source @('config', 'remote.origin.partialclonefilter', 'blob:none') | Out-Null
        Set-DesktopSparseCheckout $source
        Invoke-Git $source @('fetch', '--filter=blob:none', '--depth=1', '--no-tags', 'origin', $release.sourceCommit) | Out-Null
        Invoke-Git $source @('checkout', '-b', 'local-diy', 'FETCH_HEAD') | Out-Null
    }
    if (-not $previousState -and (Invoke-Git $source @('rev-parse', 'HEAD')).Trim() -ne $release.sourceCommit) {
        throw 'Source checkout does not match the released DIY baseline.'
    }
    $UpstreamBase = Resolve-UpstreamBase $release $previousState $UpstreamBase
    Write-Host 'Fetching official source changes...'
    Invoke-Git $source @('remote', 'add', 'reviewed-upstream', 'https://github.com/Card-Forge/forge.git') | Out-Null
    Invoke-Git $source @('config', 'remote.reviewed-upstream.promisor', 'true') | Out-Null
    Invoke-Git $source @('config', 'remote.reviewed-upstream.partialclonefilter', 'blob:none') | Out-Null
    Invoke-Git $source @('fetch', '--filter=blob:none', '--depth=1', '--no-tags', 'reviewed-upstream', $UpstreamBase) | Out-Null
    Invoke-Git $source @('fetch', '--filter=blob:none', '--depth=1', '--no-tags', 'reviewed-upstream', $UpstreamTarget) | Out-Null
    $target = (Invoke-Git $source @('rev-parse', 'FETCH_HEAD')).Trim()
    $plan = @(Get-UpdatePlan $source $UpstreamBase $target)
    Write-Utf8 (Join-Path $job 'plan.json') (ConvertTo-Json -InputObject $plan -Depth 6)
    $blocked = @($plan | Where-Object { $_.decision -eq 'block' })
    $paths = @($plan | Where-Object { $_.decision -eq 'merge' } | Select-Object -ExpandProperty path)
    Write-Host "Selected $($paths.Count) paths; blocked $($blocked.Count); official target $target"
    if ($PlanOnly) { Write-Utf8 (Join-Path $job 'result.txt') 'Plan completed; no runtime changes.'; exit 0 }
    if ($blocked.Count) { throw "Review required for deleted/renamed engine files or build dependencies: $($blocked.path -join ', '). See plan.json." }
    if (-not $paths.Count) { Write-Utf8 (Join-Path $job 'result.txt') 'No eligible updates. Current DIY version retained.'; exit 0 }
    $tools = Join-Path $updates 'tools'
    New-Item -ItemType Directory -Path $tools -Force | Out-Null
    $jdk = Get-JavaHome $config.javaHome $tools
    $classpathRelease = if ($previousState) { [pscustomobject]@{moduleOverlays=@()} } else { $release }
    $baselineClasspath = Get-ApplicationClasspath $activeApp $classpathRelease
    Initialize-ProtectionTool $jdk $job $baselineClasspath
    # Carry the executing DIY policy into the candidate even when it was delivered as a
    # resource-only overlay over an earlier reviewed engine source commit.
    foreach ($name in @('diy-updater.ps1', 'DiyProtection.java', 'diy-protection-history.tsv')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $source "forge-gui/src/main/resources/forge/download/$name") -Force
    }
    $catalog = Join-Path $versionRoot 'protection.tsv'
    if ($previousState -and $previousState.policyVersion -eq 2) {
        $previousCatalog = Join-Path (Split-Path $activeApp) 'protection.tsv'
        if ((Get-FileHash -LiteralPath $previousCatalog -Algorithm SHA256).Hash -ne $previousState.protectionCatalogHash) { throw 'Previous protection catalog is missing or changed.' }
        Copy-Item -LiteralPath $previousCatalog -Destination $catalog
    } else { New-ProtectionCatalog $source $UpstreamBase $jdk $job $catalog }
    Invoke-Protection $jdk $job @('verify', $source, $catalog)
    $bindings = Join-Path $job 'baseline-bindings.tsv'
    Invoke-Protection $jdk $job @('bindings', $source, $bindings, $baselineClasspath)
    $protectedFiles = Get-ProtectedFileManifest $source $UpstreamBase
    if ($previousState -and $previousState.policyVersion -eq 2) {
        $previousFiles = Join-Path (Split-Path $activeApp) 'protected-files.json'
        if ((Get-FileHash -LiteralPath $previousFiles -Algorithm SHA256).Hash -ne $previousState.protectedFilesHash) { throw 'Previous protected file manifest changed.' }
        $savedFiles = Get-Content -LiteralPath $previousFiles -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($property in $savedFiles.PSObject.Properties) { $protectedFiles[$property.Name] = $property.Value }
        Assert-ProtectedFiles $source $protectedFiles
    }
    Write-Utf8 (Join-Path $versionRoot 'protected-files.json') ($protectedFiles | ConvertTo-Json -Depth 4)
    foreach ($path in $paths) {
        if ($path.StartsWith('forge-gui/res/')) {
            $installedResource = Join-Path $activeApp $path.Substring('forge-gui/'.Length)
            $baselineResource = Join-Path $source $path
            if (Test-Path -LiteralPath $installedResource) {
                if (-not (Test-Path -LiteralPath $baselineResource) -or
                    (Get-FileHash -LiteralPath $installedResource -Algorithm SHA256).Hash -ne
                    (Get-FileHash -LiteralPath $baselineResource -Algorithm SHA256).Hash) {
                    throw "Active resource has a DIY/local change; refusing official overwrite: $path"
                }
            }
        }
    }
    Merge-UpdatePaths $source $UpstreamBase $target $paths $job
    Assert-ProtectedFiles $source $protectedFiles
    Invoke-Protection $jdk $job @('verify', $source, $catalog)
    $deleted = Invoke-Git $source @('diff', '--cached', '--name-only', '--diff-filter=D')
    if ($deleted.Trim()) { throw 'Update would delete existing source files.' }
    Invoke-Git $source @('diff', '--cached', '--check') | Out-Null
    Initialize-DesktopReactor $source
    $maven = Get-Maven $tools
    $env:JAVA_HOME = $jdk
    $env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH
    $mavenCache = Join-Path $env:USERPROFILE '.m2/repository'
    if (-not (Test-Path -LiteralPath $mavenCache)) { $mavenCache = Join-Path $tools 'm2' }
    Write-Host 'Compiling and testing the merged DIY engine and preserved DIY desktop...'
    Push-Location $source
    try {
        & $maven '-B' '-ntp' "-Dmaven.repo.local=$mavenCache" '-pl' 'forge-gui-desktop' '-am' 'clean' 'package'
        if ($LASTEXITCODE -ne 0) { throw "Compilation or regression tests failed ($LASTEXITCODE)." }
    } finally { Pop-Location }
    $jar = @(Get-ChildItem -LiteralPath (Join-Path $source 'forge-gui-desktop/target') -File -Filter '*-jar-with-dependencies.jar')
    if ($jar.Count -ne 1) { throw 'Expected exactly one compiled desktop JAR.' }
    Assert-DiyClasses $jar[0].FullName
    Assert-BundledProtection $jar[0].FullName $job
    Invoke-Protection $jdk $job @('verify-bindings', $source, $catalog, $bindings, $jar[0].FullName)
    Assert-ProtectedFiles $source $protectedFiles
    Invoke-Git $source @('add', '--', 'pom.xml', 'forge-gui/src/main/resources/forge/download/diy-updater.ps1', 'forge-gui/src/main/resources/forge/download/DiyProtection.java', 'forge-gui/src/main/resources/forge/download/diy-protection-history.tsv') | Out-Null
    Invoke-Git $source @('-c', 'user.name=ForgeDIY Local Updater', '-c', 'user.email=local-updater@invalid', 'commit', '-m', "Guarded selective upstream update $target") | Out-Null
    $app = Join-Path $versionRoot 'app'
    New-Item -ItemType Directory -Path $app | Out-Null
    # Copy only application resources. Never traverse user profiles, managed custom payload or decks.
    & robocopy (Join-Path $activeApp 'res') (Join-Path $app 'res') /E /XJ /XF *.dck /R:1 /W:1 /NFL /NDL /NJH /NJS /NP
    if ($LASTEXITCODE -gt 7) { throw 'Cannot stage existing application resources.' }
    foreach ($path in $paths) {
        if ($path.StartsWith('forge-gui/res/')) {
            $dest = Join-Path $app ($path.Substring('forge-gui/'.Length))
            Assert-ChildPath $app $dest
            New-Item -ItemType Directory -Path (Split-Path $dest) -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $source $path) -Destination $dest
        }
    }
    Copy-Item -LiteralPath $jar[0].FullName -Destination $app
    $jarHash = (Get-FileHash -LiteralPath (Join-Path $app $jar[0].Name) -Algorithm SHA256).Hash
    Write-Utf8 (Join-Path $app 'BUILD-ID.txt') ("DIY-upstream-" + $target.Substring(0, 12))
    $state = [ordered]@{schema=1; generation=$generation; upstreamCommit=$target; baseReleaseHash=$releaseHash;
        sourceCommit=(Invoke-Git $source @('rev-parse', 'HEAD')).Trim(); jar=$jar[0].Name; jarHash=$jarHash;
        policyVersion=2; protectionCatalogHash=(Get-FileHash -LiteralPath $catalog -Algorithm SHA256).Hash;
        protectedFilesHash=(Get-FileHash -LiteralPath (Join-Path $versionRoot 'protected-files.json') -Algorithm SHA256).Hash;
        protectionPolicyHash=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'DiyProtection.java') -Algorithm SHA256).Hash;
        historyCatalogHash=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'diy-protection-history.tsv') -Algorithm SHA256).Hash;
        javaVersion=(Invoke-Native (Join-Path $jdk 'bin/javac.exe') @('-version')).Trim();
        verification=@('desktop-package-and-tests', 'protected-files', 'java-members', 'resolved-bindings-and-dependencies', 'bundled-policy')}
    $hashes = @(Get-ChildItem -LiteralPath $app -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($app.Length + 1).Replace('\', '/')
        (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash + ' *' + $relative
    })
    Write-Utf8 (Join-Path $app 'manifest-critical.sha256') (($hashes | Sort-Object) -join "`n")
    $state['manifestHash'] = (Get-FileHash -LiteralPath (Join-Path $app 'manifest-critical.sha256') -Algorithm SHA256).Hash
    Write-Utf8 (Join-Path $versionRoot 'update-state.json') ($state | ConvertTo-Json)
    if ((Get-FileHash -LiteralPath $releaseFile -Algorithm SHA256).Hash -ne $releaseHash) { throw 'DIY release changed during compilation; staged update was not activated.' }
    if (-not $NoActivate) { Write-ActivePointer $updates $state }
    Write-Utf8 (Join-Path $job 'result.txt') 'DIY update compiled and validated. Save your work, close Forge and use the ForgeDIY launcher to load it. The previous version is retained.'
    Write-Host 'DIY_UPDATE_READY'
} catch {
    $detail = $_.Exception.Message
    Write-Host $detail
    Write-Utf8 (Join-Path $job 'result.txt') ("DIY update stopped; current game files are unchanged.`n" + $detail)
    exit 1
} finally {
    if ($lock) { $lock.Dispose() }
}
