# 对比 1.20.1 与 1.21.11 产物中的类，输出缺失/多出的类清单。
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-class-diff.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-class-diff.ps1 -UpdateBaseline
# 基线只记录“1.21.11 相对 1.20.1 的缺失类数量”；后续缺失数不得高于基线。

param(
    [string]$Target = "1.21.11",
    [string]$Baseline = (Join-Path $PSScriptRoot "class-diff-baseline.txt"),
    [switch]$UpdateBaseline
)

$ErrorActionPreference = "Stop"
$repo = Split-Path $PSScriptRoot -Parent

function Find-MainJar([string]$TargetVersion) {
    $dir = Join-Path $repo "build/$TargetVersion/libs"
    if (!(Test-Path -LiteralPath $dir)) { throw "找不到产物目录：$dir" }

    $jar = Get-ChildItem -LiteralPath $dir -Filter "BBS-Cubed-*.jar" |
        Where-Object { $_.Name -notmatch '-(dev|sources)\.jar$' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (!$jar) { throw "$dir 下没有 BBS-Cubed-*.jar" }
    return $jar.FullName
}

function Get-JarClasses([string]$JarPath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        return @($zip.Entries |
            Where-Object { $_.FullName -like "*.class" } |
            ForEach-Object { $_.FullName -replace '/', '.' -replace '\.class$', '' } |
            Sort-Object -Unique)
    }
    finally {
        $zip.Dispose()
    }
}

$baseJar = Find-MainJar "1.20.1"
$targetJar = Find-MainJar $Target
$baseClasses = Get-JarClasses $baseJar
$targetClasses = Get-JarClasses $targetJar

$baseSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$baseClasses)
$targetSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$targetClasses)

$missing = @($baseClasses | Where-Object { !$targetSet.Contains($_) })
$extra = @($targetClasses | Where-Object { !$baseSet.Contains($_) })
$reportDir = Join-Path $repo "build/reports"
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$report = Join-Path $reportDir "class-diff-$Target.csv"

$rows = @()
$rows += $missing | ForEach-Object { [pscustomobject]@{ Kind = "missing"; Class = $_ } }
$rows += $extra | ForEach-Object { [pscustomobject]@{ Kind = "extra"; Class = $_ } }
$rows | Export-Csv -LiteralPath $report -NoTypeInformation -Encoding utf8

Write-Host "1.20.1: $($baseClasses.Count) classes"
Write-Host "$Target : $($targetClasses.Count) classes"
Write-Host "missing: $($missing.Count)  extra: $($extra.Count)  report: $report"

if ($UpdateBaseline) {
    Set-Content -LiteralPath $Baseline -Value $missing.Count -Encoding ascii
    Write-Host "baseline updated: $Baseline = $($missing.Count)"
}
elseif (Test-Path -LiteralPath $Baseline) {
    $allowed = [int](Get-Content -LiteralPath $Baseline -Raw).Trim()
    if ($missing.Count -gt $allowed) {
        throw "1.21.11 缺失类数量回退：当前 $($missing.Count)，基线 $allowed。查看 $report"
    }
    Write-Host "baseline ok: missing $($missing.Count) <= $allowed"
}
else {
    Write-Warning "基线文件不存在：$Baseline；先运行 -UpdateBaseline 建立基线"
}
