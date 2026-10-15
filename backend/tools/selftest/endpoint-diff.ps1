# 对比契约里的接口清单 vs 后端已实现的接口（输出到 _endpoints-report.txt）
$contract = "E:\personalProjects\coc-platform\contract\api-mvp.md"
$src = "E:\personalProjects\coc-platform\backend\src\main\java\uk\collectionofcreations\coc\controller"
$out = "E:\personalProjects\coc-platform\backend\tools\selftest\_endpoints-report.txt"

$text = [IO.File]::ReadAllText($contract, [System.Text.Encoding]::UTF8)
$contractPaths = @{}
foreach ($m in [regex]::Matches($text, "(?:GET|POST|PUT|DELETE|PATCH)\s+(/api/v1[^\s\)\`"',`u]*)")) {
    $contractPaths[$m.Groups[0].Value.Trim()] = $true
}
# 契约里也可能只写相对路径（如 `POST /projects/{project_id}/submit`）
foreach ($m in [regex]::Matches($text, "(?:GET|POST|PUT|DELETE|PATCH)\s+(/(?:projects|comments|categories|users|uploads|files|im|reports|auth|admin|system)[^\s\)\`"',`u]*)")) {
    $contractPaths["$($m.Groups[0].Value.Trim())"] = $true
}

$impl = New-Object System.Collections.Generic.List[string]
foreach ($f in Get-ChildItem $src -Filter *.java) {
    $c = [IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    $base = ""
    if ($c -match '@RequestMapping\("([^"]+)"\)') { $base = $Matches[1] }
    foreach ($m in [regex]::Matches($c, '@(Get|Post|Put|Delete|Patch)Mapping\(\s*(?:value\s*=\s*)?\{?([^\)]*?)\}?\s*\)')) {
        $verb = $m.Groups[1].Value.ToUpper()
        foreach ($p in [regex]::Matches($m.Groups[2].Value, '"([^"]*)"')) {
            $impl.Add("$verb $($base)$($p.Groups[1].Value)")
        }
    }
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("### 契约中出现的接口（去重）")
foreach ($k in ($contractPaths.Keys | Sort-Object)) { $lines.Add($k) }
$lines.Add("")
$lines.Add("### 后端已实现（controller 注解解析）")
foreach ($k in ($impl | Sort-Object -Unique)) { $lines.Add($k) }
$lines.Add("")
$lines.Add("### 契约有、后端可能没有（人工核对）")
$lines.Add("(见 README-dev.md「对不上契约的地方」章节)")
[IO.File]::WriteAllLines($out, $lines, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "written: $out  contract=$($contractPaths.Count) impl=$($impl.Count)"
