package com.example.importbackup

import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class RawTx(
    val date: Long,
    val type: String,
    val amount: Double,
    val fromAccountName: String,
    val toAccountName: String,
    val categoryName: String,
    val description: String,
    val notes: String
)

data class CsvPreviewResult(
    val totalRows: Int,
    val validRows: Int,
    val parsedTransactions: List<TransactionEntity>,
    val newAccountsToCreate: List<String>,
    val newCategoriesToCreate: List<String>,
    val errors: List<String>,
    val dateRange: String = "",
    val skippedRows: Int = (totalRows - validRows).coerceAtLeast(0),
    val skippedReasons: List<String> = errors
) {
    val summaryHeader: String
        get() = "Rows: $totalRows · Valid: $validRows"

    val skippedLine: String?
        get() = if (skippedRows > 0) {
            if (skippedReasons.isNotEmpty()) {
                "Skipped: $skippedRows (${skippedReasons.joinToString(", ")})"
            } else {
                "Skipped: $skippedRows"
            }
        } else null
}

object CsvImporter {

    private fun parseDate(dateStr: String): Long? =
        CsvBackupManager.parseDateOrNull(dateStr)

    private fun cleanNumber(str: String): String =
        str.replace(",", "")
            .replace("₹", "")
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .trim()

    data class ExtractedRow(
        val rawTx: RawTx?,
        val error: String?
    )

    private fun extractRow(
        txRowIndex: Int,
        lineIdx: Int,
        row: List<String>,
        dateIdx: Int,
        typeIdx: Int,
        amountIdx: Int,
        debitIdx: Int,
        creditIdx: Int,
        fromIdx: Int,
        toIdx: Int,
        catIdx: Int,
        descIdx: Int,
        notesIdx: Int,
        accountColIndices: List<Pair<String, Int>>
    ): ExtractedRow {
        val dateStr = if (dateIdx >= 0 && dateIdx < row.size) row[dateIdx] else ""
        val parsedDate = parseDate(dateStr)
            ?: return ExtractedRow(null, "unparseable row #$txRowIndex (line #${lineIdx + 1}): invalid or missing date")

        val rawType = if (typeIdx >= 0 && typeIdx < row.size) row[typeIdx].trim() else ""

        var amount: Double? = null
        var resolvedType = if (rawType.isNotBlank()) CsvColumnMapper.normalizeType(rawType) else ""

        if (amountIdx >= 0 && amountIdx < row.size) {
            val cleaned = cleanNumber(row[amountIdx])
            val num = cleaned.toDoubleOrNull()
            if (num != null && num != 0.0) {
                if (num < 0) {
                    amount = abs(num)
                    if (rawType.isBlank()) resolvedType = "EXPENSE"
                } else {
                    amount = num
                }
            }
        }

        if (amount == null && (debitIdx >= 0 || creditIdx >= 0)) {
            val drStr = if (debitIdx >= 0 && debitIdx < row.size) cleanNumber(row[debitIdx]) else ""
            val crStr = if (creditIdx >= 0 && creditIdx < row.size) cleanNumber(row[creditIdx]) else ""
            val drVal = drStr.toDoubleOrNull() ?: 0.0
            val crVal = crStr.toDoubleOrNull() ?: 0.0

            if (crVal > 0.0) {
                amount = crVal
                if (resolvedType.isBlank()) resolvedType = "INCOME"
            } else if (drVal > 0.0) {
                amount = drVal
                if (resolvedType.isBlank()) resolvedType = "EXPENSE"
            }
        }

        if (amount == null || amount <= 0.0) {
            return ExtractedRow(null, "unparseable row #$txRowIndex (line #${lineIdx + 1}): Amount is missing or zero")
        }

        if (rawType.isNotBlank() && !CsvBackupManager.isSupportedTransactionType(rawType)) {
            return ExtractedRow(null, "unsupported row #$txRowIndex (line #${lineIdx + 1}): unsupported type '$rawType'")
        }

        var fromStr = if (fromIdx >= 0 && fromIdx < row.size) row[fromIdx].trim() else ""
        var toStr = if (toIdx >= 0 && toIdx < row.size) row[toIdx].trim() else ""
        val catStr = if (catIdx >= 0 && catIdx < row.size) row[catIdx].trim() else ""
        val descStr = if (descIdx >= 0 && descIdx < row.size) row[descIdx].trim() else ""
        val notesStr = if (notesIdx >= 0 && notesIdx < row.size) row[notesIdx].trim() else ""

        if (resolvedType.isBlank()) {
            resolvedType = if (toStr.isNotBlank() && fromStr.isNotBlank()) "TRANSFER" else "EXPENSE"
        }

        // Multi-account column fallback
        if (fromStr.isBlank() && toStr.isBlank()) {
            for ((accName, colIdx) in accountColIndices) {
                if (colIdx < row.size) {
                    val cellVal = cleanNumber(row[colIdx]).toDoubleOrNull()
                    if (cellVal != null && cellVal != 0.0) {
                        if (resolvedType == "EXPENSE") {
                            fromStr = accName
                        } else if (resolvedType == "INCOME") {
                            toStr = accName
                        }
                        break
                    }
                }
            }
        }

        if (resolvedType == "INCOME") {
            if (toStr.isBlank() && fromStr.isNotBlank()) {
                toStr = fromStr
                fromStr = ""
            }
        }

        val effectiveDesc = descStr.ifBlank { notesStr }
        val effectiveNotes = notesStr.ifBlank { descStr }

        val rawTx = RawTx(
            date = parsedDate,
            type = resolvedType,
            amount = amount,
            fromAccountName = fromStr,
            toAccountName = toStr,
            categoryName = catStr,
            description = effectiveDesc,
            notes = effectiveNotes
        )
        return ExtractedRow(rawTx, null)
    }

