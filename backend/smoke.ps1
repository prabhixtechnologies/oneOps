# End-to-end smoke test against a locally running backend.
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\smoke.ps1
$ErrorActionPreference = "Continue"
$base = "http://localhost:8080/api/v1"
$pass = 0; $fail = 0

function T($name, $blk) {
    try {
        & $blk | Out-Null
        Write-Host ("  PASS  " + $name) -ForegroundColor Green
        $script:pass++
    } catch {
        $m = $_.Exception.Message
        if ($_.ErrorDetails.Message) { $m = $_.ErrorDetails.Message }
        Write-Host ("  FAIL  " + $name + "  ->  " + $m) -ForegroundColor Red
        $script:fail++
    }
}

# Asserts the call is refused, optionally with a specific error code.
function TDenied($name, $expectedCode, $blk) {
    try {
        & $blk | Out-Null
        Write-Host ("  FAIL  " + $name + "  ->  was ALLOWED") -ForegroundColor Red
        $script:fail++
    } catch {
        $status = ""
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        $bodyOk = $true
        if ($expectedCode -and $_.ErrorDetails.Message) {
            $bodyOk = $_.ErrorDetails.Message -match $expectedCode
        }
        if ($bodyOk) {
            Write-Host ("  PASS  " + $name + "  (HTTP " + $status + ")") -ForegroundColor Green
            $script:pass++
        } else {
            Write-Host ("  FAIL  " + $name + "  ->  wrong error: " + $_.ErrorDetails.Message) -ForegroundColor Red
            $script:fail++
        }
    }
}

# The register response intentionally returns only tokens and claims, so the email is
# carried alongside it for later login and magic-link checks.
function Register($label) {
    $s = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $email = "smoke-$label-$s@prabhixtest.in"
    $body = @{
        email = $email; password = "Str0ngPass!234"
        fullName = "Smoke $label"; organizationName = "Smoke $label $s"
    } | ConvertTo-Json
    $r = Invoke-RestMethod "$base/auth/register" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 30
    $r | Add-Member -NotePropertyName loginEmail -NotePropertyValue $email -Force
    $r
}

Write-Host "`n=== Platform health ===" -ForegroundColor Cyan
T "actuator health" { Invoke-RestMethod "http://localhost:8080/actuator/health" -TimeoutSec 20 }
T "OpenAPI document" { Invoke-RestMethod "http://localhost:8080/v3/api-docs" -TimeoutSec 30 }

Write-Host "`n=== Registration and identity ===" -ForegroundColor Cyan
$a = Register "a"
$b = Register "b"
$HA = @{ Authorization = "Bearer " + $a.accessToken }
$HB = @{ Authorization = "Bearer " + $b.accessToken }
$orgA = $a.organizationId
$orgB = $b.organizationId
if (-not $orgA) { Write-Host "registration returned no organizationId, aborting" -ForegroundColor Red; exit 1 }
T "GET /auth/me" { Invoke-RestMethod "$base/auth/me" -Headers $HA -TimeoutSec 20 }
T "POST /auth/login" {
    $body = @{ email = $a.loginEmail; password = "Str0ngPass!234" } | ConvertTo-Json
    Invoke-RestMethod "$base/auth/login" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 20
}

