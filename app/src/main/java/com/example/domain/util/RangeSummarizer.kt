package com.example.domain.util

object RangeSummarizer {

    fun summarizeRange(
        weeks: List<Int> = emptyList(),
        months: List<Int> = emptyList(), // 1..12
        years: List<Int> = emptyList(),
        contextMonthYear: String = "Sept 2026",
        contextYear: String = "2026",
        singleExactDates: String? = null
    ): String {
        return when {
            weeks.isNotEmpty() -> summarizeWeeks(weeks, contextMonthYear, singleExactDates)
            months.isNotEmpty() -> summarizeMonths(months, contextYear, singleExactDates)
            years.isNotEmpty() -> summarizeYears(years, singleExactDates)
            else -> singleExactDates ?: ""
        }
    }

    fun summarizeWeeks(
        weeks: List<Int>,
        contextMonthYear: String = "Sept 2026",
        singleExactDates: String? = null
    ): String {
        val sorted = weeks.distinct().sorted()
        return when {
            sorted.isEmpty() -> singleExactDates ?: ""
            sorted.size == 1 -> singleExactDates ?: "W${sorted[0]}"
            isConsecutive(sorted) -> "W${sorted.first()}–W${sorted.last()}"
            sorted.size >= 3 -> "${sorted.size} weeks • $contextMonthYear"
            else -> sorted.joinToString(", ") { "W$it" }
        }
    }

    fun summarizeMonths(
        months: List<Int>, // 1..12
        contextYear: String = "2026",
        singleExactDates: String? = null
    ): String {
        val sorted = months.distinct().sorted()
        return when {
            sorted.isEmpty() -> singleExactDates ?: ""
            sorted.size == 1 -> singleExactDates ?: Formatters.getMonthName(sorted[0])
            isConsecutive(sorted) -> "${Formatters.getMonthName(sorted.first())}–${Formatters.getMonthName(sorted.last())}"
            sorted.size >= 3 -> "${sorted.size} months • $contextYear"
            else -> sorted.joinToString(", ") { Formatters.getMonthName(it) }
        }
    }

    fun summarizeYears(
        years: List<Int>,
        singleExactDates: String? = null
    ): String {
        val sorted = years.distinct().sorted()
        return when {
            sorted.isEmpty() -> singleExactDates ?: ""
            sorted.size == 1 -> singleExactDates ?: sorted[0].toString()
            isConsecutive(sorted) -> "${sorted.first()}–${sorted.last()}"
            sorted.size >= 3 -> "${sorted.size} years"
            else -> sorted.joinToString(", ")
        }
    }

    private fun isConsecutive(list: List<Int>): Boolean {
        if (list.size <= 1) return false
        return list.last() - list.first() == list.size - 1
    }
}
