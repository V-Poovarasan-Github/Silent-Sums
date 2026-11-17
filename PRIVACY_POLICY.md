# Privacy Policy — SilentSums

**Effective Date**: November 11, 2026  
**Last Updated**: November 17, 2026  
**Application**: SilentSums (`app.silentsums`)

## 1. Summary

SilentSums is a 100% offline personal finance application for Android. We do not collect, transmit, store, share, or sell any of your personal or financial information. Because the app does not request the Android `INTERNET` permission, it is technically incapable of sending data over a network.

## 2. What Data the App Handles Locally

When you use SilentSums, the app stores the information you enter directly on your device inside private app storage (`context.filesDir` and a local Room SQLite database). The only copies kept outside private storage are the CSV files described in section 5:

- **Financial Ledger Records**: Transactions, split transaction lines, dates, descriptions, notes, and custom ledger field values.
- **Organization & Planning Data**: Accounts/sources (cash, bank accounts, credit cards) with opening balances, categories, monthly budgets, and recurring commitments (bills, subscriptions, loans, and IOUs).
- **Optional Media Attachments**: Receipt photos (compressed JPEGs) and voice notes (AAC/M4A audio clips) that you choose to attach to transactions, stored inside your device's private `filesDir/media/` directory.
- **App Preferences & Security State**: Display preferences (theme, currency symbol, privacy mask toggle) and, if you enable App Lock, a salted PBKDF2-HMAC-SHA256 hash of your 4-digit PIN along with brute-force lockout counters.

## 3. How Your Data Is Protected on Your Device

- **No Network Access**: `android.permission.INTERNET` is not declared in `AndroidManifest.xml`. No data is ever transmitted to us or to any third party.
- **No Analytics, Ads, or Third-Party SDKs**: SilentSums contains zero analytics libraries, advertising SDKs, telemetry trackers, or remote crash-reporting tools.
- **Hashed PIN Storage**: If you set a 4-digit PIN, the plaintext PIN is never written to disk. Only a salted `PBKDF2WithHmacSHA256` hash (`120,000` iterations, 16-byte random salt) is stored in local preferences, and it never leaves your device.
- **Cloud Backup Disabled**: Android Auto Backup and device-to-device cloud transfer are disabled (`android:allowBackup="false"` with explicit domain exclusions in `backup_rules.xml` and `data_extraction_rules.xml`), ensuring your ledger database and media files are not uploaded to cloud backup servers. (The auto-saved CSV described in section 5 is a local file, not a cloud backup.)
- **Screen Capture Protection**: While App Lock or Privacy Mode (`hideBalances`) is turned on, SilentSums enables `FLAG_SECURE` to prevent screenshots, screen recordings, and Recent Apps thumbnail previews.

## 4. Device Permissions

SilentSums requests permissions only for specific local features that you initiate:

- **Microphone (`RECORD_AUDIO`)**: Requested only when you tap the Voice Memo button on a transaction entry to record an offline audio note.
- **Notifications (`POST_NOTIFICATIONS`)**: Requested on Android 13+ when you turn on local Due Reminders or Auto-Backup alerts in Settings, or when you save your first commitment while Due Reminders is on. Reminders are sent when an item is overdue, due today or due tomorrow. On a secured lock screen, Android shows only a generic message ("You have a payment or IOU due.") and the item name and amount appear after you unlock. With Privacy Mode on, the amount is also masked in the notification. If your device has no screen lock, or your notification settings differ, Android may show the full text.
- **Biometrics (`USE_BIOMETRIC`)**: Used locally through Android's `BiometricPrompt` if you enable fingerprint/biometric unlock in Settings.
- **Legacy Storage (`WRITE_EXTERNAL_STORAGE`, Android 9 / API 28 and below only)**: Used on older Android versions when saving CSV or JSON files (auto-saves and exports) to your `Downloads` folder.

## 5. Backups, Exports, and Sharing

SilentSums never sends your data anywhere. Readable copies are written to your phone's file storage in the cases below, and sharing a file is always your own action:

- **Daily Auto-Save (on by default)**: Once your ledger has transactions, SilentSums saves a readable `.csv` copy of it to `Downloads/SilentSums/auto/backup.csv` (or to the folder you choose in Settings) once a day and whenever you leave the app. It is a single rolling file, overwritten each time, and it is skipped while your ledger is empty. It is a plain, unencrypted text file, so anyone who can open your phone's files (a file manager, a USB connection, or an app you have given file access) can read it, and PIN lock and Privacy Mode do not apply to it. Switch it off or pick another folder under **Settings → Daily Auto-Save**. A matching copy is also kept in the app's private storage.
- **CSV Files (`.csv`, auto-saved or exported)**: Contain your text-based ledger records (transactions, accounts, categories, budgets, commitments, custom fields, and a few display settings). Receipt photos, voice notes, and your PIN hash are **not** included in CSV files.
- **JSON Exports (`.json`, only when you export)**: Contain your accounts, categories, custom fields, transactions, and a few display settings. Receipt photos, voice notes, and your PIN hash are **not** included in JSON exports.
- **Optional Media Mirror**: Photos and voice notes live only in the app's private storage on your phone. If you turn on the Mirror setting and choose a folder, copies of your attachments are written to that folder so they can survive a reinstall or a new phone. Mirror is off by default and the folder is one you pick.
- **Crash Logs**: If the app encounters an unexpected crash, the stack trace is saved locally in private storage. It is never uploaded automatically; you choose whether to inspect or share it via Android's share sheet.

Once a CSV or JSON file is saved to `Downloads` or a folder you chose, or you send one through Android's share sheet, it is governed by your own device storage and whichever app or service you choose to send it to. SilentSums cannot protect or recall it.

## 6. Deleting Your Data

Because all data lives exclusively on your device, you have complete control over deletion at any time:

- Delete individual transactions, attachments, accounts, categories, budgets, or commitments directly inside the app.
- Use **Settings → Clear All Ledger Data** to wipe the local ledger and media vault.
- Clear the app's storage in Android System Settings or uninstall SilentSums to permanently remove all private app data, databases, preferences, and PIN hashes from your device.
- **CSV files are separate.** Clearing data or uninstalling does not delete CSV or JSON files already saved to `Downloads` or another folder (and because Auto-Save skips an empty ledger, the last auto-saved CSV is left as it was). To remove them, delete the `SilentSums` folder in your Files app and any copies you shared.

## 7. Contact

If you have questions about this privacy policy or SilentSums, please open an issue on the project repository.