    fun previewCsv(
        inputStream: InputStream,
        existingAccounts: List<Account>,
        existingCategories: List<Category>,
        mergeTransfers: Boolean = false
    ): CsvPreviewResult {
        val fullText = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        val lines = CsvBackupManager.splitLines(fullText)
        if (lines.isEmpty()) {
            return CsvPreviewResult(0, 0, emptyList(), emptyList(), emptyList(), listOf("File is empty"))
        }

        val isUnifiedFormat = lines.any { it.trim().removePrefix("\uFEFF").uppercase().startsWith("#FORMAT=") }

        val headerIdx = lines.indexOfFirst { line ->
            val clean = line.trim().removePrefix("\uFEFF").trim('"', '\'')
            !clean.startsWith("#") && (
                clean.contains("date", ignoreCase = true) ||
                clean.contains("amount", ignoreCase = true) ||
                clean.contains("debit", ignoreCase = true)
            )
        }.takeIf { it >= 0 } ?: 0

        val headerLine = lines[headerIdx]
        val headers = parseCsvRow(headerLine).map { it.trim() }

        fun findIndex(target: String): Int =
            headers.indexOfFirst { CsvColumnMapper.resolveStandardColumn(it) == target }

        val idIdx = findIndex("id")
        val dateIdx = findIndex("date")
        val typeIdx = findIndex("type")
        val amountIdx = findIndex("amount")
        val debitIdx = findIndex("debit")
        val creditIdx = findIndex("credit")
        val fromIdx = findIndex("fromaccount")
        val toIdx = findIndex("toaccount")
        val catIdx = findIndex("category")
        val descIdx = findIndex("description")
        val notesIdx = findIndex("notes")

        val accountColIndices = mutableListOf<Pair<String, Int>>()
        headers.forEachIndexed { index, h ->
            if (h.isNotBlank() && !CsvColumnMapper.isKnownStandardHeader(h)) {
                accountColIndices.add(h to index)
            }
        }

        val errors = mutableListOf<String>()
        val rawTransactions = mutableListOf<RawTx>()
        val newAccounts = mutableSetOf<String>()
        val newCategories = mutableSetOf<String>()
        val seenSignatures = mutableSetOf<String>()

        val existingAccountNames = existingAccounts.map { it.name.lowercase() }.toSet()
        val existingCategoryNames = existingCategories.map { it.name.lowercase() }.toSet()

        var txRowIndex = 0
        for (lineIdx in (headerIdx + 1) until lines.size) {
            val rawLine = lines[lineIdx]
            val line = rawLine.trim()
            if (line.isBlank()) continue
            if (line.startsWith("#")) {
                val upper = line.uppercase()
                if (upper.startsWith("#TRANSACTIONS") || upper.startsWith("#FORMAT=")) continue
                break
            }
            val row = parseCsvRow(rawLine)
            if (row.isEmpty() || row.all { it.isBlank() }) continue
            txRowIndex++

            try {
                val extracted = extractRow(
                    txRowIndex, lineIdx, row, dateIdx, typeIdx, amountIdx, debitIdx, creditIdx,
                    fromIdx, toIdx, catIdx, descIdx, notesIdx, accountColIndices
                )
                if (extracted.error != null) {
                    errors.add(extracted.error)
                    continue
                }
                val raw = extracted.rawTx ?: continue

                val sig = CsvBackupManager.transactionSignature(
                    date = raw.date,
                    type = raw.type,
                    amount = raw.amount,
                    fromAccount = raw.fromAccountName,
                    toAccount = raw.toAccountName,
                    category = raw.categoryName,
                    description = raw.description,
                    notes = raw.notes
                )
                if (!seenSignatures.add(sig)) {
                    errors.add("duplicate signature row #$txRowIndex (line #${lineIdx + 1})")
                    continue
                }

                if (raw.fromAccountName.isNotBlank() && !existingAccountNames.contains(raw.fromAccountName.lowercase())) {
                    newAccounts.add(raw.fromAccountName)
                }
                if (raw.toAccountName.isNotBlank() && !existingAccountNames.contains(raw.toAccountName.lowercase())) {
                    newAccounts.add(raw.toAccountName)
                }
                if (raw.categoryName.isNotBlank() && !existingCategoryNames.contains(raw.categoryName.lowercase())) {
                    newCategories.add(raw.categoryName)
                }

                rawTransactions.add(raw)
            } catch (e: Exception) {
                errors.add("unparseable row #$txRowIndex (line #${lineIdx + 1}): ${e.message}")
            }
        }

        val hasTwoSidedColumns = fromIdx >= 0 && toIdx >= 0
        val processedTransactions = if (mergeTransfers && !isUnifiedFormat && !hasTwoSidedColumns) {
            mergePairedTransfers(rawTransactions)
        } else {
            rawTransactions
        }

        var dummyId = 1000L
        val dummyAccountMap = existingAccounts.associate { it.name.lowercase() to it.id }.toMutableMap()
        val dummyCategoryMap = existingCategories.associate { it.name.lowercase() to it.id }.toMutableMap()

        newAccounts.forEach { name ->
            dummyAccountMap[name.lowercase()] = dummyId++
        }
        newCategories.forEach { name ->
            dummyCategoryMap[name.lowercase()] = dummyId++
        }

        val entities = processedTransactions.map { raw ->
            val fromId = if (raw.fromAccountName.isNotBlank()) dummyAccountMap[raw.fromAccountName.lowercase()] else null
            var toId = if (raw.toAccountName.isNotBlank()) dummyAccountMap[raw.toAccountName.lowercase()] else null
            if (raw.type == "INCOME" && toId == null && fromId != null) {
                toId = fromId
            }
            val catId = if (raw.categoryName.isNotBlank()) dummyCategoryMap[raw.categoryName.lowercase()] else null

            TransactionEntity(
                date = raw.date,
                type = raw.type,
                amount = raw.amount,
                fromAccountId = if (raw.type == "INCOME") null else fromId,
                toAccountId = toId,
                categoryId = catId,
                description = raw.description,
                notes = raw.notes
            )
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val minDate = entities.minOfOrNull { it.date }
        val maxDate = entities.maxOfOrNull { it.date }
        val range = if (minDate != null && maxDate != null) {
            "${sdf.format(Date(minDate))} to ${sdf.format(Date(maxDate))}"
        } else ""

        return CsvPreviewResult(
            totalRows = txRowIndex,
            validRows = entities.size,
            parsedTransactions = entities,
            newAccountsToCreate = newAccounts.toList(),
            newCategoriesToCreate = newCategories.toList(),
            errors = errors,
            dateRange = range
        )
    }

    private fun mergePairedTransfers(items: List<RawTx>): List<RawTx> {
        val merged = mutableListOf<RawTx>()
        val consumed = BooleanArray(items.size)

        for (i in items.indices) {
            if (consumed[i]) continue
            val itemA = items[i]
            // Never merge rows that are already two-sided or marked TRANSFER
            if (itemA.type == "TRANSFER" || (itemA.fromAccountName.isNotBlank() && itemA.toAccountName.isNotBlank())) {
                consumed[i] = true
                merged.add(itemA)
                continue
            }

            var foundPair = false
            for (j in i + 1 until items.size) {
                if (consumed[j]) continue
                val itemB = items[j]
                if (itemB.type == "TRANSFER" || (itemB.fromAccountName.isNotBlank() && itemB.toAccountName.isNotBlank())) {
                    continue
                }

                val sameAmount = abs(itemA.amount - itemB.amount) < 0.01
                val aIsOutBIsIn = itemA.type == "EXPENSE" && itemB.type == "INCOME" &&
                        itemA.fromAccountName.isNotBlank() && itemA.toAccountName.isBlank() &&
                        itemB.toAccountName.isNotBlank() && itemB.fromAccountName.isBlank() &&
                        !itemA.fromAccountName.equals(itemB.toAccountName, ignoreCase = true)
                val aIsInBIsOut = itemA.type == "INCOME" && itemB.type == "EXPENSE" &&
                        itemA.toAccountName.isNotBlank() && itemA.fromAccountName.isBlank() &&
                        itemB.fromAccountName.isNotBlank() && itemB.toAccountName.isBlank() &&
                        !itemA.toAccountName.equals(itemB.fromAccountName, ignoreCase = true)
                val looksLikeTransferDesc = itemA.description.contains("transfer", ignoreCase = true) ||
                        itemB.description.contains("transfer", ignoreCase = true)

                if (sameAmount && (aIsOutBIsIn || aIsInBIsOut) && looksLikeTransferDesc && abs(itemA.date - itemB.date) < 86400000) {
                    consumed[i] = true
                    consumed[j] = true
                    foundPair = true
                    val from = if (aIsOutBIsIn) itemA.fromAccountName else itemB.fromAccountName
                    val to = if (aIsOutBIsIn) itemB.toAccountName else itemA.toAccountName
                    merged.add(
                        RawTx(
                            date = itemA.date,
                            type = "TRANSFER",
                            amount = itemA.amount,
                            fromAccountName = from,
                            toAccountName = to,
                            categoryName = "",
                            description = itemA.description.ifBlank { "Transfer: $from -> $to" },
                            notes = itemA.notes
                        )
                    )
                    break
                }
            }
            if (!foundPair) {
                consumed[i] = true
                merged.add(itemA)
            }
        }
        return merged
    }

    suspend fun commitImport(
        repository: FinanceRepository,
        inputStream: InputStream,
        existingAccounts: List<Account>,
        existingCategories: List<Category>,
        mergeTransfers: Boolean = true
    ): Int {
        val fullText = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        val lines = CsvBackupManager.splitLines(fullText)
        if (lines.isEmpty()) return 0

        val isUnifiedFormat = lines.any { it.trim().removePrefix("\uFEFF").uppercase().startsWith("#FORMAT=") }
        val headerIdx = lines.indexOfFirst { line ->
            val clean = line.trim().removePrefix("\uFEFF").trim('"', '\'')
            !clean.startsWith("#") && (
                clean.contains("date", ignoreCase = true) ||
                clean.contains("amount", ignoreCase = true) ||
                clean.contains("debit", ignoreCase = true)
            )
        }.takeIf { it >= 0 } ?: 0

        val headerLine = lines[headerIdx]
        val headers = parseCsvRow(headerLine).map { it.trim() }

        fun findIndex(target: String): Int =
            headers.indexOfFirst { CsvColumnMapper.resolveStandardColumn(it) == target }

        val idIdx = findIndex("id")
        val dateIdx = findIndex("date")
        val typeIdx = findIndex("type")
        val amountIdx = findIndex("amount")
        val debitIdx = findIndex("debit")
        val creditIdx = findIndex("credit")
        val fromIdx = findIndex("fromaccount")
        val toIdx = findIndex("toaccount")
        val catIdx = findIndex("category")
        val descIdx = findIndex("description")
        val notesIdx = findIndex("notes")

        val accountColIndices = mutableListOf<Pair<String, Int>>()
        headers.forEachIndexed { index, h ->
            if (h.isNotBlank() && !CsvColumnMapper.isKnownStandardHeader(h)) {
                accountColIndices.add(h to index)
            }
        }

        val accountMap = existingAccounts.associateBy { it.name.lowercase() }.toMutableMap()
        val categoryMap = existingCategories.associateBy { it.name.lowercase() }.toMutableMap()

        val rawList = mutableListOf<RawTx>()

        var txRowIndex = 0
        for (lineIdx in (headerIdx + 1) until lines.size) {
            val rawLine = lines[lineIdx]
            val line = rawLine.trim()
            if (line.isBlank()) continue
            if (line.startsWith("#")) {
                val upper = line.uppercase()
                if (upper.startsWith("#TRANSACTIONS") || upper.startsWith("#FORMAT=")) continue
                break
            }
            val row = parseCsvRow(rawLine)
            if (row.isEmpty() || row.all { it.isBlank() }) continue
            txRowIndex++

            val extracted = extractRow(
                txRowIndex, lineIdx, row, dateIdx, typeIdx, amountIdx, debitIdx, creditIdx,
                fromIdx, toIdx, catIdx, descIdx, notesIdx, accountColIndices
            )
            val raw = extracted.rawTx ?: continue
            rawList.add(raw)
        }

        val hasTwoSidedColumns = fromIdx >= 0 && toIdx >= 0
        val processed = if (mergeTransfers && !isUnifiedFormat && !hasTwoSidedColumns) mergePairedTransfers(rawList) else rawList

        // Ensure missing accounts and categories are created automatically
        for (raw in processed) {
            if (raw.fromAccountName.isNotBlank() && !accountMap.containsKey(raw.fromAccountName.lowercase())) {
                val accType = CsvBackupManager.guessAccountType(raw.fromAccountName)
                val newAcc = Account(name = raw.fromAccountName, openingBalance = 0.0, accountType = accType)
                val id = repository.insertAccount(newAcc)
                accountMap[raw.fromAccountName.lowercase()] = newAcc.copy(id = id)
            }
            if (raw.toAccountName.isNotBlank() && !accountMap.containsKey(raw.toAccountName.lowercase())) {
                val accType = CsvBackupManager.guessAccountType(raw.toAccountName)
                val newAcc = Account(name = raw.toAccountName, openingBalance = 0.0, accountType = accType)
                val id = repository.insertAccount(newAcc)
                accountMap[raw.toAccountName.lowercase()] = newAcc.copy(id = id)
            }
            if (raw.categoryName.isNotBlank() && !categoryMap.containsKey(raw.categoryName.lowercase())) {
                val newCat = Category(name = raw.categoryName, color = "#6B7280")
                val id = repository.insertCategory(newCat)
                categoryMap[raw.categoryName.lowercase()] = newCat.copy(id = id)
            }
        }

        var importedCount = 0
        for (raw in processed) {
            val fromId = if (raw.fromAccountName.isNotBlank()) accountMap[raw.fromAccountName.lowercase()]?.id else null
            var toId = if (raw.toAccountName.isNotBlank()) accountMap[raw.toAccountName.lowercase()]?.id else null
            if (raw.type == "INCOME" && toId == null && fromId != null) {
                toId = fromId
            }
            val catId = if (raw.categoryName.isNotBlank()) categoryMap[raw.categoryName.lowercase()]?.id else null

            val entity = TransactionEntity(
                date = raw.date,
                type = raw.type,
                amount = raw.amount,
                fromAccountId = if (raw.type == "INCOME") null else fromId,
                toAccountId = toId,
                categoryId = catId,
                description = raw.description,
                notes = raw.notes
            )
            repository.insertTransaction(entity)
            importedCount++
        }

        return importedCount
    }

    fun parseCsvRow(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    curVal.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(curVal.toString())
                curVal = StringBuilder()
            } else {
                curVal.append(ch)
            }
            i++
        }
        result.add(curVal.toString())
        return result
    }
}
