param([string]$Jdk = 'C:\Program Files\Java\jdk-21')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$outputPath = Join-Path $repoRoot 'build\semantic-tests'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $repoRoot 'src') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName })
& (Join-Path $Jdk 'bin\javac.exe') -encoding UTF-8 --release 17 -d $outputPath @sources (Join-Path $PSScriptRoot 'SemanticRegression.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $Jdk 'bin\java.exe') -cp $outputPath SemanticRegression
exit $LASTEXITCODE
