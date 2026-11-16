# SilentSums (formerly PaisaTrack) Development Log & History

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

## TASK RELEASE-3 — PHASE 2: RELEASE BUILD & R8 (`app/build.gradle.kts`, `gradle/libs.versions.toml`, `proguard-rules.pro`, `themes.xml`, `AppDatabase.kt`, `app/schemas/com.example.data.AppDatabase/10.json`)

1. **2.1 R8 Shrinking & Keep Rules (`app/build.gradle.kts`, `proguard-rules.pro`)**:
   - Enabled `isMinifyEnabled = true` and `isShrinkResources = true` on the `release` build type.
   - Configured `proguard-rules.pro` with source file and line number retention (`SourceFile,LineNumberTable`), Room `@Entity`/`@Dao`/`AppDatabase` rules, `CoroutineWorker` constructors, Kotlinx Serialization rules, and `DataStore` proto rules.
2. **2.2 Configuration-Cache-Compatible Release Signing Guard (`app/build.gradle.kts`)**:
   - Added `CheckReleaseSigningTask` (`checkReleaseSigning`) using `providers.environmentVariable(...)` that throws a `GradleException` listing any missing `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, or `KEY_PASSWORD` variables when release tasks (`preReleaseBuild`, `assembleRelease`, `bundleRelease`, `packageRelease`) run without release credentials, leaving debug builds and unit tests unaffected.
3. **2.3 Theme & Dependency Cleanup (`themes.xml`, `AndroidManifest.xml`, `gradle/libs.versions.toml`)**:
   - Renamed the XML theme from `Theme.PaisaTrack` to `Theme.SilentSums` in `values/themes.xml`, `values-night/themes.xml`, and `AndroidManifest.xml`.
   - Upgraded `androidx.biometric:biometric` to stable `1.1.0` and removed unused template dependencies.
4. **2.4 Room Schema Export (`AppDatabase.kt`, `app/build.gradle.kts`)**:
   - Enabled `exportSchema = true` on `AppDatabase` (version 10), configured KSP `room.schemaLocation` to `$projectDir/schemas`, and generated `app/schemas/com.example.data.AppDatabase/10.json`.

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

---

## TASK RELEASE-3 — PHASE 5: HELP, TIPS AND INFO ACCURACY (`DashboardScreen.kt`, `HelpGesturesSheet.kt`, `AllPagesUiRobolectricTest.kt`, `Polish1Test.kt`, `AuditAndAddendumHarnessTest.kt`, `UiFilterVaultTest.kt`, `Phase5HelpAccuracyTest.kt`)

1. **5.1 Dashboard Trend Chart Hint (`DashboardScreen.kt`)**:
   - Updated `trend_card_hint` from `"Long-press bars for exact dates"` to `"Tap to zoom, hold to open ledger"` to match the actual tap-to-zoom and long-press-to-open-ledger behavior.
2. **5.2 Credit Card Rows Typo Fix (`HelpGesturesSheet.kt`)**:
   - Fixed the `"due.."` double period typo in the `"Pay Credit Card Due?"` entry so it reads: `"Credit Card Rows: TAP opens ledger; HOLD opens a pre-filled transfer of the amount due."`
3. **5.3 Full Audit of All 28 Help Entries Against Live Code**:
   - Verified all 9 Dashboard, 7 Expenses/Ledger, 6 Lists, and 6 Settings/Common Tasks entries against `DashboardScreen.kt`, `LedgerScreen.kt`, `EntryScreen.kt`, `ListsScreen.kt`/`ListsViewModel.kt`, and `SettingsScreen.kt`.
   - Confirmed `ListsScreen.kt` has exactly 4 tabs (`ListsTab.CATEGORIES`, `ListsTab.ACCOUNTS`, `ListsTab.FIELDS`, `ListsTab.BUDGETS` rendered as Categories, Sources, Fields, and Budgets; Commitments/Bills & IOUs lives in the top header Bell sheet, not a 5th tab).
   - Confirmed `"Monthly Budgets: Tap to view rows; hold to edit limit inline."` matches the tap (`viewTransactionsAction`) and hold (`editBudgetLimitAction` / `editBudgetText`) handlers.
4. **5.4 TalkBack Guidance Line (`HelpGesturesSheet.kt`)**:
   - Added `TALKBACK_HELP_NOTE` (`"With TalkBack, use the Actions menu on rows and cards instead of swiping or holding."`) with `testTag("help_gestures_talkback_note")` at the top of `HelpGesturesSheet` across all sheet sections.
5. **5.5 Word-Count & Canonical Test Alignment**:
   - Updated `Polish1Test.kt`, `AuditAndAddendumHarnessTest.kt`, `UiFilterVaultTest.kt`, and `AllPagesUiRobolectricTest.kt` to reflect the `"due."` typo fix and `"Tap to zoom, hold to open ledger"` chart hint.
6. **5.6 Automated Gesture Drift Guard (`Phase5HelpAccuracyTest.kt`)**:
   - Added 5 Robolectric tests in `Phase5HelpAccuracyTest.kt`, including `test5_6_helpEntriesMentioningGestures_haveMatchingHandlerAndCustomAction` which scans every help entry for gesture keywords (`swipe`, `long-press`, `hold`, `drag`) and verifies both source-level handlers and live Compose `SemanticsActions.CustomActions` / `SemanticsActions.OnLongClick` on the target screen, failing immediately if any help entry mentions an unbacked gesture.

---

## TASK RELEASE-3 — PHASE 6: CLEANUP (`res/drawable/*`, `Models.kt`, `HelpGesturesSheet.kt`, `docs/DEVLOG.md`, `NOTES.md`, `shortcuts.xml`, `MainActivitySmokeTest.kt`, `Phase6CleanupTest.kt`)

1. **6.1 Unused Drawables Removed**:
   - Grepped all 21 candidate drawables across `main`, `test`, `androidTest`, XML resources, and `AndroidManifest.xml`; confirmed zero references and deleted all 21 (`ic_launcher_asset.jpg`, `ic_chevron_right.xml`, `ic_calculate.xml`, `ic_expand_less.xml`, `ic_expand_more.xml`, `ic_cloud_download.xml`, `ic_storage.xml`, `ic_currency_rupee.xml`, `ic_sync.xml`, `ic_file_download.xml`, `ic_trending_up.xml`, `ic_account_balance.xml`, `ic_history.xml`, `ic_credit_card.xml`, `ic_dark_mode.xml`, `ic_backspace.xml`, `ic_wallet.xml`, `ic_arrow_downward.xml`, `ic_upload_file.xml`, `ic_arrow_upward.xml`, `ic_light_mode.xml`). Retained `ic_fingerprint_m3.xml`, `ic_launcher_background.xml`, and `ic_launcher_foreground.xml`.
2. **6.2 Dead Code Removed (`Models.kt`, `HelpGesturesSheet.kt`)**:
   - Confirmed zero references across main and test sources and removed `enum class TxType` (`Models.kt`) and `object HelpGesturesCatalog` (`HelpGesturesSheet.kt`).
3. **6.3 & 6.4 Documentation Restructure (`app/applet/NOTES.md`, `docs/DEVLOG.md`, `NOTES.md`)**:
   - Deleted stale `app/applet/NOTES.md`.
   - Moved full development log (including all TASK RELEASE-3 Phases 1–6) to `docs/DEVLOG.md` and replaced root `NOTES.md` with the concise parked-features list.
4. **6.5 Template Test Cleanup & Smoke Test (`MainActivitySmokeTest.kt`, `Phase6CleanupTest.kt`)**:
   - Deleted `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt`.
   - Added `MainActivitySmokeTest.kt` in `androidTest` and `Phase6CleanupTest.kt` in `test` to launch `MainActivity` and verify it reaches the dashboard, onboarding, or lock screen without crashing.
5. **6.6 App Shortcut Icons (`res/xml/shortcuts.xml`, `res/drawable/ic_shortcut_*.xml`)**:
   - Created dedicated vector shortcut icons (`ic_shortcut_add_expense.xml`, `ic_shortcut_add_income.xml`, `ic_shortcut_view_dues.xml`) and updated `shortcuts.xml` so app shortcuts no longer reuse `@drawable/ic_launcher_foreground`.
6. **6.7 Log Hygiene (`MediaRepository.kt`, `DatabaseProvider.kt`, `SettingsViewModel.kt`, `MediaStoreWriter.kt`, `MainActivity.kt`)**:
   - Audited all 16 `Log.*` calls (0 `println` calls): removed 2 leftover `Log.d` calls in `MediaRepository.kt`, wrapped 12 diagnostic `Log.i`/`Log.w`/`Log.e` calls in `if (BuildConfig.DEBUG)`, and preserved the 2 intentional crash-logger fallback `Log.e` calls in `ExpenseTrackerApp.kt`.

---

## TASK F29 — CREDIT CARD OWED FONT REDUCTION & SOURCE ROW BUDGET ALIGNMENT (`DashboardScreen.kt`, `UiFilterVaultTest.kt`, `NOTES.md`)

1. **F29a — Credit Cards Owed Font (`DashboardScreen.kt`)**:
   - Reduced `kpi_credit_cards_owed` fontSize from `24.sp` to `20.sp` while keeping `FontWeight.Black` and `ExpenseRed` (when `owed > 0.0`).
2. **F29b — Source Row WITH Budget (`DashboardScreen.kt`)**:
   - Updated the `Your Money` source row WITH budget to use an outer `Column` with two full-width `Row(horizontalArrangement = Arrangement.SpaceBetween)` lines: Row 1 renders the 32.dp Icon + Account Name on the left and Current Balance + Chevron on the right; Row 2 places the Budget Chip flush left (directly under the Icon) and Opening Balance flush right (directly under Current Balance).
3. **F29c — Source Row WITHOUT Budget (`DashboardScreen.kt`)**:
   - Preserved the existing no-budget source row layout unchanged (Opening Balance stays indented under the Account Name).
4. **UT29 (`UiFilterVaultTest.kt`)**:
   - Added `testUT29_creditCardOwedFontReduction_andSourceRowBudgetAlignment` verifying 20.sp owed font size, flush-left chip / flush-right opening balance in a `SpaceBetween` row when a budget exists, and indented opening balance when no budget exists.

---

## TASK RELEASE-3 — PHASE 7: DOCS AND STORE LISTING (`README.md`, `PLAY_STORE_GUIDE.md`, `PRIVACY_POLICY.md`, `docs/DEVLOG.md`)

1. **7.1 `README.md`**:
   - Removed the *"Prefer the Play Store someday? It's not there on purpose..."* paragraph and the unverified *"Tested on a Redmi and a Samsung M51"* claim.
   - Standardized on the measured Phase 2 release artifact sizes (`3,187,628 bytes` / `3.04 MiB` / `3.19 MB` APK; `6,776,931 bytes` / `6.46 MiB` / `6.78 MB` AAB).
   - Added a factual **Security** section covering salted `PBKDF2WithHmacSHA256` PIN hashing (`120,000` iterations, 16-byte salt), persistent exponential brute-force lockout, PIN scrubbing on CSV/JSON export & restore, dynamic `FLAG_SECURE` window protection, `android:allowBackup="false"` cloud/transfer exclusion, and narrowed `FileProvider` cache subdirectories (`cacheDir/share/` and `cacheDir/media_staging/`).
   - Updated architecture and devlog references to `applicationId = "app.silentsums"`, `Theme.SilentSums`, `docs/DEVLOG.md`, and `PRIVACY_POLICY.md`.
2. **7.2 `PLAY_STORE_GUIDE.md`**:
   - Replaced *"encrypted private storage"* with *"private app storage"* (accurately reflecting that the SQLite database lives in the OS-sandboxed app directory while the PIN is hashed with salted PBKDF2-HMAC-SHA256).
   - Explicitly stated that receipt photos and voice notes are **not** included in CSV backups and are included only in the full JSON backup/restore format.
   - Added the **Google Play Console — Data Safety Form Answer Sheet** (no data collected, no data shared, no `INTERNET` permission, and explicit note that data is not encrypted in transit because nothing is transmitted).
   - Added the **Permission Declarations & Contextual Request Rationale** table covering `RECORD_AUDIO`, `POST_NOTIFICATIONS` (with pre-permission rationale dialog), `USE_BIOMETRIC`, `VIBRATE`, and legacy `WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`).
   - Added a placeholder line for the hosted Privacy Policy HTTPS URL and a complete **Play Store Screenshot Checklist** (Dashboard, Ledger, Entry, Lock screen, Settings) with Google Play Console pixel/aspect-ratio requirements.
3. **7.3 `PRIVACY_POLICY.md`**:
   - Created a plain-language privacy policy matching the verified Phase 1–6 implementation: 100% offline (`android.permission.INTERNET` absent), zero analytics or third-party SDKs, all data in private app storage, salted PBKDF2 PIN hash that never leaves the device, `allowBackup="false"`, `FLAG_SECURE` screen protection, contextual permissions, and user-controlled CSV/JSON exports.

