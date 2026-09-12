# Banking App V2 — Full Audit

**Audit date:** 2026-09-12
**Scope:** Design, UX, portfolio readiness, application behavior, code quality, project structure, security, database, testing, and missing features.

## Executive verdict

V2 is a presentable local banking MVP, but it is not yet a full-fledged banking portfolio application and must not be presented as production banking software.

**Triage score: 60/100 — risky for release, acceptable as an educational local demo.**

The recommended direction is to keep Spring Boot + Thymeleaf. React would add an API layer, CORS, Node tooling, client-side authentication concerns, and more deployment complexity without solving the current correctness and security gaps.

This audit was read-only. No application files were renamed or deleted while producing it.

## Jarvis audit status

### Route

- Full portfolio audit
- Production-readiness review
- Security and authentication review
- UI/UX and accessibility review
- Click-path behavior audit
- Java code and directory organization review
- Database and test verification

### Plan

1. Inspect the repository and current working-tree state.
2. Trace the live Spring Boot routes and user-facing forms.
3. Run the local verification suite and inspect database/runtime evidence.
4. Compare the live Thymeleaf UI with the older standalone prototype.
5. Separate verified strengths, bugs, risks, missing features, and recommended cleanup.

### Implementation

No implementation changes were made during this audit. The repository remains in its existing uncommitted V2 state from the previous Spring Boot migration.

### Verification

The following checks were performed:

- `scripts/dev.ps1 -Task verify`
- HTTP smoke checks against `http://localhost:8080`
- Live login and dashboard inspection
- Database schema and count inspection
- Route and security-header inspection
- `git diff --check`
- Static source and project-structure inspection

## Verified strengths

- Spring Boot + Thymeleaf application runs on port 8080.
- `GET /login` returns HTTP 200.
- Unauthenticated access to `/dashboard` redirects to `/login`.
- The seeded admin account can reach the dashboard.
- A separate `banking_app_v2` database exists; `banking_app` remains a separate database.
- Local verification passed with 36 tests, 0 failures, 0 skipped tests, and 0 Checkstyle violations.
- PostgreSQL-backed login, registration, cash-in, transfer, and transaction-history paths are connected.
- Cash-in and transfer repositories use database transactions.
- SQL uses prepared statements for user-provided values.
- The UI has a consistent CASH-G visual identity and is substantially cleaner than the previous desktop interface.
- Demo credentials are visible for the local educational environment.

Evidence:

- [pom.xml](pom.xml)
- [BankingWebApplication.java](src/main/java/com/vmargin/banking/web/BankingWebApplication.java)
- [BankingWebController.java](src/main/java/com/vmargin/banking/web/BankingWebController.java)
- [DatabaseConnection.java](src/main/java/com/vmargin/banking/util/DatabaseConnection.java)
- [scripts/dev.ps1](scripts/dev.ps1)
- [scripts/setup-v2-db.ps1](scripts/setup-v2-db.ps1)

## Current route surface

| Method | Route | Current behavior |
|---|---|---|
| GET | `/` | Renders login page |
| GET | `/login` | Renders login page |
| POST | `/login` | Authenticates and redirects to dashboard |
| GET | `/register` | Renders registration page |
| POST | `/register` | Creates a local user account |
| GET | `/dashboard` | Requires a session and renders balance/history/actions |
| POST | `/cash-in` | Adds money and records a transaction |
| POST | `/transfer` | Transfers money and records both ledger entries |
| POST | `/logout` | Invalidates the session |

The following expected portfolio routes do not currently exist:

- `/admin`
- `/history`
- `/profile`
- `/settings`
- `/api`
- `/actuator/health`

## Critical and high-priority findings

### 1. Cash-in and transfer balances are updated twice

`CashInService` and `TransferService` already update the in-memory user balance after the database operation. `BankingWebController` updates the same balance again.

Result:

- The database balance is updated once.
- The current session object can display a cash-in twice.
- The current session object can display a transfer deduction twice.
- A fresh login may hide the mismatch by loading the database value again.

Evidence:

- [CashInService.java](src/main/java/com/vmargin/banking/service/CashInService.java)
- [TransferService.java](src/main/java/com/vmargin/banking/service/TransferService.java)
- [BankingWebController.java](src/main/java/com/vmargin/banking/web/BankingWebController.java)

**Priority: P0. Fix before further UI work.**

### 2. Received transfers display as money out

The dashboard displays `+` only for `CASH_IN`. Both `TRANSFER_SENT` and `TRANSFER_RECEIVED` are treated as negative values. A received transfer should display as positive and use an incoming icon.

