# JCash Banking App v2

A portfolio evolution of the JCash banking simulator. Version 2 keeps the Java services, JDBC, PostgreSQL model, and tests while introducing a Counterfoil Desk web interface served by Spring Boot and Thymeleaf.

## Current direction

The Spring Boot application serves the web UI and connects its forms to the existing
Java services. The Counterfoil Desk direction uses working paper, dark ink, readable
records, and a transfer counterfoil review before the existing Java service records a movement. The original Swing screen
remains available with the --swing argument as a legacy desktop fallback.

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

### Demo login

The schema seeds one synthetic administrator account:

```text
Mobile number: 09990000000
PIN:           1234
```

Use the registration page to create another synthetic user. Do not use real
banking information or real PINs.

## Deploy on Render + Supabase

Create a Supabase PostgreSQL project, then create a Render Web Service from
this repository. Use these Render settings:

```text
Build command: ./mvnw --batch-mode clean package -DskipTests
Start command: java -jar target/banking-app-0.1.0-SNAPSHOT.jar
Health path:  /
```

Set these Render environment variables using the Supabase connection details:

```text
BANKING_DB_URL=jdbc:postgresql://<supabase-host>:5432/postgres?sslmode=require
BANKING_DB_USER=postgres
BANKING_DB_PASSWORD=<supabase-database-password>
```

Open the Render URL at `/login`. The schema runs during application startup.
Render Free may sleep after inactivity, so the first request can take longer.

## Structure

```text
PRODUCT.md          confirmed product context
DESIGN.md           Counterfoil Desk design direction and tokens
src/main/java/      Java application, domain, services, repositories, and launchers
src/main/resources/ PostgreSQL schema and live Thymeleaf UI
src/test/java/      regression tests
web/                older standalone visual prototype/reference
```

## Verification

The Java verification suite runs 36 tests with 0 failures when the v2 database
credentials are available, and reports 0 Checkstyle violations. Database-dependent
tests are skipped when PostgreSQL credentials are unavailable.

This is an educational portfolio project using synthetic data. It is not production banking software.
