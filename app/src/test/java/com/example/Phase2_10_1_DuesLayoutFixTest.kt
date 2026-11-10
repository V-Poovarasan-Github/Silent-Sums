package com.example

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.Account
import com.example.data.model.Commitment
import com.example.domain.commitment.CommitmentGroups
import com.example.domain.commitment.CommitmentItem
import com.example.ui.commitment.CommitmentsPopup
import com.example.ui.commitment.PartialSettleSheet
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class Phase2_10_1_DuesLayoutFixTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testRecurringCard_allFiveControlsExistAndAreClickable_andLiveInSeparateRows() {
        val recurring = Commitment(
            id = 101L,
            type = "RECURRING",
            name = "Netflix & Spotify Family",
            amount = 799.0,
            direction = "OUTGOING",
            dayOfMonth = 15,
            active = true
        )
        val groups = CommitmentGroups(
            overdue = emptyList(),
            next7Days = listOf(CommitmentItem(recurring, null, "in 1 day", false)),
            later = emptyList(),
            noDueDate = emptyList(),
            badgeCount = 1
        )

        var paidClicked = false
        var skipClicked = false
        var editClicked = false
        var deleteClicked = false

        composeTestRule.setContent {
            MyApplicationTheme {
                CommitmentsPopup(
                    groups = groups,
                    accounts = listOf(Account(id = 1L, name = "Personal", accountType = "BANK", openingBalance = 5000.0)),
                    categories = emptyList(),
                    accountBalances = mapOf(1L to 5000.0),
                    currencySymbol = "₹",
                    onDismiss = {},
                    onRecordAndMarkPaid = { paidClicked = true },
                    onRecordPartialPayment = { _, _, _, _, _, _ -> },
                    onSkipCycle = { skipClicked = true },
                    onSettleIou = { _, _, _ -> },
                    onSaveCommitment = { editClicked = true },
                    onDeleteCommitment = { deleteClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify Settle Row: Settle button flush left, edit & delete icon buttons flush right
        composeTestRule.onNodeWithTag("commitment_settle_row_101", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("record_paid_btn_101")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue("Record & mark paid should be clicked", paidClicked)

        composeTestRule.onNodeWithTag("edit_commitment_btn_101")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeTestRule.onNodeWithTag("delete_commitment_btn_101")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue("Delete should be clicked", deleteClicked)

        // Verify Action Row: [Partial] [Skip cycle] (50/50)
        composeTestRule.onNodeWithTag("commitment_action_row_101", useUnmergedTree = true).assertExists()
        composeTestRule.onAllNodes(hasClickAction() and hasParent(hasTestTag("commitment_action_row_101")), useUnmergedTree = true)
            .assertCountEquals(2)

        composeTestRule.onNodeWithTag("partial_settle_btn_101")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeTestRule.onNodeWithTag("skip_cycle_btn_101")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue("Skip cycle should be clicked", skipClicked)
    }

    @Test
    fun testIouCard_withPartial_allControlsExistAndAreClickable_andLiveInSeparateRows() {
        val iou = Commitment(
            id = 202L,
            type = "IOU",
            name = "Dental Treatment",
            amount = 2500.0,
            direction = "OUTGOING",
            dueDate = System.currentTimeMillis() + 86400000L,
            accountId = 1L,
            active = true,
            paidSoFar = 500.0
        )
        val groups = CommitmentGroups(
            overdue = emptyList(),
            next7Days = listOf(CommitmentItem(iou, null, "in 3 days", false)),
            later = emptyList(),
            noDueDate = emptyList(),
            badgeCount = 1
        )

        var settleClicked = false
        var deleteClicked = false

        composeTestRule.setContent {
            MyApplicationTheme {
                CommitmentsPopup(
                    groups = groups,
                    accounts = listOf(Account(id = 1L, name = "Personal", accountType = "BANK", openingBalance = 5000.0)),
                    categories = emptyList(),
                    accountBalances = mapOf(1L to 5000.0),
                    currencySymbol = "₹",
                    onDismiss = {},
                    onRecordAndMarkPaid = {},
                    onRecordPartialPayment = { _, _, _, _, _, _ -> },
                    onSkipCycle = {},
                    onSettleIou = { _, _, _ -> settleClicked = true },
                    onSaveCommitment = {},
                    onDeleteCommitment = { deleteClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Conditional Header: Chip header with math text exists for partial IOU
        composeTestRule.onNodeWithTag("commitment_chip_header_202", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("commitment_math_text_202", useUnmergedTree = true).assertExists()

        // Full rail Meta and Account lines exist
        composeTestRule.onNodeWithTag("commitment_title_row_202", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("commitment_meta_line_202", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("commitment_account_line_202", useUnmergedTree = true).assertExists()

        // Settle Row: Settle remaining + edit + delete
        composeTestRule.onNodeWithTag("commitment_settle_row_202", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("mark_settled_btn_202")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule.onNodeWithTag("edit_commitment_btn_202")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeTestRule.onNodeWithTag("delete_commitment_btn_202")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue("Delete should be clicked", deleteClicked)

        // Action Row: Partial alone (fillMaxWidth)
        composeTestRule.onNodeWithTag("partial_settle_btn_202")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun testIouCard_withoutPartial_allControlsExistAndAreClickable_andLiveInSeparateRows() {
        val iou = Commitment(
            id = 203L,
            type = "IOU",
            name = "Lent to Amit for Concert Booking",
            amount = 3500.0,
            direction = "OUTGOING",
            dueDate = System.currentTimeMillis() + 86400000L,
            active = true,
            paidSoFar = 0.0
        )
        val groups = CommitmentGroups(
            overdue = emptyList(),
            next7Days = listOf(CommitmentItem(iou, null, "in 3 days", false)),
            later = emptyList(),
            noDueDate = emptyList(),
            badgeCount = 1
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                CommitmentsPopup(
                    groups = groups,
                    accounts = listOf(Account(id = 1L, name = "Personal", accountType = "BANK", openingBalance = 5000.0)),
                    categories = emptyList(),
                    accountBalances = mapOf(1L to 5000.0),
                    currencySymbol = "₹",
                    onDismiss = {},
                    onRecordAndMarkPaid = {},
                    onRecordPartialPayment = { _, _, _, _, _, _ -> },
                    onSkipCycle = {},
                    onSettleIou = { _, _, _ -> },
                    onSaveCommitment = {},
                    onDeleteCommitment = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Unpaid State: Compact header (Title row has amount, no chip header)
        composeTestRule.onNodeWithTag("commitment_chip_header_203", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("commitment_title_row_203", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("commitment_meta_line_203", useUnmergedTree = true).assertExists()

        // Settle Row: Mark settled + edit + delete
        composeTestRule.onNodeWithTag("commitment_settle_row_203", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("mark_settled_btn_203")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule.onNodeWithTag("edit_commitment_btn_203")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule.onNodeWithTag("delete_commitment_btn_203")
            .assertIsDisplayed()
            .assertHasClickAction()

        // Action Row: Partial alone (fillMaxWidth)
        composeTestRule.onNodeWithTag("partial_settle_btn_203")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun testPartialSheet_scrollableRowRendersAllAccountChips_selectionPostsCorrectAccount() {
        val commitment = Commitment(
            id = 303L,
            type = "IOU",
            name = "Lent to Amit",
            amount = 3500.0,
            direction = "INCOMING",
            active = true
        )
        val accounts = listOf(
            Account(id = 1L, name = "Salary A/C", accountType = "BANK", openingBalance = 1000.0),
            Account(id = 2L, name = "Personal", accountType = "BANK", openingBalance = 1000.0),
            Account(id = 3L, name = "Mom's A/C", accountType = "BANK", openingBalance = 1000.0),
            Account(id = 4L, name = "Dad's A/C", accountType = "BANK", openingBalance = 1000.0),
            Account(id = 5L, name = "Cash at Home", accountType = "CASH", openingBalance = 1000.0),
            Account(id = 6L, name = "HDFC Credit Card", accountType = "CREDIT_CARD", openingBalance = 1000.0)
        )

        var confirmedAccountId: Long? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                PartialSettleSheet(
                    commitment = commitment,
                    accounts = accounts,
                    currencySymbol = "₹",
                    onDismiss = {},
                    onConfirm = { _, _, _, _, chosenAccountId ->
                        confirmedAccountId = chosenAccountId
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify chips exist and are clickable
        for (acc in accounts) {
            composeTestRule.onNodeWithTag("partial_account_chip_${acc.id}")
                .assertExists()
                .assertHasClickAction()
        }

        // Click the last account chip (HDFC Credit Card, id = 6)
        composeTestRule.onNodeWithTag("partial_account_chip_6")
            .performScrollTo()
            .performClick()

        composeTestRule.waitForIdle()

        // Confirm partial
        composeTestRule.onNodeWithTag("confirm_partial_btn").performClick()

        // Assert confirmed account is 6
        assertEquals(6L, confirmedAccountId)
    }
}