Evidence:

- [dashboard.html](src/main/resources/templates/dashboard.html)
- [TransactionType.java](src/main/java/com/vmargin/banking/model/TransactionType.java)

**Priority: P0. Add a regression test.**

### 3. Login lockout is global instead of per account

`LoginService` stores `failedAttempts` and `locked` as fields on a shared service instance. Three failed attempts by any user can lock the entire application. Restarting the application clears the lock.

Evidence:

- [LoginService.java](src/main/java/com/vmargin/banking/service/LoginService.java)

**Priority: P0 for correct authentication behavior.**

### 4. PINs are stored in plaintext

The user model and database schema store the raw PIN. This is acceptable only for a clearly labelled classroom prototype, not for a portfolio claiming realistic banking security.

Evidence:

- [User.java](src/main/java/com/vmargin/banking/model/User.java)
- [JdbcUserRepository.java](src/main/java/com/vmargin/banking/repository/JdbcUserRepository.java)
- [schema.sql](src/main/resources/schema.sql)

**Priority: P0 before public deployment.**

### 5. The full `User` object is stored in the HTTP session

The model contains the PIN, so storing the complete `User` object in the session risks retaining sensitive data longer than necessary. Use an `AuthenticatedUser` session record containing only the user ID, display name, mobile number, and role.

Evidence:

- [BankingWebController.java](src/main/java/com/vmargin/banking/web/BankingWebController.java)
- [User.java](src/main/java/com/vmargin/banking/model/User.java)

### 6. No Spring Security, CSRF protection, or security headers

The application currently has no Spring Security dependency or security filter chain. The POST forms do not include CSRF tokens, and the live response did not expose CSP, frame-protection, or content-type-protection headers.

Evidence:

- [pom.xml](pom.xml)
- [login.html](src/main/resources/templates/login.html)
- [register.html](src/main/resources/templates/register.html)
- [dashboard.html](src/main/resources/templates/dashboard.html)

## Click-path audit

### Sign in

1. User submits mobile number and PIN.
2. Controller calls `LoginService.login`.
3. On success, the full `User` object is placed in the session.
4. User is redirected to `/dashboard`.

**Works locally, but has security and global-lockout risks.**

### Registration

1. User submits name, mobile number, and PIN.
2. Controller calls `RegistrationService.register`.
3. User is redirected to the login page with a success message.

**Works locally, but uses plaintext PIN storage and raw request parameters.**

### Cash-in

1. User submits amount and details.
2. Controller calls `CashInService.cashIn`.
3. Service updates the database and the in-memory account.
4. Controller deposits the same amount into the in-memory account again.
5. User is redirected to `/dashboard`.

**Bug: final displayed session balance is too high.**

### Transfer

1. User submits recipient and amount.
2. Controller calls `TransferService.transfer`.
3. Repository atomically debits the sender, credits the recipient, and records both transactions.
4. Service withdraws from the in-memory sender account.
5. Controller withdraws from the same account again.
6. User is redirected to `/dashboard`.

**Bug: final displayed session balance is too low.**

### Logout

1. Controller invalidates the HTTP session.
2. User is redirected to `/login`.

**Basic path works, but Spring Security should own the session lifecycle later.**

## Design and UX audit

### What is working

- Strong visual hierarchy.
- Consistent navy, mint, blue, and neutral palette.
- Good desktop spacing and readable typography.
- Login and registration pages feel related.
- Dashboard balance is visually prominent.
- Action forms are easy to discover.
- Empty transaction state exists.
- Thymeleaf escapes displayed user values through `th:text`.

### What needs improvement

- The live app has only one dashboard page.
- There is no real navigation or sidebar.
- There is no dedicated transaction history page.
- There is no transaction search, filtering, pagination, or export.
- Transfers have no review or confirmation step.
- The recipient's name is not shown before sending.
- There is no receipt or transaction reference number.
- There is no profile or settings page.
- There is no admin web surface.
- Error messages are not implemented as accessible live regions.
- Explicit input IDs and `for` attributes would improve consistency and automation.
- Mobile CSS exists but was not visually verified at mobile viewport sizes.
- The “recent activity” section currently loads the complete user history rather than a bounded recent list.
- The older prototype has more visible product concepts than the live application, which creates a misleading comparison between simulated and real functionality.

The visual prototype in [web/index.html](web/index.html) includes search, filters, navigation, modals, history, and review language, but [web/js/app.js](web/js/app.js) keeps those actions local and simulated. Those features should not be described as implemented banking functionality until connected to the Spring application.

