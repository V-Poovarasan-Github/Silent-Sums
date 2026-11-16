# Google Play Store Listing Guide — SilentSums

## 1. Application Name
**SilentSums**

* **Application ID**: `app.silentsums`
* **Version**: `1.0.0` (`versionCode = 1`)
* **Release Artifact Sizes (Measured Phase 2 Release Build)**:
  * **Release APK**: `3,187,628 bytes` (`3.04 MiB` / `3.19 MB`)
  * **Release AAB**: `6,776,931 bytes` (`6.46 MiB` / `6.78 MB`)

---

## 2. Short Description
Offline-first personal finance tracker with zero telemetry

*(Character count: 58 / 80 limit)*

---

## 3. Full Description

**Take complete control of your personal finances with total privacy.**

SilentSums is an offline-first money and expense manager built on a simple premise: your financial life is nobody's business but yours. With zero cloud dependencies, no account logins, and no background analytics, SilentSums keeps every transaction, account balance, and financial receipt strictly on your device.

### Key Features

* **Absolute Privacy**: No accounts, no registration, and no cloud logins required.
* **100% Local Storage**: Your financial ledger stays entirely inside your phone's private app storage (`context.filesDir` / Room SQLite database), with cloud backup disabled (`android:allowBackup="false"`).
* **Open CSV Portability & JSON Snapshot**: Own your data unconditionally. Export and import ledger backups in universal CSV format anytime for spreadsheets, or use the JSON snapshot format for accounts, categories, and transactions. *(Note: Receipt photos and voice notes are NOT included in CSV or JSON backups; they stay in private app storage and can optionally be mirrored to a user-selected folder via the Mirror setting.)*
* **Automated Rolling Backups**: Automatically saves rolling snapshots to private app storage and optional user-selected Storage Access Framework (SAF) or Downloads folders without sending a single byte over the wire.
* **Split Transactions**: Accurately split bills across multiple categories, tracking multi-category purchases and group outings with mathematical precision.
* **Rich Attachments (Photos & Voice Notes)**: Capture receipt photos and dictate voice memos directly alongside transactions, housed in a private on-device media vault (`filesDir/media/`).
* **Smart Dues & Debt Tracker**: Keep clear tabs on recurring bills, subscriptions, loans, and IOUs with partial settlement support, upcoming due reminders, and balance rollups.
* **Built-in Smart Calculator**: Calculate arithmetic expressions directly in number fields with automatic operator evaluation.
* **Custom Categories & Accounts**: Track cash, bank accounts, credit cards, and digital wallets with personalized icons and colors.
* **Privacy Mode & App Lock**: Mask monetary numbers with one tap in public (enforcing `FLAG_SECURE` against screenshots and recent-apps previews), and secure the app behind biometric fingerprint or salted PBKDF2-HMAC-SHA256 PIN lock with brute-force lockout protection.

---

## 4. Target Audience
* Individuals seeking a distraction-free, reliable expense manager that works completely offline.
* Privacy-conscious users who reject data harvesting, tracking SDKs, and third-party financial aggregators.
* Freelancers, students, travelers, and household budget managers who value transparent CSV backups and full data ownership.

---

## 5. Plain-Language Privacy Policy Summary & Hosting URL

* **Hosted Privacy Policy URL**: `https://v-poovarasan-github.github.io/Silent-Sums/privacy.html` *(Matches [`privacy.html`](privacy.html) and [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md); paste this URL into Google Play Console → App content → Privacy policy.)*
* **No Internet Permission (`android.permission.INTERNET`)**: SilentSums does not declare the Android internet permission in its manifest. The application cannot connect to the internet, network servers, or third-party cloud backends.
* **Zero Telemetry or Analytics**: There are no tracking SDKs, no ad networks, no crash collection services, and no analytics libraries embedded in the application.
* **Data Stays On Device**: All ledger records, accounts, categories, budgets, receipt images, voice recordings, and audit logs reside strictly in private app storage on your device. The SQLite database is stored in the OS-sandboxed app data directory (`allowBackup="false"`), and your optional 4-digit PIN is hashed using salted PBKDF2-HMAC-SHA256 (`120,000` iterations).
* **User-Controlled Export & Optional Media Mirror**: You have full control over when and where your data is exported. CSV and JSON exports contain your ledger records without receipt photos, voice notes, or your PIN hash; if you enable the optional Mirror setting and pick a folder, copies of your receipt photos and voice notes are mirrored to that folder so they survive a reinstall or device switch.
* **Crash Log Privacy**: If the app crashes, you can inspect or share the technical stack trace yourself via the Android share sheet; it is never sent automatically and contains no financial amounts or PIN data.

---

## 6. Google Play Console — Data Safety Form Answer Sheet

Use these exact responses when completing **App content → Data safety** in Google Play Console:

| Google Play Data Safety Question | Answer | Technical Justification |
| :--- | :--- | :--- |
| **Does your app collect or share any of the required user data types?** | **No** | SilentSums has no `android.permission.INTERNET` permission, no network client code, and no third-party SDKs. All financial entries, photos, and voice notes remain exclusively on the user's device. |
| **Is all of the user data collected by your app encrypted in transit?** | **N/A (Not prompted when "No" is selected above)** | *Note for reviewer / audit reference*: App data is **not** encrypted in transit because **nothing is transmitted** over any network. |
| **Do you provide a way for users to request that their data is deleted?** | **N/A (Local-only)** | Because no user account or server-side storage exists, users can delete individual records in-app, use **Settings → Clear All Data**, or uninstall the app / clear app storage in Android Settings to permanently erase all private app data. |
| **Does your app use Android Auto Backup or Cloud Backup?** | **No** | `android:allowBackup="false"` is set in `AndroidManifest.xml`, and `res/xml/backup_rules.xml` + `res/xml/data_extraction_rules.xml` explicitly exclude `sharedpref`, `database`, `file`, and `root` domains from cloud backup and device-to-device transfer. |

