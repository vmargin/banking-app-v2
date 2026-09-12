# JCash Banking App v2

A portfolio evolution of the JCash banking simulator. Version 2 keeps the Java services, JDBC, PostgreSQL model, and tests while introducing a bold HTML/CSS/JavaScript interface hosted inside the desktop app through JavaFX WebView.

## Current direction

The visual WebView shell is connected to the desktop launcher. The original Swing
screen remains available with the --swing argument while the HTML flows are
iterated safely. The next integration step is mapping the HTML forms to the
existing Java services through a small, explicit JavaScript bridge.

## Run locally

Install Temurin JDK 21 and PostgreSQL, then run:

```powershell
./scripts/dev.ps1 -Task verify
./scripts/dev.ps1 -Task run
```

The default launcher opens the WebView interface. To use the original Swing UI:

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
