param([string]$Repository = 'vmargin/banking-app')
$ErrorActionPreference = 'Stop'
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) { $gh = Join-Path $env:ProgramFiles 'GitHub CLI\gh.exe' }
if (-not (Test-Path -LiteralPath $gh)) { throw 'GitHub CLI is required.' }
$previousToken = $env:GH_TOKEN
try {
    if (-not $env:GH_TOKEN) {
        $credentialLines = "protocol=https`nhost=github.com`n`n" | git credential fill
        $credentialToken = $credentialLines | Where-Object { $_ -like 'password=*' } | Select-Object -First 1
        if (-not $credentialToken) { throw 'Sign in to GitHub first using Git Credential Manager or GitHub CLI.' }
        $env:GH_TOKEN = $credentialToken.Substring(9)
        $credentialLines = $null
        $credentialToken = $null
    }
    function Invoke-GitHub([string]$Endpoint, $Payload = $null) {
        if ($null -eq $Payload) { $result = & $gh api $Endpoint }
        else { $result = $Payload | ConvertTo-Json -Depth 8 | & $gh api -X POST $Endpoint --input - }
        if ($LASTEXITCODE -ne 0) { throw "GitHub request failed: $Endpoint" }
        return ($result | ConvertFrom-Json)
    }
    $items = Get-Content (Join-Path $PSScriptRoot '..\docs\backlog.json') -Raw | ConvertFrom-Json
    $labels = @(Invoke-GitHub "repos/$Repository/labels?per_page=100")
    foreach ($name in @('learning','ready','backlog','stretch') + @($items.area | Select-Object -Unique)) {
        if ($name -notin $labels.name) { $null = Invoke-GitHub "repos/$Repository/labels" @{name=$name; color='336699'; description='BankingApp learning workflow'} }
    }
    $milestones = @(Invoke-GitHub "repos/$Repository/milestones?state=all&per_page=100")
    foreach ($name in ($items.milestone | Select-Object -Unique)) {
        if ($name -notin $milestones.title) {
            $milestones += Invoke-GitHub "repos/$Repository/milestones" @{title=$name; description='Close only with implemented behaviour, verification and explanation.'}
        }
    }
    $existing = @(Invoke-GitHub "repos/$Repository/issues?state=all&per_page=100")
    foreach ($item in $items) {
        $title = "[$($item.id)] $($item.title)"
        $issue = $existing | Where-Object title -eq $title | Select-Object -First 1
        if (-not $issue) {
            $dependencies = if ($item.depends.Count) { $item.depends -join ', ' } else { 'None' }
            $body = @"
## Outcome

$($item.outcome)

## Concept and reasoning checkpoint

$($item.checkpoint)

## Prerequisites

$dependencies. See docs/backlog.json and BANKING-APP-GUIDE.md in the repository.

## Acceptance criteria

$($item.acceptance)

## Evidence before closing

- [ ] Explain the rule and predict a success/failure case.
- [ ] Make your own implementation attempt (or written decision for planning tasks).
- [ ] Record actual command/output or relevant visual checks.
- [ ] Link the reviewed commit or pull request.
- [ ] Explain a changed case without receiving the complete solution.

Work on one implementation issue at a time. Milestone 05 is future scope and does not block screenshot submission.
"@
            $number = ($milestones | Where-Object title -eq $item.milestone | Select-Object -First 1).number
            $issue = Invoke-GitHub "repos/$Repository/issues" @{title=$title; body=$body; milestone=$number; labels=@('learning',$item.area,$item.priority); assignees=@('vmargin')}
            $existing += $issue
        }
        Write-Output "$($item.id) #$($issue.number) $($issue.html_url)"
    }
} finally { $env:GH_TOKEN = $previousToken }
