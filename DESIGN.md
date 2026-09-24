# Clear Ledger

## Direction

Clear Ledger is a calm financial workspace for the CASH-G educational banking simulator. It puts the available balance first, keeps transaction history easy to scan, and makes each money action explicit. The design avoids paper, receipt, and counterfoil metaphors. Transfer review remains a functional confirmation step.

## Visual system

| Token | Value | Purpose |
| --- | --- | --- |
| Deep green | `#143B31` | Navigation and primary balance surface |
| Action green | `#12624D` | Primary actions and current navigation |
| Canvas | `#F5F7F3` | Workspace background |
| Ink | `#142922` | Main text and financial amounts |
| Muted | `#53685F` | Secondary information |
| Line | `#D0DCD3` | Quiet structure |
| Positive soft | `#E1F1E9` | Incoming and helpful states |
| Outgoing | `#9A4936` | Outgoing amount, paired with an `OUT` label |

Use the system sans font already in the application. Set financial amounts in tabular numerals. Keep corners small, borders quiet, and surfaces mostly flat. Do not add a UI framework or motion library for this design.

## Screen structure

- Login and registration: a dark green identity rail with a focused form on a white surface.
- Dashboard: a dark balance anchor, a compact record count, a clear transfer entry point, transaction history, and money action forms.
- Transfer review: a single details panel, one primary confirmation action, and a distinct cancellation action. The review state is not a receipt or settled transaction.

## Interaction and accessibility

- Preserve form routes, field names, Thymeleaf bindings, and server-owned banking rules.
- Use visible text and signs for incoming and outgoing amounts. Color is secondary.
- Keep controls at least 44 CSS pixels tall, show visible keyboard focus, and keep errors in semantic alert regions.
- Use short color and press feedback only. Remove nonessential motion for reduced-motion users.
- Verify 390px mobile and desktop layouts, including the transaction table's horizontal scroll.

The six-stage audit, design decisions, and verification record live in `context/DESIGN-ENGINEERING.md`.
