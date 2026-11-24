# 打包提交：把 src/ 下的源码（含根目录 config.json）打成 zip，结构与希冀平台要求一致。
# 不会包含 .class / .git / tools / tests / out。
#
#   pwsh -File tools/pack.ps1 [-Release 17]

param([int]$Release = 17)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$root = [System.IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$sourceRootPath = [System.IO.Path]::GetFullPath((Join-Path $root 'src'))
$outDir = Join-Path $root 'out'
$zip = Join-Path $outDir 'Compiler-jdk17.zip'

if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
if (Test-Path $zip) { Remove-Item -Force $zip }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$archive = [System.IO.Compression.ZipFile]::Open($zip, 'Create')
$count = 0
try {
    $files = Get-ChildItem -LiteralPath $sourceRootPath -Recurse -File | Where-Object { $_.Extension -in '.java', '.json' }
    foreach ($f in $files) {
        # 相对 src 的路径，统一用 /，保证 Compiler.java 与 config.json 在包根
        if (-not $f.FullName.StartsWith($sourceRootPath + [IO.Path]::DirectorySeparatorChar)) { throw 'Source outside workspace' }
        $rel = $f.FullName.Substring($sourceRootPath.Length + 1) -replace '\\', '/'
        if ([string]::IsNullOrWhiteSpace($rel)) { throw "Empty archive name: $($f.FullName)" }
        $entry = $archive.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
        $es = $entry.Open()
        $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
        $es.Write($bytes, 0, $bytes.Length)
        $es.Dispose()
        $count++
    }
} finally {
    $archive.Dispose()
}

Write-Host ("打包 $count 个文件 -> $zip")

# ---------------- 验证：解包后用目标 release 编译 ----------------
$tmp = Join-Path $outDir 'pack-verify'
if (-not ([IO.Path]::GetFullPath($tmp).StartsWith($root + [IO.Path]::DirectorySeparatorChar))) { throw 'Unsafe verify path' }
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
[System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $tmp)

$jdk = 'C:\Program Files\Java\jdk-21\bin\javac.exe'
$classes = Join-Path $outDir 'pack-classes'
if (-not ([IO.Path]::GetFullPath($classes).StartsWith($root + [IO.Path]::DirectorySeparatorChar))) { throw 'Unsafe classes path' }
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$srcs = Get-ChildItem -Recurse $tmp -Filter *.java | Select-Object -ExpandProperty FullName
$res = & $jdk "--release" $Release -encoding UTF-8 -d $classes $srcs 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host ("验证失败：--release $Release 编译不通过") -ForegroundColor Red
    $res | Select-Object -First 20 | ForEach-Object { Write-Host $_ }
    exit 1
}
Write-Host ("验证通过：解包后 --release $Release 编译成功，0 错误") -ForegroundColor Green

# 顺带确认入口与配置都在包根
$hasCompiler = Test-Path (Join-Path $tmp 'Compiler.java')
$hasConfig = Test-Path (Join-Path $tmp 'config.json')
Write-Host ("包根 Compiler.java: $hasCompiler ; config.json: $hasConfig")
