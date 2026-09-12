# JCash Banking App v2

A portfolio evolution of the JCash banking simulator. Version 2 keeps the Java services, JDBC, PostgreSQL model, and tests while introducing a polished Thymeleaf web interface served by Spring Boot.

## Current direction

The Spring Boot application serves the web UI and connects its forms to the existing
Java services. The original Swing screen remains available with the --swing argument
as a legacy desktop fallback.

## Run locally

Install Temurin JDK 21 and PostgreSQL. Configure a separate v2 database connection
before using login or banking actions:

```powershell
$env:BANKING_DB_USER = "postgres"
$env:BANKING_DB_PASSWORD = "your-local-postgres-password"
```

The default database is `banking_app_v2`; the v1 `banking_app` database is not used.
Use `scripts/setup-v2-db.ps1` after setting the variables above, then run:

```powershell
./scripts/dev.ps1 -Task verify
./scripts/dev.ps1 -Task run
```

The default launcher starts the Spring Boot web application at http://localhost:8080/login.
To use the original Swing UI:

```powershell
./mvnw.cmd --batch-mode compile exec:java -Dexec.mainClass=com.vmargin.banking.Main -Dexec.args=--swing
```

## Structure

```text
src/main/java/      Java application, domain, services, repositories, and launchers
src/main/resources/ PostgreSQL schema
src/test/java/      regression tests
web/                HTML, CSS, JavaScript, and generated local artwork
```

## Verification

The Java verification suite runs 32 tests with 0 failures, skips only database-dependent tests when PostgreSQL is unavailable, and reports 0 Checkstyle violations.

This is an educational portfolio project using synthetic data. It is not production banking software.
