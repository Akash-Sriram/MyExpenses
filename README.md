# MyExpenses (Debloated & Unlocked)

<div align="center">

![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square)
![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?style=flat-square)
![Kotlin](https://img.shields.io/badge/Language-Kotlin%20%2F%20Compose-purple.svg?style=flat-square)
![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline-orange.svg?style=flat-square)
![Unlocked](https://img.shields.io/badge/Status-100%25%20Unlocked-brightgreen.svg?style=flat-square)
![Visibility](https://img.shields.io/badge/Visibility-Private-red.svg?style=flat-square)

**A high-performance, privacy-first, and 100% unlocked personal finance manager for Android, tailored for the Indian financial ecosystem.**

[Key Highlights](#-key-highlights) • [India-Specific Features](#-india-specific-tuning--integrations) • [Architectural Enhancements](#-architectural-enhancements) • [Build & Install](#-building--installation) • [Attribution](#-license--attribution)

</div>

---

## 🌟 Overview

This repository is a modern, privacy-focused fork of [MyExpenses](https://github.com/mtotschnig/MyExpenses). Built with modern Jetpack Compose and Kotlin, it eliminates all proprietary paywalls, strips legacy bloat, introduces robust atomic backup pipelines, and integrates deeply with everyday financial workflows like UPI receipts, credit card billing cycles, and loan amortization.

> [!NOTE]
> **Personal & Standalone Fork**: This repository is strictly configured and maintained for personal daily use on specific hardware (Samsung Galaxy S24 and POCO F1). All development, issues, and changes are handled locally for this private workflow; external issues and pull requests are not tracked.

---

## ✨ Key Highlights

### 1. 🛡️ 100% Free & Unlocked Forever
- **Zero In-App Purchases or Paywalls**: Completely eliminated the proprietary licensing stack, Google Play Billing dependencies, and license validation dialogs.
- **Unlimited Capabilities**:
  - **Unlimited Accounts** (removed the legacy 5-account limit).
  - **Unlimited Recurring Plans & Commitments** (removed the 3-plan cap).
  - **Unlimited Split Templates**.
  - Full access to budgets, category distribution graphs, CSV/QIF import/export, and audit logs.

### 2. 🔒 Strict Privacy & Complete Data Sovereignty
- **Local-Only Storage**: All financial records, receipts, and attachments reside strictly in your device's local SQLite database.
- **Zero Tracking**: Completely purged `Settings.Secure.ANDROID_ID` collection, anti-tamper tracking layers, and third-party analytics.
- **Biometric Security**: Native Android biometric authentication and device screen lock support.

### 3. 💾 Solid Single-Copy Atomic Full Backup
- **Fast Monolithic Packaging**: Employs high-throughput 64 KB buffers and `Deflater.NO_COMPRESSION` for picture attachments, packing entire databases with thousands of receipts in **~30–35 seconds** (down from 10+ minutes).
- **Atomic Verification Pipeline**: Backups are verified inside a staging sandbox using SQLite `PRAGMA integrity_check` before replacing older files. If validation fails, your existing healthy backup remains 100% untouched.
- **Pruning & Single-Copy Retention**: Enforces strict retention (1 verified solid `.zip` archive, ~220 MB), preventing storage accumulation on device.
- **Silent Automated Daily Backups**: Daily background backups dispatched via WorkManager (`AutoBackupWorker`) execute and prune seamlessly without requiring user intervention.

### 4. 🎛️ Modernized, Clutter-Free UI
- **Direct Top-Bar Action Icons**: Replaced the legacy 3-dot overflow menu with direct, one-tap icon buttons:
  - 🔍 **Search**: Quick search toggle.
  - 🎛️ **Options**: Instant access to sorting and grouping preferences.
  - 📦 **Archive**: Direct account archiving.
- **Smart Account-Bounded Archiving**:
  - Automatically queries active transaction bounds (`min(date)` to `max(date)`).
  - Constrains the date picker's year range strictly to the account's active years (no scrolling back to 1970).
  - One-tap quick selection preset chips: `FY <prevYear>` (e.g. FY 2025) and `All Active (<minYear>–<maxYear>)`.

---

## 🇮🇳 India-Specific Tuning & Integrations

- **UPI Ecosystem Heuristic Parser**:
  - Local, offline parser recognizing transaction receipts and notifications from **Google Pay**, **PhonePe**, **Paytm**, **CRED**, **BHIM**, **Navi**, and **Super.money**.
  - Extracts amounts, merchant/recipient names, VPA handles (`@okaxis`, `@okhdfcbank`, `@paytm`, `@ybl`, etc.), and 12-digit UTR/reference numbers.
- **Indian Numbering & Rupee Format**:
  - Native Lakhs and Crores formatting (e.g., `₹1,50,000.00`).
  - Rupee (`₹`) configured as the default base currency.
- **Loan EMI & Amortization Schedules**:
  - Dedicated calculators for Home, Car, Personal, and No-Cost consumer EMIs.
  - Computes monthly EMI, principal vs. interest breakdown, loan tenure remaining, and prepayment projections.
- **Credit Card Billing Cycles & Due Dates**:
  - Tracks statement dates, grace periods, minimum due alerts, and total bill dates across major Indian card issuers (HDFC, SBI Card, ICICI, Axis, Kotak).
- **Localized Category Presets**:
  - Pre-configured categories for FASTag, electricity boards, LPG cylinder bookings, broadband, Mutual Fund SIPs, and tax deductions.

---

## ⚡ Architectural Enhancements

- **~148,000+ Lines of Legacy Bloat Removed**:
  - Stripped WebDAV network sync engine (`webdav`).
  - Stripped internal HTTP webserver (`webui` and tailwind web assets).
  - Removed deprecated SQLCrypt wrapper (`sqlcrypt`).
  - Removed legacy C++ Tesseract OCR module (`tesseract`).
  - Purged legacy Android 4.4/5.1 SetupWizard (`setupwizard`).
- **Modern Android Manifest Hygiene**:
  - Dropped deprecated Android 5.1 AccountManager sync permissions (`GET_ACCOUNTS`, `MANAGE_ACCOUNTS`).
  - Removed legacy Samsung TouchWiz multiwindow metadata.
- **Single-Module Gradle Build**:
  - Consolidated build into a streamlined, fast-compiling single module (`:myExpenses`).
  - R8 full minification and resource shrinking enabled for production builds.

---

## 📱 Tech Stack

- **UI**: Jetpack Compose, Material 3, Adaptive Navigation Suite
- **Architecture**: MVVM, Kotlin Coroutines & Flow, Dagger 2
- **Data**: SQLite (Room & ContentProvider), Jetpack DataStore Preferences
- **Background Work**: AndroidX WorkManager
- **Charts**: MPAndroidChart, Donut Chart
- **Target OS**: Android 8.0 (API 26) through Android 15 (API 35)

---

## 🛠️ Building & Installation

### Prerequisites
- JDK 17 or JDK 21
- Android SDK (API 35, Build-Tools 35.0.0)

### Clone the Repository
```bash
git clone git@github.com:Akash-Sriram/MyExpenses.git
cd MyExpenses
```

### Build APKs
- **Debug Build**:
  ```bash
  ./gradlew assembleInternDebug
  ```
  *Output:* `myExpenses/build/outputs/apk/intern/debug/myExpenses-intern-debug.apk`

- **Optimized Release Build**:
  ```bash
  ./gradlew assembleInternRelease
  ```
  *Output:* `myExpenses/build/outputs/apk/intern/release/myExpenses-intern-release.apk`

### Install on Device via ADB
```bash
adb install -r myExpenses/build/outputs/apk/intern/release/myExpenses-intern-release.apk
```

---

## 📄 License & Attribution

- **License**: Released under the GNU General Public License v3.0 ([GPL-3.0](LICENSE)).
- **Upstream Project**: Forked with gratitude from [MyExpenses](https://github.com/mtotschnig/MyExpenses), originally created by [Michael Totschnig](https://github.com/mtotschnig) and community contributors.
- **Third-Party Libraries**: Refer to upstream repository for full acknowledgments of foundational open-source dependencies.
