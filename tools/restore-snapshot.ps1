# Shared verified snapshot restore engine; stage-specific wrappers select the backup.
param([string]$Backup, [string]$Destination, [switch]$InPlace)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if (-not $Backup) {
    $Backup = (Get-ChildItem -LiteralPath (Join-Path $root 'backups') -Directory -Filter 'llvm-stage-*' |
        Sort-Object Name -Descending | Select-Object -First 1).FullName
}
if (-not $Backup) { throw 'No LLVM snapshot found' }
$Backup = [IO.Path]::GetFullPath($Backup)
$archivePath = Join-Path $Backup 'source.zip'
$manifest = Get-Content -LiteralPath (Join-Path $Backup 'manifest.json') -Raw | ConvertFrom-Json
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($archivePath)
try {
    # Verify every member before changing any source file.
    foreach ($entry in $archive.Entries) {
        $expected = $manifest.files.PSObject.Properties[$entry.FullName].Value
        if (-not $expected) { throw "Missing checksum: $($entry.FullName)" }
        $stream = $entry.Open()
        $sha = [Security.Cryptography.SHA256]::Create()
        try { $actual = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
        finally { $stream.Dispose(); $sha.Dispose() }
        if ($actual -ne $expected) { throw "Checksum mismatch: $($entry.FullName)" }
    }
    if ($InPlace) {
        if ($Destination) { throw 'Use either -InPlace or -Destination' }
        $Destination = $root
        # Preserve every overwritten file and every added source file for undo.
        [IO.Directory]::CreateDirectory((Join-Path $root 'backups')) | Out-Null
        $kind = if ((Split-Path -Leaf $Backup).StartsWith('mips-stage-')) { 'mips' } else { 'llvm' }
        $saved = Join-Path $root ('backups/pre-' + $kind + '-rollback-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.zip')
        $added = @()
        foreach ($codeDir in @('src', 'ui')) {
            $codeRoot = [IO.Path]::GetFullPath((Join-Path $root $codeDir))
            if (-not $codeRoot.StartsWith($root + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe source directory' }
            if (Test-Path -LiteralPath $codeRoot) {
                $added += Get-ChildItem -LiteralPath $codeRoot -Recurse -File | Where-Object {
                    $_.Extension -in '.java', '.json' -and -not $manifest.files.PSObject.Properties[
                        $_.FullName.Substring($root.Length + 1).Replace('\', '/')]
                }
            }
        }
        $current = [IO.Compression.ZipFile]::Open($saved, 'Create')
        try {
            $paths = @($archive.Entries | ForEach-Object { $_.FullName })
            $paths += $added | ForEach-Object { $_.FullName.Substring($root.Length + 1).Replace('\', '/') }
            foreach ($relative in ($paths | Select-Object -Unique)) {
                $file = [IO.Path]::GetFullPath((Join-Path $root $relative))
                if (-not $file.StartsWith($root + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe snapshot member' }
                if (Test-Path -LiteralPath $file -PathType Leaf) {
                    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($current, $file, $relative) | Out-Null
                }
            }
        } finally { $current.Dispose() }
        foreach ($file in $added) {
            if (-not $file.FullName.StartsWith($root + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe source file' }
            Remove-Item -LiteralPath $file.FullName -Force
        }
        Write-Output "Saved current version to $saved"
    } else {
        if (-not $Destination) { $Destination = Join-Path $root 'build/llvm-rollback' }
        $Destination = [IO.Path]::GetFullPath($Destination)
        if ((Test-Path -LiteralPath $Destination) -and (Get-ChildItem -LiteralPath $Destination -Force)) {
            throw 'Destination must be empty; choose a different -Destination'
        }
    }
    [IO.Directory]::CreateDirectory($Destination) | Out-Null
    foreach ($entry in $archive.Entries) {
        $target = [IO.Path]::GetFullPath((Join-Path $Destination $entry.FullName))
        if (-not $target.StartsWith($Destination.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar)) {
            throw 'Unsafe archive member'
        }
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, [bool]$InPlace)
    }
    Write-Output "Restored verified compiler snapshot to $Destination"
} finally { $archive.Dispose() }
