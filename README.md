<p align="center">
  <img src="src/main/resources/com/mrcdprm/bank/icon.png" alt="Bank Manager icon" width="96">
</p>

<h1 align="center">Bank Manager</h1>

<p align="center">
  <b>English</b> | <a href="README.tr.md">Türkçe</a>
</p>

<p align="center">
  A two-sided desktop banking app written in Java and JavaFX: a <b>branch panel</b> where staff open customers<br>
  and accounts and handle cash, and a <b>customer panel</b> where customers move and track their own money.
</p>

<p align="center">
  <a href="https://github.com/MrcDprm/bank-management/releases/latest"><b>⬇️ Download for Windows</b></a>
</p>

<p align="center">
  <img src="docs/overview.png" alt="Customer panel home page with account cards and recent transactions" width="820">
</p>

> This is a portfolio project, not a real bank. It runs locally: the branch panel and the customer panel are two sides of the same desktop app and share one SQLite database on the same computer; there is no server or network connection. The bank, exchange rates and interest rates are made up, and every PDF it produces is marked as a sample.

## Features

**Customer panel**
- Account cards with IBAN (one-click copy), total assets in TRY and recent transactions
- **Money transfer by IBAN** within the bank: the recipient's name is shown masked (`Me**** Ka**`) as soon as the IBAN is typed, so money does not go to the wrong person
- Saved recipients with nicknames; own accounts are offered in the same picker
- **Daily transfer limit** that the customer can change, and a password check for transfers of 10,000 TRY or more
- **Currency exchange** between own TRY, USD and EUR accounts with buy/sell rates
- **Time deposits** (32 to 365 days): interest preview, a progress bar, breaking the deposit early, and automatic payout at maturity
- Transaction history with date range, search and receipt number lookup; transactions can be recategorised
- **Spending insights:** spending by category (pie chart) and income vs. expenses for the last 6 months (bar chart)
- **PDF receipts** for every transaction and **PDF account statements** for any date range

**Branch panel (staff)**
- Customer search by name, ID number or phone; summary of customers, accounts, total deposits and today's transactions
- New customers with Turkish ID number check digits, phone and email validation; a TRY account and a temporary password are created
- Opening TRY / USD / EUR accounts, cash deposit and withdrawal with a receipt, freezing and closing accounts
- Password reset (unlocks the customer) and daily limit changes
- **Administrator only:** staff management (new teller or administrator, roles, deactivation, password reset) and closing accounts

**Correct money handling**
- Amounts are stored in kuruş/cents as whole numbers, never as floating-point values
- Each transfer runs in a single database transaction: the money never leaves one account without reaching the other
- The balance check is inside the `UPDATE` statement and the database also refuses negative balances, so even parallel transfers cannot overdraw an account
- Turkish IBANs are generated and validated with the ISO 13616 mod-97 check

**Security**
- Passwords are hashed with **Argon2id** (Bouncy Castle) and must be strong; the rules are shown live while typing
- Accounts are locked for 15 minutes after 5 failed sign-ins; the message never says whether the ID or the password was wrong
- Temporary passwords must be changed at first sign-in, and nothing else works until they are
- Permissions are checked in the service layer, not only by hiding buttons; a customer can never read or move another customer's money
- Every SQL query is parameterised; search input is escaped for `LIKE`
- Automatic sign-out after 5 minutes of inactivity
- Unexpected errors show a short message, never a stack trace

**Desktop app**
- Turkish and English, light and dark theme (AtlantaFX)
- First-start setup creates the administrator; sample data (60 customers, 6 months of transactions) can be loaded with one tick
- Data is stored in the user folder (`%APPDATA%\MrcDprm\BankManager`), never in the program folder
- Windows installer, icon, version and About window

## Screenshots

| Sign in | Send money |
|:---:|:---:|
| <img src="docs/login.png" alt="Sign-in screen with personal and branch tabs" width="420"> | <img src="docs/transfer.png" alt="Money transfer form" width="420"> |
| **Transactions** | **Spending insights** |
| <img src="docs/history.png" alt="Transaction history with filters" width="420"> | <img src="docs/insights.png" alt="Spending by category and monthly income-expense chart" width="420"> |
| **Branch panel** | **Time deposit** |
| <img src="docs/customers.png" alt="Customer search and account management for staff" width="420"> | <img src="docs/deposits.png" alt="Time deposit form and open deposits" width="420"> |

<p align="center">
  <img src="docs/overview-dark.png" alt="Dark theme" width="620">
</p>

## Installation

