# SilentSums — Parked Features & Scope Decisions

For the full engineering and release development log (including TASK RELEASE-3 Phases 1–6), see [`docs/DEVLOG.md`](docs/DEVLOG.md).

## Currently Parked Features

1. **R2 — Game-Like Guided Tour (`GuidedTourOverlay`)**
   - **Status**: Parked.
   - **Rationale**: Interactive spotlight overlays in Jetpack Compose require dynamic coordinate calculation across arbitrary device configurations and postures. First-launch focus is directed toward clean onboarding source selection (Start Fresh vs Import Backup).

2. **R4 Tier-2 — AES-GCM Encrypted Exports (`CryptoBackupManager`)**
   - **Status**: Parked.
   - **Rationale**: Rolling auto-saves live inside the app-private OS sandbox (`context.filesDir/backups/`), while visible exports use user-selected Storage Access Framework (SAF) folders. Password-derived `.ptenc` files are parked to avoid irreversible data loss from forgotten passphrases.

3. **Merge-Based CSV Restore**
   - **Status**: Parked as a future proposal.
   - **Rationale**: Restore standardizes on Overwrite-only (`clearExisting = true`) to prevent ledger skew and partial entity collisions.

4. **Uncategorized Sentinel Category Schema**
   - **Status**: Deferred.
   - **Rationale**: The non-blocking UI nudge surfaces uncategorized rows without requiring a sentinel database row migration.

5. **WebP Lossy Compression for Receipt Photos**
   - **Status**: Rejected / Parked.
   - **Rationale**: Due to Android API level compatibility variance, receipt attachments use the deterministic 5-rung JPEG compression ladder; voice notes remain at 120s max duration with 64kbps AAC-LC mono.
