# =====================================================================
# coc-platform backend self-test v2 (curl based, ASCII only)
# closed loop: dev-apply -> admin approve -> login -> draft -> upload
#              -> submit -> admin approve -> list -> view -> stats
# =====================================================================
$ErrorActionPreference = "Continue"
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$BASE = "http://127.0.0.1:8090/api/v1"
$LOG  = "E:\personalProjects\coc-platform\backend\target\selftest.log"
$TMP  = "E:\personalProjects\coc-platform\backend\target"
$BODYFILE = "$TMP\_body.json"
$RESPFILE = "$TMP\_resp.json"

Set-Content -Path $LOG -Value "=== coc-platform self test $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ===" -Encoding UTF8

function Call {
    param([string]$Method, [string]$Path, [string]$Json = $null, [string]$Token = $null, [string[]]$Form = $null)
    $a = @("-s", "-o", $RESPFILE, "-w", "%{http_code}", "-X", $Method)
    if ($Token) { $a += @("-H", "Authorization: Bearer $Token") }
    if (-not [string]::IsNullOrEmpty($Json)) {
        [System.IO.File]::WriteAllText($BODYFILE, $Json, (New-Object System.Text.UTF8Encoding($false)))
        $a += @("-H", "Content-Type: application/json; charset=utf-8", "--data-binary", "@$BODYFILE")
    }
    if ($Form) { foreach ($f in $Form) { $a += @("-F", $f) } }
    $a += "$BASE$Path"
    $codeTxt = (& curl.exe @a) -join ""
    $code = 0
    if ($codeTxt -match "(\d{3})") { $code = [int]$Matches[1] }
    $text = ""
    if (Test-Path $RESPFILE) { $text = [System.IO.File]::ReadAllText($RESPFILE, [System.Text.Encoding]::UTF8) }
    return @{ status = $code; body = $text.Trim() }
}

function Show {
    param([string]$Tag, $R, [int]$Max = 600)
    $b = [string]$R.body
    if ($b.Length -gt $Max) { $b = $b.Substring(0, $Max) + "...[truncated]" }
    $line = "== $Tag -> HTTP $($R.status) : $b"
    Add-Content -Path $LOG -Value $line -Encoding UTF8
    Write-Host $line
}

function J($R) { try { return ($R.body | ConvertFrom-Json) } catch { return $null } }

# ---------- 0. health ----------
Show "0.health" (Call "GET" "/system/health")

# ---------- 1. developer apply (pending) ----------
$r = Call "POST" "/auth/dev-apply" '{"qq_account":"900001","password":"selftest123","nickname":"selftestdev","contact":"qq:900001"}'
Show "1.dev-apply" $r
$devUserId = (J $r).data.user_id
Add-Content $LOG "   dev_user_id=$devUserId" -Encoding UTF8

# ---------- 2. login while pending -> 403 ----------
Show "2.login-pending(expect 403)" (Call "POST" "/auth/login" '{"qq_account":"900001","password":"selftest123"}')

# ---------- 3. admin login ----------
$r = Call "POST" "/auth/login" '{"qq_account":"900000","password":"admin12345"}'
Show "3.admin-login" $r
$admToken = (J $r).data.token
$admUserId = (J $r).data.user.user_id

# ---------- 4. admin sees pending applications ----------
$r = Call "GET" "/admin/developer-applications?status=0" $null $admToken
Show "4.admin-dev-applications" $r
$profileId = $null
foreach ($a in (J $r).data.list) { if ($a.qq_account -eq "900001") { $profileId = $a.developer_profile_id } }
Add-Content $LOG "   developer_profile_id=$profileId" -Encoding UTF8

# ---------- 5. approve developer ----------
Show "5.admin-approve-dev" (Call "POST" "/admin/developer-applications/$profileId/approve" '{"reason":"selftest approve"}' $admToken)

# ---------- 6. guest write attempts -> 401 ----------
Show "6a.guest-comment(expect 401)" (Call "POST" "/comments" '{"project_id":1,"content":"guest"}')
Show "6b.guest-download(expect 401)" (Call "GET" "/files/1/content")
Show "6c.guest-admin(expect 401)" (Call "GET" "/admin/stats/platform")

