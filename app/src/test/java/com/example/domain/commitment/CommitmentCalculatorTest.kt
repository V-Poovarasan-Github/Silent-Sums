package com.example.domain.commitment

import com.example.data.model.Commitment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CommitmentCalculatorTest {

    @Test
    fun testClampDayOfMonth() {
        // February 2025 (non-leap year, month=1)
        val febDays = CommitmentCalculator.clampedDayForMonth(2025, Calendar.FEBRUARY, 31)
        assertEquals(28, febDays)

        // February 2024 (leap year, month=1)
        val febLeapDays = CommitmentCalculator.clampedDayForMonth(2024, Calendar.FEBRUARY, 31)
        assertEquals(29, febLeapDays)

        // April (30 days, month=3)
        val aprDays = CommitmentCalculator.clampedDayForMonth(2025, Calendar.APRIL, 31)
        assertEquals(30, aprDays)

        // July (31 days, month=6)
        val julDays = CommitmentCalculator.clampedDayForMonth(2025, Calendar.JULY, 31)
        assertEquals(31, julDays)
    }

    @Test
    fun testMissedCycles_ShowsOldestMissedAsOverdue() {
        // Recurring bill created in Jan 2026, day 10. Last posted cycle is 2026-01.
        // Today is April 15, 2026. Next due should be for February 2026 (oldest missed occurrence).
        val commitment = Commitment(
            id = 1L,
            type = "RECURRING",
            name = "Gym Membership",
            amount = 1500.0,
            direction = "OUTGOING",
            dayOfMonth = 10,
            lastPostedCycle = "2026-01",
            createdAt = 1735689600000L // Jan 1, 2026
        )

        val calApril = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 15, 12, 0, 0)
        }
        val nextDue = CommitmentCalculator.computeNextDueDate(commitment, calApril.timeInMillis)
        assertNotNull(nextDue)

        val dueCal = Calendar.getInstance().apply { timeInMillis = nextDue!! }
        assertEquals(2026, dueCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, dueCal.get(Calendar.MONTH))
        assertEquals(10, dueCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testRecurringAdvanceWhenCyclePosted() {
        val commitment = Commitment(
            id = 1L,
            type = "RECURRING",
            name = "Electricity Bill",
            amount = 2200.0,
            direction = "OUTGOING",
            dayOfMonth = 10,
            lastPostedCycle = "2026-04",
            createdAt = 1735689600000L
        )

        val calApril = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 15, 12, 0, 0)
        }
        val nextDue = CommitmentCalculator.computeNextDueDate(commitment, calApril.timeInMillis)
        assertNotNull(nextDue)

        val dueCal = Calendar.getInstance().apply { timeInMillis = nextDue!! }
        assertEquals(2026, dueCal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, dueCal.get(Calendar.MONTH))
        assertEquals(10, dueCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testGroupingAndBadgeCount() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 10, 10, 0, 0)
        }
        val now = nowCal.timeInMillis

        // Overdue IOU: due May 5 (5 days ago)
        val overdueCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 5, 9, 0, 0)
        }
        val overdueIou = Commitment(
            id = 1L,
            type = "IOU",
            name = "Lent to Bob",
            amount = 500.0,
            direction = "INCOMING",
            dueDate = overdueCal.timeInMillis
        )

        // Due in 3 days: May 13
        val next7DaysCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 13, 9, 0, 0)
        }
        val next7DaysIou = Commitment(
            id = 2L,
            type = "IOU",
            name = "Borrowed from Alice",
            amount = 1000.0,
            direction = "OUTGOING",
            dueDate = next7DaysCal.timeInMillis
        )

        // Due later (next month): June 15
        val laterCommitment = Commitment(
            id = 3L,
            type = "RECURRING",
            name = "Internet Bill",
            amount = 899.0,
            direction = "OUTGOING",
            dayOfMonth = 15,
            lastPostedCycle = "2026-05" // Next due is 2026-06-15
        )

        // No due date IOU
        val noDueDateIou = Commitment(
            id = 4L,
            type = "IOU",
            name = "Casual loan",
            amount = 250.0,
            direction = "OUTGOING",
            dueDate = null
        )

        val commitments = listOf(overdueIou, next7DaysIou, laterCommitment, noDueDateIou)
        val groups = CommitmentCalculator.groupCommitments(commitments, now)

        assertEquals(1, groups.overdue.size)
        assertEquals("Lent to Bob", groups.overdue[0].commitment.name)
        assertTrue(groups.overdue[0].dueLabel.contains("overdue", ignoreCase = true))

        assertEquals(1, groups.next7Days.size)
        assertEquals("Borrowed from Alice", groups.next7Days[0].commitment.name)

        assertEquals(1, groups.later.size)
        assertEquals(1, groups.later[0].second.size)
        assertEquals("Internet Bill", groups.later[0].second[0].commitment.name)

        assertEquals(1, groups.noDueDate.size)
        assertEquals("Casual loan", groups.noDueDate[0].commitment.name)

        // Badge count = overdue + next 7 days = 1 + 1 = 2
        assertEquals(2, groups.badgeCount)
    }
}