---

## 7. Permission Declarations & Contextual Request Rationale

Use the following declarations for Google Play Console review notes and permission justification prompts:

| Permission | Manifest Scope | When & How Requested (Post-Phase 3) | Play Console Justification Text |
| :--- | :--- | :--- | :--- |
| `android.permission.RECORD_AUDIO` | Standard runtime permission | **Contextual only** — Never requested on launch. Requested in `EntryScreen.kt` only when the user explicitly taps the **Voice Memo (`Mic`)** button inside the New/Edit Transaction sheet to record an audio note attached to a transaction. If denied, a non-blocking snackbar (`"Microphone permission needed for voice notes"`) is shown and all other ledger features continue normally. | *"Used exclusively when the user taps the Voice Memo button on a transaction entry to record an offline audio note stored in private app storage. Audio is never transmitted off the device."* |
| `android.permission.POST_NOTIFICATIONS` | Standard runtime permission (Android 13 / API 33+) | **Contextual with Pre-Permission Rationale Dialog** — Never requested on cold start. Requested in `SettingsScreen.kt` only when the user enables **Due Reminders** or **Auto-Backup Notifications**. Before invoking the Android system permission prompt, SilentSums displays a one-sentence rationale dialog (`notification_permission_rationale_dialog`) explaining why notification access is needed. | *"Requested only when the user turns on local Due Reminders or Auto-Backup status alerts in Settings, after showing an in-app explanation dialog."* |
| `android.permission.USE_BIOMETRIC` | Normal install-time permission | Used on `LockScreen.kt` only when the user has enabled App Lock with Biometric Unlock in Settings. | *"Allows optional fingerprint/biometric authentication to unlock the local app lock screen."* |
| `android.permission.VIBRATE` | Normal install-time permission | Used for optional tactile haptic feedback when enabled in Settings. | *"Provides optional haptic feedback on keypad and gesture interactions."* |
| `android.permission.WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) | Legacy API 24–28 only | Scoped strictly to Android 7.0–9.0 (`maxSdkVersion="28"`) for writing user-requested CSV/JSON exports to the `Downloads` folder. Android 10+ uses zero-permission `MediaStore.Downloads` and Storage Access Framework (SAF). `READ_EXTERNAL_STORAGE` is not declared. | *"Used only on Android 9 (API 28) and below so users can export their CSV/JSON backup files to the Downloads directory."* |

---

## 8. Play Store Screenshot Checklist

Capture real, unframed screenshots on a physical device or high-resolution emulator before submitting the store listing.

### Required Screens to Capture (Minimum 5 Recommended)
1. **Dashboard Screen (`DashboardScreen`)**:
   * Show the Net Balance hero card, Monthly Cashflow KPI tiles, Credit Cards summary (`20.sp` owed header + sub-tiles), Sources & Net Balances (`Your Money` row with flush-left Budget Chip & flush-right Opening Balance), Monthly Budgets progress cards, and Spending Insights trend chart.
2. **Ledger Screen (`LedgerScreen`)**:
   * Show grouped daily transactions, category emojis, multi-category split transaction expansion, search/filter bar, and running balance pills.
3. **Transaction Entry Sheet (`EntryScreen`)**:
   * Show the Expense / Income / Transfer type selector pills, smart inline expression calculator (e.g., `170+45`), split-line editor, and receipt photo / voice note attachment chips.
4. **Lock Screen (`LockScreen`)**:
   * Show the 4-digit PIN pad and Biometric unlock prompt (*note: temporarily disable `FLAG_SECURE` in a debug capture build or use Android Studio Layout Inspector/emulator capture, since `FLAG_SECURE` blocks screenshots on the lock screen in production builds*).
5. **Settings & Data Ownership Screen (`SettingsScreen`)**:
   * Show Privacy Mode toggle, PIN & Biometric Security controls, Due Reminders toggle, and the CSV / Full JSON Backup & Restore section.

### Google Play Console Technical Specifications for Screenshots
* **Phone Screenshots**:
  * **Count**: 2 to 8 screenshots (capture all 5 screens above).
  * **Format**: 24-bit PNG or JPEG (no alpha/transparency).
  * **Aspect Ratio**: `9:16` (portrait) or `16:9` (landscape); maximum dimension may not exceed `2x` the minimum dimension.
  * **Pixel Dimensions**: Minimum `320 px`, maximum `3840 px` per side; recommended portrait resolution is **`1080 x 1920 px`** (or `1080 x 2400 px` for modern 20:9 displays).
* **7-inch & 10-inch Tablet Screenshots (Optional / Recommended for Large Screen Quality)**:
  * **Count**: Up to 8 screenshots per form factor.
  * **Recommended Resolution**: `1200 x 1920 px` (portrait) or `1920 x 1200 px` (landscape), `9:16` or `16:9`.
* **Feature Graphic (Mandatory for Play Store Listing)**:
  * **Dimensions**: Exactly **`1024 x 500 px`**, 24-bit PNG or JPEG (no alpha).
* **High-Res App Icon (Mandatory for Play Store Listing)**:
  * **Dimensions**: Exactly **`512 x 512 px`**, 32-bit PNG, maximum file size `1024 KB`.
