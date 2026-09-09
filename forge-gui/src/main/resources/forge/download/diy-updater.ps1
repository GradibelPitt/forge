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
function Invoke-Native([string]$Exe, [string[]]$Arguments, [string]$OutputFile = '', [switch]$LiveOutput) {
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
    if ($LiveOutput) {
        # Read both pipes concurrently and flush partial lines (Git uses carriage returns).
        # Metadata/diff callers retain the exact captured-output path below.
        $readers = @($process.StandardOutput, $process.StandardError)
        $buffers = @((New-Object char[] 4096), (New-Object char[] 4096))
        $pending = @($readers[0].ReadAsync($buffers[0], 0, 4096), $readers[1].ReadAsync($buffers[1], 0, 4096))
        $tail = ''
        try {
            while ($pending[0] -or $pending[1]) {
                $received = $false
                for ($pipe = 0; $pipe -lt 2; $pipe++) {
                    if ($pending[$pipe] -and $pending[$pipe].IsCompleted) {
                        $count = $pending[$pipe].GetAwaiter().GetResult()
                        if ($count -eq 0) { $pending[$pipe] = $null; continue }
                        $chunk = New-Object string($buffers[$pipe], 0, $count)
                        [Console]::Out.Write($chunk)
                        [Console]::Out.Flush()
                        $tail += $chunk
                        if ($tail.Length -gt 8192) { $tail = $tail.Substring($tail.Length - 8192) }
                        $pending[$pipe] = $readers[$pipe].ReadAsync($buffers[$pipe], 0, 4096)
                        $received = $true
                    }
                }
                if (-not $received) { Start-Sleep -Milliseconds 30 }
            }
            $process.WaitForExit()
            if ($process.ExitCode -ne 0) { throw "$Exe exited $($process.ExitCode) : $tail" }
        } finally { $process.Dispose() }
        return
    }
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
function Invoke-Git([string]$Root, [string[]]$Arguments, [string]$OutputFile = '', [switch]$LiveOutput) {
    Invoke-Native 'git' (@('-c', "safe.directory=$Root", '-c', 'core.quotepath=false', '-c', 'core.longpaths=true',
        '-c', 'core.autocrlf=false', '--literal-pathspecs', '-C', $Root) + $Arguments) $OutputFile -LiveOutput:$LiveOutput
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
    # These files have a separate complete-tree audit; an engine commit is not a resource receipt.
    if (Test-OfficialCardResourcePath $Path) { return 'resource-audit' }
    return 'skip'
}
function Test-OfficialCardResourcePath([string]$Path) {
    return $Path -cmatch '^forge-gui/res/(cardsfolder/|editions/|tokenscripts/)[^:\\\x00-\x1f]+\.txt$' -and
        $Path -notmatch '(^|/)\.\.(/|$)'
}
function Get-OfficialCardResourceTree([string]$Root, [string]$Commit) {
    $tree = @{}
    $rows = Invoke-Git $Root @('ls-tree', '-r', '-z', $Commit, '--',
        'forge-gui/res/cardsfolder', 'forge-gui/res/editions', 'forge-gui/res/tokenscripts')
    foreach ($row in ($rows -split "`0")) {
        if (-not $row) { continue }
        if ($row -notmatch '^([0-9]{6}) blob ([a-f0-9]{40})\t(.+)$') { throw 'Unexpected official card resource tree entry.' }
        $mode = $Matches[1]; $blob = $Matches[2]; $path = $Matches[3]
        # Exclude all other extensions BEFORE opening any file, including .dck.
        if (-not (Test-OfficialCardResourcePath $path)) { continue }
        if ($mode -ne '100644' -and $mode -ne '100755') { throw "Official resource is not a regular file: $path" }
        $tree[$path] = $blob
    }
    if (-not $tree.Count) { throw "Official card resource tree is empty: $Commit" }
    return $tree
}
function Get-CardResourceBases($Release, $PreviousState, [string]$EngineBase) {
    $recorded = if ($PreviousState -and $PreviousState.PSObject.Properties['cardResourceCommit']) {
        $PreviousState.cardResourceCommit
    } elseif ($Release.PSObject.Properties['cardResourceCommit']) { $Release.cardResourceCommit } else { '' }
    if ($recorded) {
        if ($recorded -notmatch '^[a-f0-9]{40}$') { throw 'Invalid independent cardResourceCommit.' }
        return @($recorded)
    }
    # The pre-receipt DIY releases carried this official resource snapshot even after
    # advancing upstreamCommit for the engine. It is a recognition baseline, never proof
    # that installed files match it: every source AND runtime file is still inspected.
    return @($EngineBase, 'ebf900109c882d7027b0651ddcff65a57519237a') | Select-Object -Unique
}
function Initialize-CardResourceHasher {
    if ('ForgeDiyResourceReader' -as [type]) { return }
    Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Text;
using System.Security.Cryptography;
using System.Collections.Generic;
public sealed class ForgeDiyResourceReader {
    readonly string root;
    readonly HashSet<string> directories = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
    public ForgeDiyResourceReader(string root) { this.root = Path.GetFullPath(root).TrimEnd('\\', '/') + Path.DirectorySeparatorChar; }
    public string Hash(string relative) {
        if (!relative.EndsWith(".txt", StringComparison.OrdinalIgnoreCase)) throw new IOException("Only card resource TXT files may be read.");
        string path = Path.GetFullPath(Path.Combine(root, relative));
        if (!path.StartsWith(root, StringComparison.OrdinalIgnoreCase)) throw new IOException("Card resource path escapes root.");
        string directory = Path.GetDirectoryName(path);
        while (directory.Length >= root.Length && directories.Add(directory)) {
            if (Directory.Exists(directory) && (File.GetAttributes(directory) & FileAttributes.ReparsePoint) != 0)
                throw new IOException("Card resource directory is a link: " + directory);
            directory = Path.GetDirectoryName(directory);
        }
        if (!File.Exists(path)) return "ABSENT";
        if ((File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0) throw new IOException("Card resource is a link: " + path);
        byte[] input = File.ReadAllBytes(path);
        // Git text checkouts may be CRLF. Normalize only CRLF; preserve every other byte.
        int count = 0;
        for (int i = 0; i < input.Length; i++) {
            if (input[i] == 13 && i + 1 < input.Length && input[i + 1] == 10) continue;
            input[count++] = input[i];
        }
        byte[] prefix = Encoding.ASCII.GetBytes("blob " + count + "\0");
        using (SHA1 hash = SHA1.Create()) {
            hash.TransformBlock(prefix, 0, prefix.Length, prefix, 0);
            hash.TransformFinalBlock(input, 0, count);
            return BitConverter.ToString(hash.Hash).Replace("-", "").ToLowerInvariant();
        }
    }
}
'@
}
function Get-CardResourceAudit([string]$Root, [string]$App, [string]$Target, [string[]]$Bases) {
    Initialize-CardResourceHasher
    $targetTree = Get-OfficialCardResourceTree $Root $Target
    $baseTrees = @($Bases | ForEach-Object { Get-OfficialCardResourceTree $Root $_ })
    $sourceReader = New-Object ForgeDiyResourceReader($Root)
    $runtimeReader = New-Object ForgeDiyResourceReader($App)
    $plan = New-Object 'Collections.Generic.List[object]'
    $number = 0
    foreach ($path in @($targetTree.Keys | Sort-Object)) {
        $wanted = $targetTree[$path]
        $sourceHash = $sourceReader.Hash($path)
        $runtimeHash = $runtimeReader.Hash($path.Substring('forge-gui/'.Length))
        $known = @($wanted, 'ABSENT') + @($baseTrees | ForEach-Object { if ($_.ContainsKey($path)) { $_[$path] } })
        $unknown = $sourceHash -notin $known -or $runtimeHash -notin $known
        $decision = 'current'; $reason = ''
        if ($unknown) {
            if ($baseTrees.Count -and $baseTrees[0][$path] -eq $wanted) {
                $decision = 'preserve-diy'; $reason = 'Official content is unchanged; retain distinct DIY/local content.'
            } else {
                $decision = 'block'; $reason = 'Official and DIY/local content both changed; explicit file review is required.'
            }
        } elseif ($sourceHash -ne $wanted -or $runtimeHash -ne $wanted) { $decision = 'repair' }
        $plan.Add([pscustomobject]@{path=$path; targetBlob=$wanted; sourceBlob=$sourceHash;
            runtimeBlob=$runtimeHash; decision=$decision; reason=$reason})
        $number++
        if ($number % 2000 -eq 0) { Write-Host "已核对官方卡牌资源 $number / $($targetTree.Count)（源码和运行文件）" }
    }
    # Only proven replacements may retire a previous official file. Edition filenames
    # may change while Code stays identical; retaining both breaks the series selector.
    $removed = @{}
    $replacementEditions = @{}
    $targetBlobPaths = @{}
    foreach ($path in $targetTree.Keys) {
        $targetBlobPaths[$targetTree[$path]] = $path
        if ($path -notmatch '^forge-gui/res/editions/') { continue }
        $newInBaseline = @($baseTrees | Where-Object { -not $_.ContainsKey($path) }).Count -gt 0
        if (-not $newInBaseline) { continue }
        $editionText = Invoke-Git $Root @('show', "${Target}:$path")
        if ($editionText -match '(?m)^Code=([^\r\n]+)') { $replacementEditions[$Matches[1].Trim()] = $path }
    }
    foreach ($baseTree in $baseTrees) {
        foreach ($path in $baseTree.Keys) {
            if ($targetTree.ContainsKey($path) -or $removed.ContainsKey($path)) { continue }
            $removed[$path] = $true
            $sourceHash = $sourceReader.Hash($path)
            $runtimeHash = $runtimeReader.Hash($path.Substring('forge-gui/'.Length))
            if ($sourceHash -eq 'ABSENT' -and $runtimeHash -eq 'ABSENT') { continue }
            $knownOld = @('ABSENT') + @($baseTrees | ForEach-Object { if ($_.ContainsKey($path)) { $_[$path] } })
            $replacement = ''
            if ($sourceHash -in $knownOld -and $runtimeHash -in $knownOld) {
                if ($targetBlobPaths.ContainsKey($baseTree[$path])) { $replacement = $targetBlobPaths[$baseTree[$path]] }
                elseif ($path -match '^forge-gui/res/editions/') {
                    $oldText = Invoke-Git $Root @('show', $baseTree[$path])
                    if ($oldText -match '(?m)^Code=([^\r\n]+)' -and $replacementEditions.ContainsKey($Matches[1].Trim())) {
                        $replacement = $replacementEditions[$Matches[1].Trim()]
                    }
                }
            }
            $decision = if ($replacement) { 'retire' } else { 'block' }
            $reason = if ($replacement) { "Verified official replacement: $replacement" } else { 'Official resource was removed; existing content requires review.' }
            $plan.Add([pscustomobject]@{path=$path; targetBlob='ABSENT'; sourceBlob=$sourceHash;
                runtimeBlob=$runtimeHash; decision=$decision; reason=$reason; replacement=$replacement})
        }
    }
    return [pscustomobject]@{schema=1; targetCommit=$Target; recognitionBases=@($Bases); officialFileCount=$targetTree.Count;
        complete=(@($plan | Where-Object { $_.decision -eq 'block' }).Count -eq 0); files=$plan.ToArray()}
}
function Repair-CardResourceSource([string]$Root, [string]$Target, $Audit) {
    if (-not $Audit.complete -or $Audit.targetCommit -ne $Target) { throw 'Cannot repair resources from an incomplete or mismatched audit.' }
    $reader = New-Object ForgeDiyResourceReader($Root)
    $paths = @($Audit.files | Where-Object { $_.decision -eq 'repair' -and $_.sourceBlob -ne $_.targetBlob })
    foreach ($row in $paths) {
        if ($reader.Hash($row.path) -ne $row.sourceBlob) { throw "Source card resource changed after audit: $($row.path)" }
    }
    for ($offset = 0; $offset -lt $paths.Count; $offset += 100) {
        $batch = @($paths | Select-Object -Skip $offset -First 100 | Select-Object -ExpandProperty path)
        Write-Host "补齐官方卡牌资源：$([Math]::Min($offset + 100, $paths.Count)) / $($paths.Count)"
        Invoke-Git $Root (@('restore', '--source', $Target, '--staged', '--worktree', '--') + $batch) -LiveOutput | Out-Null
    }
    foreach ($row in @($Audit.files | Where-Object { $_.decision -eq 'retire' -and $_.sourceBlob -ne 'ABSENT' })) {
        Assert-ChildPath $Root (Join-Path $Root $row.path)
        if ($reader.Hash($row.path) -ne $row.sourceBlob) { throw "Source resource changed before retiring official replacement: $($row.path)" }
        Invoke-Git $Root @('rm', '--', $row.path) | Out-Null
    }
}
function Copy-AuditedCardResources([string]$Root, [string]$ActiveApp, [string]$App, $Audit) {
    if (-not $Audit.complete) { throw 'Cannot stage resources with unresolved audit conflicts.' }
    $sourceReader = New-Object ForgeDiyResourceReader($Root)
    $activeReader = New-Object ForgeDiyResourceReader($ActiveApp)
    foreach ($row in $Audit.files) {
        $runtimePath = $row.path.Substring('forge-gui/'.Length)
        if ($activeReader.Hash($runtimePath) -ne $row.runtimeBlob) { throw "Active card resource changed after audit: $($row.path)" }
        if ($row.decision -eq 'retire') {
            $dest = Join-Path $App $runtimePath
            Assert-ChildPath $App $dest
            if (Test-Path -LiteralPath $dest -PathType Leaf) { Remove-Item -LiteralPath $dest -Force }
            continue
        }
        if ($row.decision -ne 'repair') { continue }
        if ($sourceReader.Hash($row.path) -ne $row.targetBlob) { throw "Candidate card resource differs from audited official target: $($row.path)" }
        $dest = Join-Path $App $runtimePath
        Assert-ChildPath $App $dest
        New-Item -ItemType Directory -Path (Split-Path $dest) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $Root $row.path) -Destination $dest -Force
    }
}
function Assert-CardResourceCandidate([string]$Root, [string]$App, $Audit) {
    if (-not $Audit.complete) { throw 'Cannot accept resources with unresolved audit conflicts.' }
    $sourceReader = New-Object ForgeDiyResourceReader($Root)
    $runtimeReader = New-Object ForgeDiyResourceReader($App)
    foreach ($row in $Audit.files) {
        $sourceExpected = if ($row.decision -eq 'preserve-diy') { $row.sourceBlob } else { $row.targetBlob }
        $runtimeExpected = if ($row.decision -eq 'preserve-diy') { $row.runtimeBlob } else { $row.targetBlob }
        if ($sourceReader.Hash($row.path) -ne $sourceExpected -or
            $runtimeReader.Hash($row.path.Substring('forge-gui/'.Length)) -ne $runtimeExpected) {
            throw "Final source/runtime card resource verification failed: $($row.path)"
        }
    }
    Assert-UniqueEditionCodes $Root 'forge-gui/res/editions'
    Assert-UniqueEditionCodes $App 'res/editions'
}
function Assert-UniqueEditionCodes([string]$Root, [string]$RelativeDirectory) {
    $directory = Join-Path $Root $RelativeDirectory
    Assert-ChildPath $Root $directory
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) { return }
    $codes = @{}
    foreach ($file in @(Get-ChildItem -LiteralPath $directory -File -Filter '*.txt')) {
        Assert-ChildPath $Root $file.FullName
        $text = [IO.File]::ReadAllText($file.FullName)
        if ($text -notmatch '(?m)^Code=([^\r\n]+)') { continue }
        $code = $Matches[1].Trim()
        if ($codes.ContainsKey($code)) {
            throw "系列代码重复，会使游戏系列筛选失败：$code；$($codes[$code])；$($file.Name)。需审核旧官方改名或 DIY 系列代码，未激活候选。"
        }
        $codes[$code] = $file.Name
    }
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
        Write-Host "合并引擎与卡牌：$([Math]::Min($offset + 30, $Paths.Count)) / $($Paths.Count) 个文件"
        Invoke-Git $Root @('apply', '--3way', '--index', '--whitespace=nowarn', $patch) -LiveOutput | Out-Null
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
            'forge/download/DiyUpdateBridge.class', 'forge/download/DiyUpdateLog.class',
            'forge/download/DiyUpdateProgress.class', 'forge/download/DiyUpdateDecision.class',
            'forge/download/diy-updater.ps1', 'forge/download/DiyProtection.java',
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
function Get-ProtectedFileManifest([string]$Root, [string]$Base, $CardResourceAudit = $null) {
    $rules = @{}
    $officialResources = @{}
    if ($CardResourceAudit) {
        foreach ($row in $CardResourceAudit.files) {
            if ($row.decision -in @('current', 'repair', 'retire')) { $officialResources[$row.path] = $true }
        }
    }
    # All tracked non-Java DIY files are immutable during an official update. This also
    # protects registrations/resources/tests, in addition to the Java member catalog.
    $changed = Invoke-Git $Root @('diff', '--name-only', '--no-renames', $Base, 'HEAD', '--',
        'custom', 'forge-core', 'forge-game', 'forge-ai', 'forge-gui', 'forge-gui-desktop')
    $owned = Invoke-Git $Root @('ls-files', '--', 'custom', 'forge-gui/res/skins/warmwood', 'forge-gui/src/main/resources/forge/download')
    foreach ($path in @((($changed + "`n" + $owned) -split "`n") | Sort-Object -Unique)) {
        $path = $path.TrimEnd("`r")
        if (-not $path -or $path -match '(?i)\.dck$') { continue }
        # A resource can predate the ENGINE baseline without being a DIY edit. Only
        # complete-tree recognition in BOTH locations permits this classification.
        if ($officialResources.ContainsKey($path)) { continue }
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

function Get-FailedTests([string]$Source, [string]$BuildLog, [switch]$ReportsOnly) {
    $log = [IO.File]::ReadAllText($BuildLog)
    # Offer a choice only for a completed Surefire test run, never compile/VM/plugin failures.
    if (-not $ReportsOnly -and $log -notmatch '(?m)^\[ERROR\] Failed to execute goal [^\r\n]*:maven-surefire-plugin:[^\r\n]*:test[^\r\n]*There are test failures') { return @() }
    $failures = @()
    foreach ($module in @('forge-core','forge-game','forge-ai','forge-gui','forge-gui-desktop')) {
        $reports = Join-Path $Source "$module/target/surefire-reports"
        if (-not (Test-Path -LiteralPath $reports)) { continue }
        foreach ($file in Get-ChildItem -LiteralPath $reports -Filter 'TEST-*.xml' -File) {
            $settings = New-Object Xml.XmlReaderSettings
            $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
            $settings.XmlResolver = $null
            $reader = [Xml.XmlReader]::Create($file.FullName, $settings)
            $doc = New-Object Xml.XmlDocument
            $doc.XmlResolver = $null
            try { $doc.Load($reader) } finally { $reader.Dispose() }
            foreach ($case in $doc.SelectNodes('//testcase[failure or error]')) {
                $detail = $case.SelectSingleNode('failure|error')
                $failures += [pscustomobject]@{module=$module; name=($case.GetAttribute('classname') + '.' + $case.GetAttribute('name'));
                    message=$detail.GetAttribute('message'); report=$file.Name}
            }
        }
    }
    return $failures
}
function Request-TestFailureDecision([string]$Job, [object[]]$Failures, [long]$ControllerPid = 0) {
    if (-not $Failures.Count) { throw 'No test failures to acknowledge.' }
    $id = [guid]::NewGuid().ToString('N')
    Write-Utf8 (Join-Path $Job 'test-failures.json') (ConvertTo-Json -InputObject @($Failures) -Depth 6)
    $lines = @("有 $($Failures.Count) 项测试未通过：")
    foreach ($failure in @($Failures | Select-Object -First 50)) {
        $message = $failure.message -replace '[\r\n]+', ' '
        if ($message.Length -gt 500) { $message = $message.Substring(0,500) + '…' }
        $lines += "$($failure.name)`n  $message"
    }
    if ($Failures.Count -gt 50) { $lines += '其余失败项见 test-failures.json。' }
    $lines += '继续后仍会运行测试并完成打包；这些已确认的失败允许构建继续，新出现的失败会再次询问。'
    $lines += '启用新版本前仍会检查 DIY 代码、资源和依赖保护，失败记录会随版本保存。'
    $lines += '选择“保留当前版本”则结束本次更新。'
    $summary = $lines -join "`n"
    Write-Host $summary
    $temporary = Join-Path $Job "test-decision-$id.tmp"
    Write-Utf8 $temporary ($id + "`n" + $summary)
    $requestFile = Join-Path $Job 'test-decision.request'
    if (Test-Path -LiteralPath $requestFile) { [IO.File]::Replace($temporary, $requestFile, (Join-Path $Job "test-decision-previous-$id.request")) }
    else { [IO.File]::Move($temporary, $requestFile) }
    Write-Host '等待你选择：已知晓失败，继续更新 / 保留当前版本。'
    $response = Join-Path $Job "test-decision-$id.response"
    while (-not (Test-Path -LiteralPath $response)) {
        if ($ControllerPid -gt 0) {
            try { $viewer = [Diagnostics.Process]::GetProcessById([int]$ControllerPid); $viewer.Dispose() }
            catch { throw '游戏已退出，未收到继续确认；本次更新保留当前版本。' }
        }
        Start-Sleep -Milliseconds 200
    }
    $choice = [IO.File]::ReadAllText($response).Trim()
    if ($choice -notin @('continue','stop')) { throw 'Invalid test-failure decision; current version retained.' }
    $decision = [pscustomobject]@{id=$id; choice=$choice; atUtc=[DateTime]::UtcNow.ToString('o'); failures=@($Failures)}
    Write-Utf8 (Join-Path $Job "test-acknowledgement-$id.json") ($decision | ConvertTo-Json -Depth 6)
    return $decision
}
function Invoke-MavenPass([string]$Maven, [string[]]$Arguments, [string]$Log) {
    $savedPreference = $ErrorActionPreference
    Write-Utf8 $Log ''
    try {
        $ErrorActionPreference = 'Continue'
        $global:LASTEXITCODE = -1
        & $Maven @Arguments 2>&1 | ForEach-Object { $_.ToString() } | Tee-Object -FilePath $Log | Out-Host
        return $global:LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
}
function Invoke-CheckedPackage([string]$Maven, [string]$Source, [string]$Cache, [string]$Job, [long]$ControllerPid = 0) {
    $arguments = @('-B','-ntp',"-Dmaven.repo.local=$Cache",'-pl','forge-gui-desktop','-am')
    $log = Join-Path $Job 'build-with-tests.log'
    Push-Location $Source
    try {
        $code = Invoke-MavenPass $Maven ($arguments + @('clean','package')) $log
        if ($code -eq 0) { return [pscustomobject]@{status='passed'; acknowledgement=@(); failures=@()} }
        $failures = @(Get-FailedTests $Source $log)
        if (-not $failures.Count) { throw "Compilation or build infrastructure failed ($code). See build-with-tests.log." }
        $known = @{}
        $acknowledgements = @()
        while ($failures.Count) {
            $decision = Request-TestFailureDecision $Job $failures $ControllerPid
            if ($decision.choice -ne 'continue') { throw '你已选择保留当前版本，本次更新已停止。' }
            $acknowledgements += $decision
            foreach ($failure in $failures) { $known[($failure | ConvertTo-Json -Compress)] = $true }
            Write-Host '已记录你的确认，继续构建并运行测试；新的失败会再次询问。'
            $retryLog = Join-Path $Job "build-after-acknowledgement-$($acknowledgements.Count).log"
            $code = Invoke-MavenPass $Maven ($arguments + @('-Dmaven.test.failure.ignore=true','package')) $retryLog
            if ($code -ne 0 -or [IO.File]::ReadAllText($retryLog) -match 'The forked VM terminated|There was an error in the forked process|Error occurred in starting fork') {
                throw "用户确认后继续构建仍发生编译或测试进程错误 ($code)，当前版本保持不变。"
            }
            $remaining = @(Get-FailedTests $Source $retryLog -ReportsOnly)
            $failures = @($remaining | Where-Object { -not $known.ContainsKey(($_ | ConvertTo-Json -Compress)) })
            if (-not $failures.Count) {
                $status = if ($remaining.Count) { 'failures-acknowledged' } else { 'passed' }
                return [pscustomobject]@{status=$status; acknowledgement=$acknowledgements; failures=$remaining}
            }
        }
    } finally { Pop-Location }
}

if ($LibraryOnly) { return }
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$env:GIT_TERMINAL_PROMPT = '0'
Write-Host '[1/7] 正在检查更新环境…'
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
        Write-Host '[2/7] 正在准备已有 DIY 桌面源码…'
        Invoke-Native git @('-c', 'core.autocrlf=false', '-c', 'core.longpaths=true', 'clone', '--progress', '--no-checkout', '--no-hardlinks', $previousSource, $source) -LiveOutput | Out-Null
        Set-DesktopSparseCheckout $source
        Invoke-Git $source @('checkout', '--progress') -LiveOutput | Out-Null
    } elseif ($SourceRoot) {
        # Developer/test input is cloned, never modified in place.
        Write-Host '[2/7] 正在准备指定 DIY 桌面源码…'
        Invoke-Native git @('-c', 'core.autocrlf=false', '-c', 'core.longpaths=true', 'clone', '--progress', '--no-checkout', '--no-hardlinks', $SourceRoot, $source) -LiveOutput | Out-Null
        Set-DesktopSparseCheckout $source
        Invoke-Git $source @('checkout', '--progress') -LiveOutput | Out-Null
    } else {
        Write-Host '[2/7] 正在下载当前版本对应的 DIY 源码…'
        Invoke-Native git @('init', $source) | Out-Null
        Invoke-Git $source @('remote', 'add', 'origin', 'https://github.com/GradibelPitt/forge.git') | Out-Null
        Invoke-Git $source @('config', 'remote.origin.promisor', 'true') | Out-Null
        Invoke-Git $source @('config', 'remote.origin.partialclonefilter', 'blob:none') | Out-Null
        Set-DesktopSparseCheckout $source
        Invoke-Git $source @('fetch', '--progress', '--filter=blob:none', '--depth=1', '--no-tags', 'origin', $release.sourceCommit) -LiveOutput | Out-Null
        Invoke-Git $source @('checkout', '--progress', '-b', 'local-diy', 'FETCH_HEAD') -LiveOutput | Out-Null
    }
    if (-not $previousState -and (Invoke-Git $source @('rev-parse', 'HEAD')).Trim() -ne $release.sourceCommit) {
        throw 'Source checkout does not match the released DIY baseline.'
    }
    $UpstreamBase = Resolve-UpstreamBase $release $previousState $UpstreamBase
    Write-Host '[3/7] 正在下载官方源码改动…'
    Invoke-Git $source @('remote', 'add', 'reviewed-upstream', 'https://github.com/Card-Forge/forge.git') | Out-Null
    Invoke-Git $source @('config', 'remote.reviewed-upstream.promisor', 'true') | Out-Null
    Invoke-Git $source @('config', 'remote.reviewed-upstream.partialclonefilter', 'blob:none') | Out-Null
    Invoke-Git $source @('fetch', '--progress', '--filter=blob:none', '--depth=1', '--no-tags', 'reviewed-upstream', $UpstreamBase) -LiveOutput | Out-Null
    Invoke-Git $source @('fetch', '--progress', '--filter=blob:none', '--depth=1', '--no-tags', 'reviewed-upstream', $UpstreamTarget) -LiveOutput | Out-Null
    $target = (Invoke-Git $source @('rev-parse', 'FETCH_HEAD')).Trim()
    Write-Host '[4/7] 正在检查改动范围与 DIY 保留规则…'
    $resourceBases = @(Get-CardResourceBases $release $previousState $UpstreamBase)
    foreach ($resourceBase in $resourceBases) {
        if ($resourceBase -eq $UpstreamBase -or $resourceBase -eq $target) { continue }
        Invoke-Git $source @('fetch', '--progress', '--filter=blob:none', '--depth=1', '--no-tags', 'reviewed-upstream', $resourceBase) -LiveOutput | Out-Null
    }
    Write-Host '正在独立核对完整官方卡牌、系列和衍生物资源；不会只根据引擎版本判断…'
    $resourceAudit = Get-CardResourceAudit $source $activeApp $target $resourceBases
    Write-Utf8 (Join-Path $job 'card-resource-audit.json') ($resourceAudit | ConvertTo-Json -Depth 8)
    $resourceRepairs = @($resourceAudit.files | Where-Object { $_.decision -in @('repair', 'retire') })
    $resourceConflicts = @($resourceAudit.files | Where-Object { $_.decision -eq 'block' })
    $resourceOverrides = @($resourceAudit.files | Where-Object { $_.decision -eq 'preserve-diy' })
    $plan = @(Get-UpdatePlan $source $UpstreamBase $target)
    Write-Utf8 (Join-Path $job 'plan.json') (ConvertTo-Json -InputObject $plan -Depth 6)
    $blocked = @($plan | Where-Object { $_.decision -eq 'block' })
    $paths = @($plan | Where-Object { $_.decision -eq 'merge' } | Select-Object -ExpandProperty path)
    Write-Host "Selected $($paths.Count) paths; blocked $($blocked.Count); official target $target"
    Write-Host "官方卡牌资源共 $($resourceAudit.officialFileCount) 项；需补齐 $($resourceRepairs.Count)；保留 DIY 差异 $($resourceOverrides.Count)；冲突 $($resourceConflicts.Count)"
    if ($PlanOnly) { Write-Utf8 (Join-Path $job 'result.txt') 'Plan completed; no runtime changes.'; exit 0 }
    if ($resourceConflicts.Count) {
        $examples = @($resourceConflicts | Select-Object -First 12 | ForEach-Object { "$($_.path): $($_.reason)" }) -join "`n"
        throw "卡牌资源与 DIY/本地内容存在 $($resourceConflicts.Count) 项冲突，当前文件未覆盖。完整清单：$(Join-Path $job 'card-resource-audit.json')`n$examples"
    }
    if ($blocked.Count) { throw "Review required for deleted/renamed engine files or build dependencies: $($blocked.path -join ', '). See plan.json." }
    if (-not $paths.Count -and -not $resourceRepairs.Count) {
        Assert-UniqueEditionCodes $source 'forge-gui/res/editions'
        Assert-UniqueEditionCodes $activeApp 'res/editions'
        Write-Utf8 (Join-Path $job 'result.txt') "已核对官方完整卡牌资源 $($resourceAudit.officialFileCount) 项及引擎改动，无需更新；保留 $($resourceOverrides.Count) 项 DIY/本地资源差异，详见 card-resource-audit.json。"
        exit 0
    }
    Write-Host '[5/7] 正在准备工具并核对 DIY 保护规则…'
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
    Write-Host '正在准备 DIY 符号保护目录…'
    if ($previousState -and $previousState.policyVersion -eq 2) {
        $previousCatalog = Join-Path (Split-Path $activeApp) 'protection.tsv'
        if ((Get-FileHash -LiteralPath $previousCatalog -Algorithm SHA256).Hash -ne $previousState.protectionCatalogHash) { throw 'Previous protection catalog is missing or changed.' }
        Copy-Item -LiteralPath $previousCatalog -Destination $catalog
    } else { New-ProtectionCatalog $source $UpstreamBase $jdk $job $catalog }
    Invoke-Protection $jdk $job @('verify', $source, $catalog)
    $bindings = Join-Path $job 'baseline-bindings.tsv'
    Write-Host '正在核对 DIY 调用关系与依赖…'
    Invoke-Protection $jdk $job @('bindings', $source, $bindings, $baselineClasspath)
    $protectedFiles = Get-ProtectedFileManifest $source $UpstreamBase $resourceAudit
    if ($previousState -and $previousState.policyVersion -eq 2) {
        $previousFiles = Join-Path (Split-Path $activeApp) 'protected-files.json'
        if ((Get-FileHash -LiteralPath $previousFiles -Algorithm SHA256).Hash -ne $previousState.protectedFilesHash) { throw 'Previous protected file manifest changed.' }
        $savedFiles = Get-Content -LiteralPath $previousFiles -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($property in $savedFiles.PSObject.Properties) { $protectedFiles[$property.Name] = $property.Value }
        Assert-ProtectedFiles $source $protectedFiles
    }
    Write-Utf8 (Join-Path $versionRoot 'protected-files.json') ($protectedFiles | ConvertTo-Json -Depth 4)
    foreach ($row in $resourceRepairs) {
        if ($protectedFiles.ContainsKey($row.path) -and $row.sourceBlob -ne $row.targetBlob) {
            throw "历史保护清单固定了待更新资源，需明确审核该条误归属或 DIY 改动；不会缩减旧保护规则：$($row.path)"
        }
    }
    Write-Host '正在合并允许的改动，并复核 DIY 保护规则…'
    Merge-UpdatePaths $source $UpstreamBase $target $paths $job
    Repair-CardResourceSource $source $target $resourceAudit
    Assert-UniqueEditionCodes $source 'forge-gui/res/editions'
    Assert-ProtectedFiles $source $protectedFiles
    Invoke-Protection $jdk $job @('verify', $source, $catalog)
    $deleted = Invoke-Git $source @('diff', '--cached', '--name-only', '--diff-filter=D')
    $allowedRetired = @($resourceAudit.files | Where-Object decision -eq 'retire' | Select-Object -ExpandProperty path)
    foreach ($deletedPath in ($deleted -split "`n")) {
        if ($deletedPath.Trim() -and $deletedPath.TrimEnd("`r") -notin $allowedRetired) { throw "Update would delete an unreviewed existing source file: $deletedPath" }
    }
    Invoke-Git $source @('diff', '--cached', '--check') | Out-Null
    Initialize-DesktopReactor $source
    $maven = Get-Maven $tools
    $env:JAVA_HOME = $jdk
    $env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH
    $mavenCache = Join-Path $env:USERPROFILE '.m2/repository'
    if (-not (Test-Path -LiteralPath $mavenCache)) { $mavenCache = Join-Path $tools 'm2' }
    Write-Host '[6/7] 正在编译并测试 DIY 引擎与界面…'
    $testResult = Invoke-CheckedPackage $maven $source $mavenCache $job $config.controllerPid
    $jar = @(Get-ChildItem -LiteralPath (Join-Path $source 'forge-gui-desktop/target') -File -Filter '*-jar-with-dependencies.jar')
    if ($jar.Count -ne 1) { throw 'Expected exactly one compiled desktop JAR.' }
    Write-Host '[7/7] 正在验证并准备新版本…'
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
    Copy-AuditedCardResources $source $activeApp $app $resourceAudit
    Assert-CardResourceCandidate $source $app $resourceAudit
    $resourceReceipt = [ordered]@{schema=1; cardResourceCommit=$target; officialFileCount=$resourceAudit.officialFileCount;
        verifiedSourceAndRuntime=$true; preservedDiyOverrides=$resourceOverrides.Count;
        files=@($resourceAudit.files | ForEach-Object {
            [ordered]@{path=$_.path; officialBlob=$_.targetBlob;
                sourceBlob=$(if ($_.decision -eq 'preserve-diy') { $_.sourceBlob } else { $_.targetBlob });
                runtimeBlob=$(if ($_.decision -eq 'preserve-diy') { $_.runtimeBlob } else { $_.targetBlob });
                preservedDiyOverride=($_.decision -eq 'preserve-diy')}
        })}
    $resourceReceiptFile = Join-Path $versionRoot 'card-resource-receipt.json'
    Write-Utf8 $resourceReceiptFile ($resourceReceipt | ConvertTo-Json -Depth 8)
    Copy-Item -LiteralPath $jar[0].FullName -Destination $app
    $jarHash = (Get-FileHash -LiteralPath (Join-Path $app $jar[0].Name) -Algorithm SHA256).Hash
    Write-Utf8 (Join-Path $app 'BUILD-ID.txt') ("DIY-upstream-" + $target.Substring(0, 12))
    $state = [ordered]@{schema=1; generation=$generation; upstreamCommit=$target; cardResourceCommit=$target; baseReleaseHash=$releaseHash;
        cardResourceReceiptHash=(Get-FileHash -LiteralPath $resourceReceiptFile -Algorithm SHA256).Hash;
        cardResourceCount=$resourceAudit.officialFileCount; cardResourceDiyOverrides=$resourceOverrides.Count;
        sourceCommit=(Invoke-Git $source @('rev-parse', 'HEAD')).Trim(); jar=$jar[0].Name; jarHash=$jarHash;
        policyVersion=2; protectionCatalogHash=(Get-FileHash -LiteralPath $catalog -Algorithm SHA256).Hash;
        protectedFilesHash=(Get-FileHash -LiteralPath (Join-Path $versionRoot 'protected-files.json') -Algorithm SHA256).Hash;
        protectionPolicyHash=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'DiyProtection.java') -Algorithm SHA256).Hash;
        historyCatalogHash=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'diy-protection-history.tsv') -Algorithm SHA256).Hash;
        javaVersion=(Invoke-Native (Join-Path $jdk 'bin/javac.exe') @('-version')).Trim();
        testResult=$testResult;
        verification=@('desktop-package', 'protected-files', 'java-members', 'resolved-bindings-and-dependencies', 'bundled-policy', 'complete-official-card-resources')}
    if ($testResult.status -eq 'passed') { $state.verification += 'tests-passed' }
    else { $state.verification += 'test-failures-acknowledged-by-user' }
    $hashes = @(Get-ChildItem -LiteralPath $app -Recurse -File | ForEach-Object {
        if ($_.Extension -ieq '.dck') { return }
        $relative = $_.FullName.Substring($app.Length + 1).Replace('\', '/')
        (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash + ' *' + $relative
    })
    Write-Utf8 (Join-Path $app 'manifest-critical.sha256') (($hashes | Sort-Object) -join "`n")
    $state['manifestHash'] = (Get-FileHash -LiteralPath (Join-Path $app 'manifest-critical.sha256') -Algorithm SHA256).Hash
    Write-Utf8 (Join-Path $versionRoot 'update-state.json') ($state | ConvertTo-Json -Depth 10)
    if ((Get-FileHash -LiteralPath $releaseFile -Algorithm SHA256).Hash -ne $releaseHash) { throw 'DIY release changed during compilation; staged update was not activated.' }
    if (-not $NoActivate) { Write-ActivePointer $updates $state }
    $resultMessage = 'DIY 更新已完成构建与保护检查。保存后关闭游戏，再通过 ForgeDIY 启动器打开即可加载；旧版本已保留。'
    $resultMessage += "`n完整核对官方卡牌资源 $($resourceAudit.officialFileCount) 项，补齐 $($resourceRepairs.Count) 项，保留 $($resourceOverrides.Count) 项 DIY/本地差异。"
    if ($testResult.status -ne 'passed') { $resultMessage += "`n本次按你的确认继续更新，仍有 $($testResult.failures.Count) 项测试未通过，记录已随版本保存。" }
    Write-Utf8 (Join-Path $job 'result.txt') $resultMessage
    Write-Host 'DIY_UPDATE_READY'
} catch {
    $detail = $_.Exception.Message
    Write-Host $detail
    Write-Utf8 (Join-Path $job 'result.txt') ("DIY update stopped; current game files are unchanged.`n" + $detail)
    exit 1
} finally {
    if ($lock) { $lock.Dispose() }
}
