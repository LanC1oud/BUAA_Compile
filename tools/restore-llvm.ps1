# Restore the latest LLVM snapshot, preserving the current version first.
param([string]$Backup, [string]$Destination, [switch]$InPlace)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if (-not $Backup) {
    $Backup = (Get-ChildItem -LiteralPath (Join-Path $root 'backups') -Directory -Filter 'llvm-stage-*' |
        Sort-Object Name -Descending | Select-Object -First 1).FullName
}
if (-not $Backup) { throw 'No LLVM snapshot found' }
if (-not $Destination -and -not $InPlace) { $Destination = Join-Path $root 'build/llvm-rollback' }
$options = @{ Backup = $Backup; InPlace = [bool]$InPlace }
if ($Destination) { $options.Destination = $Destination }
& (Join-Path $PSScriptRoot 'restore-snapshot.ps1') @options
