# PaisaTrack Development Notes & Parked Features

## Parked for Future Releases (Scope Shrink - Decision Log)

### 1. R2 — Game-Like Guided Tour (`GuidedTourOverlay`)
- **Status**: Parked.
- **Rationale**: Interactive spotlight overlays in Jetpack Compose require dynamic coordinate calculation (`Modifier.onGloballyPositioned` / `boundsInRoot`) across arbitrary device configurations, screen rotations, and system navigation postures. To keep the app lightweight, robust, and zero-bloat, interactive guided tours are postponed. First-launch focus is directed toward clean onboarding source selection (Start Fresh vs Import Backup).

### 2. R4 Tier-2 — AES-GCM Encrypted Exports (`CryptoBackupManager`)
- **Status**: Parked.
- **Rationale**: Moving the non-negotiable rolling auto-save into app-private internal storage (`context.filesDir/backups/`) eliminates 95% of data-at-rest exposure by leveraging the OS-level Linux UID application sandbox. Password-derived AES-GCM (`.ptenc`) exports introduce irreversible data loss risk if users forget their passphrases. User agency is restored through Requirement R8 (Three-Tier Storage Model), allowing users to pick their own trusted Storage Access Framework (SAF) folder for visible exports while the safety net remains private.

### 3. R3 — Rupee Trademark & Statutory Emblem Compliance
- **Status**: Verified compliant.
- **Audit Findings**: Launcher icon assets use generic Unicode Rupee glyph (`₹`, U+20B9) with zero presence of the State Emblem of India or RBI seals, complying with the State Emblem of India Act (2005) and Trade Marks Act (1999).

### 4. Merge-Based CSV Restore
- **Status**: Parked as a future proposal.
- **Rationale**: Phase 2 standardizes on Overwrite-only restore (`clearExisting = true`) to prevent ledger skew and partial entity collisions.
### 5. Phase 2.10.3 — Dues-Card Canonical Grid Anatomy (Edge-to-Edge Packed Grid, Icon-in-Title-Row, Conditional Header, Red Trash)
### 6. Uncategorized Sentinel Category Schema Approach
- **Status**: Considered and deferred (migration + contract cost this late); the non-blocking UI nudge achieves visibility without it.

### 7. Phase 2.12.1 Media Decisions & Parked Items
- **Phase 2.12.1 Demo Media Section Rule (Supersession)**: Entry sheet completely hides the media capture row, helper copy, and draft chips in demo mode (superseding the 2.12 disable-with-caption rule).
- **Post-launch file_paths.xml hardening (Parked)**: Relocate share CSV to cacheDir/share/, then narrow file_paths.xml from root '.' to <cache-path name="share" path="share/"/> + <cache-path name="media_staging" path="media_staging"/> (FileProvider paths must be directories).
- **Locked Media Decisions**: Voice recording stays at 120s max duration with 64kbps AAC-LC mono; WebP_LOSSY is rejected due to Android API level compatibility variance, and the deterministic 5-rung JPEG compression ladder stands.
