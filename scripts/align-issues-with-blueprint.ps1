param(
    [string]$Repository = 'vmargin/banking-app',
    [switch]$DryRun
)

# Compatibility entry point. The original Spring-first blueprint is superseded
# by the assessment-aligned backlog; use the safe idempotent rebaseline instead.
& (Join-Path $PSScriptRoot 'rebaseline-assessment-issues.ps1') @PSBoundParameters
