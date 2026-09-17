# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Primary users are learners and portfolio reviewers evaluating a Java/Spring Boot banking simulator. The application also represents a basic personal-banking user completing account and money-movement tasks.

## Product Purpose

JCash is an educational banking simulator that demonstrates authentication, account balances, cash-in, transfers, and transaction history using Java, Spring Boot, Thymeleaf, JDBC, and PostgreSQL.

## Positioning

The product prioritizes explainable Java domain and service logic while exposing the flows through a readable web interface.

## Operating Context

The application runs locally for development, testing, and portfolio demonstration. It uses synthetic data and a separate `banking_app_v2` PostgreSQL database.

## Capabilities and Constraints

- Current web stack: Spring Boot and Thymeleaf.
- Current banking flows: registration, login, cash-in, transfer, logout, and transaction history on the dashboard.
- Current data layer: JDBC and PostgreSQL.
- The current implementation is an educational simulator, not production banking software.
- The Counterfoil Desk visual direction is the approved redesign direction for the web UI. Transfers use a review counterfoil before confirmation reaches the existing Java service.

## Brand Commitments

- Product name: JCash / CASH-G.
- The interface should feel trustworthy, calm, readable, and intentional.
- Financial actions and balances must be unambiguous.

## Evidence on Hand

- Existing Spring Boot web implementation under `src/main`.
- Existing standalone visual prototype under `web/`.
- Existing Java domain, service, repository, and test layers.
- Synthetic local database and demo accounts.

## Product Principles

1. Correct balances and transaction records are more important than visual novelty.
2. Banking actions must be easy to understand and hard to perform accidentally.
3. The Java core should remain explainable for a portfolio reviewer.
4. Visual polish must support trust, scanability, and accessibility.

## Accessibility & Inclusion

The web interface should target WCAG 2.2 AA practices: readable contrast, keyboard-visible focus, semantic labels, responsive layouts, accessible errors, and reduced-motion support.
