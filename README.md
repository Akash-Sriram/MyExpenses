# MyExpenses (Debloated & Unlocked)

A lightweight, privacy-first, and 100% unlocked personal finance app for Android, fine-tuned for the Indian financial ecosystem.

This repository is a modernized, debloated fork of [MyExpenses](https://github.com/mtotschnig/MyExpenses) focused on peak performance, complete data sovereignty, and zero paywalls.

---

## 🇮🇳 India-Specific Tuning & Integrations

- **UPI Ecosystem Deep Integration**:
  - Local, offline regex and heuristic parser for major Indian UPI payment apps: **Google Pay (GPay)**, **PhonePe**, **Paytm**, **CRED**, **BHIM**, **Navi**, and **Super.money**.
  - Extracts transaction amounts, merchant and recipient names, VPA handles (`@okaxis`, `@okhdfcbank`, `@paytm`, `@ybl`, etc.), and 12-digit UTR/reference numbers directly from payment receipts and notifications.
- **Indian Numbering & Rupee Format**:
  - Native support for the Indian numbering system (Lakhs and Crores, e.g., `₹1,50,000.00`).
  - Rupee (`₹`) as the primary default currency with locale-aware precision.
- **Loan EMI & Amortization Schedules**:
  - Dedicated repository and calculators for Home Loans, Car Loans, Personal Loans, and No-Cost consumer EMIs.
  - Automatically calculates monthly EMI, principal vs. interest breakdown, remaining loan tenure, and premature closure projections.
- **Credit Card Billing Cycles & Due Dates**:
  - Tracks statement generation dates, interest-free grace periods, minimum due alerts, and total bill due dates across major Indian card issuers (HDFC, SBI Card, ICICI, Axis, Kotak, etc.).
- **Tailored Category Presets**:
  - Built-in categories designed for Indian households: FASTag recharges, electricity/water boards, LPG cylinder bookings, broadband, Mutual Fund SIPs, and tax deductions.

---

## Key Pillars of this Fork

### 1. 100% Free & Unlocked Forever
- **No Paywalls or In-App Purchases**: Completely eliminated the proprietary billing stack, license key validation dialogs, and remote calls to external license validation databases.
- **Zero Feature Gates**: All Professional features are available out of the box with no artificial limits:
  - **Unlimited accounts** (removed the legacy 5-account free cap).
  - **Unlimited recurring plans** (removed the 3-plan limit).
  - **Unlimited split transaction templates** (removed the single split template limitation).
  - Full access to budgets, category distribution graphs, CSV/QIF import and export, and audit logs.

### 2. Privacy-First & Zero Tracking
- **No Hardware Identifiers**: Completely purged `Settings.Secure.ANDROID_ID` collection and anti-tamper obfuscation layers.
- **Offline & Local-Only**: Your personal finance data resides strictly in your local SQLite database on your device. Zero telemetry, zero analytics, zero external network pings.

### 3. Aggressive Debloat & Modernized Architecture
- **~148,000+ Lines of Bloat Removed**: Purged 11 legacy submodules and unused legacy components:
  - WebDAV network sync engine (`webdav`)
  - Local HTTP webserver (`webui` & tailwind bundles)
  - Deprecated SQLCrypt wrapper (`sqlcrypt`)
  - Legacy C++ Tesseract OCR module (`tesseract`)
  - Legacy Android 4.4/5.1 SetupWizard library (`setupwizard`)
- **Manifest Hygiene**: Removed deprecated Android 5.1 AccountManager sync permissions (`GET_ACCOUNTS`, `MANAGE_ACCOUNTS` with `maxSdkVersion=22`) and legacy Samsung TouchWiz multiwindow metadata.
- **Streamlined Gradle Build**: Consolidated into a lean, fast-compiling single-module structure (`:myExpenses`).

## Core Features

- **Comprehensive Expense & Income Tracking**: Manage transactions across multiple accounts, currencies, and categories.
- **Commitments & Recurring Schedules**: Set up automatic recurring plans, bills, and loan EMIs.
- **Data Portability**: Full backup and restore using standard SQLite database backups and CSV/QIF formats.
- **Security**: Local device screen lock and biometric protection.
- **Visual Analytics**: Interactive category breakdown charts and historical balance trends.


---

## License & Attribution

- **License**: GNU General Public License v3.0 ([GPL-3.0](LICENSE)).
- **Upstream Project**: Forked from [MyExpenses](https://github.com/mtotschnig/MyExpenses) created by [Michael Totschnig](https://github.com/mtotschnig) and community contributors.
- **Original Credits & Dependencies**: For the full list of third-party libraries, localization contributors, and upstream release notes, refer to the [upstream MyExpenses repository](https://github.com/mtotschnig/MyExpenses).


