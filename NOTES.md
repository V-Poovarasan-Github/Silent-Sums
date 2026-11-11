# SilentSums (formerly PaisaTrack) Development Notes & Parked Features

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
- **Status**: Implemented and verified via Robolectric tests.

### 6. Uncategorized Sentinel Category Schema Approach
- **Status**: Considered and deferred (migration + contract cost this late); the non-blocking UI nudge achieves visibility without it.

### 7. Phase 2.12.1 Media Decisions & Parked Items
- **Phase 2.12.1 Demo Media Section Rule (Supersession)**: Entry sheet completely hides the media capture row, helper copy, and draft chips in demo mode (superseding the 2.12 disable-with-caption rule).
- **Post-launch file_paths.xml hardening**: Completed in TASK RELEASE-3 Phase 1.6 (`<cache-path name="share" path="share/"/>` + `<cache-path name="media_staging" path="media_staging/"/>`).
- **Locked Media Decisions**: Voice recording stays at 120s max duration with 64kbps AAC-LC mono; WebP_LOSSY is rejected due to Android API level compatibility variance, and the deterministic 5-rung JPEG compression ladder stands.

---

## TASK RELEASE-3 — PHASE 1: SECURITY (`PinHasher.kt`, `AppSettingsRepository.kt`, `LockScreen.kt`, `BackupManager.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `file_paths.xml`, `Phase1SecurityTest.kt`)

1. **1.1 Salted PBKDF2-HMAC-SHA256 PIN Hashing (`PinHasher.kt`)**:
   - Replaced plaintext PIN storage with `PBKDF2WithHmacSHA256` (120,000 iterations, 16-byte `SecureRandom` salt, 256-bit key) formatted as `"v1$<iterations>$<saltB64>$<hashB64>"` using `android.util.Base64` (`NO_WRAP | NO_PADDING`, API 24 compatible) and verified in constant time via `MessageDigest.isEqual`.
   - Injected a `CoroutineDispatcher` (defaulting to `Dispatchers.Default`) into `PinHasher` and `AppSettingsRepository` so verification runs off the main thread without environment-sniffing branches.
   - Transparently upgrades legacy plaintext PINs (`!isHashed(stored)`) to `"v1$..."` on the first successful unlock.
2. **1.2 PIN Scrubbing in Backups (`BackupManager.kt`)**:
   - Omitted `pinHash` and lockout metadata from exported JSON and CSV backups, and ignored any `pinHash` present during backup restore so existing device lock settings remain intact.
3. **1.3 Brute-Force Lockout (`AppSettingsRepository.kt`, `LockScreen.kt`)**:
   - Enforced persistent exponential lockout starting at 5 failed attempts (30s -> 60s -> 120s -> 240s -> 480s -> 900s max) tracked via monotonic `SystemClock.elapsedRealtime()` with wall-clock fallback across process death/reboots.
   - Disabled the numeric keypad until saved lockout state finishes loading (`lockoutStateLoaded`) and while a lockout countdown (`"Try again in M:SS"`) is active, while keeping biometric unlock enabled.
4. **1.4 `FLAG_SECURE` Privacy Protection (`MainActivity.kt`)**:
   - Dynamically sets `WindowManager.LayoutParams.FLAG_SECURE` whenever the app lock screen is active (`lockEnabled && !isUnlocked`) or Privacy Mode (`hideBalances`) is enabled, and clears it otherwise.
5. **1.5 Cloud Backup & Transfer Exclusion (`AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml`)**:
   - Set `android:allowBackup="false"` and excluded `sharedpref`, `database`, `file`, and `root` domains across both `<full-backup-content>` and `<data-extraction-rules>`.
6. **1.6 Narrowed `FileProvider` Paths (`res/xml/file_paths.xml`)**:
   - Replaced broad `<cache-path path="."/>` and `<files-path/>` rules with `<cache-path name="share" path="share/"/>` and `<cache-path name="media_staging" path="media_staging/"/>`, routing all CSV/JSON share exports to `cacheDir/share/` and temporary media viewing files to `cacheDir/media_staging/`.

---

## TASK RELEASE-3 — PHASE 2: RELEASE BUILD & R8 (`app/build.gradle.kts`, `gradle/libs.versions.toml`, `proguard-rules.pro`, `themes.xml`, `AppDatabase.kt`, `app/schemas/com.example.data.local.AppDatabase/10.json`)

1. **2.1 R8 Shrinking & Keep Rules (`app/build.gradle.kts`, `proguard-rules.pro`)**:
   - Enabled `isMinifyEnabled = true` and `isShrinkResources = true` on the `release` build type.
   - Configured `proguard-rules.pro` with source file and line number retention (`SourceFile,LineNumberTable`), Room `@Entity`/`@Dao`/`AppDatabase` rules, `CoroutineWorker` constructors, Kotlinx Serialization rules, and `DataStore` proto rules.
