# CASH-G Counterfoil Desk

## Direction

Counterfoil Desk treats every money movement as a working record. The account desk shows what is settled; a transfer becomes a short-lived counterfoil that the user checks before the existing Java service records it. The visual system uses warm paper, carbon ink, one terracotta signal, and exact tabular amounts.

## Product mode

Operate and verify. The user should see the available balance, scan recorded entries, prepare the next action, and understand the difference between a reviewed request and a settled transaction.

## Visual tokens

| Token | Value | Use |
|---|---|---|
| Desk paper | `#EEE9DF` | Main workspace |
| Strong paper | `#F8F4EB` | Forms and review counterfoil |
| Carbon | `#1B211F` | Primary text and balance |
| Desk muted | `#6B655C` | Supporting text |
| Desk line | `#BDB3A5` | Rules and field borders |
| Signal | `#C9603D` | Primary action, review, outgoing money |
| Settled | `#506F60` | Incoming money and success state |

## Composition

- Auth screens pair a dark index panel with a light form desk on large screens and use one reading sequence on narrow screens.
- The dashboard uses a balance strip, a records column, and a new-entry column instead of a generic card grid.
- The review route gives a transfer its own counterfoil before confirmation; it is not a fake receipt.
- Incoming and outgoing records use both `IN`/`OUT` text and color so meaning never depends on color alone.

## Interaction and motion

- Buttons have a subtle pressed translation and concise color transition.
- No consequential balance or transfer state depends on animation.
- A reviewed transfer is stored only as a short-lived session request containing a normalized recipient and amount; it is cleared after confirmation, failure, or cancellation.

## Accessibility and responsive rules

- Every form control has an explicit label and input ID.
- Errors and success messages use semantic alert/status roles.
- Keyboard focus remains visible with a high-contrast ring.
- The layout must remain readable without horizontal overflow at desktop and 390px mobile widths.
- Text and essential controls must remain readable against all surfaces.

## Content and safety rules

- Use clear, specific banking language: add funds, review transfer, confirm record, available balance, transaction records.
- Label synthetic data as local educational/demo data.
- The review screen applies basic server-side checks for a blank recipient, self-transfer, invalid precision, and insufficient available balance. `TransferService` remains the final business-rule authority.
- Do not imply real payment-provider integration or production banking security. Spring Security/CSRF, hardened credentials, and end-to-end financial safety remain out of scope for this portfolio slice.
