# Seeds one demo organization with enough real data that every console screen has something in
# it. Written to be re-runnable: each run makes a fresh organization with a fresh login, so a
# broken run never leaves you half-seeded.
#
#   .\seed-demo.ps1                          # defaults below
#   .\seed-demo.ps1 -Email me@example.com -Password 'My!Pass123'
#
# Requires the backend on $BaseUrl. Inbound demo mail needs MAIL_LMTP_TOKEN set on the backend
# and passed in as -LmtpToken; without it the inbox seeding is skipped rather than failing.

param(
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [string]$Email = "demo.owner@prabhixtechnologies.com",
    [string]$Password = "Prabhix!Demo123",
    [string]$FullName = "Demo Owner",
    [string]$OrgName = "Prabhix Technologies",
    [string]$LmtpToken = $env:MAIL_LMTP_TOKEN
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd('/')
$steps = 0
$skips = @()

function Step($label, $block) {
    try {
        $r = & $block
        $script:steps++
        Write-Host "  ok    $label" -ForegroundColor Green
        return $r
    } catch {
        $detail = $_.Exception.Message
        # The API's own message is far more useful than "422 Unprocessable Entity".
        if ($_.Exception.Response) {
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $body = $reader.ReadToEnd()
                if ($body) { $detail = $body }
            } catch { }
        }
        Write-Host "  FAIL  $label" -ForegroundColor Red
        Write-Host "        $detail" -ForegroundColor DarkGray
        throw
    }
}

function Skip($label, $why) {
    $script:skips += "$label - $why"
    Write-Host "  skip  $label" -ForegroundColor Yellow
    Write-Host "        $why" -ForegroundColor DarkGray
}

# Creating something that a previous run already created is the expected case on a re-run, not a
# failure. Returns $null so callers can tell "made it" from "it was already there".
function StepNew($label, $block) {
    try {
        $r = & $block
        $script:steps++
        Write-Host "  ok    $label" -ForegroundColor Green
        return $r
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        if ($status -eq 409) {
            Write-Host "  have  $label" -ForegroundColor DarkGray
            return $null
        }
        Write-Host "  FAIL  $label" -ForegroundColor Red
        if ($_.Exception.Response) {
            try {
                $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                Write-Host ("        " + $reader.ReadToEnd()) -ForegroundColor DarkGray
            } catch { }
        }
        throw
    }
}

Write-Host "`n=== Creating the organization ===" -ForegroundColor Cyan

# Re-running against an already-seeded account should top it up, not fail. Registration is tried
# first and a 409 falls back to signing in with the same credentials.
$acct = Step "sign in as $Email" {
    try {
        $body = @{
            email = $Email; password = $Password
            fullName = $FullName; organizationName = $OrgName
        } | ConvertTo-Json
        Invoke-RestMethod "$base/auth/register" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 30
    } catch {
        $conflict = $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 409
        if (-not $conflict) { throw }
        Write-Host "        account exists, signing in instead" -ForegroundColor DarkGray
        $body = @{ email = $Email; password = $Password } | ConvertTo-Json
        Invoke-RestMethod "$base/auth/login" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 30
    }
}

$H = @{ Authorization = "Bearer " + $acct.accessToken }
$orgId = $acct.organizationId
$me = Step "resolve identity" { Invoke-RestMethod "$base/auth/me" -Headers $H -TimeoutSec 20 }
$org = Step "resolve organization slug" { Invoke-RestMethod "$base/organizations/$orgId" -Headers $H -TimeoutSec 20 }
$slug = $org.slug

Write-Host "`n=== Mail: mailbox, tags, canned replies ===" -ForegroundColor Cyan

# Fetch before create for anything the seed needs an id for later. Creating first and recovering
# from the error does not work here: the trial plan caps mailboxes at one, so a second attempt
# fails the entitlement check with 422 rather than a duplicate 409.
$mailAddress = "support@$slug.prabhixtechnologies.com"
$mailbox = @((Invoke-RestMethod "$base/mail/mailboxes" -Headers $H -TimeoutSec 20)) |
    Where-Object { $_.address -eq $mailAddress } | Select-Object -First 1
