# NEXA Bank Product Definition

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Portfolio reviewers evaluating Java, Spring Boot, JDBC, relational data, and clear financial workflows. The user-facing screens represent a synthetic personal-banking customer.

## Purpose

NEXA Bank demonstrates common retail-banking screens and safe local ledger workflows in an explainable Java application. Its visual direction comes from the user-provided NEXA board. Sample names, balances, brands, and provider claims in that board are not evidence of real integrations.

## Product scope

- Dashboard with PHP account totals, a separately displayed USD practice account, recent activity, quick actions, a spending summary, goals, and display-only card information.
- Owner-scoped account details and unique 12-digit Nexa simulator account numbers.
- Same-currency internal transfers with recipient ownership checks, a review step, one-use confirmation, paired ledger entries, and a receipt.
- Local money requests. Creating a request does not debit the payer; payment requires the payer to review and confirm it.
- Fictional-biller payments with review, masked reference data, one local debit, and a receipt.
- Savings goals whose contributions and withdrawals move money between the primary PHP account and the goal reserve in one JDBC transaction.
- Display-only card records with local freeze, online-payment preference, and replacement-request controls.
- Activity filters, pagination, account-scoped receipts, CSV statement export, notifications, and monthly spending grouped by persisted category.
- Local PIN sign-in and PIN change, account settings, help, and an explicit identity-verification availability panel.

## Money and identifier rules

- Monetary input is positive, limited to two decimal places, and bounded before persistence.
- PHP and USD balances remain separate. The dashboard total labeled “Total across PHP accounts” sums PHP bank accounts only. Savings-goal reserves appear in the goals area; they are not bank accounts.
- The board's displayed PHP accounts are PHP 24,850 and PHP 80,000, totaling PHP 104,850. Its PHP 124,850 headline is PHP 20,000 higher. NEXA calculates the displayed account total from stored balances rather than copying that mismatch.
- Each Nexa account number is a randomly generated, unique 12-digit numeric simulator identifier. Twelve digits is not a Philippine banking standard, IBAN, or routing number.
- Database primary keys are internal. A money-operation reference is a UUID string shared by both sides of an internal transfer and protected against reuse.
- Spending categories are persisted with ledger rows. Existing transactions receive a safe category during schema migration; older card purchases default to `OTHER` when their merchant category cannot be inferred.
- Internal transfers and savings-goal movements are not counted as purchases or bill spending. USD is excluded from PHP insights.

## Trust and integration boundaries

- Authenticated owner identity comes from the server session. Each account, request, card, goal, receipt, activity, and notification-read operation is scoped to that owner.
- Money changes run in bounded JDBC transactions with account locks, unique operation claims, and rollback on failure. Transfer account locks are taken in ascending account-ID order.
- Financial actions require CSRF-protected forms and server-owned, expiring review state before confirmation.
- The local simulator does not connect to InstaPay, PESONet, external banks, wallets, billers, card networks, identity or KYC providers, SMS, push, email, or biometric services.
- The demo contains no real PAN, CVV, card PIN, OTP delivery, account recovery channel, interest, investment, lending, or foreign-exchange service.
- No security certification or regulatory compliance claim is made.

## Runtime

- Java 21, Spring Boot, Thymeleaf, JDBC, Spring Security, and Maven.
- The local demo runs against an isolated H2 file and synthetic seed data. PostgreSQL maintenance uses explicit local migration and seed tasks; normal application startup does not run those operations.
- The protected BankingApp v1 database and source are outside this product.

## Experience principles

1. Money totals and ledger records must agree.
2. Review makes the exact recipient, account, amount, currency, and local-only nature clear before confirmation.
3. Navigation keeps accounts, transfer, bills, cards, savings, insights, activity, notifications, settings, and help easy to reach.
4. The interface follows the supplied dark NEXA board while keeping controls, status, focus, and data readable across screen sizes.
5. A simulated provider action is labeled as local or unavailable rather than represented as completed external banking.

## Accessibility target

Use semantic headings, labels, tables, keyboard-visible focus, status and error regions, reduced-motion support, adequate contrast, and responsive layouts down to 320 CSS pixels. The product targets WCAG 2.2 AA practices; this target is not a certification claim.

## Evidence

- [NEXA product and banking-pattern audit](context/NEXA-PRODUCT-AUDIT.md)
- Spring and JDBC implementation in `src/main/java`
- Thymeleaf screens and shared CSS in `src/main/resources`
- H2 migration, repository, service, and web tests in `src/test/java`