# ---------- 7. dev login ----------
$r = Call "POST" "/auth/login" '{"qq_account":"900001","password":"selftest123"}'
Show "7.dev-login" $r
$devToken = (J $r).data.token
Show "7b.dev-to-admin(expect 403)" (Call "GET" "/admin/stats/platform" $null $devToken)

# ---------- 8. create draft ----------
$r = Call "POST" "/projects" '{"title":"selftest-html-project","summary":"selftest summary","description":"selftest description","project_type":"html","tags":["selftest","demo"]}' $devToken
Show "8.create-draft" $r
$projectId = (J $r).data.project_id
Add-Content $LOG "   project_id=$projectId" -Encoding UTF8

# ---------- 9. upload a zip (single part) ----------
$zip = "$TMP\selftest-upload.zip"
$stage = "$TMP\selftest-zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage | Out-Null
Set-Content -Path "$stage\index.html" -Value "<html><body><h1>coc-platform selftest ok</h1></body></html>" -Encoding UTF8
[System.IO.Compression.ZipFile]::CreateFromDirectory($stage, $zip)
$fsize = (Get-Item $zip).Length
$sha = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
Add-Content $LOG "   zip size=$fsize sha256=$sha" -Encoding UTF8

$initJson = '{"file_name":"selftest.zip","file_size":' + $fsize + ',"sha256":"' + $sha + '","project_id":' + $projectId + '}'
$r = Call "POST" "/uploads/init" $initJson $devToken
Show "9a.upload-init" $r
$sessionId = (J $r).data.upload_session_id
Add-Content $LOG "   upload_session_id=$sessionId total_chunks=$((J $r).data.total_chunks) chunk_size=$((J $r).data.chunk_size) instant=$((J $r).data.instant)" -Encoding UTF8

Show "9b.upload-parts" (Call "POST" "/uploads/$sessionId/parts" $null $devToken @("part_number=1", "chunk=@$zip;filename=selftest.zip"))
Show "9c.upload-progress" (Call "GET" "/uploads/$sessionId" $null $devToken)

$r = Call "POST" "/uploads/$sessionId/complete" ('{"project_id":' + $projectId + ',"file_type":"source","changelog":"v1 selftest upload"}') $devToken
Show "9d.upload-complete" $r
$fileId = (J $r).data.project_file_id
$versionId = (J $r).data.project_version_id
Add-Content $LOG "   project_file_id=$fileId project_version_id=$versionId storage_path=$((J $r).data.storage_path)" -Encoding UTF8

$initJson2 = '{"file_name":"selftest.zip","file_size":' + $fsize + ',"sha256":"' + $sha + '","project_id":' + $projectId + '}'
Show "9e.upload-init-instant(2nd)" (Call "POST" "/uploads/init" $initJson2 $devToken)

# ---------- 10. submit ----------
Show "10.submit" (Call "POST" "/projects/$projectId/submit" $null $devToken)

# ---------- 11. public list before approval ----------
$r = Call "GET" "/projects?page=1&size=5"
$found = $false
foreach ($p in (J $r).data.list) { if ($p.project_id -eq $projectId) { $found = $true } }
Add-Content $LOG "11.public-list-before-approve: HTTP $($r.status) total=$((J $r).data.total) contains_project=$found" -Encoding UTF8
Write-Host "11.public-list-before-approve: total=$((J $r).data.total) contains_project=$found"

# ---------- 12. admin approve ----------
Show "12.admin-approve-project" (Call "POST" "/admin/reviews/$versionId/approve" '{"reason":"selftest ok"}' $admToken)

# ---------- 13. public list after approval ----------
$r = Call "GET" "/projects?page=1&size=5"
$found2 = $false
foreach ($p in (J $r).data.list) { if ($p.project_id -eq $projectId) { $found2 = $true } }
Add-Content $LOG "13.public-list-after-approve: HTTP $($r.status) total=$((J $r).data.total) contains_project=$found2" -Encoding UTF8
Write-Host "13.public-list-after-approve: total=$((J $r).data.total) contains_project=$found2"
Show "13b.public-list-body" $r 900

# ---------- 14. detail ----------
Show "14.detail" (Call "GET" "/projects/$projectId") 1400

