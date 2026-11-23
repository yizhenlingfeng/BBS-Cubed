# 对比 1.20.1 与目标版本（默认 1.21.11）产物中的类，输出缺失/多出的类清单。
# 用法：
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-class-diff.ps1
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-class-diff.ps1 -UpdateBaseline
#
# 判定依据是“白名单精确匹配”，不是“缺失数量不超过基线”：
#   - scripts/class-diff-allowlist.txt 逐条列出允许缺失的类，并写明归因分组；
#   - 任一缺失类不在白名单里 => 失败（真回归）；
#   - 白名单里已不再缺失的条目只警告，提示可以裁剪，不阻塞构建。
# 这样既不会因现有 1.20.x 专属代码告警，也不会把新回归掩盖在宽松的计数基线里。

param(
    [string]$Target = "1.21.11",
    [string]$Allowlist = (Join-Path $PSScriptRoot "class-diff-allowlist.txt"),
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

function Read-Allowlist([string]$Path) {
    $set = [System.Collections.Generic.HashSet[string]]::new()
    if (!(Test-Path -LiteralPath $Path)) { return $set }

    foreach ($line in [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)) {
        $t = $line.Trim()
        if ($t.Length -eq 0) { continue }
        if ($t.StartsWith("#")) { continue }
        $null = $set.Add($t)
    }
    return $set
}

function Write-Allowlist([string]$Path, [string[]]$Missing, [string]$TargetVersion) {
    $oneTwenty = New-Object System.Collections.Generic.List[string]
    $shared = New-Object System.Collections.Generic.List[string]

    foreach ($cls in ($Missing | Sort-Object)) {
        $outer = $cls
        $i = $outer.IndexOf([char]36)
        if ($i -gt 0) { $outer = $outer.Substring(0, $i) }
        $rel = ($outer -replace '\.', '\') + ".java"

        if (Test-Path -LiteralPath (Join-Path $repo "src/main/java-1.20.x" $rel)) { $oneTwenty.Add($cls) }
        else { $shared.Add($cls) }
    }

    $out = New-Object System.Collections.Generic.List[string]
    $out.Add("# $TargetVersion 相对 1.20.1 允许缺失的类白名单（精确匹配）。")
    $out.Add("# 任一未列出的缺失类，或白名单外的类缺失，都会让 scripts/check-class-diff.ps1 失败。")
    $out.Add("# 增加条目前必须能说明归因；不要为了通过构建而追加。")
    $out.Add("# 与 build/reports/class-diff-$TargetVersion.csv 对照复核。")
    $out.Add("#")
    $out.Add("# 总计 $($Missing.Count) 条：组 A（1.20.x 专属源码）$($oneTwenty.Count) 条，组 B（共享源码按版本排除）$($shared.Count) 条。")
    $out.Add("")
    $out.Add("# --- 组 A：源码仅存在于 src/main/java-1.20.x ---")
    $out.Add("# 1.21.11 侧没有对应实现。其中 morph 相关（client/morph、client/ui/morph、mixin/morph、")
    $out.Add("# keyframes/IModelMorphKeyframe）属于 Model Morph 尚未移植到 1.21.11；其余为 1.20.x 专属适配层。")
    foreach ($c in $oneTwenty) { $out.Add($c) }
    $out.Add("")
    $out.Add("# --- 组 B：源码在共享 src/main/java，但 1.21.11 构建按 build.gradle 的 sourceSets 排除 ---")
    $out.Add("# 排除项覆盖 outline / texture-grade / particle-plus / irlite / shadercurves / vfx 等上游 API 差异较大的子系统。")
    foreach ($c in $shared) { $out.Add($c) }
    $out.Add("")

    [System.IO.File]::WriteAllText($Path, ($out -join "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "allowlist updated: $Path = $($Missing.Count) entries (groupA $($oneTwenty.Count), groupB $($shared.Count))"
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
    Write-Allowlist $Allowlist $missing $Target
    return
}

if (!(Test-Path -LiteralPath $Allowlist)) {
    Write-Warning "白名单不存在：$Allowlist；先运行 -UpdateBaseline 生成"
    return
}

$allowed = Read-Allowlist $Allowlist
$unexpected = @($missing | Where-Object { !$allowed.Contains($_) })
$missingSet = [System.Collections.Generic.HashSet[string]]::new([string[]]$missing)
$stale = @($allowed | Where-Object { !$missingSet.Contains($_) })

if ($stale.Count -gt 0) {
    Write-Warning "白名单有 $($stale.Count) 条已不再缺失，可以裁剪（不阻塞构建）"
}

if ($unexpected.Count -gt 0) {
    $unexpected | ForEach-Object { Write-Host "  UNEXPECTED MISSING: $_" }
    throw "出现 $($unexpected.Count) 个未列入白名单的缺失类（疑似回归）。查看 $report"
}

Write-Host "allowlist ok: missing $($missing.Count) 条全部已归因"