## Architecture and code-quality audit

### Current architecture

```text
Spring Boot
  -> BankingWebController
      -> existing Java services
          -> JDBC repositories
              -> PostgreSQL banking_app_v2
```

The bridge approach is understandable and preserves the existing Java domain and service layer. However, the web layer currently mixes authentication, session handling, page rendering, action processing, error handling, and balance mutation in one controller.

### Main code-quality issues

- `BankingWebController.java` is doing too many jobs.
- Web methods use raw `@RequestParam` values instead of form DTOs.
- Broad `RuntimeException` catches can expose internal exception messages to users.
- Spring beans are configured manually in the application class rather than through conventional configuration and constructor injection across the application.
- The service layer and controller both mutate the same account state.
- The model mixes persistence credentials, domain state, and session state.
- The JDBC repositories contain repeated connection and mapping boilerplate.
- `LoginFrame.java` is 1,414 lines and remains a large legacy surface.
- `app.css` is currently one very long line, which makes maintenance difficult.
- The README test count is stale: it says 32, while the verified local result is 36.

### Code that can be simplified

1. Remove the duplicate controller-side balance mutations.
2. Split the web controller into authentication, dashboard, and banking-action controllers.
3. Introduce `LoginForm`, `RegisterForm`, `CashInForm`, and `TransferForm` records.
4. Use validation annotations and a centralized controller exception handler.
5. Introduce a safe `AuthenticatedUser` session record.
6. Move Spring bean wiring into a dedicated configuration class.
7. Format and split CSS into tokens, base, authentication, dashboard, and component styles.
8. Consider `JdbcTemplate` only after the correctness and security work; retaining plain JDBC is currently useful for explaining the Java core.
9. Remove the Swing surface only if the project decision is permanently web-only.

## Recommended directory cleanup

The main source of confusion is that three UI-related surfaces remain:

```text
src/main/resources/templates/   live Thymeleaf HTML
src/main/resources/static/      live CSS
web/                            old standalone visual prototype
LoginFrame.java                 legacy Swing UI
```

Recommended target structure:

```text
src/main/java/com/vmargin/banking/
├── BankingApplication.java
├── config/
├── controller/
│   ├── AuthController.java
│   ├── DashboardController.java
│   └── BankingActionController.java
├── dto/
├── model/
├── repository/
├── service/
└── session/

src/main/resources/
├── templates/
│   ├── auth/
│   ├── dashboard/
│   └── fragments/
└── static/
    ├── css/
    └── js/

design-prototype/
└── old standalone UI
```

Recommended renames or refactors:

- `BankingWebApplication.java` → `BankingApplication.java` in the root package.
- Split `BankingWebController.java` rather than giving it another long name.
- `historyService()` → `transactionHistoryService()`.
- `Main.java` → `DesktopLauncher.java` if Swing remains intentionally supported.
- `web/` → `design-prototype/` if the visual reference should be retained.
- `app.css` → smaller named stylesheet files.

Do not delete the old prototype or Swing implementation until the final web-only decision is made and any portfolio screenshots have been preserved.

## Database and operations audit

### Verified

- `banking_app_v2` exists separately from `banking_app`.
- The current default JDBC URL targets `banking_app_v2`.
- The v2 schema has `users` and `transactions` tables.
- User mobile numbers are unique.
- Transaction rows reference users.
- Cash-in and transfers use database transactions.
- A local setup script exists.

### Missing or risky

- No Flyway or Liquibase migrations.
- No migration history or rollback process.
- `schema.sql` is an idempotent setup script, not a versioned migration system.
- The seed operation updates the administrator PIN whenever the schema script runs.
- No DataSource or connection-pool configuration.
- No health endpoint.
- No structured application logging.
- No backup/recovery documentation.
- No Docker Compose setup for reproducible PostgreSQL startup.
- No production profile or deployment configuration.
- No TLS configuration.

The local v2 database currently contains synthetic/demo data and should be treated as disposable development data.

## Testing and CI audit

### Current status

Local verification is healthy:

- 36 tests passed.
- 0 failures.
- 0 skipped locally when database credentials were available.
- 0 Checkstyle violations.
- Spring Boot packaging completed successfully.

### Missing coverage

