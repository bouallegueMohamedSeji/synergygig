##############################################################################
# SynergyGig — Create & Activate 5 n8n Workflows
# Run once to deploy all missing business-logic workflows
##############################################################################

$n8nBase    = "https://n8n.benzaitsue.work.gd/api/v1"
$apiKey     = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI5ZGE0MTcyZS1lYzk2LTQ4NzgtOTc5YS1hNTNkZGNiZWZmZTUiLCJpc3MiOiJuOG4iLCJhdWQiOiJwdWJsaWMtYXBpIiwianRpIjoiYTg5YzQ5ZTktZmY3Mi00NmJkLThkZDctNWExNGY1YTkwMDMxIiwiaWF0IjoxNzcxOTIwMTE0LCJleHAiOjE3NzQ0OTc2MDB9.hRhEZkVoisVZKzWo-2e3IdXO9I33iFde9efVsYDmqZE"
$apiInternal = "http://172.21.0.3:8000/api"
$headers    = @{ "X-N8N-API-KEY" = $apiKey; "Content-Type" = "application/json"; "Accept" = "application/json" }
$tagId      = "D5syfGcEFDef7ZXq"   # existing "synergygig" tag

function New-Workflow($json) {
    try {
        $r = Invoke-RestMethod -Uri "$n8nBase/workflows" -Method POST -Headers $headers -Body $json
        $id = $r.id
        Write-Host "  Created: $($r.name)  [id=$id]" -ForegroundColor Green
        # Activate
        Invoke-RestMethod -Uri "$n8nBase/workflows/$id/activate" -Method POST -Headers $headers | Out-Null
        Write-Host "  Activated." -ForegroundColor Cyan
        return $id
    } catch {
        Write-Host "  ERROR: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

# ═══════════════════════════════════════════════════════════════════════
# 1. Training Enrollment Notification
# ═══════════════════════════════════════════════════════════════════════
Write-Host "`n[1/5] Training Enrollment Notification" -ForegroundColor Yellow

$wf1 = @'
{
  "name": "\ud83d\udce7 Training Enrollment Notification",
  "nodes": [
    {
      "parameters": {
        "httpMethod": "POST",
        "path": "training-enroll",
        "responseMode": "responseNode",
        "options": {}
      },
      "id": "wh-train-enroll",
      "name": "Webhook",
      "type": "n8n-nodes-base.webhook",
      "typeVersion": 2,
      "position": [220, 300],
      "webhookId": "train-enroll-hook"
    },
    {
      "parameters": {
        "jsCode": "const body = $input.first().json.body || $input.first().json;\nconst userId = body.user_id || 0;\nconst userName = body.user_name || 'Unknown';\nconst courseTitle = body.course_title || 'Unknown Course';\nconst enrolledAt = body.enrolled_at || new Date().toISOString();\n\nconst notifBody = `You have successfully enrolled in: ${courseTitle}`;\nconst postContent = `\ud83d\udce7 **Training Enrollment**\\n\\n\ud83d\udc64 **${userName}** has enrolled in:\\n\ud83d\udcda **${courseTitle}**\\n\\n\ud83d\udd50 ${enrolledAt}\\n\\n_Automated by SynergyGig n8n_`;\n\nreturn [{ json: { userId, userName, courseTitle, notifBody, postContent } }];"
      },
      "id": "code-format-enroll",
      "name": "Format Data",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [440, 300]
    },
    {
      "parameters": {
        "method": "POST",
        "url": "API_BASE/notifications",
        "sendBody": true,
        "specifyBody": "json",
        "jsonBody": "={{ JSON.stringify({ user_id: $json.userId, type: 'TRAINING', title: 'Enrollment Confirmed', body: $json.notifBody }) }}"
      },
      "id": "http-notif-enroll",
      "name": "Create Notification",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [660, 300]
    },
    {
      "parameters": {
        "method": "POST",
        "url": "API_BASE/posts",
        "sendBody": true,
        "specifyBody": "json",
        "jsonBody": "={{ JSON.stringify({ author_id: 1, content: $('Format Data').first().json.postContent }) }}"
      },
      "id": "http-post-enroll",
      "name": "Post to Community",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [660, 480]
    },
    {
      "parameters": {
        "respondWith": "json",
        "responseBody": "={{ JSON.stringify({ status: 'ok', message: 'Enrollment notification processed' }) }}"
      },
      "id": "respond-enroll",
      "name": "Respond OK",
      "type": "n8n-nodes-base.respondToWebhook",
      "typeVersion": 1.1,
      "position": [880, 300]
    }
  ],
  "connections": {
    "Webhook": { "main": [[{ "node": "Format Data", "type": "main", "index": 0 }]] },
    "Format Data": { "main": [[{ "node": "Create Notification", "type": "main", "index": 0 }, { "node": "Post to Community", "type": "main", "index": 0 }]] },
    "Create Notification": { "main": [[{ "node": "Respond OK", "type": "main", "index": 0 }]] }
  },
  "settings": { "executionOrder": "v1" }
}
'@
$wf1 = $wf1.Replace("API_BASE", $apiInternal)
New-Workflow $wf1

# ═══════════════════════════════════════════════════════════════════════
# 2. Training Completion Celebration
# ═══════════════════════════════════════════════════════════════════════
Write-Host "`n[2/5] Training Completion Celebration" -ForegroundColor Yellow

$wf2 = @'
{
  "name": "\ud83c\udf93 Training Completion Celebration",
  "nodes": [
    {
      "parameters": {
        "httpMethod": "POST",
        "path": "training-complete",
        "responseMode": "responseNode",
        "options": {}
      },
      "id": "wh-train-complete",
      "name": "Webhook",
      "type": "n8n-nodes-base.webhook",
      "typeVersion": 2,
      "position": [220, 300],
      "webhookId": "train-complete-hook"
    },
    {
      "parameters": {
        "jsCode": "const body = $input.first().json.body || $input.first().json;\nconst userId = body.user_id || 0;\nconst userName = body.user_name || 'Unknown';\nconst courseTitle = body.course_title || 'Unknown Course';\nconst completedAt = body.completed_at || new Date().toISOString();\n\nconst notifBody = `Congratulations! You have completed: ${courseTitle}`;\nconst postContent = `\ud83c\udf93 **Training Completed!** \ud83c\udf89\\n\\n\ud83c\udfc6 **${userName}** has successfully completed:\\n\ud83d\udcda **${courseTitle}**\\n\\n\u2b50 A certificate has been generated!\\n\ud83d\udd50 ${completedAt}\\n\\n_Automated by SynergyGig n8n_`;\n\nreturn [{ json: { userId, userName, courseTitle, notifBody, postContent } }];"
      },
      "id": "code-format-complete",
      "name": "Format Data",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [440, 300]
    },
    {
      "parameters": {
        "method": "POST",
        "url": "API_BASE/notifications",
        "sendBody": true,
        "specifyBody": "json",
        "jsonBody": "={{ JSON.stringify({ user_id: $json.userId, type: 'TRAINING', title: 'Course Completed! \ud83c\udf89', body: $json.notifBody }) }}"
      },
      "id": "http-notif-complete",
      "name": "Create Notification",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [660, 300]
    },
    {
      "parameters": {
        "method": "POST",
        "url": "API_BASE/posts",
        "sendBody": true,
        "specifyBody": "json",
        "jsonBody": "={{ JSON.stringify({ author_id: 1, content: $('Format Data').first().json.postContent }) }}"
      },
      "id": "http-post-complete",
      "name": "Post Congratulations",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [660, 480]
    },
    {
      "parameters": {
        "respondWith": "json",
        "responseBody": "={{ JSON.stringify({ status: 'ok', message: 'Completion notification processed' }) }}"
      },
      "id": "respond-complete",
      "name": "Respond OK",
      "type": "n8n-nodes-base.respondToWebhook",
      "typeVersion": 1.1,
      "position": [880, 300]
    }
  ],
  "connections": {
    "Webhook": { "main": [[{ "node": "Format Data", "type": "main", "index": 0 }]] },
    "Format Data": { "main": [[{ "node": "Create Notification", "type": "main", "index": 0 }, { "node": "Post Congratulations", "type": "main", "index": 0 }]] },
    "Create Notification": { "main": [[{ "node": "Respond OK", "type": "main", "index": 0 }]] }
  },
  "settings": { "executionOrder": "v1" }
}
'@
$wf2 = $wf2.Replace("API_BASE", $apiInternal)
New-Workflow $wf2

# ═══════════════════════════════════════════════════════════════════════
# 3. Daily HR Summary (Cron 8 AM UTC)
# ═══════════════════════════════════════════════════════════════════════
Write-Host "`n[3/5] Daily HR Summary" -ForegroundColor Yellow

$wf3 = @'
{
  "name": "\ud83d\udcca Daily HR Summary",
  "nodes": [
    {
      "parameters": {
        "rule": {
          "interval": [{ "field": "cronExpression", "expression": "0 8 * * *" }]
        }
      },
      "id": "cron-hr-summary",
      "name": "Every Day 8 AM",
      "type": "n8n-nodes-base.scheduleTrigger",
      "typeVersion": 1.2,
      "position": [220, 300]
    },
    {
      "parameters": {
        "method": "GET",
        "url": "API_BASE/attendance",
        "options": { "timeout": 10000 }
      },
      "id": "http-attendance",
      "name": "Fetch Attendance",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [440, 200]
    },
    {
      "parameters": {
        "method": "GET",
        "url": "API_BASE/leaves",
        "options": { "timeout": 10000 }
      },
      "id": "http-leaves",
      "name": "Fetch Leaves",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [440, 400]
    },
    {
      "parameters": {
        "method": "GET",
        "url": "API_BASE/payroll",
        "options": { "timeout": 10000 }
      },
      "id": "http-payroll",
      "name": "Fetch Payroll",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [440, 600]
    },
    {
      "parameters": {
        "jsCode": "const attendance = $('Fetch Attendance').first().json;\nconst leaves = $('Fetch Leaves').first().json;\nconst payroll = $('Fetch Payroll').first().json;\n\nconst attArr = Array.isArray(attendance) ? attendance : [];\nconst leaveArr = Array.isArray(leaves) ? leaves : [];\nconst payArr = Array.isArray(payroll) ? payroll : [];\n\nconst today = new Date().toISOString().split('T')[0];\nconst todayAtt = attArr.filter(a => a.date === today);\nconst presentToday = todayAtt.filter(a => a.status === 'PRESENT').length;\nconst lateToday = todayAtt.filter(a => a.status === 'LATE').length;\nconst absentToday = todayAtt.filter(a => a.status === 'ABSENT').length;\nconst pendingLeaves = leaveArr.filter(l => l.status === 'PENDING').length;\nconst approvedLeaves = leaveArr.filter(l => l.status === 'APPROVED').length;\nconst pendingPayroll = payArr.filter(p => p.status === 'PENDING').length;\nconst paidPayroll = payArr.filter(p => p.status === 'PAID').length;\n\nconst content = `\ud83d\udcca **Daily HR Summary** — ${today}\\n\\n` +\n  `\ud83d\udcdd **Attendance Today**\\n` +\n  `  \u2705 Present: ${presentToday}\\n` +\n  `  \u23f0 Late: ${lateToday}\\n` +\n  `  \u274c Absent: ${absentToday}\\n\\n` +\n  `\ud83c\udfd6\ufe0f **Leave Requests**\\n` +\n  `  \u23f3 Pending: ${pendingLeaves}\\n` +\n  `  \u2705 Approved (all-time): ${approvedLeaves}\\n\\n` +\n  `\ud83d\udcb0 **Payroll**\\n` +\n  `  \u23f3 Pending: ${pendingPayroll}\\n` +\n  `  \u2705 Paid: ${paidPayroll}\\n\\n` +\n  `\ud83e\udd16 _Generated automatically by SynergyGig n8n_`;\n\nreturn [{ json: { content } }];"
      },
      "id": "code-hr-summary",
      "name": "Build Summary",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [700, 400]
    },
    {
      "parameters": {
        "method": "POST",
        "url": "API_BASE/posts",
        "sendBody": true,
        "specifyBody": "json",
        "jsonBody": "={{ JSON.stringify({ author_id: 1, content: $json.content }) }}"
      },
      "id": "http-post-summary",
      "name": "Post HR Summary",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [920, 400]
    }
  ],
  "connections": {
    "Every Day 8 AM": { "main": [[{ "node": "Fetch Attendance", "type": "main", "index": 0 }, { "node": "Fetch Leaves", "type": "main", "index": 0 }, { "node": "Fetch Payroll", "type": "main", "index": 0 }]] },
    "Fetch Attendance": { "main": [[{ "node": "Build Summary", "type": "main", "index": 0 }]] },
    "Fetch Leaves": { "main": [[{ "node": "Build Summary", "type": "main", "index": 0 }]] },
    "Fetch Payroll": { "main": [[{ "node": "Build Summary", "type": "main", "index": 0 }]] },
    "Build Summary": { "main": [[{ "node": "Post HR Summary", "type": "main", "index": 0 }]] }
  },
  "settings": { "executionOrder": "v1" }
}
'@
$wf3 = $wf3.Replace("API_BASE", $apiInternal)
New-Workflow $wf3

# ═══════════════════════════════════════════════════════════════════════
# 4. Contract Expiry Alert (Cron 9 AM UTC)
# ═══════════════════════════════════════════════════════════════════════
Write-Host "`n[4/5] Contract Expiry Alert" -ForegroundColor Yellow

$wf4 = @'
{
  "name": "\u23f0 Contract Expiry Alert",
  "nodes": [
    {
      "parameters": {
        "rule": {
          "interval": [{ "field": "cronExpression", "expression": "0 9 * * *" }]
        }
      },
      "id": "cron-contract-expiry",
      "name": "Every Day 9 AM",
      "type": "n8n-nodes-base.scheduleTrigger",
      "typeVersion": 1.2,
      "position": [220, 300]
    },
    {
      "parameters": {
        "method": "GET",
        "url": "API_BASE/contracts",
        "options": { "timeout": 10000 }
      },
      "id": "http-contracts",
      "name": "Fetch Contracts",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [440, 300]
    },
    {
      "parameters": {
        "jsCode": "const contracts = $input.first().json;\nconst arr = Array.isArray(contracts) ? contracts : [];\nconst now = new Date();\nconst sevenDays = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);\n\nconst expiring = arr.filter(c => {\n  if (c.status !== 'ACTIVE' || !c.end_date) return false;\n  const end = new Date(c.end_date);\n  return end >= now && end <= sevenDays;\n});\n\nif (expiring.length === 0) {\n  return [{ json: { skip: true, notifications: [] } }];\n}\n\nconst notifications = [];\nfor (const c of expiring) {\n  const daysLeft = Math.ceil((new Date(c.end_date) - now) / (1000*60*60*24));\n  // Notify freelancer\n  if (c.freelancer_id) {\n    notifications.push({\n      user_id: c.freelancer_id,\n      type: 'CONTRACT_EXPIRY',\n      title: 'Contract Expiring Soon',\n      body: `Your contract (ID: ${c.id}) expires in ${daysLeft} day(s) on ${c.end_date}`\n    });\n  }\n  // Notify client\n  if (c.client_id) {\n    notifications.push({\n      user_id: c.client_id,\n      type: 'CONTRACT_EXPIRY',\n      title: 'Contract Expiring Soon',\n      body: `Contract (ID: ${c.id}) with freelancer expires in ${daysLeft} day(s) on ${c.end_date}`\n    });\n  }\n}\n\nreturn notifications.map(n => ({ json: n }));"
      },
      "id": "code-filter-expiring",
      "name": "Filter Expiring",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [660, 300]
    },
    {
      "parameters": {
        "conditions": {
          "options": { "caseSensitive": true, "leftValue": "", "typeValidation": "strict" },
          "conditions": [
            { "id": "cond-skip", "leftValue": "={{ $json.skip }}", "rightValue": true, "operator": { "type": "boolean", "operation": "notTrue" } }
          ],
          "combinator": "and"
        }
      },
      "id": "if-has-expiring",
      "name": "Has Expiring?",
      "type": "n8n-nodes-base.if",
      "typeVersion": 2.2,
      "position": [880, 300]
    },
    {
      "parameters": {
        "method": "POST",
        "url": "API_BASE/notifications",
        "sendBody": true,
        "specifyBody": "json",
        "jsonBody": "={{ JSON.stringify({ user_id: $json.user_id, type: $json.type, title: $json.title, body: $json.body }) }}"
      },
      "id": "http-notif-expiry",
      "name": "Send Notifications",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [1100, 200]
    }
  ],
  "connections": {
    "Every Day 9 AM": { "main": [[{ "node": "Fetch Contracts", "type": "main", "index": 0 }]] },
    "Fetch Contracts": { "main": [[{ "node": "Filter Expiring", "type": "main", "index": 0 }]] },
    "Filter Expiring": { "main": [[{ "node": "Has Expiring?", "type": "main", "index": 0 }]] },
    "Has Expiring?": { "main": [[{ "node": "Send Notifications", "type": "main", "index": 0 }], []] }
  },
  "settings": { "executionOrder": "v1" }
}
'@
$wf4 = $wf4.Replace("API_BASE", $apiInternal)
New-Workflow $wf4

# ═══════════════════════════════════════════════════════════════════════
# 5. Project Deadline Alert (Cron 8:30 AM UTC)
# ═══════════════════════════════════════════════════════════════════════
Write-Host "`n[5/5] Project Deadline Alert" -ForegroundColor Yellow

$wf5 = @'
{
  "name": "\ud83d\udcc5 Project Deadline Alert",
  "nodes": [
    {
      "parameters": {
        "rule": {
          "interval": [{ "field": "cronExpression", "expression": "30 8 * * *" }]
        }
      },
      "id": "cron-project-deadline",
      "name": "Every Day 8:30 AM",
      "type": "n8n-nodes-base.scheduleTrigger",
      "typeVersion": 1.2,
      "position": [220, 300]
    },
    {
      "parameters": {
        "method": "GET",
        "url": "API_BASE/projects",
        "options": { "timeout": 10000 }
      },
      "id": "http-projects",
      "name": "Fetch Projects",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [440, 300]
    },
    {
      "parameters": {
        "jsCode": "const projects = $input.first().json;\nconst arr = Array.isArray(projects) ? projects : [];\nconst now = new Date();\nconst threeDays = new Date(now.getTime() + 3 * 24 * 60 * 60 * 1000);\n\nconst approaching = arr.filter(p => {\n  if (!p.deadline || p.status === 'COMPLETED' || p.status === 'CANCELLED') return false;\n  const dl = new Date(p.deadline);\n  return dl >= now && dl <= threeDays;\n});\n\nif (approaching.length === 0) {\n  return [{ json: { skip: true } }];\n}\n\nconst notifications = [];\nfor (const p of approaching) {\n  const daysLeft = Math.ceil((new Date(p.deadline) - now) / (1000*60*60*24));\n  if (p.owner_id || p.manager_id) {\n    const managerId = p.owner_id || p.manager_id;\n    notifications.push({\n      user_id: managerId,\n      type: 'PROJECT_DEADLINE',\n      title: 'Project Deadline Approaching',\n      body: `Project \"${p.name}\" deadline is in ${daysLeft} day(s) (${p.deadline})`\n    });\n  }\n}\n\nreturn notifications.map(n => ({ json: n }));"
      },
      "id": "code-filter-deadlines",
      "name": "Filter Approaching",
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [660, 300]
    },
    {
      "parameters": {
        "conditions": {
          "options": { "caseSensitive": true, "leftValue": "", "typeValidation": "strict" },
          "conditions": [
            { "id": "cond-skip2", "leftValue": "={{ $json.skip }}", "rightValue": true, "operator": { "type": "boolean", "operation": "notTrue" } }
          ],
          "combinator": "and"
        }
      },
      "id": "if-has-deadline",
      "name": "Has Deadlines?",
      "type": "n8n-nodes-base.if",
      "typeVersion": 2.2,
      "position": [880, 300]
    },
    {
      "parameters": {
        "method": "POST",
        "url": "API_BASE/notifications",
        "sendBody": true,
        "specifyBody": "json",
        "jsonBody": "={{ JSON.stringify({ user_id: $json.user_id, type: $json.type, title: $json.title, body: $json.body }) }}"
      },
      "id": "http-notif-deadline",
      "name": "Send Notifications",
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.2,
      "position": [1100, 200]
    }
  ],
  "connections": {
    "Every Day 8:30 AM": { "main": [[{ "node": "Fetch Projects", "type": "main", "index": 0 }]] },
    "Fetch Projects": { "main": [[{ "node": "Filter Approaching", "type": "main", "index": 0 }]] },
    "Filter Approaching": { "main": [[{ "node": "Has Deadlines?", "type": "main", "index": 0 }]] },
    "Has Deadlines?": { "main": [[{ "node": "Send Notifications", "type": "main", "index": 0 }], []] }
  },
  "settings": { "executionOrder": "v1" }
}
'@
$wf5 = $wf5.Replace("API_BASE", $apiInternal)
New-Workflow $wf5

Write-Host "`n✅ All 5 workflows created and activated!" -ForegroundColor Green

# List final state
Write-Host "`nFinal workflow list:" -ForegroundColor Yellow
$all = Invoke-RestMethod -Uri "$n8nBase/workflows" -Headers $headers
$all.data | ForEach-Object { [PSCustomObject]@{name=$_.name; active=$_.active} } | Format-Table -AutoSize
