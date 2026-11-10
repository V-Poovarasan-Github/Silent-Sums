# SilentSums

> **Your money. Your phone. Nobody else's business.**
> A 100% offline personal finance app for Android. No account. No cloud. No ads. No analytics. No tracking. Just a fast, beautiful ledger that lives entirely on your device — and a CSV you can read, edit, and walk away with, forever.

**~45.5 MB installed.** Works in airplane mode. Asks for the internet *once*, for nothing.

---

## Why this exists

Most "free" money apps are a trap: 100+ MB, an account before you can type a single rupee, ads on your salary screen, and your entire financial life quietly syncing to someone else's server. Then one day they pivot, get acquired, or shut down — and your data is hostage.

SilentSums is the opposite bet. Built by one person, for real life: the ₹170 tea on the highway home, the hospital tablets for Mom, Dad's cash salary kept in the pooja room, four Paradise movie tickets, the car EMI that auto-records itself on the 10th. If an app can't handle *that* without phoning home, it isn't yours.

## What it does

- **🔒 Truly offline.** Core features need zero network permission. Your balances never leave the phone unless *you* export them.
- **😀 Emoji categories.** Tag spending with 🍽️  🏥  💰 — or paste any emoji you want, because a budget shouldn't feel like homework. Colors + glyphs are yours to choose.
- **👁️ Privacy mode.** One tap turns every number into `••••`. Open it on the bus, in the office, in front of anyone — no shoulder-surfing, no embarrassment.
- **🔐 PIN lock + grace.** Optional app lock with a configurable grace window, so returning to the app doesn't nag you every 30 seconds.
- **🔁 Dues & recurring engine.** Subscriptions, EMIs, loans, IOUs — with due-date reminders, "Record & mark paid" that writes the ledger entry *and* advances the cycle, and skip-cycle for the month you don't pay.
- **💳 Multi-source net worth.** Cash pockets, bank accounts, and credit-card debt tracked separately; see your true net position at a glance.
- **📊 Budget guardrails.** Per-category and per-account limits with live green/amber/red chips, so overspending screams before the statement does.
- **📈 Trends that respect you.** Running-balance and income-vs-spend charts with drill-down and long-press exact values — no vanity dashboards.
- **🧾 CSV you own.** Export everything to a single human-readable `expense_tracker.csv`; import it back anytime, on any device, into any tool. No proprietary lock-in. Ever.
- **🪶 Featherweight.** ~9.5 MB. Installs in seconds, opens instantly, sips battery.

## Privacy & security, in plain words

| Promise | Reality |
| --- | --- |
| No account | There is no signup, no login, no server to log in to. |
| No cloud | Data lives in a local Room (SQLite) database on your phone. |
| No tracking | No analytics SDK, no ad SDK, no crash-reporting upload. |
| No hidden network calls | Offline-first by architecture; the internet is optional, not required. |
| Your data, portable | One-tap CSV export/import. Read it in Excel. Git it. Take it elsewhere. |
| Lockable | Optional PIN with grace period; privacy mask for public use. |

## Get it

Grab the latest signed build from **[Releases](../../releases/latest)** → `app-release.apk`. On your phone, allow **"Install unknown apps"** for your browser/file manager (Android asks once), tap the APK, install, open, set a PIN if you like. Done.

> Signed with a stable release key, so future versions install **over** this one with no data loss and no uninstall.

*Prefer the Play Store someday? It's not there on purpose — the store's data-safety forms would be a fiction we'd have to lie on. This is a direct-download app, the honest way.*

## Under the hood

- **Kotlin + Jetpack Compose (Material 3)** — native, smooth, no WebView, no Flutter bloat.
- **Room** with explicit, non-destructive migrations (your history survives every upgrade).
- **WorkManager** for background auto-save and due reminders — battery-friendly, no foreground services pretending to be useful.
- **MediaStore / SAF** for CSV I/O — plays nice with scoped storage on modern Android.
- Min SDK **24 (Android 7.0)**. Tested on a Redmi and a Samsung M51.

## Roadmap (v1.1+)

- 🪙 Animated launch (a ₹1 coin that flips into the logo — because money apps deserve a moment of joy).
- 🗄️ Personal Vault: a second, separately-PIN-locked space with its own encrypted CSV.
- 🌐 A companion **PWA** so your CSV opens in any browser, anywhere — same data, no install.
- 🧪 Test-suite realignment for the surviving unit tests.

## Contributing / feedback

Built solo, shipped in the open. Issues and PRs welcome — especially from anyone who's been burned by a "free" finance app and wants a real offline alternative. If SilentSums saved your sanity (or your data), a ⭐ on the repo is the best thank-you.

## License

MIT — do whatever you want with it, including forking your own fork. Just don't sell my privacy promises back to me as a subscription.

---

<p align="center"><sub>Made with too much coffee and a healthy distrust of the cloud. 🇮🇳</sub></p>