- No end-to-end browser test suite.
- No controller tests for successful registration, cash-in, transfer, or logout.
- No regression test for the double balance mutation.
- No regression test for incoming-transfer display.
- No concurrency tests for transfers.
- No security tests.
- No accessibility tests.
- No coverage threshold or coverage report.
- CI does not provision PostgreSQL, so database-dependent tests are skipped in the GitHub workflow when credentials are unavailable.

Evidence:

- [BankingWebControllerTest.java](src/test/java/com/vmargin/banking/web/BankingWebControllerTest.java)
- [java.yml](.github/workflows/java.yml)

## Missing features

### Authentication and security

- Hashed PIN/password storage
- Per-user lockout
- Lockout expiry and unlock flow
- Change PIN
- Forgot/reset PIN
- Secure session principal
- Session fixation protection
- CSRF protection
- Secure cookie settings
- Rate limiting
- Generic authentication errors
- Optional MFA/OTP
- Active, locked, suspended, and disabled account states

### Customer banking features

- Profile page
- Account details page
- Recipient name lookup
- Transfer review and confirmation
- Transfer receipt
- Transaction reference numbers
- Transfer limits
- Daily limits
- Fees
- Pending and failed transaction states
- Reversal/refund flow
- Duplicate-transfer protection
- Idempotency handling
- Reliable balance refresh from the database
- Cash-in source/provider details

### Transaction features

- Full transaction-history page
- Search
- Filters
- Pagination
- Date-range filtering
- Transaction detail view
- CSV/PDF export
- Correct incoming/outgoing labels
- Monthly summaries
- Charts or spending insights

### Admin features

`AdminService` exists, but it is not exposed through the web application.

- Admin dashboard
- User search
- View all users
- Lock/unlock users
- Suspend accounts
- View all transactions
- Transaction audit trail
- Role management
- Admin-only route protection
- Admin action logging

### UX and accessibility

- Shared Thymeleaf fragments
- Navigation/sidebar
- Loading states
- Complete empty states
- Field-level validation
- Accessible error announcements
- Keyboard and focus review
- Mobile viewport verification
- Confirmation dialogs
- Success notifications/toasts
- Custom 404 and 500 pages
- Reusable form and button components
- Production-safe handling of demo credentials

### Engineering and operations

- Versioned migrations
- Migration rollback strategy
- Separate seed-data profile
- Environment profiles
- Pooled DataSource configuration
- Health endpoint
- Structured logging
- Error monitoring
- Docker Compose setup
- Testcontainers or CI database service
- Dependency vulnerability scanning
- Deployment configuration
- Backup and recovery documentation
- HTTPS configuration

### Testing

- Registration controller tests
- Cash-in controller tests
- Transfer controller tests
- Logout controller tests
- Error-state tests
- Double-balance regression test
- Incoming-transfer display test
- Browser end-to-end tests
- Concurrent-transfer tests
- Security tests
- Accessibility tests
- Coverage reporting

### Portfolio documentation

- Architecture diagram
- Database ERD
- Feature matrix
- Route documentation
- Screenshots
- Demo walkthrough
- Known limitations
- Seed-data documentation
- Deployment instructions
- Updated verification count
- Clear statement that this is an educational banking simulator, not real banking software

## Recommended implementation order

### Phase 0 — correctness blockers

1. Fix duplicate balance updates.
2. Fix incoming-transfer display.
3. Add regression tests for both bugs.
4. Reload or safely update the account balance from the database after actions.

### Phase 1 — security foundation

1. Hash PINs.
2. Add Spring Security.
3. Add CSRF protection.
4. Replace the session `User` object with a safe session DTO.
5. Make lockout per account with expiry.
6. Add generic authentication error handling and secure headers.

### Phase 2 — structure and portfolio UX

1. Split the controller.
2. Add DTO-based validation.
3. Clean and rename the project structure.
4. Choose one live UI surface and clearly label the other as prototype/reference.
5. Add transaction history, filters, transfer confirmation, and receipts.

### Phase 3 — complete banking portfolio

1. Build the admin web interface.
2. Add profile and account settings.
3. Add limits, statuses, references, and reversal concepts.
4. Add Testcontainers or a CI PostgreSQL service.
5. Add browser E2E and accessibility testing.

### Phase 4 — operational polish

1. Add Flyway or Liquibase.
2. Add Docker Compose.
3. Add health checks and structured logs.
4. Add dependency scanning.
5. Add deployment documentation and portfolio evidence.

## Final status

The V2 foundation is real and runnable, but the next work should start with correctness and security rather than adding more screens. The most urgent fix is the double balance mutation, followed by secure authentication and removal of the duplicate UI ambiguity.
