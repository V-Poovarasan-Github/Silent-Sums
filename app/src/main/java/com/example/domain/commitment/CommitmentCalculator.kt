package com.example.domain.commitment

import com.example.data.model.Commitment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CommitmentItem(
    val commitment: Commitment,
    val nextDueDate: Long?,
    val dueLabel: String,
    val isOverdue: Boolean
)

data class CommitmentGroups(
    val overdue: List<CommitmentItem>,
    val next7Days: List<CommitmentItem>,
    val later: List<Pair<String, List<CommitmentItem>>>, // Month label -> items
    val noDueDate: List<CommitmentItem>,
    val badgeCount: Int
)

object CommitmentCalculator {

    private val cycleFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
    private val dayMonthFormat = SimpleDateFormat("d MMM", Locale.US)

    fun clampedDayForMonth(year: Int, monthZeroBased: Int, targetDay: Int): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return targetDay.coerceIn(1, maxDays)
    }

    fun clampedDueDate(year: Int, monthZeroBased: Int, targetDay: Int): Long {
        val clampedDay = clampedDayForMonth(year, monthZeroBased, targetDay)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, clampedDay)
            set(Calendar.HOUR_OF_DAY, 9) // 9 AM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun nextCycle(cycle: String): String {
        val parts = cycle.split("-")
        var year = parts[0].toInt()
        var month = parts[1].toInt() // 1-12
        if (month == 12) {
            year += 1
            month = 1
        } else {
            month += 1
        }
        return String.format(Locale.US, "%04d-%02d", year, month)
    }

    fun getCurrentCycle(now: Long = System.currentTimeMillis()): String {
        return cycleFormat.format(Date(now))
    }

    fun computeNextDueDate(commitment: Commitment, now: Long = System.currentTimeMillis()): Long? {
        if (commitment.type != "RECURRING") {
            return commitment.dueDate
        }
        val day = commitment.dayOfMonth ?: return null

        val cycleStr = if (commitment.lastPostedCycle != null) {
            nextCycle(commitment.lastPostedCycle)
        } else {
            // First unposted cycle starting from createdAt
            val cCal = Calendar.getInstance().apply { timeInMillis = commitment.createdAt }
            val cYear = cCal.get(Calendar.YEAR)
            val cMonth = cCal.get(Calendar.MONTH)
            val firstDueDate = clampedDueDate(cYear, cMonth, day)
            // If created after the due date in createdAt month (with 1-day grace), start from next month
            if (firstDueDate < commitment.createdAt - 86_400_000L) {
                val nextM = if (cMonth == 11) 0 else cMonth + 1
                val nextY = if (cMonth == 11) cYear + 1 else cYear
                String.format(Locale.US, "%04d-%02d", nextY, nextM + 1)
            } else {
                String.format(Locale.US, "%04d-%02d", cYear, cMonth + 1)
            }
        }

        val parts = cycleStr.split("-")
        val year = parts[0].toInt()
        val monthZeroBased = parts[1].toInt() - 1
        return clampedDueDate(year, monthZeroBased, day)
    }

    fun rollCycleOnPaid(commitment: Commitment, now: Long = System.currentTimeMillis()): String {
        val curCycle = getCurrentCycle(now)
        val nextDueCycle = if (commitment.lastPostedCycle != null) {
            nextCycle(commitment.lastPostedCycle)
        } else {
            val due = computeNextDueDate(commitment, now)
            if (due != null) cycleFormat.format(Date(due)) else curCycle
        }

        return if (nextDueCycle > curCycle) {
            nextDueCycle
        } else {
            curCycle
        }
    }

    fun skipCycle(commitment: Commitment, now: Long = System.currentTimeMillis()): String {
        return if (commitment.lastPostedCycle != null) {
            nextCycle(commitment.lastPostedCycle)
        } else {
            val due = computeNextDueDate(commitment, now)
            if (due != null) cycleFormat.format(Date(due)) else getCurrentCycle(now)
        }
    }

    fun getDueLabel(dueDate: Long?, now: Long = System.currentTimeMillis()): String {
        if (dueDate == null) return "No due date"

        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayEnd = todayStart + 86_400_000L

        return when {
            dueDate < todayStart -> {
                val diffDays = maxOf(1, ((todayStart - dueDate) / 86_400_000L).toInt())
                "overdue ${diffDays}d"
            }
            dueDate in todayStart until todayEnd -> "due today"
            dueDate in todayEnd until (todayStart + 2 * 86_400_000L) -> "in 1 day"
            dueDate in (todayStart + 2 * 86_400_000L) until (todayStart + 8 * 86_400_000L) -> {
                val diffDays = ((dueDate - todayStart) / 86_400_000L).toInt()
                "in $diffDays days"
            }
            else -> "due ${dayMonthFormat.format(Date(dueDate))}"
        }
    }

    fun groupCommitments(
        commitments: List<Commitment>,
        now: Long = System.currentTimeMillis()
    ): CommitmentGroups {
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sevenDaysEnd = todayStart + 8 * 86_400_000L

        val activeList = commitments.filter { it.active && !it.settled }

        val overdue = mutableListOf<CommitmentItem>()
        val next7Days = mutableListOf<CommitmentItem>()
        val laterList = mutableListOf<CommitmentItem>()
        val noDueDate = mutableListOf<CommitmentItem>()

        for (c in activeList) {
            val due = computeNextDueDate(c, now)
            val label = getDueLabel(due, now)
            val isOverdue = due != null && due < todayStart
            val item = CommitmentItem(
                commitment = c,
                nextDueDate = due,
                dueLabel = label,
                isOverdue = isOverdue
            )

            when {
                due == null -> noDueDate.add(item)
                due < todayStart -> overdue.add(item)
                due < sevenDaysEnd -> next7Days.add(item)
                else -> laterList.add(item)
            }
        }

        // Sort items by due date ascending
        overdue.sortBy { it.nextDueDate }
        next7Days.sortBy { it.nextDueDate }
        laterList.sortBy { it.nextDueDate }

        // Group later by month label
        val laterGrouped = laterList
            .groupBy { monthYearFormat.format(Date(it.nextDueDate!!)) }
            .toList()

        val badgeCount = overdue.size + next7Days.size

        return CommitmentGroups(
            overdue = overdue,
            next7Days = next7Days,
            later = laterGrouped,
            noDueDate = noDueDate,
            badgeCount = badgeCount
        )
    }
}