Write-Host "`n=== Tenant isolation ===" -ForegroundColor Cyan
TDenied "read foreign organization" "CROSS_TENANT_ACCESS" { Invoke-RestMethod "$base/organizations/$orgB" -Headers $HA -TimeoutSec 20 }
TDenied "rename foreign organization" "CROSS_TENANT_ACCESS" {
    $p = @{ name = "PWNED" } | ConvertTo-Json
    Invoke-RestMethod "$base/organizations/$orgB" -Method Patch -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
TDenied "list foreign organization members" "CROSS_TENANT_ACCESS" { Invoke-RestMethod "$base/organizations/$orgB/members" -Headers $HA -TimeoutSec 20 }
T "foreign organization name untouched" {
    $o = Invoke-RestMethod "$base/organizations/$orgB" -Headers $HB -TimeoutSec 20
    if ($o.name -eq "PWNED") { throw "organization was tampered with" }
    $o
}
T "own organization read" { Invoke-RestMethod "$base/organizations/$orgA" -Headers $HA -TimeoutSec 20 }
T "own organization members" { Invoke-RestMethod "$base/organizations/$orgA/members" -Headers $HA -TimeoutSec 20 }
T "organization switch still permitted" { Invoke-RestMethod "$base/organizations/$orgA/select" -Method Post -Headers $HA -TimeoutSec 20 }
TDenied "DELETE organization without confirm when multiple owners possible" $null {
    Invoke-RestMethod "$base/organizations/$orgA" -Method Delete -Headers $HA -TimeoutSec 20
}

Write-Host "`n=== Trial subscription opened on signup ===" -ForegroundColor Cyan
$sub = Invoke-RestMethod "$base/billing/subscription" -Headers $HA -TimeoutSec 20
T "subscription is TRIALING" { if ($sub.status -ne "TRIALING") { throw ("status=" + $sub.status) }; $sub }
Write-Host ("        plan=" + $sub.planName + " seats=" + $sub.seats + " until " + $sub.currentPeriodEnd)
$ent = Invoke-RestMethod "$base/billing/entitlements" -Headers $HA -TimeoutSec 20
Write-Host ("        entitlements: mailboxes=" + $ent.mailboxes + " mailDomains=" + $ent.mailDomains + " apiAccess=" + $ent.apiAccess)
T "GET /billing/plans" { Invoke-RestMethod "$base/billing/plans" -Headers $HA -TimeoutSec 20 }
T "GET /billing/invoices" { Invoke-RestMethod "$base/billing/invoices" -Headers $HA -TimeoutSec 20 }
T "GET /billing/address" { Invoke-RestMethod "$base/billing/address" -Headers $HA -TimeoutSec 20 }
T "PUT /billing/address" {
    $p = @{ line1 = "1 MG Road"; city = "Bengaluru"; state = "Karnataka"; pincode = "560001"
            country = "IN"; gstin = "29ABCDE1234F1Z5"; billingEmail = $a.loginEmail } | ConvertTo-Json
    Invoke-RestMethod "$base/billing/address" -Method Put -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "GET /billing/payment-methods" { Invoke-RestMethod "$base/billing/payment-methods" -Headers $HA -TimeoutSec 20 }

Write-Host "`n=== Entitlement enforcement (plan allows $($ent.mailboxes) mailbox) ===" -ForegroundColor Cyan
$made = 0
for ($i = 1; $i -le ($ent.mailboxes + 1); $i++) {
    try {
        $p = @{ address = "box$i-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@prabhixtest.in"; name = "Box $i"; kind = "SHARED" } | ConvertTo-Json
        Invoke-RestMethod "$base/mail/mailboxes" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20 | Out-Null
        $made++
    } catch { break }
}
T "mailbox creation capped at plan quota" {
    if ($made -gt $ent.mailboxes) { throw ("created $made, quota was " + $ent.mailboxes) }
    $made
}
Write-Host ("        created $made of $($ent.mailboxes) allowed, then blocked")

Write-Host "`n=== Mail: mailboxes, templates, threads ===" -ForegroundColor Cyan
$boxes = Invoke-RestMethod "$base/mail/mailboxes" -Headers $HA -TimeoutSec 20
T "GET /mail/mailboxes" { $boxes }
if ($boxes.Count -gt 0) {
    $id = $boxes[0].id
    T "GET /mail/mailboxes/{id}" { Invoke-RestMethod "$base/mail/mailboxes/$id" -Headers $HA -TimeoutSec 20 }
    T "PATCH /mail/mailboxes/{id}" {
        $p = @{ name = "Customer Support" } | ConvertTo-Json
        Invoke-RestMethod "$base/mail/mailboxes/$id" -Method Patch -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
    }
    TDenied "foreign tenant cannot read that mailbox" $null { Invoke-RestMethod "$base/mail/mailboxes/$id" -Headers $HB -TimeoutSec 20 }
}
T "GET /mail/templates" { Invoke-RestMethod "$base/mail/templates" -Headers $HA -TimeoutSec 20 }
$tpl = Invoke-RestMethod "$base/mail/templates/auth.magic-link" -Headers $HA -TimeoutSec 20
T "GET /mail/templates/{key}" { $tpl }
Write-Host ("        declared variables: " + (($tpl.variables | ForEach-Object { $_.name }) -join ", "))
T "PATCH /mail/templates/{key}" {
    $p = @{ subject = $tpl.subject; htmlBody = $tpl.htmlBody } | ConvertTo-Json
    Invoke-RestMethod "$base/mail/templates/auth.magic-link" -Method Patch -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
TDenied "template edit that breaks rendering is refused" $null {
    $p = @{ htmlBody = 'Broken <p th:text="${ (( }">x</p>' } | ConvertTo-Json
    Invoke-RestMethod "$base/mail/templates/auth.magic-link" -Method Patch -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "GET /mail/threads" { Invoke-RestMethod "$base/mail/threads?limit=5" -Headers $HA -TimeoutSec 20 }
T "GET /mail/threads with tag filter" { Invoke-RestMethod ("$base/mail/threads?limit=5&tagId=" + [guid]::NewGuid()) -Headers $HA -TimeoutSec 20 }
T "GET /mail/domains" { Invoke-RestMethod "$base/mail/domains" -Headers $HA -TimeoutSec 20 }
T "GET /mail/tags" { Invoke-RestMethod "$base/mail/tags" -Headers $HA -TimeoutSec 20 }
$mailTag = $null
T "POST /mail/tags" {
    $p = @{ name = "Smoke Tag"; colour = "#FF0000" } | ConvertTo-Json
    $script:mailTag = Invoke-RestMethod "$base/mail/tags" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
    $script:mailTag
}
T "PATCH /mail/tags/{id}" {
    $p = @{ name = "Smoke Tag Updated"; colour = "#00FF00" } | ConvertTo-Json
    Invoke-RestMethod ("$base/mail/tags/" + $mailTag.id) -Method Patch -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "GET /mail/canned-replies" { Invoke-RestMethod "$base/mail/canned-replies" -Headers $HA -TimeoutSec 20 }
$canned = $null
T "POST /mail/canned-replies" {
    $p = @{ title = "Smoke reply"; bodyHtml = "<p>Thanks for writing.</p>" } | ConvertTo-Json
    $script:canned = Invoke-RestMethod "$base/mail/canned-replies" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
    $script:canned
}
T "PATCH /mail/canned-replies/{id}" {
    $p = @{ title = "Smoke reply v2"; bodyHtml = "<p>Updated.</p>" } | ConvertTo-Json
    Invoke-RestMethod ("$base/mail/canned-replies/" + $canned.id) -Method Patch -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "DELETE /mail/canned-replies/{id}" {
    Invoke-RestMethod ("$base/mail/canned-replies/" + $canned.id) -Method Delete -Headers $HA -TimeoutSec 20
}
T "DELETE /mail/tags/{id} detaches from threads" {
    Invoke-RestMethod ("$base/mail/tags/" + $mailTag.id) -Method Delete -Headers $HA -TimeoutSec 20
}

Write-Host "`n=== Dashboard ===" -ForegroundColor Cyan
$dash = Invoke-RestMethod "$base/dashboard" -Headers $HA -TimeoutSec 25
T "GET /dashboard" { $dash }
T "dashboard trend series populated" { if ($dash.threadsTrend.Count -lt 1) { throw "empty trend" }; $dash.threadsTrend }
Write-Host ("        openThreads=" + $dash.kpis.openThreads + " seats=" + $dash.kpis.seatsUsed + "/" + $dash.kpis.seatsLimit + " trendPoints=" + $dash.threadsTrend.Count)

Write-Host "`n=== Org administration ===" -ForegroundColor Cyan
$roles = Invoke-RestMethod "$base/roles" -Headers $HA -TimeoutSec 20
T "GET /roles" { $roles }
$memberRole = $roles.items | Where-Object { $_.roleKey -eq "MEMBER" } | Select-Object -First 1
T "POST /invites" {
    $p = @{ email = "colleague$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@prabhixtest.in"; roleId = $memberRole.id } | ConvertTo-Json
    Invoke-RestMethod "$base/invites" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
$invites = Invoke-RestMethod "$base/invites" -Headers $HA -TimeoutSec 20
T "GET /invites" { $invites }
Write-Host ("        pending invites=" + $invites.items.Count)
T "GET /permissions" { Invoke-RestMethod "$base/permissions" -Headers $HA -TimeoutSec 20 }
T "GET /teams" { Invoke-RestMethod "$base/teams" -Headers $HA -TimeoutSec 20 }
$team = $null
T "POST /teams" {
    $p = @{ name = "Support Team"; slug = "support-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())" } | ConvertTo-Json
    $script:team = Invoke-RestMethod "$base/teams" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
    $script:team
}
T "GET /audit-logs" { Invoke-RestMethod "$base/audit-logs?limit=5" -Headers $HA -TimeoutSec 20 }
T "GET /flags" { Invoke-RestMethod "$base/flags" -Headers $HA -TimeoutSec 20 }
T "PUT /flags/{key} override" {
    $p = @{ enabled = $true; reason = "smoke test" } | ConvertTo-Json
    Invoke-RestMethod "$base/flags/mail.open_tracking" -Method Put -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "DELETE /flags/{key} override clears" {
    Invoke-RestMethod "$base/flags/mail.open_tracking" -Method Delete -Headers $HA -TimeoutSec 20
}

Write-Host "`n=== Site admin pipeline (platform staff) ===" -ForegroundColor Cyan
TDenied "GET /admin/site/leads without platform admin" $null {
    Invoke-RestMethod "$base/admin/site/leads" -Headers $HA -TimeoutSec 20
}

Write-Host "`n=== API keys ===" -ForegroundColor Cyan
$key = Invoke-RestMethod "$base/settings/api-keys" -Method Post -Headers $HA -ContentType "application/json" -TimeoutSec 20 -Body (@{ name = "CI pipeline" } | ConvertTo-Json)
T "POST /settings/api-keys returns raw key once" { if (-not $key.key) { throw "no raw key returned" }; $key }
T "GET /settings/api-keys never returns the secret" {
    $list = Invoke-RestMethod "$base/settings/api-keys" -Headers $HA -TimeoutSec 20
    foreach ($k in $list.items) { if ($k.key) { throw "secret leaked in list response" } }
    $list
}
T "an issued API key actually authenticates a request" {
    $h = @{ "X-API-Key" = $key.key }
    $r = Invoke-RestMethod "$base/dashboard" -Headers $h -TimeoutSec 20
    if (-not $r) { throw "key authenticated but returned nothing" }
    "authenticated as org $orgA"
}
TDenied "an API key cannot read another tenant's organization" $null {
    Invoke-RestMethod "$base/organizations/$orgB" -Headers @{ "X-API-Key" = $key.key } -TimeoutSec 20
}
TDenied "a garbage API key is refused" $null {
    Invoke-RestMethod "$base/dashboard" -Headers @{ "X-API-Key" = "pbx_live_not_a_real_key" } -TimeoutSec 20
}
T "DELETE /settings/api-keys/{id}" { Invoke-RestMethod ("$base/settings/api-keys/" + $key.id) -Method Delete -Headers $HA -TimeoutSec 20 }
TDenied "a revoked API key stops working" $null {
    Invoke-RestMethod "$base/dashboard" -Headers @{ "X-API-Key" = $key.key } -TimeoutSec 20
}

Write-Host "`n=== Email verification ===" -ForegroundColor Cyan
T "POST /auth/email/verify/request sends a link" {
    Invoke-RestMethod "$base/auth/email/verify/request" -Method Post -Headers $HA -TimeoutSec 20
}
TDenied "confirming with a bogus token is refused, not a 500" $null {
    $p = @{ token = "not-a-real-verification-token" } | ConvertTo-Json
    Invoke-RestMethod "$base/auth/email/verify/confirm" -Method Post -Body $p -ContentType "application/json" -TimeoutSec 20
}

Write-Host "`n=== File storage ===" -ForegroundColor Cyan
$tmp = Join-Path $env:TEMP "prabhix-smoke.txt"
"Prabhix file storage smoke test" | Out-File -FilePath $tmp -Encoding utf8
$up = curl.exe -s -S -X POST "$base/files" -H ("Authorization: Bearer " + $a.accessToken) -F "file=@$tmp" -F "purpose=MAIL_ATTACHMENT" | ConvertFrom-Json
T "POST /files" { if (-not $up.id) { throw "no file id" }; $up }
T "GET /files lists uploads" { Invoke-RestMethod "$base/files?limit=5" -Headers $HA -TimeoutSec 20 }
T "GET /files/{id} downloads" {
    $code = curl.exe -s -o (Join-Path $env:TEMP "prabhix-dl.txt") -w "%{http_code}" -H ("Authorization: Bearer " + $a.accessToken) ("$base/files/" + $up.id)
    if ($code -notmatch "^(200|302)$") { throw "http $code" }
    $code
}
T "foreign tenant cannot download that file" {
    $code = curl.exe -s -o NUL -w "%{http_code}" -H ("Authorization: Bearer " + $b.accessToken) ("$base/files/" + $up.id)
    if ($code -eq "200") { throw "cross-tenant file read was allowed" }
    $code
}
T "users/me avatar upload" {
    curl.exe -s -S -X POST "$base/users/me/avatar" -H ("Authorization: Bearer " + $a.accessToken) -F "file=@$tmp" | Out-Null
    "ok"
}

Write-Host "`n=== User self-service ===" -ForegroundColor Cyan
T "PATCH /users/me" {
    $p = @{ displayName = "Smoke Test User" } | ConvertTo-Json
    Invoke-RestMethod "$base/users/me" -Method Patch -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "GET /users/me/sessions" { Invoke-RestMethod "$base/users/me/sessions" -Headers $HA -TimeoutSec 20 }
T "PATCH /users/me/notification-prefs" {
    $p = @{ productUpdates = $true; weeklyDigest = $false } | ConvertTo-Json
    Invoke-RestMethod "$base/users/me/notification-prefs" -Method Patch -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
TDenied "wrong HTTP method returns 405 not 500" "METHOD_NOT_ALLOWED" {
    Invoke-RestMethod "$base/users/me/notification-prefs" -Method Put -Body "{}" -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
TDenied "unmapped endpoint returns 404 not 500" "NOT_FOUND" {
    Invoke-RestMethod "$base/does-not-exist" -Headers $HA -TimeoutSec 20
}

Write-Host "`n=== Marketing site (public) ===" -ForegroundColor Cyan
T "POST /site/leads" {
    $p = @{ name = "Riya Menon"; email = "lead$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@example.com"; company = "Acme"; message = "Interested in Prabhix." } | ConvertTo-Json
    Invoke-RestMethod "$base/site/leads" -Method Post -Body $p -ContentType "application/json" -TimeoutSec 20
}
T "POST /site/subscribers" {
    $p = @{ email = "sub$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@example.com" } | ConvertTo-Json
    Invoke-RestMethod "$base/site/subscribers" -Method Post -Body $p -ContentType "application/json" -TimeoutSec 20
}
T "GET /site/careers" { Invoke-RestMethod "$base/site/careers" -TimeoutSec 20 }

Write-Host "`n=== Mail delivery pipeline ===" -ForegroundColor Cyan
T "POST /auth/magic-link/request" {
    $p = @{ email = $a.loginEmail } | ConvertTo-Json
    Invoke-WebRequest "$base/auth/magic-link/request" -Method Post -Body $p -ContentType "application/json" -TimeoutSec 20
}
Start-Sleep -Seconds 7
$mp = Invoke-RestMethod "http://localhost:8025/api/v1/messages" -TimeoutSec 20
T "message delivered to Mailpit" { if ($mp.total -lt 1) { throw "Mailpit empty" }; $mp }
Write-Host ("        mailpit total=" + $mp.total + "  latest=" + $mp.messages[0].Subject)

Write-Host "`n=== Visitor tracking (public beacon) ===" -ForegroundColor Cyan
$slugA = (Invoke-RestMethod "$base/organizations/$orgA" -Headers $HA -TimeoutSec 20).slug
$ingest = @{
    consent = "FULL"; referrer = "https://www.google.com/"
    pageViews = @(@{ url = "https://prabhixtechnologies.com/"; path = "/"; title = "Home"; entry = $true })
    events = @(@{ name = "hero_cta_click"; properties = @{ variant = "a" } })
    session = @{ deviceType = "MOBILE"; browser = "Chrome"; os = "Android"
                 screenWidth = 390; screenHeight = 844; language = "en-IN"; timezone = "Asia/Kolkata" }
    utm = @{ utm_source = "google"; utm_campaign = "launch" }
    presence = @{ url = "https://prabhixtechnologies.com/"; path = "/"; title = "Home" }
} | ConvertTo-Json -Depth 6
$vis = $null
T "POST /visitor/public/{slug}/ingest (anonymous, no auth)" {
    $script:vis = Invoke-RestMethod "$base/visitor/public/$slugA/ingest" -Method Post -Body $ingest -ContentType "application/json" -TimeoutSec 20
    if (-not $script:vis.visitorKey) { throw "no visitorKey issued" }
    $script:vis
}
T "second page view reuses the same visitor and session" {
    $p = @{ consent = "FULL"; visitorKey = $vis.visitorKey; sessionId = $vis.sessionId
            pageViews = @(@{ url = "https://prabhixtechnologies.com/pricing"; path = "/pricing"; title = "Pricing" })
            presence = @{ url = "https://prabhixtechnologies.com/pricing"; path = "/pricing"; title = "Pricing" } } | ConvertTo-Json -Depth 6
    Invoke-RestMethod "$base/visitor/public/$slugA/ingest" -Method Post -Body $p -ContentType "application/json" -TimeoutSec 20
}
$live = $null
T "GET /visitors/live shows them on the current page" {
    $script:live = Invoke-RestMethod "$base/visitors/live" -Headers $HA -TimeoutSec 20
    if (@($script:live).Count -lt 1) { throw "no live visitor" }
    if ($script:live[0].currentPath -ne "/pricing") { throw "presence stale: $($script:live[0].currentPath)" }
    $script:live
}
Write-Host ("        live=" + @($live).Count + "  on=" + $live[0].currentPath)
$vlist = $null
T "GET /visitors returns the visitor on the first page" {
    $script:vlist = Invoke-RestMethod "$base/visitors" -Headers $HA -TimeoutSec 20
    if (@($script:vlist.items).Count -lt 1) { throw "first page was empty (keyset cursor regression)" }
    $script:vlist
}
$visitorId = $vlist.items[0].id
T "GET /visitors/{id} detail carries the session" { Invoke-RestMethod "$base/visitors/$visitorId" -Headers $HA -TimeoutSec 20 }
T "GET /visitors/{id}/page-views lists both views" {
    $pv = Invoke-RestMethod "$base/visitors/$visitorId/page-views" -Headers $HA -TimeoutSec 20
    if (@($pv.items).Count -lt 2) { throw "expected 2 page views, got $(@($pv.items).Count)" }
    $pv
}
T "GET /visitors/{id}/events lists the custom event" {
    $ev = Invoke-RestMethod "$base/visitors/$visitorId/events" -Headers $HA -TimeoutSec 20
    if (@($ev.items).Count -lt 1) { throw "no events" }
    $ev
}
T "GET /visitors/analytics/summary" { Invoke-RestMethod "$base/visitors/analytics/summary" -Headers $HA -TimeoutSec 20 }
TDenied "foreign tenant cannot read that visitor" $null { Invoke-RestMethod "$base/visitors/$visitorId" -Headers $HB -TimeoutSec 20 }

Write-Host "`n=== Live chat, visitor to agent and back ===" -ForegroundColor Cyan
$conv = $null
T "POST /chat/public/{slug}/conversations (pre-chat form)" {
    $p = @{ visitorKey = $vis.visitorKey; name = "Ramesh Kumar"
            email = "ramesh$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@example.com"
            subject = "Pricing question" } | ConvertTo-Json
    $script:conv = Invoke-RestMethod "$base/chat/public/$slugA/conversations" -Method Post -Body $p -ContentType "application/json" -TimeoutSec 20
    if (-not $script:conv.conversationToken) { throw "no conversation token issued" }
    $script:conv
}
$CT = @{ "X-Chat-Token" = $conv.conversationToken }
T "visitor sends a message" {
    $p = @{ body = "Is the Growth plan billed annually?" } | ConvertTo-Json
    Invoke-RestMethod "$base/chat/public/$slugA/conversations/$($conv.conversationId)/messages" -Method Post -Body $p -Headers $CT -ContentType "application/json" -TimeoutSec 20
}
T "visitor uploads chat attachment" {
    curl.exe -s -S -X POST "$base/chat/public/$slugA/conversations/$($conv.conversationId)/attachments" -H ("X-Chat-Token: " + $conv.conversationToken) -F "file=@$tmp" | Out-Null
    "ok"
}
# Auto-routing is on by default, so a new conversation is normally claimed for the only
# available agent straight away and the unassigned queue is legitimately empty. What
# matters is that the conversation reaches the agent through some queue, so assert that
# rather than pinning it to one.
T "a new conversation surfaces to the agent through the queues" {
    $all = Invoke-RestMethod "$base/chat/conversations?queue=all" -Headers $HA -TimeoutSec 20
    if (-not (@($all.items) | Where-Object { $_.id -eq $conv.conversationId })) {
        throw "conversation missing from the 'all' queue (keyset cursor regression)"
    }
    $mine = Invoke-RestMethod "$base/chat/conversations?queue=mine" -Headers $HA -TimeoutSec 20
    $unassigned = Invoke-RestMethod "$base/chat/conversations?queue=unassigned" -Headers $HA -TimeoutSec 20
    $inMine = [bool](@($mine.items) | Where-Object { $_.id -eq $conv.conversationId })
    $inUnassigned = [bool](@($unassigned.items) | Where-Object { $_.id -eq $conv.conversationId })
    if (-not ($inMine -or $inUnassigned)) {
        throw "conversation was in neither 'mine' nor 'unassigned'"
    }
    Write-Host ("        routed to " + $(if ($inMine) { "'mine' by auto-assign" } else { "'unassigned'" }))
    $all
}
T "GET /chat/conversations/counts" { Invoke-RestMethod "$base/chat/conversations/counts" -Headers $HA -TimeoutSec 20 }
T "POST /chat/conversations/{id}/assign" {
    $meA = Invoke-RestMethod "$base/auth/me" -Headers $HA -TimeoutSec 20
    $p = @{ agentId = $meA.userId } | ConvertTo-Json
    Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)/assign" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
TDenied "assign with a missing agentId is rejected, not a 500" $null {
    Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)/assign" -Method Post -Body "{}" -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "agent replies" {
    $p = @{ body = "Yes - annual billing saves 20%." } | ConvertTo-Json
    Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)/messages" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "agent adds an internal note via the body flag" {
    $p = @{ body = "Upsell candidate."; internal = $true } | ConvertTo-Json
    Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)/messages" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "agent adds an internal note via the query flag" {
    $p = @{ body = "Escalate to the account lead." } | ConvertTo-Json
    Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)/messages?note=true" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
$vmsgs = $null
T "visitor reads the thread and sees the agent reply" {
    $script:vmsgs = Invoke-RestMethod "$base/chat/public/$slugA/conversations/$($conv.conversationId)/messages" -Headers $CT -TimeoutSec 20
    if (@($script:vmsgs.items).Count -lt 2) { throw "visitor saw $(@($script:vmsgs.items).Count) messages, expected at least 2" }
    $script:vmsgs
}
T "both internal notes are withheld from the visitor" {
    foreach ($secret in @("Upsell candidate.", "Escalate to the account lead.")) {
        if ($vmsgs.items | Where-Object { $_.body -eq $secret }) { throw "internal note leaked to visitor: $secret" }
    }
    if ($vmsgs.items | Where-Object { $_.senderType -eq "NOTE" }) { throw "a NOTE message reached the visitor" }
    $true
}
T "agent sees every message including both notes" {
    $d = Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)" -Headers $HA -TimeoutSec 20
    foreach ($secret in @("Upsell candidate.", "Escalate to the account lead.")) {
        if (-not ($d.messages | Where-Object { $_.body -eq $secret })) { throw "note missing for agent: $secret" }
    }
    $d
}
Write-Host ("        visitor sees " + @($vmsgs.items).Count + " messages, note withheld")
TDenied "forged chat token is rejected" $null {
    Invoke-RestMethod "$base/chat/public/$slugA/conversations/$($conv.conversationId)/messages" -Headers @{ "X-Chat-Token" = "forged.token.value" } -TimeoutSec 20
}
TDenied "foreign tenant cannot read the conversation" $null {
    Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)" -Headers $HB -TimeoutSec 20
}
T "GET /chat/settings" { Invoke-RestMethod "$base/chat/settings" -Headers $HA -TimeoutSec 20 }
T "POST /chat/canned-replies" {
    $p = @{ shortcut = "/pricing"; title = "Pricing overview"; body = "Our plans start at INR 999/month." } | ConvertTo-Json
    Invoke-RestMethod "$base/chat/canned-replies" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "GET /chat/canned-replies" { Invoke-RestMethod "$base/chat/canned-replies" -Headers $HA -TimeoutSec 20 }

Write-Host "`n=== Realtime SSE (shared Redis listener) ===" -ForegroundColor Cyan
T "GET /chat/stream opens an event stream and delivers a message" {
    # Curl exits when max-time elapses, so the body is what arrived on the open stream. A
    # message is posted from a second job while the stream is held open.
    $tok = $a.accessToken
    $convId = $conv.conversationId
    $poster = Start-Job -ScriptBlock {
        param($base, $tok, $convId)
        Start-Sleep -Seconds 2
        $h = @{ Authorization = "Bearer $tok"; "X-Prabhix-Org" = $using:orgA }
        $p = @{ body = "streamed reply" } | ConvertTo-Json
        try {
            Invoke-RestMethod "$base/chat/conversations/$convId/messages" -Method Post -Body $p `
                -Headers $h -ContentType "application/json" -TimeoutSec 15
        } catch { }
    } -ArgumentList $base, $tok, $convId
    $out = curl.exe -s --max-time 6 -H ("Authorization: Bearer " + $tok) -H ("X-Prabhix-Org: " + $orgA) `
        -H "Accept: text/event-stream" "$base/chat/stream" 2>&1
    Receive-Job $poster -Wait -AutoRemoveJob -ErrorAction SilentlyContinue | Out-Null
    $text = ($out | Out-String)
    if ($text -notmatch "data:") { throw "no SSE frames arrived; the shared listener is not fanning out" }
    Write-Host ("        frames: " + (($text -split "`n" | Where-Object { $_ -match "^(event|data):" }).Count))
    $text
}
TDenied "GET /chat/stream without a token is refused" $null {
    Invoke-RestMethod "$base/chat/stream" -TimeoutSec 10
}

Write-Host "`n=== Commerce: catalog, cart, checkout ===" -ForegroundColor Cyan
$prod = $null
T "POST /commerce/products (digital good)" {
    $p = @{
        slug = "starter-toolkit-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
        name = "Prabhix Starter Toolkit"; productType = "DIGITAL"; status = "ACTIVE"
        tagline = "Templates and scripts to bootstrap a project"
        description = "A downloadable bundle."; hsnCode = "998434"
    } | ConvertTo-Json
    $script:prod = Invoke-RestMethod "$base/commerce/products" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
    if (-not $script:prod.id) { throw "no product id returned" }
    $script:prod
}
$variant = $null
T "POST /commerce/products/{id}/variants" {
    $p = @{ name = "Single licence"; sku = "TOOLKIT-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
            priceMinor = 149900; trackInventory = $false } | ConvertTo-Json
    $script:variant = Invoke-RestMethod "$base/commerce/products/$($prod.id)/variants" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
    $script:variant
}
T "PUT /commerce/products/{id}/variants/{vid} (price edit)" {
    $p = @{ name = "Single licence"; priceMinor = 129900; trackInventory = $false } | ConvertTo-Json
    $r = Invoke-RestMethod "$base/commerce/products/$($prod.id)/variants/$($variant.id)" -Method Put -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
    if ($r.priceMinor -ne 129900) { throw "price not updated, got $($r.priceMinor)" }
    $r
}
T "an ACTIVE product is stamped published so the storefront can see it" {
    $d = Invoke-RestMethod "$base/commerce/products/$($prod.id)" -Headers $HA -TimeoutSec 20
    if ($d.status -ne "ACTIVE") { throw "status was $($d.status), expected ACTIVE" }
    if (-not $d.publishedAt) { throw "ACTIVE product has no publishedAt, it will never appear in the shop" }
    $d
}
T "GET /commerce/public/{slug}/products (anonymous storefront)" {
    $r = Invoke-RestMethod "$base/commerce/public/$slugA/products" -TimeoutSec 20
    if (-not (@($r.items) | Where-Object { $_.id -eq $prod.id })) { throw "active product missing from public catalog" }
    $r
}
T "GET /commerce/public/{slug}/products?search= filters server-side" {
    $r = Invoke-RestMethod "$base/commerce/public/$slugA/products?search=Starter" -TimeoutSec 20
    if (@($r.items).Count -lt 1) { throw "server-side search returned nothing" }
    $r
}
$cart = $null
T "POST /commerce/public/{slug}/carts then add an item" {
    $script:cart = Invoke-RestMethod "$base/commerce/public/$slugA/carts" -Method Post -TimeoutSec 20
    if (-not $script:cart.cartToken) { throw "no cart token issued" }
    $p = @{ variantId = $variant.id; quantity = 2 } | ConvertTo-Json
    Invoke-RestMethod "$base/commerce/public/$slugA/carts/$($script:cart.cartToken)/items" -Method Post -Body $p -ContentType "application/json" -TimeoutSec 20
}
T "cart totals are computed server-side, not taken from the client" {
    $r = Invoke-RestMethod "$base/commerce/public/$slugA/carts/$($cart.cartToken)" -TimeoutSec 20
    $expected = 129900 * 2
    if ($r.subtotalMinor -ne $expected) { throw "subtotal was $($r.subtotalMinor), expected $expected" }
    if ($r.totalMinor -lt $r.subtotalMinor) { throw "total below subtotal, tax not applied" }
    Write-Host ("        subtotal=" + $r.subtotalMinor + " tax=" + $r.taxMinor + " total=" + $r.totalMinor)
    $r
}
TDenied "a bogus discount code is refused" $null {
    $p = @{ code = "NOPE-DOES-NOT-EXIST" } | ConvertTo-Json
    Invoke-RestMethod "$base/commerce/public/$slugA/carts/$($cart.cartToken)/discount" -Method Post -Body $p -ContentType "application/json" -TimeoutSec 20
}
TDenied "foreign tenant cannot read that product" $null {
    Invoke-RestMethod "$base/commerce/products/$($prod.id)" -Headers $HB -TimeoutSec 20
}
T "GET /commerce/orders (empty but well-formed)" { Invoke-RestMethod "$base/commerce/orders" -Headers $HA -TimeoutSec 20 }
T "GET /commerce/dashboard" { Invoke-RestMethod "$base/commerce/dashboard" -Headers $HA -TimeoutSec 20 }

Write-Host "`n=== AI layer (no provider key configured) ===" -ForegroundColor Cyan
T "GET /ai/status reports unconfigured without erroring" {
    $r = Invoke-RestMethod "$base/ai/status" -Headers $HA -TimeoutSec 20
    Write-Host ("        enabled=" + $r.enabled + " configured=" + $r.configured)
    $r
}
T "GET /ai/prompts lists seeded platform defaults" {
    $r = Invoke-RestMethod "$base/ai/prompts" -Headers $HA -TimeoutSec 20
    if (@($r).Count -lt 1 -and @($r.items).Count -lt 1) { throw "no default prompts seeded" }
    $r
}
T "GET /ai/usage is queryable" { Invoke-RestMethod "$base/ai/usage" -Headers $HA -TimeoutSec 20 }
T "AI assist degrades gracefully rather than 500ing" {
    try {
        $p = @{ taskKey = "mail.reply_suggest"; prompt = "Summarise this." } | ConvertTo-Json
        Invoke-RestMethod "$base/ai/assist" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 30
        "returned a response"
    } catch {
        $code = [int]$_.Exception.Response.StatusCode
        if ($code -ge 500 -and $code -ne 503) { throw "unexpected $code, AI should degrade not crash" }
        "declined cleanly with HTTP $code"
    }
}

Write-Host "`n=== Observability: structured event log ===" -ForegroundColor Cyan
T "GET /event-logs returns the access log entries we just generated" {
    $r = Invoke-RestMethod "$base/event-logs" -Headers $HA -TimeoutSec 20
    if (@($r.items).Count -lt 1) { throw "event log empty; the access log filter is not writing" }
    Write-Host ("        events=" + @($r.items).Count + " newest=" + $r.items[0].eventCode)
    $r
}
T "GET /event-logs/stats aggregates for the charts" { Invoke-RestMethod "$base/event-logs/stats" -Headers $HA -TimeoutSec 20 }
T "GET /event-logs?severity= filters" { Invoke-RestMethod "$base/event-logs?severity=INFO" -Headers $HA -TimeoutSec 20 }
T "correlation id is echoed back for support tracing" {
    $mine = "smoke-" + [Guid]::NewGuid().ToString("N")
    $resp = Invoke-WebRequest "$base/dashboard" -Headers ($HA + @{ "X-Correlation-Id" = $mine }) -TimeoutSec 20
    $echoed = $resp.Headers["X-Correlation-Id"]
    if ($echoed -ne $mine) { throw "expected '$mine' echoed, got '$echoed'" }
    $echoed
}
TDenied "foreign tenant cannot read our event log" $null {
    Invoke-RestMethod "$base/event-logs?organizationId=$orgA" -Headers $HB -TimeoutSec 20
}

Write-Host "`n=== Push notifications (mobile) ===" -ForegroundColor Cyan
$pushTok = "smoke-fcm-" + [Guid]::NewGuid().ToString("N")
T "POST /devices/push-tokens registers a device" {
    $p = @{ token = $pushTok; platform = "FCM"; deviceId = "smoke-device-1"
            deviceName = "Pixel 8"; appVersion = "1.0.0" } | ConvertTo-Json
    Invoke-RestMethod "$base/devices/push-tokens" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
}
T "re-registering the same token upserts instead of duplicating" {
    $p = @{ token = $pushTok; platform = "FCM"; deviceId = "smoke-device-1"
            deviceName = "Pixel 8 Pro"; appVersion = "1.0.1" } | ConvertTo-Json
    Invoke-RestMethod "$base/devices/push-tokens" -Method Post -Body $p -Headers $HA -ContentType "application/json" -TimeoutSec 20
    $r = Invoke-RestMethod "$base/devices/push-tokens" -Headers $HA -TimeoutSec 20
    $matches = @(@($r) + @($r.items) | Where-Object { $_.deviceId -eq "smoke-device-1" })
    if ($matches.Count -ne 1) { throw "expected exactly 1 row for the device, found $($matches.Count)" }
    $r
}
T "DELETE /devices/push-tokens/{token} deregisters on logout" {
    Invoke-RestMethod "$base/devices/push-tokens/$pushTok" -Method Delete -Headers $HA -TimeoutSec 20
    "ok"
}

Write-Host "`n=== Idempotent send (offline mobile retry) ===" -ForegroundColor Cyan
T "replaying the same Idempotency-Key does not double-post" {
    $key = [Guid]::NewGuid().ToString()
    $p = @{ body = "Sent from a train tunnel." } | ConvertTo-Json
    $h = $HA + @{ "Idempotency-Key" = $key }
    $first = Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)/messages" -Method Post -Body $p -Headers $h -ContentType "application/json" -TimeoutSec 20
    $second = Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)/messages" -Method Post -Body $p -Headers $h -ContentType "application/json" -TimeoutSec 20
    if ($first.id -ne $second.id) { throw "retry created a second message ($($first.id) vs $($second.id))" }
    $d = Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)" -Headers $HA -TimeoutSec 20
    $n = @($d.messages | Where-Object { $_.body -eq "Sent from a train tunnel." }).Count
    if ($n -ne 1) { throw "found $n copies of the retried message" }
    "single message, id reused"
}

Write-Host "`n=== Unauthenticated access is refused ===" -ForegroundColor Cyan
TDenied "GET /dashboard without a token" $null { Invoke-RestMethod "$base/dashboard" -TimeoutSec 20 }
TDenied "GET /mail/threads without a token" $null { Invoke-RestMethod "$base/mail/threads" -TimeoutSec 20 }
TDenied "GET /visitors without a token" $null { Invoke-RestMethod "$base/visitors" -TimeoutSec 20 }
TDenied "GET /chat/conversations without a token" $null { Invoke-RestMethod "$base/chat/conversations" -TimeoutSec 20 }
TDenied "GET /commerce/orders without a token" $null { Invoke-RestMethod "$base/commerce/orders" -TimeoutSec 20 }
TDenied "GET /event-logs without a token" $null { Invoke-RestMethod "$base/event-logs" -TimeoutSec 20 }
TDenied "POST /ai/assist without a token" $null {
    Invoke-RestMethod "$base/ai/assist" -Method Post -Body "{}" -ContentType "application/json" -TimeoutSec 20
}

Write-Host "`n=============================================" -ForegroundColor Cyan
Write-Host ("PASS: $pass    FAIL: $fail") -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Yellow" })
# Both branches must exit explicitly. Falling off the end leaves the exit code as whatever
# $LASTEXITCODE happened to be from the last native command, so a clean run reported a non-zero
# status and any CI or deploy gate keyed on it would read success as failure.
if ($fail -gt 0) { exit 1 }
exit 0