1. Download `BankManager-1.0.0-Setup.exe` from the [Releases](https://github.com/MrcDprm/bank-management/releases/latest) page and run it. Java does not need to be installed; it is bundled.
2. On first start, create the branch administrator account. Leave **Load sample data** ticked to try the app right away.
3. With sample data loaded, the sign-in screen shows **Demo customer** and **Demo teller** buttons:

| Side | ID / username | Password |
|---|---|---|
| Customer panel | `12345678950` | `Musteri.2026` |
| Branch (teller) | `veznedar` | `Sube.Demo2026` |

Your data stays in `%APPDATA%\MrcDprm\BankManager`. Uninstall from the Windows "Apps" settings; the data folder is kept and can be deleted by hand.

## Tech Stack

- **Java**, **Maven**
- **JavaFX**: user interface and charts
- **AtlantaFX** (theme), **Ikonli + Feather** (icons)
- **SQLite** (sqlite-jdbc): local database
- **Bouncy Castle**: Argon2id password hashing
- **Apache PDFBox** + **Noto Sans**: PDF receipts and statements with Turkish characters
- **JUnit**: tests
- **jpackage** + **Inno Setup**: Windows installer

## Project Structure

```
src/main/java/com/mrcdprm/bank/
├── core/      Money, IBAN, Turkish ID, password rules and Argon2id, exchange rates, interest, enums
├── data/      SQLite connection, schema, transactions and row records
├── service/   Sign-in, staff, customers, accounts and transfers, history, recipients, sample data
├── pdf/       Receipt and account statement PDFs
├── ui/        Sign-in, setup, branch panel, customer panel pages, dialogs, theme, translations
├── BankApp.java
└── Launcher.java
src/main/resources/   Styles, icon, fonts, Turkish and English texts
src/test/java/        JUnit tests
installer/            Build script, Inno Setup script and icon
```

## Building from Source

Requires a JDK (version in `pom.xml`) and Maven.

```
mvn test          # 43 tests
mvn javafx:run    # run the app
```

### Building the installer

Requires [Inno Setup](https://jrsoftware.org/isinfo.php).

```
powershell -ExecutionPolicy Bypass -File installer\build.ps1
ISCC installer\BankManager.iss
```

`build.ps1` runs the tests, finds the JDK modules the app needs with `jdeps` and uses `jpackage` to create `dist\BankManager` with a trimmed Java runtime (only Turkish and English locale data). The installer is created in `installer\Output\`.

**When releasing a new version:** update the version in `pom.xml`, `BankApp.VERSION`, `installer/build.ps1` and `installer/BankManager.iss`, run both commands and upload the installer to a new GitHub Release.

## What I Learned

- **Money is not a `double`.** `0.1 + 0.2` is not exactly `0.3` in binary, so I store every amount in kuruş as a `long` and only turn it into text at the edge. Parsing what people type was harder than I expected: in Turkish `1.500` means one thousand five hundred, in English `1,500` does, so the parser looks at the language and at the digit groups.
- **Atomic transfers with database transactions.** A transfer is two updates and two history rows. I wrapped them in one transaction so either all of them are saved or none. I put the balance check inside the `UPDATE ... WHERE balance >= ?` statement instead of reading first and writing later, and a test fires 20 transfers from 8 threads at the same account to prove the balance never goes below zero.
- **Validating real-world numbers.** I implemented the IBAN mod-97 check (letters become numbers, then the remainder by 97 must be 1) and the check digits of the Turkish ID number, and used them both to validate input and to generate sample data.
- **Permissions belong in the service layer.** Every service method receives the signed-in session and checks the role and the account owner itself. Hiding a button is only convenience; the tests call the services directly as the "wrong" user and expect a refusal.
- **Password storage done properly.** Argon2id with a random salt in the standard PHC format, a constant-time comparison, a lockout after failed attempts, a fake hash check for unknown users so timing does not reveal who exists, and limits on the stored parameters so a tampered database cannot make the app allocate gigabytes.
- **Testing time.** Maturity dates and daily limits depend on "today", so the services take a `Clock`. In tests I move the clock forward 32 days and check that the interest is paid exactly once.
- **Generating PDFs.** PDFBox draws text at coordinates, so I wrote a small layer with a moving cursor, page breaks with a repeated table header, and "Page 2 / 5" footers added after the last page. The built-in PDF fonts have no `ş`, `ğ` or `ı`, so I embedded Noto Sans.
- **Building a bilingual UI.** All texts live in two properties files, and a test scans the source code to make sure every key used exists in both languages. I avoided `MessageFormat` because it treats the apostrophe in Turkish words like "TL'ye" as a special character.
- **JavaFX compared to Windows Forms.** Layout panes (`BorderPane`, `VBox`, `GridPane`) instead of absolute positions, CSS instead of property grids, and `Task` to keep slow work such as password hashing off the UI thread.

## Future Plans

- A client-server version (REST API and a central database) so customers can sign in from their own device
- Scheduled and recurring transfers
- Bill payments
- Cards and card statements
- Transfers to other banks (EFT/FAST simulation)
- Live exchange rates
- Audit log of staff actions

## License

[MIT](LICENSE) © 2026 Miraç Deprem
