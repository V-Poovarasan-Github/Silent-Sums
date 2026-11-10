---

## TASK RELEASE-3 — PHASE 4A: ACCESSIBILITY — CUSTOM ACTIONS & GESTURE PARITY (`LedgerScreen.kt`, `DashboardScreen.kt`, `MediaViewerSheet.kt`, `Phase4AAccessibilityTest.kt`)

1. **4A.1 Ledger Rows (`LedgerScreen.kt`)**:
   - Single-transaction row (`TransactionCardRow`, `transaction_row_$id`): Added `customActions` `"Delete transaction"` (invoking `viewModel.deleteTransaction(tx)` with the 5-second undo snackbar) and `"Edit transaction"` (invoking `onClick`).
   - Split-group row (`SplitGroupCardRow`, `split_group_card_$groupId`): Added `customActions` `"Expand"` / `"Collapse"` (invoking `onToggleExpand`), `"Edit split"` (invoking `onEditGroup`), and `"Delete all ${splitGroup.lines.size} split lines"` (invoking `viewModel.deleteTransaction(rowItem.representativeTx)`).
2. **4A.2 Dashboard Sources & Credit Card Rows (`DashboardScreen.kt`)**:
   - Credit card source row (`source_row_${cardItem.account.id}`): Added `customActions` `"Pay due via transfer"` (calling the identical prefill logic and surfacing `"No payment needed"` via `effectiveNoticeDispatcher.showNotice(key = ..., message = "No payment needed")` when `netDebt <= 0.0`) and `"View in ledger"` (filtering ledger to that account).
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
   - Added 6 Robolectric Compose UI semantics tests verifying custom action labels and state mutations for single ledger rows, split-group ledger rows, credit-card rows (`netDebt == 0` notice and `netDebt > 0` transfer prefill), budget rows, trend bars, and KPI balance breakdown.
