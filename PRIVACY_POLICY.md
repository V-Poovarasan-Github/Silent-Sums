# Privacy Policy — SilentSums

**Effective Date**: November 11, 2026  
**Application**: SilentSums (`app.silentsums`)

## 1. Summary

SilentSums is a 100% offline personal finance application for Android. We do not collect, transmit, store, share, or sell any of your personal or financial information. Because the app does not request the Android `INTERNET` permission, it is technically incapable of sending data over a network.

## 2. What Data the App Handles Locally

When you use SilentSums, the app stores the information you enter directly on your device inside private app storage (`context.filesDir` and a local Room SQLite database):

- **Financial Ledger Records**: Transactions, split transaction lines, dates, descriptions, notes, and custom ledger field values.
- **Organization & Planning Data**: Accounts/sources (cash, bank accounts, credit cards) with opening balances, categories, monthly budgets, and recurring commitments (bills, subscriptions, loans, and IOUs).
- **Optional Media Attachments**: Receipt photos (compressed JPEGs) and voice notes (AAC/M4A audio clips) that you choose to attach to transactions, stored inside your device's private `filesDir/media/` directory.
- **App Preferences & Security State**: Display preferences (theme, currency symbol, privacy mask toggle) and, if you enable App Lock, a salted PBKDF2-HMAC-SHA256 hash of your 4-digit PIN along with brute-force lockout counters.

## 3. How Your Data Is Protected on Your Device

- **No Network Access**: `android.permission.INTERNET` is not declared in `AndroidManifest.xml`. No data is ever transmitted to us or to any third party.
- **No Analytics, Ads, or Third-Party SDKs**: SilentSums contains zero analytics libraries, advertising SDKs, telemetry trackers, or remote crash-reporting tools.
- **Hashed PIN Storage**: If you set a 4-digit PIN, the plaintext PIN is never written to disk. Only a salted `PBKDF2WithHmacSHA256` hash (`120,000` iterations, 16-byte random salt) is stored in local preferences, and it never leaves your device.
- **Cloud Backup Disabled**: Android Auto Backup and device-to-device cloud transfer are disabled (`android:allowBackup="false"` with explicit domain exclusions in `backup_rules.xml` and `data_extraction_rules.xml`), ensuring your ledger database and media files are not silently uploaded to cloud backup servers.
- **Screen Capture Protection**: When the lock screen is active or Privacy Mode (`hideBalances`) is enabled, SilentSums enables `FLAG_SECURE` to prevent screenshots, screen recordings, and Recent Apps thumbnail previews.

## 4. Device Permissions

SilentSums requests permissions only for specific local features that you initiate:

- **Microphone (`RECORD_AUDIO`)**: Requested only when you tap the Voice Memo button on a transaction entry to record an offline audio note.
- **Notifications (`POST_NOTIFICATIONS`)**: Requested on Android 13+ only when you enable local Due Reminders or Auto-Backup status alerts in Settings.
- **Biometrics (`USE_BIOMETRIC`)**: Used locally through Android's `BiometricPrompt` if you enable fingerprint/biometric unlock in Settings.
- **Legacy Storage (`WRITE_EXTERNAL_STORAGE`, Android 9 / API 28 and below only)**: Used on older Android versions when exporting CSV or JSON backup files to your `Downloads` folder.

## 5. Backups, Exports, and Sharing

None of your data leaves your device unless you explicitly export or share a file yourself:

- **CSV Backups (`.csv`)**: Contain your text-based ledger records (transactions, accounts, categories, budgets, commitments, and custom fields). Receipt photos, voice notes, and your PIN hash are **not** included in CSV files.
- **JSON Backups (`.json`)**: Contain your accounts, categories, custom fields, transactions, and a few display settings. Receipt photos, voice notes, and your PIN hash are **not** included in JSON backups.
- **Optional Media Mirror**: Photos and voice notes live only in the app's private storage on your phone. If you turn on the Mirror setting and choose a folder, copies of your attachments are written to that folder so they can survive a reinstall or a new phone. Mirror is off by default and the folder is one you pick.
- **Crash Logs**: If the app encounters an unexpected crash, the stack trace is saved locally in private storage. It is never uploaded automatically; you choose whether to inspect or share it via Android's share sheet.

When you export a backup to a custom folder (via Android's Storage Access Framework or `Downloads`) or share a file through Android's share sheet, that exported file is governed by your own device storage and whichever app or service you choose to send it to.

## 6. Deleting Your Data

Because all data lives exclusively on your device, you have complete control over deletion at any time:

- Delete individual transactions, attachments, accounts, categories, budgets, or commitments directly inside the app.
- Use **Settings → Clear All Data** to wipe the local ledger and media vault.
- Clear the app's storage in Android System Settings or uninstall SilentSums to permanently remove all private app data, databases, preferences, and PIN hashes from your device.

## 7. Contact

If you have questions about this privacy policy or SilentSums, please open an issue on the project repository.