# ---------- 15. views ----------
Show "15a.view" (Call "POST" "/projects/$projectId/view")
Show "15b.view" (Call "POST" "/projects/$projectId/view")
Show "15c.view" (Call "POST" "/projects/$projectId/view")

# ---------- 16. stats ----------
Show "16a.stats-range7" (Call "GET" "/projects/$projectId/stats?range=7") 500
Show "16b.stats-range2" (Call "GET" "/projects/$projectId/stats?range=2") 500

# ---------- 17. run ----------
$r = Call "GET" "/projects/$projectId/run"
Show "17a.run-info" $r
$runUrl = (J $r).data.url
$rf = Call "GET" "/run/$projectId/"
Add-Content $LOG "17b.run-fetch(relative /api/v1/run/$projectId/): HTTP $($rf.status) body=$($rf.body)" -Encoding UTF8
Write-Host "17b.run-fetch: HTTP $($rf.status) body=$($rf.body)"
$af = & curl.exe -s -o NUL -w "%{http_code}" $runUrl
Add-Content $LOG "17c.run-fetch(absolute $runUrl): HTTP $af" -Encoding UTF8
Write-Host "17c.run-fetch(absolute): HTTP $af"

# ---------- 18. comments ----------
$r = Call "POST" "/comments" ('{"project_id":' + $projectId + ',"content":"selftest root comment"}') $devToken
Show "18a.comment-root" $r
$commentId = (J $r).data.comment_id
Show "18b.comment-reply" (Call "POST" "/comments" ('{"project_id":' + $projectId + ',"content":"selftest reply","parent_comment_id":' + $commentId + '}') $admToken)
Show "18c.comment-like" (Call "POST" "/comments/$commentId/like" $null $admToken)
Show "18d.comment-tree" (Call "GET" "/projects/$projectId/comments?page=1&size=20") 1500

# ---------- 19. interactions ----------
Show "19a.project-like" (Call "POST" "/projects/$projectId/like" $null $devToken)
Show "19b.project-favorite" (Call "POST" "/projects/$projectId/favorite" $null $devToken)
Show "19c.follow" (Call "POST" "/users/$admUserId/follow" $null $devToken)
Show "19d.follow-status" (Call "GET" "/users/$admUserId/follow-status")
Show "19e.user-profile" (Call "GET" "/users/$admUserId") 500
Show "19f.my-projects" (Call "GET" "/users/me/projects" $null $devToken) 700
Show "19g.my-favorites" (Call "GET" "/users/me/favorites" $null $devToken) 500

# ---------- 20. reports ----------
$r = Call "POST" "/reports" ('{"target_type":"project","target_id":' + $projectId + ',"reason_type":"selftest","detail":"self test report"}') $devToken
Show "20a.report-create" $r
$reportId = (J $r).data.report_id
Show "20b.my-reports-before" (Call "GET" "/reports/me" $null $devToken) 700
Show "20c.admin-reports" (Call "GET" "/admin/reports?status=0" $null $admToken) 700
Show "20d.admin-handle-report" (Call "POST" "/admin/reports/$reportId/handle" '{"action":"resolve","result":"selftest handled; content removed"}' $admToken)
Show "20e.my-reports-after(+points)" (Call "GET" "/reports/me" $null $devToken) 900
Show "20f.my-reports-read" (Call "POST" "/reports/me/read" $null $devToken)

# ---------- 21. IM ----------
$r = Call "POST" "/im/conversations/direct" ('{"user_id":' + $admUserId + '}') $devToken
Show "21a.im-direct-open" $r
$sid = (J $r).data.im_session_id
Show "21b.im-send-dev" (Call "POST" "/im/conversations/$sid/messages" '{"content":"hello from dev selftest"}' $devToken)
Show "21c.im-send-admin" (Call "POST" "/im/conversations/$sid/messages" '{"content":"reply from admin"}' $admToken)
Show "21d.im-messages" (Call "GET" "/im/conversations/$sid/messages" $null $devToken) 1200
Show "21e.im-conversations-dev" (Call "GET" "/im/conversations" $null $devToken) 1500
Show "21f.im-unread-admin" (Call "GET" "/im/unread" $null $admToken)
Show "21g.im-read-admin" (Call "POST" "/im/conversations/$sid/read" $null $admToken)
Show "21h.im-unread-admin-after" (Call "GET" "/im/unread" $null $admToken)

