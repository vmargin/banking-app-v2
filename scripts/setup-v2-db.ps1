$ErrorActionPreference = 'Stop'

$dbUser = [Environment]::GetEnvironmentVariable('BANKING_DB_USER')
$dbPassword = [Environment]::GetEnvironmentVariable('BANKING_DB_PASSWORD')
$postgresBin = 'C:\Program Files\PostgreSQL\18\bin'
$psql = Join-Path $postgresBin 'psql.exe'
$createdb = Join-Path $postgresBin 'createdb.exe'

if (-not (Test-Path -LiteralPath $psql) -or -not (Test-Path -LiteralPath $createdb)) {
    throw "PostgreSQL client tools were not found under $postgresBin."
}
if ([string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbPassword)) {
    throw 'Set BANKING_DB_USER and BANKING_DB_PASSWORD in this PowerShell session first.'
}

$env:PGPASSWORD = $dbPassword
try {
    $databaseExists = (& $psql -h localhost -U $dbUser -d postgres -w -tAc "SELECT 1 FROM pg_database WHERE datname = 'banking_app_v2'" | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not connect to PostgreSQL with the supplied credentials.' }
    if ($databaseExists.Trim() -ne '1') {
        & $createdb -h localhost -U $dbUser -w banking_app_v2
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated banking_app_v2 database.' }
    }

    Get-Content -Raw (Join-Path $PSScriptRoot '..\src\main\resources\schema.sql') |
        & $psql -h localhost -U $dbUser -d banking_app_v2 -w
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize the banking_app_v2 schema.' }
    Write-Output 'banking_app_v2 is ready.'
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}