if ($mailbox) {
    Write-Host "  have  shared mailbox $mailAddress" -ForegroundColor DarkGray
} else {
    $mailbox = Step "shared mailbox $mailAddress" {
        $p = @{ address = $mailAddress; name = "Support"
                kind = "SHARED"; description = "Customer support queue" } | ConvertTo-Json
        Invoke-RestMethod "$base/mail/mailboxes" -Method Post -Body $p -Headers $H -ContentType "application/json" -TimeoutSec 20
    }
}

$existingTags = @((Invoke-RestMethod "$base/mail/tags" -Headers $H -TimeoutSec 20))
$tags = @{}
foreach ($t in @(
    @{ name = "Urgent";   color = "#ef4444" },
    @{ name = "Billing";  color = "#f59e0b" },
    @{ name = "Sales";    color = "#10b981" },
    @{ name = "Bug";      color = "#8b5cf6" }
)) {
    $have = $existingTags | Where-Object { $_.name -eq $t.name } | Select-Object -First 1
    if ($have) {
        Write-Host "  have  tag $($t.name)" -ForegroundColor DarkGray
        $tags[$t.name] = $have
    } else {
        $tags[$t.name] = Step "tag $($t.name)" {
            $p = $t | ConvertTo-Json
            Invoke-RestMethod "$base/mail/tags" -Method Post -Body $p -Headers $H -ContentType "application/json" -TimeoutSec 20
        }
    }
}

# Mail canned replies are rich text, so they take bodyHtml plus a plain-text fallback for
# clients that will not render HTML. Chat canned replies are plain text and take body.
foreach ($c in @(
    @{ shortcut = "/thanks";  title = "Thanks for reaching out"
       text = "Thanks for getting in touch. I have picked this up and will come back to you within one business day." },
    @{ shortcut = "/pricing"; title = "Pricing overview"
       text = "Our plans start at INR 999 per month for Starter. Annual billing saves 20%. Happy to walk you through which tier fits." },
    @{ shortcut = "/onboard"; title = "Onboarding next steps"
       text = "Great to have you aboard. Two things to get you started: confirm your sending domain, then invite your team from Settings." }
)) {
    StepNew "canned reply $($c.shortcut)" {
        $p = @{ shortcut = $c.shortcut; title = $c.title
                bodyHtml = "<p>$($c.text)</p>"; bodyText = $c.text } | ConvertTo-Json
        Invoke-RestMethod "$base/mail/canned-replies" -Method Post -Body $p -Headers $H -ContentType "application/json" -TimeoutSec 20
    } | Out-Null
}

Write-Host "`n=== Mail: inbound threads ===" -ForegroundColor Cyan