# ---------- 22. download source (login only) ----------
$dl = "$TMP\selftest-download.zip"
if (Test-Path $dl) { Remove-Item $dl -Force }
$code = & curl.exe -s -o $dl -w "%{http_code}" -H "Authorization: Bearer $devToken" "$BASE/files/$fileId/content"
$dlsize = if (Test-Path $dl) { (Get-Item $dl).Length } else { 0 }
Add-Content $LOG "22a.download-with-token: HTTP $code bytes=$dlsize" -Encoding UTF8
Write-Host "22a.download-with-token: HTTP $code bytes=$dlsize"
$code2 = & curl.exe -s -o NUL -w "%{http_code}" "$BASE/files/$fileId/content"
Add-Content $LOG "22b.download-guest(expect 401): HTTP $code2" -Encoding UTF8
Write-Host "22b.download-guest(expect 401): HTTP $code2"

# ---------- 23. admin tools ----------
Show "23a.admin-storage(absolute paths)" (Call "GET" "/admin/projects/$projectId/storage" $null $admToken) 1500
Show "23b.admin-stats" (Call "GET" "/admin/stats/platform" $null $admToken) 1200
Show "23c.admin-logs" (Call "GET" "/admin/logs?limit=6" $null $admToken) 1500

# ---------- 24. reset password + forced change ----------
$r = Call "POST" "/auth/dev-apply" '{"qq_account":"900002","password":"selftest456","nickname":"selftestdev2"}'
Show "24a.dev-apply-2" $r
$uid2 = (J $r).data.user_id
$r = Call "GET" "/admin/developer-applications?status=0" $null $admToken
$profileId2 = $null
foreach ($a in (J $r).data.list) { if ($a.qq_account -eq "900002") { $profileId2 = $a.developer_profile_id } }
Show "24b.admin-approve-2" (Call "POST" "/admin/developer-applications/$profileId2/approve" '{"reason":"ok"}' $admToken)
$r = Call "POST" "/admin/users/$uid2/reset-password" '{}' $admToken
Show "24c.admin-reset-password" $r
$tempPwd = (J $r).data.temp_password
$r = Call "POST" "/auth/login" ('{"qq_account":"900002","password":"' + $tempPwd + '"}')
Show "24d.login-with-temp" $r
$tempToken = (J $r).data.token
$r = Call "POST" "/auth/change-password" ('{"old_password":"' + $tempPwd + '","new_password":"newpass789"}') $tempToken
Show "24e.change-password-forced" $r
$newToken = (J $r).data.token
$old = Call "GET" "/auth/me" $null $tempToken
Add-Content $LOG "24f.old-token-after-change(expect 401): HTTP $($old.status)" -Encoding UTF8
Write-Host "24f.old-token-after-change(expect 401): HTTP $($old.status)"
Show "24g.new-token-me" (Call "GET" "/auth/me" $null $newToken)

# ---------- 25. down / restore / cancel ----------
Show "25a.admin-down" (Call "POST" "/admin/projects/$projectId/down" '{"reason":"selftest down"}' $admToken)
$pl = Call "GET" "/projects?page=1&size=5"
$still = $false
foreach ($p in (J $pl).data.list) { if ($p.project_id -eq $projectId) { $still = $true } }
Add-Content $LOG "25b.public-list-after-down: total=$((J $pl).data.total) contains_project=$still" -Encoding UTF8
Write-Host "25b.public-list-after-down: total=$((J $pl).data.total) contains_project=$still"
Show "25c.admin-restore" (Call "POST" "/admin/projects/$projectId/restore" $null $admToken)
Show "25d.admin-cancel-user" (Call "POST" "/admin/users/$uid2/cancel" '{"reason":"selftest cancel"}' $admToken)
Show "25e.cancelled-login(expect 403)" (Call "POST" "/auth/login" '{"qq_account":"900002","password":"newpass789"}')
Show "25f.cancelled-user-profile" (Call "GET" "/users/$uid2") 500

Write-Host ""
Write-Host "=== self test finished, log: $LOG ==="
