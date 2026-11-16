# SilentSums

> **Your money. Your phone. Nobody else's business.**
> A 100% offline personal finance app for Android. No account. No cloud. No ads. No analytics. No tracking. Just a fast, local ledger that lives entirely on your device — and a CSV you can read, edit, and walk away with, forever.

**3.19 MB release APK (3.04 MiB / 3,187,628 bytes).** Works in airplane mode. Zero network permissions.

---

## Why this exists

Most "free" money apps are a trap: 100+ MB, an account before you can type a single rupee, ads on your salary screen, and your entire financial life quietly syncing to someone else's server. Then one day they pivot, get acquired, or shut down — and your data is hostage.

SilentSums is the opposite bet. Built for real life: the ₹170 tea on the highway home, the hospital tablets for Mom, Dad's cash salary kept in the pooja room, four Paradise movie tickets, the car EMI that auto-records itself on the 10th. If an app can't handle *that* without phoning home, it isn't yours.

## What it does

- **🔒 Truly offline.** Declares zero network permissions (`android.permission.INTERNET` is absent from the manifest). Your balances never leave the phone unless *you* export them.
- **😀 Emoji categories.** Tag spending with 🍽️ 🏥 💰 — or paste any emoji you want, because a budget shouldn't feel like homework. Colors + glyphs are yours to choose.
- **👁️ Privacy mode.** One tap turns every number into `••••` (with TalkBack announcing `"hidden"`). Open it on the bus, in the office, in front of anyone — no shoulder-surfing, no embarrassment.
- **🔐 PIN lock + grace.** Optional 4-digit PIN and biometric app lock with a configurable grace window, so returning to the app doesn't nag you every 30 seconds.
- **🔁 Dues & recurring engine.** Subscriptions, EMIs, loans, IOUs — with due-date reminders, "Record & mark paid" that writes the ledger entry *and* advances the cycle, and skip-cycle for the month you don't pay.
- **💳 Multi-source net worth.** Cash pockets, bank accounts, and credit-card debt tracked separately; see your true net position at a glance.
- **📊 Budget guardrails.** Per-category and per-account limits with live green/amber/red status chips, so overspending is visible before the statement arrives.
- **📈 Trends that respect you.** Running-balance and income-vs-spend charts with tap-to-zoom, hold-to-open-ledger, and accessible exact-value announcements.
- **🧾 CSV & JSON you own.** Export your ledger to a human-readable `expense_tracker.csv` (or a full `.json` backup including receipt photos and voice memos); import it back anytime, on any device, into any tool. No proprietary lock-in.
- **🪶 Featherweight.** 3.19 MB R8-minified release APK (6.78 MB AAB). Installs in seconds, opens instantly, sips battery.

## Security

SilentSums enforces defense-in-depth for local financial data:

- **Salted PBKDF2-HMAC-SHA256 PIN Hashing (`PinHasher.kt`)**: User PINs are never stored in plaintext. PINs are hashed off the main thread using `PBKDF2WithHmacSHA256` with 120,000 iterations, a 16-byte `SecureRandom` salt, and a 256-bit derived key (`"v1$<iterations>$<saltB64>$<hashB64>"`), verified in constant time via `MessageDigest.isEqual`.
- **Persistent Brute-Force Lockout**: Failed PIN attempts are tracked across process death and device reboots using monotonic `SystemClock.elapsedRealtime()` with wall-clock fallback. Starting at 5 failed attempts, exponential backoff locks the keypad (`30s → 60s → 120s → 240s → 480s → 900s` max) while keeping biometric authentication available.
- **PIN Scrubbing on Export/Restore**: Exported CSV and JSON backups omit `pinHash` and lockout metadata, and backup restores ignore any external PIN fields so local device lock settings cannot be overwritten or leaked by a backup file.
- **`FLAG_SECURE` Window Protection**: `WindowManager.LayoutParams.FLAG_SECURE` is dynamically applied whenever the lock screen is active or Privacy Mode (`hideBalances`) is enabled, blocking screenshots, screen recording, and Recent Apps thumbnails of sensitive balances.
- **Cloud Backup & Device Transfer Disabled (`allowBackup="false"`)**: `android:allowBackup="false"` is enforced alongside explicit `<full-backup-content>` and `<data-extraction-rules>` exclusions across `sharedpref`, `database`, `file`, and `root` domains so ledger data is never silently uploaded to cloud backup providers.
- **Narrowed `FileProvider` Scope**: `res/xml/file_paths.xml` exposes only two dedicated cache subdirectories (`cacheDir/share/` for user-initiated CSV/JSON share sheets and `cacheDir/media_staging/` for temporary attachment viewing), never the root `filesDir` or database directory.

