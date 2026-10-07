# Cash - G Visual System

## Direction

Keep the existing Cash - G visual direction: near-black navy canvases, layered blue account panels, fine illuminated borders, direct sans-serif type, clear balance hierarchy, and grouped banking tasks in the Spring/Thymeleaf interface. Use Cash - G as the product name across every user-facing screen and artifact.

The dashboard puts the PHP account total and next actions first, then accounts, recent activity, category spending, savings goals, and the local practice card. Dedicated pages cover accounts, transfers, bills, cards, savings, insights, activity, notifications, settings, verification availability, and help. The collage guides information hierarchy and visual treatment; each displayed banking action must still match the simulator's real behavior in `PRODUCT.md`.

Dark mode is the default and the persisted light theme remains a complete alternate palette. Both themes use the same component hierarchy, spacing, and control shapes. No CSS framework or third-party design-system dependency is needed for this server-rendered application.

## Tokens

| Role | Dark | Light |
| --- | --- | --- |
| Canvas | `#071522` | `#EDF3FA` |
| Workspace | `#0A1B2C` | `#F4F8FC` |
| Main surface | `#0D2034` | `#FFFFFF` |
| Raised surface | `#132A42` | `#F7FAFE` |
| Soft surface | `#10243A` | `#EBF2F9` |
| Rail | `#081523` | `#E7EFF8` |
| Main text | `#ECF4FD` | `#172B42` |
| Muted text | `#A7B9CB` | `#4E647B` |
| Border | `#29445F` | `#CBD8E6` |
| Input boundary | `#597796` | `#6E849A` |
| Accent | `#82B7FF` | `#2866B2` |
| Strong action | `#347FE3` | `#1D5CA9` |
| Primary action / label | `#82B7FF` / `#071522` | `#1D5CA9` / `#FFFFFF` |
| Soft accent | `#17324F` | `#E2EEFD` |
| Positive text / surface | `#70E0A4` / `#12382C` | `#176342` / `#E4F5EC` |
| Negative text / surface | `#FFAAA6` / `#3A2028` | `#A74344` / `#FDEEEE` |
| Focus ring | `#9BC5FF` | `#155FC4` |

Use semantic CSS custom properties for every shared surface, action, status, border, and focus state. Typography uses the existing system sans stack: 16px body and input text, 14px form labels and guidance, and a 12px minimum for compact metadata. Money uses tabular numerals. Keep section labels concise, headings distinct, and balance values easy to scan. Use blue edges and soft offset shadows to define depth without obscuring content. The stronger input-boundary token identifies editable controls; the quieter border token separates panels.

Use a shared spacing scale of 4, 8, 12, 16, 24, 32, and 40px. Leave about 24px between page sections, 16px between related content groups, and at least 8px between adjacent controls. Keep profile labels and values in a two-column definition list; keep phone and account numbers together with tabular numerals and allow them to fit narrow screens without breaking a digit sequence.

Use one control system in both themes: 44px minimum field height, 9px radius, consistent padding, semantic field surface and boundary, and a visible focus ring. Keep native `<select>` controls and their keyboard behavior, and style their closed field and option colors from the active theme where the browser supports it.

## Layout and content

- Keep the compact navigation rail and top utility row on wide screens. On narrow screens, use Home, Accounts, Transfer, Activity, and More. The native More disclosure reuses the rail links and provides cash-in, requests, settings, help, and logout.
- On the dashboard, pair the balance panel with quick actions, show all account cards in one grouped section, then place recent activity beside data-backed monthly spending.
- Show the most-progressed savings goal as the featured image panel and up to two additional goals as compact progress rows. The full goal list remains on the savings page.
- Keep the display-only card preview visually distinct from issued-card controls. Use the standard ID-1 card aspect ratio (85.60 × 53.98 mm, about 1.586:1), with a 340px maximum width for full card views and a smaller preview on the dashboard. Label it as a Cash - G practice card; do not show a real card network mark. Use PHP and USD separately, and derive all totals from the ledger-backed account data.
- Keep transfer, bill, request, and savings review pages explicit about the local operation, amount, recipient/account, and cancel path before any write.
- At narrow widths, stack dashboard panels and keep all primary routes reachable. Tables scroll within their own containers, never across the page.

## Theme and interaction

- Start in dark mode and persist only the user's theme preference in local storage.
- Apply the saved theme before the stylesheet renders on every page. Keep the theme switch available during sign-in and registration as well as inside the app.
- Keep navigation, forms, cards, alerts, status messages, and data visualizations in the active semantic palette.
- Use native HTML controls and forms. Retain server-rendered money values, visible focus, labeled inputs, error/status regions, and at least 44px touch targets where practical.
- Motion is limited to brief, purposeful interaction feedback. Never animate balances, ledger values, or financial confirmation. Respect `prefers-reduced-motion` and scope hover treatment to hover-capable fine pointers.
- Spending bars and goal progress are derived from persisted values and remain understandable without color or motion.
- Client validation uses the controls' native validity rules, inline errors, and a focused summary linking to each invalid field. Server errors receive focus; failed authentication forms retain the submitted name/mobile for retry and clear the PIN. Valid POST forms show submitting feedback, while Java services and one-use server tokens own the financial result.
- Use the shared SVG icon fragment for navigation and utilities. Search has a visible group focus ring; the `/` shortcut works across authenticated screens.

## Audit evidence


## Product truth

Cash - G is a synthetic local banking simulator. Refer to `PRODUCT.md` for exact account, ledger, provider, card, security, and identity-verification boundaries. The visual direction does not override database-derived balances or justify claims of external settlement, card issuance, payment rails, SMS/OTP, KYC, or a security certification.

## Implementation boundary

The UI remains Spring Boot, Thymeleaf, semantic HTML, CSS, and native JavaScript over the existing Java services and JDBC repositories. Preserve routes, form methods and names, Thymeleaf bindings, session guards, error recovery, and server-owned financial invariants when changing the presentation.
