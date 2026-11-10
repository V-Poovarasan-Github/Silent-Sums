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
import com.example.data.model.Account
import com.example.data.model.Commitment
import com.example.domain.commitment.CommitmentGroups
import com.example.domain.commitment.CommitmentItem
import com.example.ui.commitment.CommitmentsPopup
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
class Phase2_10_3_DuesAnatomyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testRecurringCard_partialState_canonicalGridAnatomy() {
        val recurringPartial = Commitment(
            id = 501L,
            type = "RECURRING",
            name = "Netflix & Spotify Family Subscription Long Title",
            amount = 799.0,
            direction = "OUTGOING",
            dayOfMonth = 15,
            accountId = 1L,
            active = true,
            paidSoFar = 79.0 // 720 remaining
        )
        val groups = CommitmentGroups(
            overdue = emptyList(),
            next7Days = listOf(CommitmentItem(recurringPartial, null, "in 1 day", false)),
            later = emptyList(),
            noDueDate = emptyList(),
            badgeCount = 1
        )

        var paidClicked = false
        var partialClicked = false
        var skipClicked = false
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
                    onSaveCommitment = {},
                    onDeleteCommitment = { deleteClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Conditional Header: Chip header with math text exists for partial
        composeTestRule.onNodeWithTag("commitment_chip_header_501", useUnmergedTree = true).assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("commitment_math_text_501", useUnmergedTree = true).assertExists().assertIsDisplayed()

        // 2. Title row exists (icon + title, no amount on this row)
        composeTestRule.onNodeWithTag("commitment_title_row_501", useUnmergedTree = true).assertExists().assertIsDisplayed()

        // 3. Meta line and Account line are full-rail column siblings
        composeTestRule.onNodeWithTag("commitment_meta_line_501", useUnmergedTree = true).assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("commitment_account_line_501", useUnmergedTree = true).assertExists().assertIsDisplayed()

        // 4. Settle row: Settle button flush left, edit & delete buttons flush right
        composeTestRule.onNodeWithTag("commitment_settle_row_501", useUnmergedTree = true).assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("record_paid_btn_501")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue(paidClicked)

        composeTestRule.onNodeWithTag("edit_commitment_btn_501")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule.onNodeWithTag("delete_commitment_btn_501")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue(deleteClicked)

        // 5. Action row: 50/50 [Partial] [Skip cycle]
        composeTestRule.onNodeWithTag("commitment_action_row_501", useUnmergedTree = true).assertExists().assertIsDisplayed()
        composeTestRule.onAllNodes(hasClickAction() and hasParent(hasTestTag("commitment_action_row_501")), useUnmergedTree = true)
            .assertCountEquals(2)

        composeTestRule.onNodeWithTag("partial_settle_btn_501")
            .assertIsDisplayed()
            .assertHasClickAction()

        composeTestRule.onNodeWithTag("skip_cycle_btn_501")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue(skipClicked)
    }

    @Test
    fun testIouCard_unpaidState_compactHeaderAndFullWidthPartial() {
        val iouUnpaid = Commitment(
            id = 502L,
            type = "IOU",
            name = "Dental Treatment Final Payment",
            amount = 2500.0,
            direction = "OUTGOING",
            dueDate = System.currentTimeMillis() + 86400000L * 3,
            accountId = 1L,
            active = true,
            paidSoFar = 0.0
        )
        val groups = CommitmentGroups(
            overdue = emptyList(),
            next7Days = listOf(CommitmentItem(iouUnpaid, null, "in 3 days", false)),
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

        // 1. Compact header: Title row contains amount; no chip header
        composeTestRule.onNodeWithTag("commitment_chip_header_502", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("commitment_title_row_502", useUnmergedTree = true).assertExists().assertIsDisplayed()

        // 2. Full rail Meta and Account lines
        composeTestRule.onNodeWithTag("commitment_meta_line_502", useUnmergedTree = true).assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("commitment_account_line_502", useUnmergedTree = true).assertExists().assertIsDisplayed()

        // 3. Settle row
        composeTestRule.onNodeWithTag("commitment_settle_row_502", useUnmergedTree = true).assertExists().assertIsDisplayed()
        composeTestRule.onNodeWithTag("mark_settled_btn_502").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag("edit_commitment_btn_502").assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag("delete_commitment_btn_502").assertIsDisplayed().assertHasClickAction()

        // 4. Action row: IOU has [Partial] full width
        composeTestRule.onNodeWithTag("partial_settle_btn_502").assertIsDisplayed().assertHasClickAction()
    }
}