## Privacy & security, in plain words

| Promise | Reality |
| --- | --- |
| No account | There is no signup, no login, no server to log in to. |
| No cloud | Data lives in a local Room (SQLite) database in private app storage (`app.silentsums`). |
| No tracking | No analytics SDK, no ad SDK, no crash-reporting upload. |
| No network capability | No `INTERNET` permission in `AndroidManifest.xml`. |
| Your data, portable | One-tap CSV export/import for spreadsheets, plus full JSON backup/restore with media attachments. |
| Lockable | Salted PBKDF2 PIN + biometric unlock with grace period and `FLAG_SECURE` privacy mask. |

See [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) for the full plain-language privacy policy and [`PLAY_STORE_GUIDE.md`](PLAY_STORE_GUIDE.md) for Google Play Data Safety details.

## Get it

Grab the latest signed build from **[Releases](../../releases/latest)** → `app-release.apk` (3.19 MB / 3,187,628 bytes), or install via Google Play once published.

> Signed with a stable release key, so future versions install **over** this one with no data loss and no uninstall.

## Under the hood

- **Application ID & Theme** — `applicationId = "app.silentsums"`, `Theme.SilentSums`, version `1.0.0` (`versionCode = 1`).
- **Kotlin + Jetpack Compose (Material 3)** — native UI with TalkBack custom actions, semantic roles, merged card announcements, and 200% font-scale elasticity.
- **Room (Schema v10)** — explicit, non-destructive migrations (`1 → 10`) with exported schema JSON (`app/schemas/com.example.data.local.AppDatabase/10.json`).
- **WorkManager** — periodic background auto-save and due reminders with zero foreground service bloat.
- **MediaStore / SAF** — scoped-storage-compliant CSV and JSON I/O.
- **Verification** — 417 unit and Robolectric Compose UI tests across 42 test classes (`gradle testDebugUnitTest`), plus `MainActivitySmokeTest` in `androidTest`.
- **Min SDK 24 (Android 7.0)**, Target/Compile SDK **36**.
- **Engineering Log** — see [`docs/DEVLOG.md`](docs/DEVLOG.md) for the full architecture and release engineering history, and [`NOTES.md`](NOTES.md) for parked scope items.

## Roadmap (v1.1+)

- 🪙 Animated launch (a ₹1 coin that flips into the logo).
- 🗄️ Personal Vault: a second, separately-PIN-locked space.
- 🌐 A companion **PWA** so your CSV opens in any browser, anywhere — same data, no install.

## Feedback

Built solo. Bug reports and feedback via GitHub issues are welcome — especially from anyone who's been burned by a "free" finance app and wants a real offline alternative. If SilentSums saved your sanity (or your data), a ⭐ on the repo is the best thank-you.

## License

Copyright (c) 2026 V Poovarasan. All rights reserved. This source code is provided for viewing purposes only; no permission is granted to use, copy, modify, or distribute it, in whole or in part, for any purpose.

---

<p align="center"><sub>Made with too much coffee and a healthy distrust of the cloud. 🇮🇳</sub></p>
