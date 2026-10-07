param([ValidateSet('Setup','Migrate','Seed')][string]$Action = 'Setup')
$ErrorActionPreference = 'Stop'

$dbUser = [Environment]::GetEnvironmentVariable('BANKING_DB_USER')
$dbPassword = [Environment]::GetEnvironmentVariable('BANKING_DB_PASSWORD')
$databaseUrl = [Environment]::GetEnvironmentVariable('BANKING_DB_URL')
if ([string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbPassword)) {
    throw 'Set BANKING_DB_USER and BANKING_DB_PASSWORD in this PowerShell session first.'
}

if ([string]::IsNullOrWhiteSpace($databaseUrl)) {
    $databaseUrl = 'jdbc:postgresql://localhost:5432/banking_app_v2'
}
$target = [regex]::Match($databaseUrl, '^jdbc:postgresql://(?:localhost|127\.0\.0\.1):(?<port>[0-9]+)/banking_app_v2$')
if (-not $target.Success) {
    throw 'This script only accepts a local PostgreSQL URL targeting banking_app_v2, without URL credentials or options.'
}
$dbPort = [int]$target.Groups['port'].Value

function Invoke-Maintenance([string]$Operation) {
    & (Join-Path $PSScriptRoot 'dev.ps1') -Task $Operation.ToLowerInvariant()
    if ($LASTEXITCODE -ne 0) {
        throw "The local database $Operation operation failed with exit code $LASTEXITCODE."
    }
}

if ($Action -eq 'Setup') {
    $postgresBin = 'C:\Program Files\PostgreSQL\18\bin'
    $psql = Join-Path $postgresBin 'psql.exe'
    $createdb = Join-Path $postgresBin 'createdb.exe'
    if (-not (Test-Path -LiteralPath $psql) -or -not (Test-Path -LiteralPath $createdb)) {
        throw "PostgreSQL client tools were not found under $postgresBin."
    }

    $previousPgPassword = $env:PGPASSWORD
    $hadPgPassword = Test-Path Env:PGPASSWORD
    try {
        $env:PGPASSWORD = $dbPassword
        $databaseExists = (& $psql -h localhost -p $dbPort -U $dbUser -d postgres -w -tAc `
            "SELECT 1 FROM pg_database WHERE datname = 'banking_app_v2'" | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) {
            throw 'Could not connect to local PostgreSQL with the supplied credentials.'
        }
        if ($databaseExists.Trim() -ne '1') {
            & $createdb -h localhost -p $dbPort -U $dbUser -w banking_app_v2
            if ($LASTEXITCODE -ne 0) {
                throw 'Could not create the local banking_app_v2 database.'
            }
        }
    } finally {
        if ($hadPgPassword) {
            $env:PGPASSWORD = $previousPgPassword
        } else {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        }
    }

    Invoke-Maintenance 'Migrate'
    Invoke-Maintenance 'Seed'
    Write-Output 'Local banking_app_v2 is ready.'
} elseif ($Action -eq 'Migrate') {
    Invoke-Maintenance 'Migrate'
} else {
    Invoke-Maintenance 'Seed'
}
