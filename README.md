# Cash - G

Cash - G is a Java banking simulator with a Spring Boot and Thymeleaf web interface, JDBC persistence, and a local synthetic-data demo. It is a portfolio project, not a bank or payment service.

## Run the local demo

Install Temurin JDK 21, then start the isolated H2 demo:

```powershell
.\scripts\dev.ps1 -Task demo
```

Open [http://127.0.0.1:8081/login](http://127.0.0.1:8081/login).

The demo seeds these synthetic users:

| Name | Mobile | PIN |
| --- | --- | --- |
| Miguel Santos | `09990000001` | `1234` |
| Maria Reyes | `09990000002` | `1234` |

The demo keeps its existing local H2 data file so prior practice records remain available. Never enter real banking details or PINs.

## What the demo supports

- Multiple PHP accounts and a separate USD practice account, with unique 12-digit Cash - G account numbers.
- Same-currency Cash - G customer-to-customer transfers with a server-held review and confirmation step.
- Local money requests that require the named payer to review and confirm before a ledger change.
- Fictional billers with review, masking, and local-only settlement records.
- Savings goals funded from and returned to the practice account after review.
- Display-only card records with local freeze, online-payment, and replacement-request controls.
- Owner-scoped activity, account details, receipts, statement CSV, and monthly spending categories from persisted ledger data.
- Local activity notifications with owner-scoped read state, a PIN change flow, help, and settings.

No external bank, payment network, biller, card issuer, SMS, identity, or biometric provider is connected. Cards are display-only practice records. Cash - G sends no messages or security codes.

## Data and money rules

- The dashboard PHP total is the sum of PHP bank-account balances. USD stays separate. Savings-goal reserves are displayed in the goals area, not counted as bank accounts.
- The supplied visual board shows PHP 124,850 while its PHP accounts show PHP 24,850 and PHP 80,000, which sum to PHP 104,850. Cash - G calculates account totals from its ledger and keeps currencies separate.
- Account numbers are Cash - G simulator identifiers. Twelve digits is a local product choice, not a Philippine banking standard or routing code.
- Internal database IDs are separate from customer-facing account numbers. A money-operation reference is a UUID and is not an account identifier.
- Use only synthetic data. This project is not production banking software.

## Architecture and checks

The web layer maps requests and sessions to Java services. Services own product rules, JDBC repositories perform database work, and bounded JDBC transactions keep account balances, goal reserves, operation claims, and paired ledger entries consistent. The demo profile uses a local H2 file. PostgreSQL migrations and synthetic seeding are explicit maintenance operations.

Run the project checks locally:

```powershell
.\scripts\dev.ps1 -Task compile
.\scripts\dev.ps1 -Task test
.\scripts\dev.ps1 -Task verify
```

The PostgreSQL-backed tests require the isolated local database setup. H2 migration and money-flow tests run without PostgreSQL credentials. Run the demo without configuring database credentials.

## Project references

- [Product scope](PRODUCT.md)
- [Design direction](DESIGN.md)

The primary launch experience is the Spring Boot web application. Legacy Swing classes remain in the codebase as earlier Java learning material.