2. **2.2 Configuration-Cache-Compatible Release Signing Guard (`app/build.gradle.kts`)**:
   - Added `CheckReleaseSigningTask` (`checkReleaseSigning`) using `providers.environmentVariable(...)` that throws a `GradleException` listing any missing `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, or `KEY_PASSWORD` variables when release tasks (`preReleaseBuild`, `assembleRelease`, `bundleRelease`, `packageRelease`) run without release credentials, leaving debug builds and unit tests unaffected.
3. **2.3 Theme & Dependency Cleanup (`themes.xml`, `AndroidManifest.xml`, `gradle/libs.versions.toml`)**:
   - Renamed the XML theme from `Theme.PaisaTrack` to `Theme.SilentSums` in `values/themes.xml`, `values-night/themes.xml`, and `AndroidManifest.xml`.
   - Upgraded `androidx.biometric:biometric` to stable `1.1.0` and removed unused template dependencies.
4. **2.4 Room Schema Export (`AppDatabase.kt`, `app/build.gradle.kts`)**:
   - Enabled `exportSchema = true` on `AppDatabase` (version 10), configured KSP `room.schemaLocation` to `$projectDir/schemas`, and generated `app/schemas/com.example.data.local.AppDatabase/10.json`.

---

## TASK RELEASE-3 — PHASE 3: PERMISSIONS (`AndroidManifest.xml`, `MainActivity.kt`, `SettingsScreen.kt`, `EntryScreen.kt`, `Phase1SecurityTest.kt`)

1. **3.1 Removed Cold-Start `POST_NOTIFICATIONS` Request (`MainActivity.kt`, `SettingsScreen.kt`)**:
   - Removed the automatic `POST_NOTIFICATIONS` permission launch from `MainActivity.onCreate`.
   - Added a contextual pre-permission rationale dialog (`notification_permission_rationale_dialog`) in `SettingsScreen.kt` when enabling Due Reminders or Auto-Backup notifications on Android 13+ (API 33+) without permission granted, explaining in one sentence why notification access is needed before launching the runtime permission prompt.
2. **3.2 Verified Contextual `RECORD_AUDIO` Flow (`EntryScreen.kt`)**:
   - Confirmed `RECORD_AUDIO` is requested only when tapping the voice memo button (`entry_media_voice_btn`) and surfaces `"Microphone permission needed for voice notes"` via `SingleFlightNoticeDispatcher` if denied.
3. **3.3 Storage Permission Pruning (`AndroidManifest.xml`)**:
   - Removed unused `READ_EXTERNAL_STORAGE` from `AndroidManifest.xml`, retaining only `WRITE_EXTERNAL_STORAGE` with `android:maxSdkVersion="28"` for legacy API 24–28 Downloads fallback.

---

## TASK RELEASE-3 — PHASE 4A: ACCESSIBILITY — CUSTOM ACTIONS & GESTURE PARITY (`LedgerScreen.kt`, `DashboardScreen.kt`, `MediaViewerSheet.kt`, `Phase4AAccessibilityTest.kt`)

1. **4A.1 Ledger Rows (`LedgerScreen.kt`)**:
   - Single-transaction row (`TransactionCardRow`, `transaction_row_$id`): Added `customActions` `"Delete transaction"` (invoking `viewModel.deleteTransaction(tx)` with the 5-second undo snackbar) and `"Edit transaction"` (invoking `onClick`).
   - Split-group row (`SplitGroupCardRow`, `split_group_card_$groupId`): Added `customActions` `"Expand"` / `"Collapse"` (invoking `onToggleExpand`), `"Edit split"` (invoking `onEditGroup`), and `"Delete all ${splitGroup.lines.size} split lines"` (invoking `viewModel.deleteTransaction(rowItem.representativeTx)`).
2. **4A.2 Dashboard Sources & Credit Card Rows (`DashboardScreen.kt`)**:
   - Credit card source row (`source_row_${cardItem.account.id}`): Added `customActions` `"Pay due via transfer"` and `"View in ledger"`. In `payDueAction`, when `netDebt <= 0.0`, replaced bare Toast with `effectiveNoticeDispatcher.showNotice(key = "NO_PAYMENT_NEEDED_${cardItem.account.id}", message = "No payment needed")` for consistent TalkBack and visual announcement without unannounced Toasts.
   - Non-card source rows (`yourMoney`, `source_row_${accItem.account.id}`): Added `customAction` `"View in ledger"`.
3. **4A.3 Budget Rows (`DashboardScreen.kt` & `ListsScreen.kt`)**:
   - Confirmed in code: On `DashboardScreen.kt`, tapping a budget card (`budget_item_${cb.budget.id}`) navigates to the filtered ledger, while holding (long-pressing) opens an `AlertDialog` (`editingBudget = cb`) to edit the budget limit (`edit_budget_limit_input`). On `ListsScreen.kt`, budget rows use explicit `IconButton` controls (`edit_budget_$id`, `delete_budget_$id`).
   - Added `customActions` `"View transactions"` and `"Edit budget limit"` to the `DashboardScreen.kt` budget card, calling the exact tap and long-press handlers.
4. **4A.4 Trend Chart Bars (`DashboardScreen.kt` `BarColumnView`)**:
   - Added `customActions` `"Zoom in"` (`onTap`), `"Open ledger for this period"` (`onLongPress`), and `"Read exact value"` (announcing the bar's date, amount, and net via `stateDescription` and `effectiveNoticeDispatcher.showNotice`).
5. **4A.5 Other Gesture Sites Audited (`DashboardScreen.kt`, `MediaViewerSheet.kt`)**:
   - `kpi_current_balance` (`DashboardScreen.kt`): Added `customAction` `"View balance breakdown"`.
   - `viewer_photo_card_$index` (`MediaViewerSheet.kt`): Added `customAction` `"Zoom in"` / `"Reset zoom"` mirroring the double-tap zoom toggle.
   - `viewer_voice_card_$index` (`MediaViewerSheet.kt`): Added `customAction` `"Delete voice memo"` mirroring the horizontal swipe-to-reveal delete.
6. **4A.6 Semantics Tests (`Phase4AAccessibilityTest.kt`)**:
   - Added 7 Robolectric Compose UI semantics tests verifying custom action labels and state mutations for single ledger rows, split-group ledger rows, credit-card rows (`netDebt == 0` notice and `netDebt > 0` transfer prefill), budget rows (`View transactions` and `Edit budget limit`), trend bars (`Zoom in`, `Open ledger for this period`, `Read exact value`), and KPI balance breakdown (`View balance breakdown`).

---

## TASK RELEASE-3 — PHASE 4B: ACCESSIBILITY — SEMANTICS, ROLES, TOUCH TARGETS, FONT SCALE (`SingleFlightNoticeDispatcher.kt`, `LockScreen.kt`, `Components.kt`, `CalculatorBottomSheet.kt`, `EntryScreen.kt`, `ListsScreen.kt`, `LedgerScreen.kt`, `DashboardScreen.kt`, `SettingsScreen.kt`, `Phase4BAccessibilityTest.kt`)

1. **4B.1 Icon Content Description Audit**:
   - Audited all ~93 `Icon(..., contentDescription = null)` instances across the app.
   - Identified 5 instances that conveyed standalone meaning without adjacent text or button labels, and fixed them:
     - `ListsScreen.kt`: Dropdown arrow buttons (`lists_category_dropdown_arrow`, `lists_source_dropdown_arrow`) updated to `"Select Category"` and `"Select Source"`.
     - `CalculatorBottomSheet.kt`: Backspace key icon updated to `"Backspace"`.
     - `DashboardScreen.kt`: Monthly and trend delta trend indicators updated to dynamic `contentDescription = if (isUp) "Increased" else "Decreased"`.
   - Left all other null content descriptions untouched because they are strictly decorative (paired with adjacent descriptive `Text` composables or wrapped inside already-labelled parent rows/cards/buttons).
   - Verified all `IconButton` and `HeaderIconButton` components specify non-null content descriptions.
2. **4B.2 Roles & Merged Semantics**:
   - **Roles**: Added explicit `role = Role.Button` to non-standard clickables including `HeaderIconButton` (`Components.kt`), calculator keypad keys (`CalcKey`, `CalcIconKey` in `CalculatorBottomSheet.kt` and `EntryScreen.kt`), lock screen keypad digits (`LockScreen.kt`), and ledger calculator shortcut. Added `role = Role.Tab` with `stateDescription = if (selected) "Selected" else "Not selected"` to transaction type pills (`entry_type_pill_$type` in `EntryScreen.kt`).
   - **Merged Semantics**:
     - `TransactionCardRow` (`LedgerScreen.kt`): `Modifier.semantics(mergeDescendants = true)` announcing unified sentence: `"$title, $category, $type, $amountFormatted"` (or `"$title, $category, $type, hidden"` under privacy mode).
     - `SplitGroupCardRow` (`LedgerScreen.kt`): `Modifier.semantics(mergeDescendants = true)` announcing: `"Split transaction: $note, $category, $lineCount lines, total $amountFormatted"`.
     - `AccountItemCard` (`ListsScreen.kt`): `Modifier.semantics(mergeDescendants = true)` announcing: `"$name, $type, balance $balanceFormatted"`.
     - `CategoryItemRow` (`ListsScreen.kt`): `Modifier.semantics(mergeDescendants = true)` announcing: `"$name category"`.
     - `source_row` items (`DashboardScreen.kt`): `Modifier.semantics(mergeDescendants = true)` announcing coherent source name, account type, and balance/debt summary.
     - `budget_item` cards (`DashboardScreen.kt`): `Modifier.semantics(mergeDescendants = true)` announcing unified sentence: `"$name, budget $limitFormatted, spent $spentFormatted, $percentInt percent, $healthText"`.
   - **Headings**: Added `semantics { heading() }` to section titles across `DashboardScreen.kt` ("Sources & Net Balances", "Your Money", "Credit Cards", "Monthly Budgets", "Spending Insights"), `SettingsScreen.kt` ("Preferences", "Security & Privacy", "Data & Backups"), `ListsScreen.kt` ("Active Categories", "Sources Ledger", "Custom Ledger Fields", "Monthly Spending Limits"), and `LedgerScreen.kt` (Date section headers).
3. **4B.3 State Descriptions & Non-Colour Semantics**:
   - **Settings Switches**: Added `stateDescription = if (checked) "On" else "Off"` across all 9 `Switch` controls in `SettingsScreen.kt`.
   - **Budget Chips**: Added `stateDescription` and accessible text conveying `"under limit"`, `"near limit"`, or `"over limit"` to budget progress indicators.
   - **Non-Colour Semantics**: Added `contentDescription = "Delete"` on the red swipe-to-delete band (`LedgerScreen.kt`), and `mergeDescendants = true` with text `"Demo mode: Viewing sample data"` on the amber demo banner (`DashboardScreen.kt`).
   - **Privacy Mode (`hideBalances = true`)**: Masked monetary amounts throughout `LedgerScreen.kt`, `DashboardScreen.kt`, and `ListsScreen.kt` now announce `contentDescription = "hidden"` instead of bullet characters (`••••••`).
4. **4B.4 Live Regions**:
   - `SingleFlightSnackbarHost` (`SingleFlightNoticeDispatcher.kt`): Marked with `liveRegion = LiveRegionMode.Polite` so snackbars and notices are announced automatically by TalkBack.
   - `LockScreen.kt`: Lockout countdown text marked with `liveRegion = LiveRegionMode.Polite`, and error messages (e.g. incorrect PIN) marked with `liveRegion = LiveRegionMode.Assertive`.
5. **4B.5 Touch Targets**:
   - Applied `Modifier.minimumInteractiveComponentSize()` (or equivalent >= 48dp touch targets) to interactive elements under 48dp, including `HeaderIconButton` (`Components.kt`), category/account/budget list item action buttons (`ListsScreen.kt`), dropdown arrows, split line delete buttons (`EntryScreen.kt`), and calculator keys.
6. **4B.6 Lock Screen Accessibility**:
   - Keypad digits announced with explicit number labels and `role = Role.Button`.
   - Backspace button labeled with `contentDescription = "Delete last digit"`.
   - Submit/unlock button given dynamic `stateDescription = if (isLockedOut) "Locked out" else if (enteredPin.length == 4) "Ready to unlock" else "Incomplete"`.
7. **4B.7 Entry Screen Affordances**:
   - Split lines: Split item delete button given `contentDescription = "Remove line ${index + 1}"` with `minimumInteractiveComponentSize()`; split amount field labeled with `contentDescription = "Split line ${index + 1} of ${state.splitLines.size} amount"`.
   - Voice recording button: Dynamic `stateDescription = if (isRecordingVoice) "Recording" else "Stopped"`.
8. **4B.8 Font Scaling & Layout Elasticity**:
   - Replaced fixed `.height(...)` with elastic `.heightIn(min = ...)` on `EntryScreen.kt` save button (min 52dp) and type selector pills (min 42dp), `LedgerScreen.kt` search bar (min 48dp), and `DashboardScreen.kt` trend empty cards and income/spend charts to ensure no clipping or truncation under 200% system font scaling.
9. **4B.9 Automated Tests (`Phase4BAccessibilityTest.kt`)**:
   - Added 5 Robolectric UI tests validating:
     - `testRoleOnNonStandardClickable_TypePillHasRoleTab`: Verifies `Role.Tab` and `stateDescription` on type selector pills.
     - `testMergeDescendantsOnTransactionCardRow`: Verifies unified card reading on ledger transaction cards.
     - `testHiddenSemanticsUnderPrivacyMode`: Verifies masked amounts output `"hidden"` semantics.
     - `testStateDescriptionOnBudgetCard`: Verifies `"under limit"` state description and composed budget sentence.
     - `testFontScale200PercentNonClipping`: Verifies Compose hierarchy remains accessible and non-clipping at 2.0x font scale.

