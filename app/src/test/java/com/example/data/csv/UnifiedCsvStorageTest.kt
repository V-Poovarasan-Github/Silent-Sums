import com.example.data.datastore.AppSettings
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.model.TransactionEntity
import com.example.importbackup.CsvBackupManager
import com.example.importbackup.CsvImporter
import com.example.importbackup.UnifiedCsvStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class UnifiedCsvStorageTest {

    @Test
    fun testTransactionColumnAliases() {
        // CSV with arbitrary aliases: "Date & Time", "Direction", "Cost", "Account Used", "Tag/Category", "Description"
        val csv = """
            Date & Time,Direction,Cost,Account Used,Tag/Category,Description
            2026-04-10 14:30:00,EXPENSE,350.50,HDFC Bank,Food,Lunch with team
            2026-04-11 09:00:00,INCOME,50000.00,HDFC Bank,Salary,Monthly Salary
        """.trimIndent()

        val parsed = CsvBackupManager.parseCsvFull(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))

        assertEquals(2, parsed.transactions.size)
        assertEquals(350.50, parsed.transactions[0].amount, 0.01)
        assertEquals("EXPENSE", parsed.transactions[0].type)
        assertEquals("HDFC Bank", parsed.transactions[0].fromAccount)
        assertEquals("Food", parsed.transactions[0].category)
        assertEquals("Lunch with team", parsed.transactions[0].notes)

        assertEquals(50000.00, parsed.transactions[1].amount, 0.01)
        assertEquals("INCOME", parsed.transactions[1].type)
    }

    @Test
    fun testDebitCreditColumnFallback() {
        // CSV without Type column, using Debit and Credit columns instead
        val csv = """
            Tx Date,Debit,Credit,Account,Category,Narration
            2026-04-10 12:00:00,250.00,,Wallet,Food,Snacks
            2026-04-11 10:00:00,,15000.00,Bank,Salary,Freelance payout
        """.trimIndent()

        val parsed = CsvBackupManager.parseCsvFull(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))

        assertEquals(2, parsed.transactions.size)
        assertEquals(250.00, parsed.transactions[0].amount, 0.01)
        assertEquals("EXPENSE", parsed.transactions[0].type)
        assertEquals("Snacks", parsed.transactions[0].notes)

        assertEquals(15000.00, parsed.transactions[1].amount, 0.01)
        assertEquals("INCOME", parsed.transactions[1].type)
    }

    @Test
    fun testCsvImporterWithBankStatementHeadersAndDebitCredit() {
        // Typical Indian / Excel statement export: Date, Narration, Chq/Ref No, Withdrawal Amt., Deposit Amt., Closing Balance, Account Name
        val excelCsv = """
            Txn Date,Particulars,Ref No,Withdrawal Amt.,Deposit Amt.,Balance,Bank Name,Expense Head
            15/04/2026,Swiggy Order #8812,UPI-12345,420.00,,45580.00,HDFC Bank,Food
            16/04/2026,Client Consulting Fee,NEFT-991,,25000.00,70580.00,HDFC Bank,Income
        """.trimIndent()

        val preview = CsvImporter.previewCsv(
            ByteArrayInputStream(excelCsv.toByteArray(Charsets.UTF_8)),
            existingAccounts = emptyList(),
            existingCategories = emptyList()
        )

        assertEquals(2, preview.validRows)
        assertEquals(0, preview.errors.size)

        val tx1 = preview.parsedTransactions[0]
        assertEquals(420.00, tx1.amount, 0.01)
        assertEquals("EXPENSE", tx1.type)
        assertEquals("Swiggy Order #8812", tx1.description)

        val tx2 = preview.parsedTransactions[1]
        assertEquals(25000.00, tx2.amount, 0.01)
        assertEquals("INCOME", tx2.type)
        assertEquals("Client Consulting Fee", tx2.description)
    }

    @Test
    fun testCsvImporterWithNegativeAmountsAndDrCrTypes() {
        val excelCsv = """
            Date,Type,Amount,Category,Payment Mode,Remarks
            2026-04-20,DR,-750.00,Groceries,SBI Bank,Supermarket shopping
            2026-04-21,CR,5000.00,Bonus,SBI Bank,Performance reward
        """.trimIndent()

        val preview = CsvImporter.previewCsv(
            ByteArrayInputStream(excelCsv.toByteArray(Charsets.UTF_8)),
            existingAccounts = emptyList(),
            existingCategories = emptyList()
        )

        assertEquals(2, preview.validRows)
        assertEquals(750.00, preview.parsedTransactions[0].amount, 0.01)
        assertEquals("EXPENSE", preview.parsedTransactions[0].type)
        assertEquals("Supermarket shopping", preview.parsedTransactions[0].notes)

        assertEquals(5000.00, preview.parsedTransactions[1].amount, 0.01)
        assertEquals("INCOME", preview.parsedTransactions[1].type)
    }

    @Test
    fun testCommitmentSectionExportAndParse() {
        val tx = TransactionEntity(
            id = 1L,
            amount = 120.0,
            type = "EXPENSE",
            date = 1712750000000L,
            categoryId = 1L,
            fromAccountId = 1L,
            notes = "Snack"
        )
        val acc = Account(id = 1L, name = "Cash", openingBalance = 1000.0, accountType = "CASH")
        val cat = Category(id = 1L, name = "Food", color = "#10B981")

        val commitment1 = Commitment(
            id = 10L,
            type = "RECURRING",
            name = "Netflix Subscription",
            amount = 649.0,
            direction = "OUTGOING",
            dayOfMonth = 15,
            accountId = 1L,
            categoryId = 1L,
            active = true,
            settled = false,
            lastPostedCycle = "2026-03"
        )

        val commitment2 = Commitment(
            id = 11L,
            type = "IOU",
            name = "Dave dinner share",
            amount = 450.0,
            direction = "INCOMING",
            dueDate = 1713000000000L,
            accountId = 1L,
            active = true,
            settled = false
        )

        val csvContent = CsvBackupManager.buildCsv(
            transactions = listOf(tx),
            accounts = listOf(acc),
            categories = listOf(cat),
            commitments = listOf(commitment1, commitment2)
        )

        assertTrue(csvContent.contains("Dues & Recurring") || csvContent.contains("COMMITMENTS"))
        assertTrue(csvContent.contains("Netflix Subscription"))
        assertTrue(csvContent.contains("Dave dinner share"))

        // Now parse it back
        val parsed = CsvBackupManager.parseCsvFull(ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8)))

        assertEquals(1, parsed.transactions.size)
        assertEquals(120.0, parsed.transactions[0].amount, 0.01)

        assertEquals(2, parsed.dues.size)
        val parsedRecurring = parsed.dues.find { it.name == "Netflix Subscription" }
        assertNotNull(parsedRecurring)
        assertEquals("RECURRING", parsedRecurring!!.type)
        assertEquals(649.0, parsedRecurring.amount, 0.01)
        assertEquals(15, parsedRecurring.dayOfMonth)

        val parsedIou = parsed.dues.find { it.name == "Dave dinner share" }
        assertNotNull(parsedIou)
        assertEquals("IOU", parsedIou!!.type)
        assertEquals("INCOMING", parsedIou.direction)
        assertEquals(450.0, parsedIou.amount, 0.01)
    }

    @Test
    fun testCategoryBreakdownPreservesAllCategoriesWithoutDuplicateOther() {
        val categories = listOf(
            Category(id = 1L, name = "Transport & Fuel", color = "#3B82F6"),
            Category(id = 2L, name = "Other", color = "#64748B"),
            Category(id = 3L, name = "Entertainment", color = "#EC4899"),
            Category(id = 4L, name = "Bills & Utilities", color = "#8B5CF6"),
            Category(id = 5L, name = "Health & Wellness", color = "#06B6D4"),
            Category(id = 6L, name = "Groceries & Shopping", color = "#F59E0B"),
            Category(id = 7L, name = "Food & Dining", color = "#EF4444"),
            Category(id = 8L, name = "Loan", color = "#D97706")
        )

        val transactions = listOf(
            com.example.domain.model.EnrichedTransaction(
                entity = TransactionEntity(id = 1L, date = 1000L, type = "EXPENSE", amount = 2835.0, categoryId = 1L)
            ),
            com.example.domain.model.EnrichedTransaction(
                entity = TransactionEntity(id = 2L, date = 1000L, type = "EXPENSE", amount = 1412.0, categoryId = 2L)
            ),
            com.example.domain.model.EnrichedTransaction(
                entity = TransactionEntity(id = 3L, date = 1000L, type = "EXPENSE", amount = 1345.0, categoryId = 3L)
            ),
            com.example.domain.model.EnrichedTransaction(
                entity = TransactionEntity(id = 4L, date = 1000L, type = "EXPENSE", amount = 961.0, categoryId = 4L)
            ),
            com.example.domain.model.EnrichedTransaction(
                entity = TransactionEntity(id = 5L, date = 1000L, type = "EXPENSE", amount = 643.0, categoryId = 5L)
            ),
            com.example.domain.model.EnrichedTransaction(
                entity = TransactionEntity(id = 6L, date = 1000L, type = "EXPENSE", amount = 598.0, categoryId = 6L)
            ),
            com.example.domain.model.EnrichedTransaction(
                entity = TransactionEntity(id = 7L, date = 1000L, type = "EXPENSE", amount = 567.0, categoryId = 7L)
            ),
            com.example.domain.model.EnrichedTransaction(
                entity = TransactionEntity(id = 8L, date = 1000L, type = "EXPENSE", amount = 269.0, categoryId = 8L)
            )
        )

        val breakdown = com.example.domain.calculator.LedgerCalculator.computeCategoryBreakdown(transactions, categories)

        // All 8 categories should be present, not truncated or bundled
        assertEquals(8, breakdown.size)
        // "Other" should appear exactly once (from categoryId = 2L)
        val otherItems = breakdown.filter { it.name == "Other" }
        assertEquals(1, otherItems.size)
        assertEquals(2L, otherItems.first().categoryId)
        assertEquals(1412.0, otherItems.first().amount, 0.01)

        // "Food & Dining" and "Loan" must exist with their respective IDs
        val foodItem = breakdown.find { it.name == "Food & Dining" }
        assertNotNull(foodItem)
        assertEquals(7L, foodItem!!.categoryId)

        val loanItem = breakdown.find { it.name == "Loan" }
        assertNotNull(loanItem)
        assertEquals(8L, loanItem!!.categoryId)
    }

    @Test
    fun testUncategorizedFilterMatching() {
        val tx1 = com.example.domain.model.EnrichedTransaction(
            entity = TransactionEntity(id = 1L, date = 1000L, type = "EXPENSE", amount = 100.0, categoryId = null)
        )
        val tx2 = com.example.domain.model.EnrichedTransaction(
            entity = TransactionEntity(id = 2L, date = 1000L, type = "EXPENSE", amount = 200.0, categoryId = 5L)
        )

        val filterUncat = com.example.domain.model.FilterState(selectedCategoryId = -1L)
        val filtered = com.example.domain.calculator.LedgerCalculator.filterTransactions(listOf(tx1, tx2), filterUncat)

        assertEquals(1, filtered.size)
        assertEquals(1L, filtered.first().id)
    }

    @Test
    fun testUnknownSectionWholesaleSkip_importsZeroJunkRows() {
        val csv = """
            #FORMAT=2
            Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes
            2026-09-27 13:44,EXPENSE,1092.34,ICICI Credit,,Entertainment,Paradise * 4,Dinner

            #ACCOUNTS
            Name,Type,Opening,Archived
            ICICI Credit,CREDIT_CARD,0.00,false

            #CATEGORIES
            Name,Color,Archived
            Entertainment,#EC4899,false

            #UNKNOWN_FUTURE_SECTION
            foo,bar,baz,123,456
            junk header 1,junk header 2
            some,random,rows,here,999

            #BUDGETS
            Name,ScopeType,TargetName,LimitAmount,Active
            Dining Cap,CATEGORY,Entertainment,5000.00,true
            Card Limit,ACCOUNT,ICICI Credit,20000.00,true

            #ANOTHER_UNKNOWN_TAG (with parenthesis)
            ignore,all,of,this,content
        """.trimIndent()

        val parsed = CsvBackupManager.parseCsvFull(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)))
        // Exactly 1 transaction parsed — zero junk rows from unknown sections!
        assertEquals(1, parsed.transactions.size)
        assertEquals(1092.34, parsed.transactions[0].amount, 0.01)
        assertEquals("Paradise * 4", parsed.transactions[0].description)
        assertEquals(2, parsed.formatVersion)

        // 2 budgets parsed
        assertEquals(2, parsed.budgets.size)
        assertEquals("Dining Cap", parsed.budgets[0].name)
        assertEquals("CATEGORY", parsed.budgets[0].scopeType)
        assertEquals("Entertainment", parsed.budgets[0].targetName)
        assertEquals(5000.0, parsed.budgets[0].limitAmount, 0.01)
        assertTrue(parsed.budgets[0].active)
    }

    @Test
    fun testLegacyFixtureWithoutFormatLine_parsesCorrectly() {
        val legacyCsv = """
            Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes
            2026-09-27 13:44,EXPENSE,100.00,Cash,,Food,Snack,
        """.trimIndent()

        val parsed = CsvBackupManager.parseCsvFull(ByteArrayInputStream(legacyCsv.toByteArray(Charsets.UTF_8)))
        assertEquals(1, parsed.formatVersion)
        assertEquals(1, parsed.transactions.size)
        assertEquals(0, parsed.budgets.size)
        assertEquals(0, parsed.dues.size)
    }
}
