package com.example.domain.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatters {

    const val MASK_VALUE = "••••••"
    const val MASK_COMPACT = "••••"

    @Volatile
    private var activeCurrencySymbol: String = "₹"

    fun currentCurrencySymbol(): String = activeCurrencySymbol

    fun updateCurrentCurrencySymbol(symbol: String) {
        if (symbol.isNotBlank()) {
            activeCurrencySymbol = symbol
        }
    }

    fun formatMoney(
        amount: Double,
        currencySymbol: String = "₹"
    ): String = formatIndianCurrency(amount, currencySymbol)

    fun formatMoney(
        amount: Double,
        isMasked: Boolean,
        currencySymbol: String = "₹",
        showPlusSign: Boolean = false,
        includeDecimals: Boolean = true,
        mask: String = MASK_VALUE
    ): String {
        if (isMasked) return mask
        return formatIndianCurrency(
            amount = amount,
            currencySymbol = currencySymbol,
            showPlusSign = showPlusSign,
            includeDecimals = includeDecimals,
            isMasked = false
        )
    }

    private val indianLocale = Locale("en", "IN")

    private val decimalFormatWithDecimals = object : ThreadLocal<DecimalFormat>() {
        override fun initialValue(): DecimalFormat =
            DecimalFormat("##,##,##,##0.00", DecimalFormatSymbols(indianLocale))
    }

    private val decimalFormatNoDecimals = object : ThreadLocal<DecimalFormat>() {
        override fun initialValue(): DecimalFormat =
            DecimalFormat("##,##,##,##0", DecimalFormatSymbols(indianLocale))
    }

    private val dateFormatCache = object : ThreadLocal<MutableMap<String, SimpleDateFormat>>() {
        override fun initialValue(): MutableMap<String, SimpleDateFormat> = HashMap()
    }

    private val itemCalendarCache = object : ThreadLocal<Calendar>() {
        override fun initialValue(): Calendar = Calendar.getInstance()
    }

    /**
     * Formats amounts with Indian numbering system grouping (e.g. 1,00,000.00 or 34,738.74)
     */
    fun formatIndianCurrency(
        amount: Double,
        currencySymbol: String = "₹",
        showPlusSign: Boolean = false,
        includeDecimals: Boolean = true,
        isMasked: Boolean = false
    ): String {
        if (isMasked) return MASK_VALUE
        val isNegative = amount < 0
        val absVal = kotlin.math.abs(amount)

        val df = if (includeDecimals) decimalFormatWithDecimals.get()!! else decimalFormatNoDecimals.get()!!
        val formattedNumber = df.format(absVal)

        val sign = when {
            isNegative -> "-"
            showPlusSign && amount > 0 -> "+"
            else -> ""
        }

        return "$sign$currencySymbol$formattedNumber"
    }

    fun formatDate(timestamp: Long, pattern: String = "dd MMM yyyy"): String {
        val locale = Locale.getDefault()
        val key = "$pattern|${locale.toLanguageTag()}"
        val map = dateFormatCache.get()!!
        val sdf = map.getOrPut(key) { SimpleDateFormat(pattern, locale) }
        return sdf.format(Date(timestamp))
    }

    private val refCalendarCache = object : ThreadLocal<Calendar>() {
        override fun initialValue(): Calendar = Calendar.getInstance()
    }

    fun formatDayHeader(timestamp: Long): String {
        val nowMs = System.currentTimeMillis()
        val itemCal = itemCalendarCache.get()!!.apply { timeInMillis = timestamp }
        val refCal = refCalendarCache.get()!!.apply { timeInMillis = nowMs }

        val itemYear = itemCal.get(Calendar.YEAR)
        val itemDay = itemCal.get(Calendar.DAY_OF_YEAR)

        val isToday = itemYear == refCal.get(Calendar.YEAR) &&
                itemDay == refCal.get(Calendar.DAY_OF_YEAR)

        val isYesterday = if (!isToday) {
            refCal.add(Calendar.DAY_OF_YEAR, -1)
            itemYear == refCal.get(Calendar.YEAR) &&
                    itemDay == refCal.get(Calendar.DAY_OF_YEAR)
        } else {
            false
        }

        val dateStr = formatDate(timestamp, "dd MMM yyyy")
        return when {
            isToday -> "Today, $dateStr"
            isYesterday -> "Yesterday, $dateStr"
            else -> dateStr
        }
    }

    fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "Jan"
            2 -> "Feb"
            3 -> "Mar"
            4 -> "Apr"
            5 -> "May"
            6 -> "Jun"
            7 -> "Jul"
            8 -> "Aug"
            9 -> "Sept"
            10 -> "Oct"
            11 -> "Nov"
            12 -> "Dec"
            else -> ""
        }
    }

    fun getMonthYearLabel(month: Int, year: Int): String {
        val monthName = getMonthName(month)
        return if (monthName.isNotEmpty()) "$monthName $year" else "$year"
    }

    fun formatCompactCurrency(
        amount: Double,
        currencySymbol: String = "₹",
        showPlusSign: Boolean = false,
        isMasked: Boolean = false
    ): String {
        if (isMasked) return MASK_COMPACT
        val abs = kotlin.math.abs(amount)
        val sign = when {
            amount < 0 -> "-"
            showPlusSign && amount > 0 -> "+"
            else -> ""
        }
        return when {
            abs >= 10000000 -> String.format(Locale.US, "%s%s%.1fCr", sign, currencySymbol, abs / 10000000.0)
            abs >= 100000 -> String.format(Locale.US, "%s%s%.1fL", sign, currencySymbol, abs / 100000.0)
            abs >= 1000 -> {
                val formatted = String.format(Locale.US, "%.1f", abs / 1000.0).removeSuffix(".0")
                "$sign$currencySymbol${formatted}k"
            }
            else -> {
                val formatted = String.format(Locale.US, "%.1f", abs).removeSuffix(".0")
                "$sign$currencySymbol$formatted"
            }
        }
    }
}
