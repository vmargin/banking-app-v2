param([ValidateSet('doctor','style','compile','test','verify','run','package')][string]$Task = 'doctor')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jdkRoot = Join-Path $env:ProgramFiles 'Eclipse Adoptium'
$jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like 'jdk-21*' } | Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdk) { throw 'Temurin JDK 21 is required. Install EclipseAdoptium.Temurin.21.JDK.' }
$previousJavaHome = $env:JAVA_HOME
$previousPath = $env:Path
try {
    $env:JAVA_HOME = $jdk.FullName
    $env:Path = (Join-Path $jdk.FullName 'bin') + ';' + $previousPath
    Push-Location $projectRoot
    try {
        switch ($Task) {
            doctor { & .\mvnw.cmd -version }
            style { & .\mvnw.cmd --batch-mode checkstyle:check }
            compile { & .\mvnw.cmd --batch-mode compile }
            test { & .\mvnw.cmd --batch-mode test }
            verify { & .\mvnw.cmd --batch-mode verify }
            package { & .\mvnw.cmd --batch-mode package }
            run { & .\mvnw.cmd --batch-mode compile exec:java '-Dexec.mainClass=com.vmargin.banking.Main' }
        }
        if ($LASTEXITCODE -ne 0) { throw "Maven task '$Task' failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:Path = $previousPath
}