if (-not $LmtpToken) {
    Skip "inbound demo mail" "MAIL_LMTP_TOKEN not set. Restart the backend with it and pass -LmtpToken to seed the inbox."
} else {
    $inbound = @(
        @{ from = "ramesh.kumar@acmeretail.in"; name = "Ramesh Kumar"
           subject = "Quote for 40 seats"
           body = "Hello,`r`n`r`nWe are a retail chain with 40 staff across 6 stores and are evaluating Prabhix for our support inbox. Could you share pricing for 40 seats, and whether onboarding help is included?`r`n`r`nRegards,`r`nRamesh Kumar`r`nAcme Retail" },
        @{ from = "priya@fintechlabs.io"; name = "Priya Nair"
           subject = "Invoice GST details incorrect"
           body = "Hi team,`r`n`r`nOur last invoice shows the wrong GSTIN, so our finance team cannot claim input credit. Can you reissue it against 29ABCDE1234F1Z5?`r`n`r`nThanks,`r`nPriya" },
        @{ from = "dev@buildhouse.co"; name = "Sandeep Rao"
           subject = "API returns 401 after token refresh"
           body = "Hi,`r`n`r`nSince yesterday our integration gets a 401 immediately after refreshing the access token. Nothing changed on our side. Correlation id from the response header was 8f2c-441a.`r`n`r`nSandeep" },
        @{ from = "hello@studionorth.design"; name = "Meera Iyer"
           subject = "Partnership enquiry"
           body = "Hello,`r`n`r`nWe are a design studio placing clients on platforms like yours. Do you run a partner or referral programme?`r`n`r`nMeera Iyer`r`nStudio North" },
        @{ from = "accounts@vendorsupply.in"; name = "Vendor Supply"
           subject = "Renewal confirmation needed"
           body = "Please confirm the renewal date for our annual subscription so we can raise the purchase order in time.`r`n`r`nAccounts, Vendor Supply" }
    )

    $i = 0
    foreach ($m in $inbound) {
        $i++
        Step "inbound: $($m.subject)" {
            $mid = "<demo-$i-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())@prabhixtest.in>"
            $date = (Get-Date).ToUniversalTime().ToString("ddd, dd MMM yyyy HH:mm:ss +0000")
            $mime = "From: $($m.name) <$($m.from)>`r`n" +
                    "To: $($mailbox.address)`r`n" +
                    "Subject: $($m.subject)`r`n" +
                    "Message-ID: $mid`r`n" +
                    "Date: $date`r`n" +
                    "MIME-Version: 1.0`r`n" +
                    "Content-Type: text/plain; charset=UTF-8`r`n`r`n" +
                    $m.body + "`r`n"
            $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($mime))
            $p = @{ recipient = $mailbox.address; rawMimeBase64 = $b64 } | ConvertTo-Json
            Invoke-RestMethod "$base/mail/inbound/lmtp" -Method Post -Body $p `
                -Headers @{ "X-Mail-Token" = $LmtpToken } -ContentType "application/json" -TimeoutSec 20
        } | Out-Null
    }

    # LMTP only stages the message; a scheduled worker turns it into a thread on a 60s poll. Wait
    # for the queue to actually drain rather than guessing a sleep, or the tagging below runs
    # against an inbox that is still mostly empty.
    $want = $inbound.Count
    $threads = $null
    $waited = 0
    while ($waited -lt 150) {
        $threads = Invoke-RestMethod "$base/mail/threads" -Headers $H -TimeoutSec 20
        if (@($threads.items).Count -ge $want) { break }
        Start-Sleep -Seconds 10
        $waited += 10
        Write-Host ("        waiting for ingestion: " + @($threads.items).Count + "/$want after ${waited}s") -ForegroundColor DarkGray
    }
    Write-Host ("        threads now in the inbox: " + @($threads.items).Count) -ForegroundColor DarkGray

    $tagPlan = @{ "Quote for 40 seats" = "Sales"; "Invoice GST details incorrect" = "Billing"
                  "API returns 401 after token refresh" = "Bug"; "Renewal confirmation needed" = "Billing" }
    foreach ($th in @($threads.items)) {
        $want = $tagPlan[$th.subject]
        if ($want -and $tags[$want]) {
            try {
                $p = @{ tagId = $tags[$want].id } | ConvertTo-Json
                Invoke-RestMethod "$base/mail/threads/$($th.id)/tags" -Method Post -Body $p `
                    -Headers $H -ContentType "application/json" -TimeoutSec 20 | Out-Null
                Write-Host "  ok    tag '$want' on '$($th.subject)'" -ForegroundColor Green
                $steps++
            } catch {
                Write-Host "  warn  could not tag '$($th.subject)'" -ForegroundColor Yellow
            }
        }
    }
}

Write-Host "`n=== Commerce: product catalog ===" -ForegroundColor Cyan

