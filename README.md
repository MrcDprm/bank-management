# Online Bank Manager

**English** | [Türkçe](README.tr.md)

A desktop banking app written in Java and JavaFX with two sides: a branch panel where staff open customers and accounts, and an online banking side where customers manage their own money. Data is stored locally in SQLite.

> 🚧 Work in progress. This README is the project plan and will be completed at v1.0.0.

## Plan

### MVP
- **Two sides, role-based sign-in:**
  - Branch staff: teller and administrator.
  - Customers: online banking.
  - Passwords are hashed with **Argon2id**, follow strong password rules, and the account is locked after repeated wrong attempts.
  - Permissions are checked in the service layer.
- **Branch panel:**
  - Open customers (Turkish ID number with check digits, contact details) and accounts.
  - Deposit and withdraw cash for a customer.
  - Freeze and close accounts.
  - Reset a customer's password; the customer gets a temporary password and must change it at first sign-in.
- **Customer online banking:**
  - Accounts overview and balances.
  - Money transfer by IBAN, between own accounts or to other customers.
  - Transaction history with search and date filter.
- **Correct money handling:**
  - Amounts are stored in minor units (kuruş), never as floating-point numbers.
  - Each transfer is atomic, so the money never leaves one account without reaching the other.
  - Balances cannot go below zero.
- **IBAN:** Turkish format, generated for each account and validated with the mod-97 check.
- **Desktop app:**
  - Dark and light theme (AtlantaFX), Turkish and English.
  - Icon, version, About window.
  - Data in the user's folder, Windows installer.
- **Tests:** IBAN, money arithmetic, transfers, limits, interest and permissions, with JUnit.

### Extras
- **Account types:**
  - Current account.
  - Time deposit with interest calculation.
  - Foreign currency accounts (USD, EUR) with exchange at fixed demo rates.
- **Statements:** account statement for a date range and a receipt for each transfer, as PDF.
- **Spending charts:** income and expenses by category and by month.
- **Saved recipients and limits:**
  - Frequently used recipients.
  - Daily transfer limit.
  - Extra confirmation for large transfers.
- **Sample data:** offered once on first start (customers, accounts and a few months of transactions).

### Future Plans
- Scheduled and recurring transfers.
- Bill payments.
- Cards and card statements.
- Live exchange rates.

## Tech Stack
- Java, JavaFX, AtlantaFX, Ikonli
- SQLite (sqlite-jdbc), Bouncy Castle (Argon2id)
- Maven, JUnit
- jpackage, Inno Setup
