package com.example.importbackup

import com.example.data.datastore.AppSettings
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.model.CustomField
import com.example.data.model.TransactionEntity
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CsvBackupRow(
    val date: Long,
    val type: String,
    val amount: Double,
    val fromAccount: String,
    val toAccount: String,
    val category: String,
    val description: String,
    val notes: String,
    val mediaRef: String? = null
)

data class CsvDuesRow(
    val type: String,
    val name: String,
    val amount: Double,
    val direction: String,
    val dayOfMonth: Int?,
    val dueDate: Long?,
    val account: String,
    val category: String,
    val active: Boolean,
    val description: String = "",
    val paidSoFar: Double = 0.0
)

data class CsvAccountRow(
    val name: String,
    val type: String,
    val opening: Double,
    val archived: Boolean
)

data class CsvCategoryRow(
    val name: String,
    val color: String,
    val archived: Boolean,
    val iconText: String? = null
)

data class CsvCustomFieldRow(
    val name: String,
    val label: String,
    val type: String,
    val options: String
)

data class CsvBudgetRow(
    val name: String,
    val scopeType: String,
    val targetName: String,
    val limitAmount: Double,
    val active: Boolean
)

data class ParsedCsvData(
    val transactions: List<CsvBackupRow>,
    val dues: List<CsvDuesRow> = emptyList(),
    val accounts: List<CsvAccountRow> = emptyList(),
    val categories: List<CsvCategoryRow> = emptyList(),
    val customFields: List<CsvCustomFieldRow> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val budgets: List<CsvBudgetRow> = emptyList(),
    val formatVersion: Int = 1
)

object CsvBackupManager {
    const val HEADER = "Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes"