# Slugs are stable, not stamped with the run time. A slug is the storefront URL and the natural
# key, so stamping it made every re-run create a parallel set of products: four runs, four
# "Prabhix Starter Toolkit" entries competing on /shop. Stable slugs mean a re-run collides on the
# first product and StepNew skips it.
$catalog = @(
    @{ slug = "starter-toolkit"; name = "Prabhix Starter Toolkit"; productType = "DIGITAL"
       tagline = "Templates, scripts and checklists to bootstrap a project"
       description = "A downloadable bundle of project scaffolding: CI templates, a threat-model checklist, and runbook skeletons used on our own delivery work."
       hsnCode = "998434"
       variants = @(
           @{ name = "Single licence"; priceMinor = 149900; trackInventory = $false; billingInterval = "ONE_TIME" },
           @{ name = "Team licence (10 seats)"; priceMinor = 999900; trackInventory = $false; billingInterval = "ONE_TIME" }
       ) },
    @{ slug = "mobistack-retail-pos"; name = "MobiStack Retail POS"; productType = "SUBSCRIPTION"
       tagline = "Point of sale and inventory for multi-location retail"
       description = "The vertical product Prabhix grew out of. Billing, inventory, and staff management for retail chains, priced per store."
       hsnCode = "998314"
       variants = @(
           @{ name = "Single store, monthly"; priceMinor = 99900; trackInventory = $false; billingInterval = "MONTHLY" },
           @{ name = "Single store, annual"; priceMinor = 959000; compareAtPriceMinor = 1198800; trackInventory = $false; billingInterval = "ANNUAL" }
       ) },
    @{ slug = "cloud-readiness-audit"; name = "Cloud Readiness Audit"; productType = "SERVICE"
       tagline = "A two-week review of your infrastructure and delivery pipeline"
       description = "We review your cloud footprint, CI/CD, and observability, then hand over a prioritised remediation plan with effort estimates."
       hsnCode = "998313"
       variants = @(
           @{ name = "Standard engagement"; priceMinor = 7500000; trackInventory = $false; serviceDurationDays = 14; deliverySlaDays = 3; billingInterval = "ONE_TIME" }
       ) },
    @{ slug = "prabhix-field-tablet"; name = "Prabhix Field Tablet"; productType = "PHYSICAL"
       tagline = "Pre-configured rugged tablet for field teams"
       description = "A rugged Android tablet shipped pre-loaded with the MobiStack field app and enrolled in your organization."
       hsnCode = "847130"
       variants = @(
           @{ name = "8 inch, 64GB"; priceMinor = 2899900; trackInventory = $true; stockOnHand = 25; billingInterval = "ONE_TIME" },
           @{ name = "10 inch, 128GB"; priceMinor = 3899900; trackInventory = $true; stockOnHand = 12; billingInterval = "ONE_TIME" }
       ) }
)

foreach ($p in $catalog) {
    $variants = $p.variants
    $product = StepNew "product $($p.name)" {
        $body = @{ slug = $p.slug; name = $p.name; productType = $p.productType; status = "ACTIVE"
                   tagline = $p.tagline; description = $p.description; hsnCode = $p.hsnCode } | ConvertTo-Json
        Invoke-RestMethod "$base/commerce/products" -Method Post -Body $body -Headers $H -ContentType "application/json" -TimeoutSec 20
    }
    # On a re-run StepNew returns null for a product that already exists, and its variants exist
    # too, so skipping is correct rather than a lost step.
    if (-not $product) { continue }
    $n = 0
    foreach ($v in $variants) {
        $n++
        StepNew "  variant $($v.name)" {
            $body = ($v + @{ sku = ($p.slug.Substring(0, [Math]::Min(12, $p.slug.Length)) + "-$n").ToUpper() }) | ConvertTo-Json
            Invoke-RestMethod "$base/commerce/products/$($product.id)/variants" -Method Post -Body $body -Headers $H -ContentType "application/json" -TimeoutSec 20
        } | Out-Null
    }
}

StepNew "discount code LAUNCH20" {
    $p = @{ code = "LAUNCH20"; description = "Launch discount, 20% off"
            discountType = "PERCENTAGE"; percentage = 20; maxUsesTotal = 500 } | ConvertTo-Json
    Invoke-RestMethod "$base/commerce/discounts" -Method Post -Body $p -Headers $H -ContentType "application/json" -TimeoutSec 20
} | Out-Null

Write-Host "`n=== Visitors on the site ===" -ForegroundColor Cyan

