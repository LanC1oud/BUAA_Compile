# Restore the latest pre-optimization MIPS snapshot, preserving the current version first.
param([string]$Backup, [string]$Destination, [switch]$InPlace)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if (-not $Backup) {
    $Backup = (Get-ChildItem -LiteralPath (Join-Path $root 'backups') -Directory -Filter 'mips-stage-*' |
        Sort-Object Name -Descending | Select-Object -First 1).FullName
}
if (-not $Backup) { throw 'No MIPS snapshot found' }
if (-not $Destination -and -not $InPlace) { $Destination = Join-Path $root 'build/mips-rollback' }
$options = @{ Backup = $Backup; InPlace = [bool]$InPlace }
if ($Destination) { $options.Destination = $Destination }
& (Join-Path $PSScriptRoot 'restore-snapshot.ps1') @options