    fun buildCsv(
        transactions: List<TransactionEntity>,
        accounts: List<Account>,
        categories: List<Category>,
        customFields: List<CustomField> = emptyList(),
        settings: AppSettings? = null,
        commitments: List<Commitment> = emptyList(),
        budgets: List<com.example.data.model.BudgetEntity> = emptyList(),
        mediaRefsByTxnId: Map<Long, String> = emptyMap()
    ): String {
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append("#FORMAT=2\n")

        val hasAnyMediaInBatch = transactions.any { mediaRefsByTxnId.containsKey(it.id) && !mediaRefsByTxnId[it.id].isNullOrBlank() }
        val headerToEmit = if (hasAnyMediaInBatch) "$HEADER,MediaRef" else HEADER
        sb.append(headerToEmit).append('\n')

        transactions.sortedBy { it.date }.forEach { tx ->
            val from = tx.fromAccountId?.let { accountMap[it]?.name } ?: ""
            val to = tx.toAccountId?.let { accountMap[it]?.name } ?: ""
            val cat = tx.categoryId?.let { categoryMap[it]?.name } ?: ""
            sb.append(sdf.format(Date(tx.date))).append(',')
                .append(tx.type.uppercase()).append(',')
                .append(String.format(Locale.US, "%.2f", tx.amount)).append(',')
                .append(escape(from)).append(',')
                .append(escape(to)).append(',')
                .append(escape(cat)).append(',')
                .append(escape(tx.description)).append(',')
                .append(escape(tx.notes))
            if (hasAnyMediaInBatch) {
                sb.append(',').append(escape(mediaRefsByTxnId[tx.id] ?: ""))
            }
            sb.append('\n')
        }

        if (accounts.isNotEmpty()) {
            sb.append("\n#ACCOUNTS\n")
            sb.append("Name,Type,Opening,Archived\n")
            accounts.forEach { acc ->
                sb.append(escape(acc.name)).append(',')
                    .append(acc.accountType).append(',')
                    .append(String.format(Locale.US, "%.2f", acc.openingBalance)).append(',')
                    .append(acc.archived).append('\n')
            }
        }

        if (categories.isNotEmpty()) {
            sb.append("\n#CATEGORIES\n")
            sb.append("Name,Color,Archived,icon_text\n")
            categories.forEach { cat ->
                sb.append(escape(cat.name)).append(',')
                    .append(escape(cat.color)).append(',')
                    .append(cat.archived).append(',')
                    .append(escape(cat.iconText ?: "")).append('\n')
            }
        }

        if (customFields.isNotEmpty()) {
            sb.append("\n#CUSTOMFIELDS\n")
            sb.append("Name,Label,Type,Options\n")
            customFields.forEach { cf ->
                sb.append(escape(cf.name)).append(',')
                    .append(escape(cf.name)).append(',')
                    .append(cf.type).append(',')
                    .append(escape(cf.options)).append('\n')
            }
        }

        if (settings != null) {
            sb.append("\n#SETTINGS\n")
            sb.append("currencySymbol=").append(settings.currencySymbol).append('\n')
            sb.append("monthStartDay=").append(settings.monthStartDay).append('\n')
            sb.append("theme=").append(settings.theme).append('\n')
            sb.append("hideBalances=").append(settings.hideBalances).append('\n')
            sb.append("soundHapticsEnabled=").append(settings.soundHapticsEnabled).append('\n')
        }

        if (commitments.isNotEmpty()) {
            sb.append("\n#COMMITMENTS (Dues & Recurring)\n")
            sb.append("type,name,amount,direction,dayOfMonth,dueDate,accountName,categoryName,active,description,paidSoFar\n")
            commitments.forEach { c ->
                val accName = c.accountId?.let { accountMap[it]?.name } ?: ""
                val catName = c.categoryId?.let { categoryMap[it]?.name } ?: ""
                sb.append(c.type).append(',')
                    .append(escape(c.name)).append(',')
                    .append(String.format(Locale.US, "%.2f", c.amount)).append(',')
                    .append(c.direction).append(',')
                    .append(c.dayOfMonth?.toString() ?: "").append(',')
                    .append(c.dueDate?.toString() ?: "").append(',')
                    .append(escape(accName)).append(',')
                    .append(escape(catName)).append(',')
                    .append(c.active).append(',')
                    .append(escape(c.description ?: "")).append(',')
                    .append(String.format(Locale.US, "%.2f", c.paidSoFar)).append('\n')
            }
        }

        if (budgets.isNotEmpty()) {
            sb.append("\n#BUDGETS\n")
            sb.append("Name,ScopeType,TargetName,LimitAmount,Active\n")
            budgets.forEach { b ->
                sb.append(escape(b.name)).append(',')
                    .append(escape(b.scopeType)).append(',')
                    .append(escape(b.targetName)).append(',')
                    .append(String.format(Locale.US, "%.2f", b.limitAmount)).append(',')
                    .append(b.active).append('\n')
            }
        }

        return sb.toString()
    }

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd",
        "dd/MM/yyyy HH:mm:ss",
        "dd/MM/yyyy HH:mm",
        "dd/MM/yyyy",
        "dd-MM-yyyy HH:mm:ss",
        "dd-MM-yyyy HH:mm",
        "dd-MM-yyyy",
        "MM/dd/yyyy HH:mm:ss",
        "MM/dd/yyyy HH:mm",
        "MM/dd/yyyy",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd HH:mm",
        "yyyy/MM/dd",
        "dd MMM yyyy HH:mm",
        "dd MMM yyyy",
        "d MMM yyyy",
        "dd-MMM-yyyy",
        "d-MMM-yyyy",
        "MMM dd, yyyy",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS"
    )

    fun parseDateOrNull(rawDateStr: String): Long? {
        val trimmed = rawDateStr.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toLongOrNull()?.let { return it }

        for (pattern in DATE_PATTERNS) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) { }
        }
        for (pattern in DATE_PATTERNS) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) { }
        }
        return null
    }

    private val SUPPORTED_TYPE_TOKENS = setOf(
        "EXPENSE", "DR", "DEBIT", "WITHDRAWAL", "WITHDRAW", "PAIDOUT", "MONEYOUT", "OUTGOING", "SPEND",
        "INCOME", "CR", "CREDIT", "DEPOSIT", "PAIDIN", "MONEYIN", "RECEIVED", "INCOMING",
        "TRANSFER", "TXFR", "CONTRA", "XFER"
    )

    fun isSupportedTransactionType(rawType: String): Boolean {
        val norm = rawType.trim().uppercase().replace(Regex("[^A-Z]"), "")
        return norm.isEmpty() || SUPPORTED_TYPE_TOKENS.contains(norm)
    }

    fun transactionSignature(
        date: Long,
        type: String,
        amount: Double,
        fromAccount: String,
        toAccount: String,
        category: String,
        description: String,
        notes: String = ""
    ): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val dateKey = sdf.format(Date(date))
        val normType = type.trim().uppercase()
        val normAmt = String.format(Locale.US, "%.2f", amount)
        val normFrom = if (normType == "INCOME") "" else fromAccount.trim().lowercase()
        val normTo = toAccount.trim().lowercase()
        val normCat = category.trim().lowercase()
        val normDesc = description.trim().lowercase().ifEmpty { notes.trim().lowercase() }
        val normNotes = notes.trim().lowercase().ifEmpty { normDesc }
        return "$dateKey|$normType|$normAmt|$normFrom|$normTo|$normCat|$normDesc|$normNotes"
    }

    fun transactionSignature(row: CsvBackupRow): String =
        transactionSignature(
            date = row.date,
            type = row.type,
            amount = row.amount,
            fromAccount = row.fromAccount,
            toAccount = row.toAccount,
            category = row.category,
            description = row.description,
            notes = row.notes
        )

    fun parseCsv(input: InputStream): List<CsvBackupRow> {
        return parseCsvFull(input).transactions
    }

    fun parseCsvFull(input: InputStream): ParsedCsvData {
        val fullText = input.bufferedReader().use { it.readText() }
        val allLines = splitLines(fullText)

        var formatVersion = 1
        var currentSection: String? = null
        val sectionLines = mutableMapOf<String, MutableList<String>>()

        for (rawLine in allLines) {
            val clean = rawLine.trim().removePrefix("\uFEFF").trim('"', '\'')
            if (clean.isEmpty()) continue
            if (clean.startsWith("#")) {
                val afterHash = clean.removePrefix("#").trim()
                val firstToken = afterHash.split(Regex("\\s+")).firstOrNull()?.uppercase() ?: ""
                if (firstToken.startsWith("FORMAT=")) {
                    formatVersion = firstToken.removePrefix("FORMAT=").toIntOrNull() ?: 1
                    continue
                }
                when (firstToken) {
                    "ACCOUNTS" -> currentSection = "ACCOUNTS"
                    "CATEGORIES" -> currentSection = "CATEGORIES"
                    "CUSTOMFIELDS" -> currentSection = "CUSTOMFIELDS"
                    "SETTINGS" -> currentSection = "SETTINGS"
                    "COMMITMENTS", "DUES" -> currentSection = "COMMITMENTS"
                    "BUDGETS" -> currentSection = "BUDGETS"
                    else -> {
                        // Unknown tag: skip wholesale until the next line starting with '#'
                        currentSection = "UNKNOWN"
                    }
                }
                continue
            }
            if (currentSection == "UNKNOWN") {
                // Wholesale skip of unknown block
                continue
            }
            val sec = currentSection ?: "TRANSACTIONS"
            sectionLines.getOrPut(sec) { mutableListOf() }.add(rawLine)
        }

        val transactions = parseTransactions(sectionLines["TRANSACTIONS"] ?: emptyList())
        val accounts = parseAccounts(sectionLines["ACCOUNTS"] ?: emptyList())
        val categories = parseCategories(sectionLines["CATEGORIES"] ?: emptyList())
        val customFields = parseCustomFields(sectionLines["CUSTOMFIELDS"] ?: emptyList())
        val settings = parseSettings(sectionLines["SETTINGS"] ?: emptyList())
        val dues = parseDues(sectionLines["COMMITMENTS"] ?: emptyList())
        val budgets = parseBudgets(sectionLines["BUDGETS"] ?: emptyList())

        return ParsedCsvData(
            transactions = transactions,
            dues = dues,
            accounts = accounts,
            categories = categories,
            customFields = customFields,
            settings = settings,
            budgets = budgets,
            formatVersion = formatVersion
        )
    }

    private fun parseBudgets(lines: List<String>): List<CsvBudgetRow> {
        if (lines.isEmpty()) return emptyList()
        val header = parseLine(lines.first()).map { it.lowercase() }
        val iName = header.indexOfFirst { it == "name" }
        val iScope = header.indexOfFirst { it == "scopetype" || it == "scope" }
        val iTarget = header.indexOfFirst { it == "targetname" || it == "target" }
        val iLimit = header.indexOfFirst { it == "limitamount" || it == "limit" || it == "amount" }
        val iActive = header.indexOfFirst { it == "active" }

        val list = mutableListOf<CsvBudgetRow>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#")) continue
            val cols = parseLine(lines[i])
            fun get(idx: Int) = if (idx in cols.indices) cols[idx].trim() else ""
            val name = get(iName)
            val scope = get(iScope).uppercase().ifBlank { "ALL" }
            val target = get(iTarget)
            val limit = get(iLimit).replace(",", "").replace("₹", "").replace("$", "").toDoubleOrNull() ?: 0.0
            val active = get(iActive).toBooleanStrictOrNull() ?: true
            if (limit > 0) {
                list.add(
                    CsvBudgetRow(
                        name = name.ifBlank { target.ifBlank { "Budget" } },
                        scopeType = scope,
                        targetName = target,
                        limitAmount = limit,
                        active = active
                    )
                )
            }
        }
        return list
    }

    private fun parseAccounts(lines: List<String>): List<CsvAccountRow> {
        if (lines.isEmpty()) return emptyList()
        val header = parseLine(lines.first()).map { it.lowercase() }
        val iName = header.indexOfFirst { it == "name" }
        val iType = header.indexOfFirst { it == "type" }
        val iOpening = header.indexOfFirst { it == "opening" || it.contains("balance") }
        val iArchived = header.indexOfFirst { it == "archived" }

        val list = mutableListOf<CsvAccountRow>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#")) continue
            val cols = parseLine(lines[i])
            fun get(idx: Int) = if (idx in cols.indices) cols[idx].trim() else ""
            val name = get(iName)
            if (name.isBlank()) continue
            val type = get(iType).ifBlank { guessAccountType(name) }
            val opening = get(iOpening).replace(",", "").replace("₹", "").replace("$", "").toDoubleOrNull() ?: 0.0
            val archived = get(iArchived).toBooleanStrictOrNull() ?: false
            list.add(CsvAccountRow(name, type, opening, archived))
        }
        return list
    }

    private fun parseCategories(lines: List<String>): List<CsvCategoryRow> {
        if (lines.isEmpty()) return emptyList()
        val header = parseLine(lines.first()).map { it.lowercase() }
        val iName = header.indexOfFirst { it == "name" }
        val iColor = header.indexOfFirst { it == "color" }
        val iArchived = header.indexOfFirst { it == "archived" }
        val iIconText = header.indexOfFirst { it == "icon_text" || it == "icon" }

        val list = mutableListOf<CsvCategoryRow>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#")) continue
            val cols = parseLine(lines[i])
            fun get(idx: Int) = if (idx in cols.indices) cols[idx].trim() else ""
            val name = get(iName)
            if (name.isBlank()) continue
            val color = get(iColor).ifBlank { "#3B82F6" }
            val archived = get(iArchived).toBooleanStrictOrNull() ?: false
            val iconText = if (iIconText >= 0) get(iIconText).ifBlank { null } else null
            list.add(CsvCategoryRow(name, color, archived, iconText))
        }
        return list
    }

    private fun parseCustomFields(lines: List<String>): List<CsvCustomFieldRow> {
        if (lines.isEmpty()) return emptyList()
        val header = parseLine(lines.first()).map { it.lowercase() }
        val iName = header.indexOfFirst { it == "name" }
        val iLabel = header.indexOfFirst { it == "label" }
        val iType = header.indexOfFirst { it == "type" }
        val iOptions = header.indexOfFirst { it == "options" }

        val list = mutableListOf<CsvCustomFieldRow>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#")) continue
            val cols = parseLine(lines[i])
            fun get(idx: Int) = if (idx in cols.indices) cols[idx].trim() else ""
            val name = get(iName)
            if (name.isBlank()) continue
            val label = get(iLabel).ifBlank { name }
            val type = get(iType).ifBlank { "TEXT" }
            val options = get(iOptions)
            list.add(CsvCustomFieldRow(name, label, type, options))
        }
        return list
    }

    private fun parseSettings(lines: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || !trimmed.contains("=")) continue
            val k = trimmed.substringBefore("=").trim()
            val v = trimmed.substringAfter("=").trim()
            if (k.contains("pin", ignoreCase = true) || k.contains("lock", ignoreCase = true)) continue
            map[k] = v
        }
        return map
    }

    private fun parseTransactions(lines: List<String>): List<CsvBackupRow> {
        if (lines.isEmpty()) return emptyList()
        val header = parseLine(lines.first())

        fun idxOf(target: String): Int =
            header.indexOfFirst { CsvColumnMapper.resolveStandardColumn(it) == target }

        val iDate = idxOf("date")
        val iType = idxOf("type")
        val iAmount = idxOf("amount")
        val iDebit = idxOf("debit")
        val iCredit = idxOf("credit")
        val iFrom = idxOf("fromaccount")
        val iTo = idxOf("toaccount")
        val iCat = idxOf("category")
        val iDesc = idxOf("description")
        val iNotes = idxOf("notes")
        val iMediaRef = idxOf("mediaref")

        if (iDate < 0 || (iAmount < 0 && iDebit < 0 && iCredit < 0)) return emptyList()

        val rows = mutableListOf<CsvBackupRow>()
        for (i in 1 until lines.size) {
            val rawLine = lines[i].trim()
            if (rawLine.startsWith("#")) continue
            val cols = parseLine(lines[i])
            fun get(idx: Int): String = if (idx >= 0 && idx in cols.indices) cols[idx] else ""
            val rawDateStr = get(iDate).trim()
            val date: Long? = parseDateOrNull(rawDateStr)

            var amount: Double? = null
            var txType = if (iType >= 0) get(iType).uppercase().trim() else ""
            if (txType.isNotBlank() && !isSupportedTransactionType(txType)) continue

            if (iAmount >= 0) {
                val rawAmt = get(iAmount).replace(",", "").replace("₹", "").replace("$", "").trim()
                amount = rawAmt.toDoubleOrNull()
            } else {
                val rawDebit = if (iDebit >= 0) get(iDebit).replace(",", "").replace("₹", "").replace("$", "").trim() else ""
                val rawCredit = if (iCredit >= 0) get(iCredit).replace(",", "").replace("₹", "").replace("$", "").trim() else ""
                val debitAmt = rawDebit.toDoubleOrNull() ?: 0.0
                val creditAmt = rawCredit.toDoubleOrNull() ?: 0.0

                if (creditAmt > 0.0) {
                    amount = creditAmt
                    if (txType.isBlank()) txType = "INCOME"
                } else if (debitAmt > 0.0) {
                    amount = debitAmt
                    if (txType.isBlank()) txType = "EXPENSE"
                }
            }

            if (date == null || amount == null || amount <= 0.0) continue

            var fromAcc = get(iFrom)
            var toAcc = get(iTo)
            if (txType.isBlank()) {
                txType = if (toAcc.isNotBlank() && fromAcc.isNotBlank()) "TRANSFER" else "EXPENSE"
            } else {
                txType = CsvColumnMapper.normalizeType(txType)
            }

            // INCOME rows: map receiving account from ToAccount column.
            // An income row having an empty From is correct, but must never end with both accounts null when ToAccount is present.
            if (txType == "INCOME" && toAcc.isBlank() && fromAcc.isNotBlank()) {
                toAcc = fromAcc
                fromAcc = ""
            }

            val desc = get(iDesc).ifBlank { get(iNotes) }
            val note = get(iNotes).ifBlank { get(iDesc) }
            val mRef = if (iMediaRef >= 0 && iMediaRef in cols.indices) cols[iMediaRef].trim().ifBlank { null } else null

            rows.add(
                CsvBackupRow(
                    date = date,
                    type = txType,
                    amount = amount,
                    fromAccount = fromAcc,
                    toAccount = toAcc,
                    category = get(iCat),
                    description = desc,
                    notes = note,
                    mediaRef = mRef
                )
            )
        }
        return rows
    }

    private fun parseDues(lines: List<String>): List<CsvDuesRow> {
        if (lines.isEmpty()) return emptyList()
        val header = parseLine(lines.first()).map { CsvColumnMapper.normalize(it) }

        val iType = header.indexOfFirst { it == "type" }
        val iName = header.indexOfFirst { it == "name" }
        val iAmount = header.indexOfFirst { it == "amount" || it == "amt" }
        val iDirection = header.indexOfFirst { it == "direction" }
        val iDay = header.indexOfFirst { it == "dayofmonth" || it == "day" }
        val iDueDate = header.indexOfFirst { it == "duedate" || it == "due" }
        val iAccount = header.indexOfFirst { it == "account" || it == "accountname" || it == "fromaccount" }
        val iCategory = header.indexOfFirst { it == "category" || it == "categoryname" }
        val iActive = header.indexOfFirst { it == "active" }
        val iDescription = header.indexOfFirst { it == "description" || it == "desc" }
        val iPaidSoFar = header.indexOfFirst { it == "paidsofar" || it == "paid_so_far" || it == "paid" }

        val duesList = mutableListOf<CsvDuesRow>()
        for (i in 1 until lines.size) {
            val cols = parseLine(lines[i])
            fun get(idx: Int): String = if (idx >= 0 && idx in cols.indices) cols[idx] else ""

            val type = get(iType).trim().uppercase()
            val name = get(iName).trim()
            val rawAmt = get(iAmount).replace(",", "").replace("₹", "").replace("$", "").trim()
            val amount = rawAmt.toDoubleOrNull() ?: 0.0
            if (name.isBlank() || amount <= 0.0) continue

            val direction = get(iDirection).trim().uppercase().ifBlank {
                if (type == "RECURRING") "OUTGOING" else "OUTGOING"
            }
            val dayOfMonth = get(iDay).trim().toIntOrNull()
            val rawDueDate = get(iDueDate).trim()
            val dueDate = rawDueDate.toLongOrNull()
            val account = get(iAccount).trim()
            val category = get(iCategory).trim()
            val activeStr = get(iActive).trim()
            val active = if (activeStr.isNotBlank()) activeStr.toBoolean() else true
            val desc = if (iDescription >= 0) get(iDescription).trim() else ""
            val rawPaidSoFar = if (iPaidSoFar >= 0) get(iPaidSoFar).replace(",", "").replace("₹", "").replace("$", "").trim() else ""
            val paidSoFar = rawPaidSoFar.toDoubleOrNull() ?: 0.0

            duesList.add(
                CsvDuesRow(
                    type = if (type == "IOU") "IOU" else "RECURRING",
                    name = name,
                    amount = amount,
                    direction = direction,
                    dayOfMonth = dayOfMonth,
                    dueDate = dueDate,
                    account = account,
                    category = category,
                    active = active,
                    description = desc,
                    paidSoFar = paidSoFar
                )
            )
        }
        return duesList
    }

    fun splitLines(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> { inQuotes = !inQuotes; current.append(c) }
                c == '\r' -> { }
                c == '\n' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    private fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    fun guessAccountType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("cash") || lower.contains("wallet") -> "CASH"
            lower.contains("credit") || lower.contains("card") -> "CREDIT_CARD"
            lower.contains("invest") || lower.contains("mutual") || lower.contains("stock") || lower.contains("gold") -> "INVESTMENT"
            else -> "BANK"
        }
    }
}