$journeys = @(
    @{ name = "mobile from Google Ads"; device = "MOBILE"; browser = "Chrome"; os = "Android"; w = 390; h = 844
       utm = @{ utm_source = "google"; utm_medium = "cpc"; utm_campaign = "launch" }
       pages = @(@{ path = "/"; title = "Home" }, @{ path = "/products"; title = "Products" }, @{ path = "/pricing"; title = "Pricing" })
       event = "pricing_table_view" },
    @{ name = "desktop from LinkedIn"; device = "DESKTOP"; browser = "Edge"; os = "Windows"; w = 1920; h = 1080
       utm = @{ utm_source = "linkedin"; utm_medium = "social"; utm_campaign = "founder-post" }
       pages = @(@{ path = "/"; title = "Home" }, @{ path = "/about"; title = "About" }, @{ path = "/careers"; title = "Careers" })
       event = "job_listing_open" },
    @{ name = "tablet, direct"; device = "TABLET"; browser = "Safari"; os = "iPadOS"; w = 1024; h = 1366
       utm = $null
       pages = @(@{ path = "/products"; title = "Products" }, @{ path = "/products/mobistack"; title = "MobiStack" })
       event = "add_to_cart_click" },
    @{ name = "desktop, organic search"; device = "DESKTOP"; browser = "Firefox"; os = "macOS"; w = 1512; h = 982
       utm = @{ utm_source = "bing"; utm_medium = "organic" }
       pages = @(@{ path = "/blog"; title = "Blog" }, @{ path = "/contact"; title = "Contact" })
       event = "contact_form_start" }
)

$visitors = @()
foreach ($j in $journeys) {
    $v = Step "visitor: $($j.name)" {
        $first = $j.pages[0]
        $body = @{
            consent = "FULL"
            pageViews = @(@{ url = "https://prabhixtechnologies.com$($first.path)"; path = $first.path; title = $first.title; entry = $true })
            events = @(@{ name = $j.event; properties = @{ source = "seed" } })
            session = @{ deviceType = $j.device; browser = $j.browser; os = $j.os
                         screenWidth = $j.w; screenHeight = $j.h; language = "en-IN"; timezone = "Asia/Kolkata" }
            presence = @{ url = "https://prabhixtechnologies.com$($first.path)"; path = $first.path; title = $first.title }
        }
        if ($j.utm) { $body.utm = $j.utm }
        Invoke-RestMethod "$base/visitor/public/$slug/ingest" -Method Post -Body ($body | ConvertTo-Json -Depth 6) `
            -ContentType "application/json" -TimeoutSec 20
    }
    # Walk the rest of the journey so the visitor timeline has depth, ending on the last page
    # so live presence shows somewhere plausible.
    foreach ($page in $j.pages[1..($j.pages.Count - 1)]) {
        $body = @{ consent = "FULL"; visitorKey = $v.visitorKey; sessionId = $v.sessionId
                   pageViews = @(@{ url = "https://prabhixtechnologies.com$($page.path)"; path = $page.path; title = $page.title })
                   presence = @{ url = "https://prabhixtechnologies.com$($page.path)"; path = $page.path; title = $page.title } }
        Invoke-RestMethod "$base/visitor/public/$slug/ingest" -Method Post -Body ($body | ConvertTo-Json -Depth 6) `
            -ContentType "application/json" -TimeoutSec 20 | Out-Null
    }
    $visitors += @{ key = $v.visitorKey; journey = $j }
}

Write-Host "`n=== Live chat conversations ===" -ForegroundColor Cyan

$chats = @(
    @{ visitor = 0; name = "Ramesh Kumar"; email = "ramesh.kumar@acmeretail.in"; subject = "Pricing question"
       visitorMsgs = @("Hi, is the Growth plan billed annually?", "And does it include onboarding help?")
       agentMsgs = @("Hello Ramesh - yes, Growth can be billed annually and that saves 20%.", "Onboarding is included on Growth and above. I can set up a call this week if useful.")
       note = "40 seats, retail chain. Strong upsell candidate - loop in sales." },
    @{ visitor = 1; name = "Meera Iyer"; email = "meera@studionorth.design"; subject = "Partner programme"
       visitorMsgs = @("Do you have a referral programme for agencies?")
       agentMsgs = @("We do - 15% recurring for the first year. I will email you the agreement.")
       note = $null },
    @{ visitor = 2; name = "Anonymous visitor"; email = $null; subject = "Quick question"
       visitorMsgs = @("Does MobiStack work offline?")
       agentMsgs = @()
       note = $null }
)

