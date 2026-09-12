param(
    [string]$Repository = 'vmargin/banking-app',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) { $gh = Join-Path $env:ProgramFiles 'GitHub CLI\gh.exe' }
if (-not (Test-Path -LiteralPath $gh)) { throw 'GitHub CLI is required.' }

function Invoke-GitHubApi([string]$Method, [string]$Endpoint, $Payload = $null) {
    if ($null -eq $Payload) {
        $result = & $gh api -X $Method $Endpoint
    } else {
        $result = $Payload | ConvertTo-Json -Depth 8 | & $gh api -X $Method $Endpoint --input -
    }
    if ($LASTEXITCODE -ne 0) { throw "GitHub request failed: $Method $Endpoint" }
    return ($result | ConvertFrom-Json)
}

function Get-IssueBody($Item) {
    $dependencies = if ($Item.depends.Count) { $Item.depends -join ', ' } else { 'None' }
    $cutLine = if ($Item.id -in @('BA-28', 'BA-29')) {
@"

## Submission cut line

This is portfolio-upgrade work. Do not start it before the September assessment flow, screenshots, and PDF are verified.
"@
    } else { '' }
@"
## Outcome

$($Item.outcome)

## Concept and reasoning checkpoint

$($Item.checkpoint)

## Prerequisites

$($dependencies). Read `docs/ASSESSMENT-ALIGNMENT.md` and `docs/IMPLEMENTATION-BLUEPRINT.md` first.

## Acceptance criteria

$($Item.acceptance)

## Evidence before closing

- [ ] Explain the rule and predict one successful and one rejected case.
- [ ] Make your own implementation attempt (or a written decision for planning work).
- [ ] Record actual command, test, database, or UI evidence.
- [ ] Link the reviewed commit or pull request.
- [ ] Explain one changed case without receiving a complete solution.

The coach explains concepts and reviews evidence. The learner writes the banking implementation.$cutLine
"@
}

$previousToken = $env:GH_TOKEN
try {
    if (-not $env:GH_TOKEN) {
        $credentialLines = "protocol=https`nhost=github.com`n`n" | git credential fill
        $credentialToken = $credentialLines | Where-Object { $_ -like 'password=*' } | Select-Object -First 1
        if (-not $credentialToken) { throw 'Sign in to GitHub first using Git Credential Manager or GitHub CLI.' }
        $env:GH_TOKEN = $credentialToken.Substring(9)
    }

    $items = @(Get-Content (Join-Path $PSScriptRoot '..\docs\backlog.json') -Raw | ConvertFrom-Json)
    $labelNames = @('learning') + @($items.area | Select-Object -Unique) + @($items.priority | Select-Object -Unique)
    $labels = @(Invoke-GitHubApi 'GET' "repos/${Repository}/labels?per_page=100")
    foreach ($name in $labelNames) {
        if ($name -in $labels.name) { continue }
        if ($DryRun) { Write-Output "label '$name' would create"; continue }
        $labels += Invoke-GitHubApi 'POST' "repos/${Repository}/labels" @{ name = $name; color = '336699'; description = 'JCash assessment workflow' }
    }

    $milestones = @(Invoke-GitHubApi 'GET' "repos/${Repository}/milestones?state=all&per_page=100")
    foreach ($title in ($items.milestone | Select-Object -Unique)) {
        if ($title -in $milestones.title) { continue }
        if ($DryRun) { Write-Output "milestone '$title' would create"; continue }
        $milestones += Invoke-GitHubApi 'POST' "repos/${Repository}/milestones" @{ title = $title; description = 'Close only with learner-authored behaviour, verification, and explanation.' }
    }

    $issues = @(Invoke-GitHubApi 'GET' "repos/${Repository}/issues?state=all&per_page=100" | Where-Object { -not $_.pull_request })
    foreach ($item in $items) {
        $issue = $issues | Where-Object { $_.title -match "^\[$($item.id)\]" } | Select-Object -First 1
        $title = "[$($item.id)] $($item.title)"
        $milestone = $milestones | Where-Object { $_.title -eq $item.milestone } | Select-Object -First 1
        $body = Get-IssueBody $item
        $issueLabels = @('learning', $item.area, $item.priority)
        $payload = @{ title = $title; body = $body; labels = $issueLabels }
        if ($milestone) { $payload.milestone = $milestone.number }

        $currentLabels = @($issue.labels | ForEach-Object name | Sort-Object)
        $expectedLabels = @($issueLabels | Sort-Object)
        $labelsMatch = (@(Compare-Object -ReferenceObject $expectedLabels -DifferenceObject $currentLabels)).Count -eq 0
        $currentMilestone = if ($issue.milestone) { $issue.milestone.title } else { $null }
        $needsUpdate = -not $issue -or
            $issue.title -ne $title -or
            $issue.body.TrimEnd() -ne $body.TrimEnd() -or
            $currentMilestone -ne $item.milestone -or
            -not $labelsMatch

        if (-not $needsUpdate) {
            Write-Output "$($item.id) #$($issue.number) unchanged"
            continue
        }

        if ($DryRun) {
            $action = if ($issue) { 'would update' } else { 'would create' }
            Write-Output "$($item.id) ${action}: $title"
            continue
        }
        if ($issue) {
            Invoke-GitHubApi 'PATCH' "repos/${Repository}/issues/$($issue.number)" $payload | Out-Null
            Write-Output "$($item.id) #$($issue.number) updated"
        } else {
            $created = Invoke-GitHubApi 'POST' "repos/${Repository}/issues" $payload
            $issues += $created
            Write-Output "$($item.id) #$($created.number) created"
        }
    }
} finally {
    $env:GH_TOKEN = $previousToken
}