foreach ($c in $chats) {
    $vkey = $visitors[$c.visitor].key
    $conv = Step "chat: $($c.subject)" {
        $body = @{ visitorKey = $vkey; subject = $c.subject }
        if ($c.name -ne "Anonymous visitor") { $body.name = $c.name }
        if ($c.email) { $body.email = $c.email }
        Invoke-RestMethod "$base/chat/public/$slug/conversations" -Method Post -Body ($body | ConvertTo-Json) `
            -ContentType "application/json" -TimeoutSec 20
    }
    $CT = @{ "X-Chat-Token" = $conv.conversationToken }
    foreach ($m in $c.visitorMsgs) {
        Invoke-RestMethod "$base/chat/public/$slug/conversations/$($conv.conversationId)/messages" -Method Post `
            -Body (@{ body = $m } | ConvertTo-Json) -Headers $CT -ContentType "application/json" -TimeoutSec 20 | Out-Null
    }
    foreach ($m in $c.agentMsgs) {
        Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)/messages" -Method Post `
            -Body (@{ body = $m } | ConvertTo-Json) -Headers $H -ContentType "application/json" -TimeoutSec 20 | Out-Null
    }
    if ($c.note) {
        Invoke-RestMethod "$base/chat/conversations/$($conv.conversationId)/messages" -Method Post `
            -Body (@{ body = $c.note; internal = $true } | ConvertTo-Json) -Headers $H -ContentType "application/json" -TimeoutSec 20 | Out-Null
    }
}

foreach ($c in @(
    @{ shortcut = "/hi";      title = "Greeting";       body = "Hi! Thanks for stopping by. How can I help?" },
    @{ shortcut = "/callback"; title = "Offer a call";   body = "Happy to jump on a quick call - what time suits you today or tomorrow?" }
)) {
    StepNew "chat canned reply $($c.shortcut)" {
        Invoke-RestMethod "$base/chat/canned-replies" -Method Post -Body ($c | ConvertTo-Json) `
            -Headers $H -ContentType "application/json" -TimeoutSec 20
    } | Out-Null
}

Write-Host "`n=== Team ===" -ForegroundColor Cyan

StepNew "team Support" {
    $p = @{ name = "Support"; description = "Front line customer support" } | ConvertTo-Json
    Invoke-RestMethod "$base/teams" -Method Post -Body $p -Headers $H -ContentType "application/json" -TimeoutSec 20
} | Out-Null

$roles = @((Invoke-RestMethod "$base/roles" -Headers $H -TimeoutSec 20))
$agentRole = $roles | Where-Object { $_.key -eq "AGENT" -or $_.name -eq "Agent" } | Select-Object -First 1
if ($agentRole) {
    StepNew "invite demo.agent@prabhixtechnologies.com as Agent" {
        $p = @{ email = "demo.agent@prabhixtechnologies.com"; roleId = $agentRole.id } | ConvertTo-Json
        Invoke-RestMethod "$base/invites" -Method Post -Body $p -Headers $H -ContentType "application/json" -TimeoutSec 20
    } | Out-Null
} else {
    Skip "team invite" "no Agent role found to attach the invite to"
}

Step "billing address" {
    $p = @{ line1 = "1st Floor, Tech Park"; line2 = "Whitefield"; city = "Bengaluru"
            state = "Karnataka"; pincode = "560066"; country = "IN"
            gstin = "29ABCDE1234F1Z5"; billingEmail = $Email } | ConvertTo-Json
    Invoke-RestMethod "$base/billing/address" -Method Put -Body $p -Headers $H -ContentType "application/json" -TimeoutSec 20
} | Out-Null

Write-Host "`n=============================================" -ForegroundColor Cyan
Write-Host " Demo organization ready - $steps objects created" -ForegroundColor Green
if ($skips.Count) {
    Write-Host "`n Skipped:" -ForegroundColor Yellow
    $skips | ForEach-Object { Write-Host "   - $_" -ForegroundColor Yellow }
}
Write-Host @"

 Sign in at the console
   URL       http://localhost:5173/login
   Email     $Email
   Password  $Password

 Organization
   Name      $OrgName
   Slug      $slug
   Org id    $orgId
   Mailbox   $($mailbox.address)

 Storefront (public, no login)
   http://localhost:3002/products

 Outgoing mail lands in Mailpit, not a real inbox
   http://localhost:8025

"@ -ForegroundColor White
