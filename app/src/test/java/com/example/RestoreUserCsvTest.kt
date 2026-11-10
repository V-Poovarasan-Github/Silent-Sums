package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.repository.FinanceRepository
import com.example.importbackup.UnifiedCsvStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.Calendar
import com.example.domain.calculator.LedgerCalculator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoreUserCsvTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository

    private val userCsv = """
Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes
2026-09-18 19:25,EXPENSE,269.36,ICICI Credit,,Loan,"Washing Machine, 17th Sept",additional chargers
2026-09-18 21:58,EXPENSE,605.80,ICICI Credit,,Health & Wellness,Tablets for Mom,At JayaDeva Hospital [+11.80 Card charges]
2026-09-18 21:59,EXPENSE,960.41,ICICI Credit,,Transport & Fuel,FastTag for Car,13 Sept
2026-09-18 22:00,EXPENSE,170.00,ICICI Credit,,Food & Dining,Tea,"While returning Bangalore, 13th Sept"
2026-09-18 22:00,EXPENSE,1674.53,ICICI Credit,,Transport & Fuel,Petrol for Car,13 Sept
2026-09-18 22:04,EXPENSE,247.49,ICICI Credit,,Food & Dining,"BlinkIt Snacks Order, 16th Sept","199 + 48.49, Unibic Cake and Stuff"
2026-09-18 22:06,EXPENSE,960.82,ICICI Credit,,Bills & Utilities,"TV Bill, 17th Sept","TV Bill, 17th Sept"
2026-09-19 10:34,EXPENSE,150.00,ICICI,,Food & Dining,2 * Idle Parcel,2 * Idle Parcel
2026-09-19 11:23,EXPENSE,1400.00,Cash,,Others,Shravana Shanivara,Includes Pooja related items and Groceries.
2026-09-19 18:58,EXPENSE,200.00,SBI Amma,,Beverages/Snacks,5 * Grape Juice,At Gopi Ice Cream
2026-09-19 20:49,EXPENSE,12.00,KBL,,Others,Xerox,Xerox
2026-09-21 11:16,EXPENSE,37.00,ICICI,,Health & Wellness,Clobetasol propionate Ointment,Clobetasol propionate Ointment
2026-09-21 11:25,EXPENSE,200.00,Cash,,Transport & Fuel,Petrol for Dad's Vehicle.,Petrol for Dad's Vehicle.
2026-09-21 12:17,EXPENSE,60.00,UBI,,Beverages/Snacks,SugarCane Juice * 2,SugarCane Juice * 2
2026-09-21 19:17,INCOME,5000.00,,Cash,Salary & Income,Dad's Salary,week 3 Payment.
2026-09-22 18:07,INCOME,11200.00,,Cash,Salary & Income,Dad's Salary,kept inside Pooja Room.
2026-09-22 19:16,EXPENSE,598.00,ICICI Credit,,Groceries & Shopping,Bought Shirt & Pant,From Flipkart
2026-09-22 20:46,INCOME,1500.00,,KBL,Salary & Income,From Kiran,To return Wonderla money to friends.
2026-09-22 20:47,EXPENSE,1345.00,KBL,,Entertainment,Wonderla Expenses,sent to Suprith
2026-09-22 21:53,INCOME,268.00,,KBL,Salary & Income,Flipkart Refund,Necklace
2026-09-23 20:48,EXPENSE,189.00,KBL,,Bills & Utilities,Mom's Mobile Recharge,
2026-09-23 21:11,EXPENSE,100.00,Cash,,Dad's Expense,,
2026-09-23 21:58,EXPENSE,100.00,Cash,,Beverages/Snacks,Cream bun + Orange Juice,
2026-09-24 18:59,EXPENSE,220.00,Cash,,Groceries & Shopping,Cooking Oil + Milk,
2026-09-26 19:05,EXPENSE,100.00,Cash,,Transport & Fuel,Jupiter 125CC,
2026-09-26 19:05,EXPENSE,100.00,Cash,,Beverages/Snacks,Ice Cream,
2026-09-26 19:06,EXPENSE,100.00,Cash,,Dad's Expense,Dad's Expenses,
2026-09-26 19:06,EXPENSE,29.00,KBL,,Bills & Utilities,Data AddOn 2GB,M51
2026-09-26 19:07,EXPENSE,100.00,ICICI Credit,,Others,Xerox + File,At PES College
2026-09-26 19:08,INCOME,7000.00,,Cash,Salary & Income,Dad's Salary,
2026-09-26 19:17,EXPENSE,80.00,Cash,,Dad's Expense,Dad's Expenses,80 was missing in total So adding now.
2026-09-27 13:44,EXPENSE,1092.34,ICICI Credit,,Entertainment,Paradise * 4,

#ACCOUNTS
Name,Type,Opening,Archived
Cash,CASH,9000.00,false
KBL,BANK,44.40,false
ICICI,BANK,258.47,false
ICICI Credit,CREDIT_CARD,0.00,false
SBI Appa,BANK,219.05,false
SBI Amma,BANK,355.23,false
UBI,BANK,1074.29,false

#CATEGORIES
Name,Color,Archived
Beverages/Snacks,#6366F1,false
Food & Dining,#EF4444,false
Loan,#EF4444,false
Groceries & Shopping,#F59E0B,false
Transport & Fuel,#3B82F6,false
Bills & Utilities,#8B5CF6,false
Entertainment,#EC4899,false
Health & Wellness,#06B6D4,false
Salary & Income,#10B981,false
Investments,#059669,false
Others,#64748B,false
Dad's Expense,#8B5CF6,false

#SETTINGS
currencySymbol=₹
monthStartDay=1
theme=AUTO
hideBalances=false
soundHapticsEnabled=false

#COMMITMENTS (Dues & Recurring)
type,name,amount,direction,dayOfMonth,dueDate,accountName,categoryName,active
IOU,To Kiran,1500.00,OUTGOING,,1793503800121,KBL,Others,true
RECURRING,Car Loan,11122.00,OUTGOING,10,,KBL,Loan,true
RECURRING,House Loan,25000.00,OUTGOING,4,,ICICI,Loan,true
    """.trimIndent()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRestoreUserCsv() = runBlocking {
        val stream = ByteArrayInputStream(userCsv.toByteArray(Charsets.UTF_8))
        val result = UnifiedCsvStorage.restoreFromInputStream(repository, stream, clearExisting = true)
        val allTx = repository.allTransactions.first()
        assertEquals(32, allTx.size)

        val paradiseTx = allTx.firstOrNull { it.description == "Paradise * 4" }
        assertNotNull(paradiseTx)
        val accounts = repository.allAccounts.first()
        val categories = repository.allCategories.first()
        val cardAcc = accounts.firstOrNull { it.name.equals("ICICI Credit", ignoreCase = true) }
        val entCat = categories.firstOrNull { it.name.equals("Entertainment", ignoreCase = true) }
        assertNotNull(cardAcc)
        assertNotNull(entCat)
        assertEquals(cardAcc!!.id, paradiseTx!!.fromAccountId)
        assertEquals(entCat!!.id, paradiseTx.categoryId)
        assertEquals(1092.34, paradiseTx.amount, 0.001)
        assertEquals("EXPENSE", paradiseTx.type)
        assertEquals("Paradise * 4", paradiseTx.description)
        assertEquals("Paradise * 4", paradiseTx.notes)

        val commitments1 = repository.allCommitments.first()
        assertEquals(3, commitments1.size)
        val kiran = commitments1.firstOrNull { it.name == "To Kiran" }
        assertNotNull(kiran)
        assertEquals("IOU", kiran!!.type)
        assertEquals(1500.00, kiran.amount, 0.001)
        assertEquals("OUTGOING", kiran.direction)
        assertNotNull(kiran.dueDate)
        assertEquals(true, kiran.active)
        val kiranAccount = accounts.firstOrNull { it.id == kiran.accountId }
        val kiranCategory = categories.firstOrNull { it.id == kiran.categoryId }
        assertNotNull(kiranAccount)
        assertNotNull(kiranCategory)
        assertEquals("KBL", kiranAccount!!.name)
        assertEquals("Others", kiranCategory!!.name)

        val carLoan = commitments1.firstOrNull { it.name == "Car Loan" }
        assertNotNull(carLoan)
        assertEquals("RECURRING", carLoan!!.type)
        assertEquals(11122.00, carLoan.amount, 0.001)
        assertEquals(10, carLoan.dayOfMonth)
        assertEquals(true, carLoan.active)
        val carAccount = accounts.firstOrNull { it.id == carLoan.accountId }
        val carCategory = categories.firstOrNull { it.id == carLoan.categoryId }
        assertNotNull(carAccount)
        assertNotNull(carCategory)
        assertEquals("KBL", carAccount!!.name)
        assertEquals("Loan", carCategory!!.name)

        val houseLoan = commitments1.firstOrNull { it.name == "House Loan" }
        assertNotNull(houseLoan)
        assertEquals("RECURRING", houseLoan!!.type)
        assertEquals(25000.00, houseLoan.amount, 0.001)
        assertEquals(4, houseLoan.dayOfMonth)
        assertEquals(true, houseLoan.active)
        val houseAccount = accounts.firstOrNull { it.id == houseLoan.accountId }
        val houseCategory = categories.firstOrNull { it.id == houseLoan.categoryId }
        assertNotNull(houseAccount)
        assertNotNull(houseCategory)
        assertEquals("ICICI", houseAccount!!.name)
        assertEquals("Loan", houseCategory!!.name)

        // Second restore over existing data
        val stream2 = ByteArrayInputStream(userCsv.toByteArray(Charsets.UTF_8))
        val result2 = UnifiedCsvStorage.restoreFromInputStream(repository, stream2, clearExisting = false)
        val allTx2 = repository.allTransactions.first()
        val commitments2 = repository.allCommitments.first()
        println("Tx after second restore: ${allTx2.size}, inserted=${result2.inserted}, skipped=${result2.skipped}")
        println("Commitments after second restore: ${commitments2.size}")
        assertEquals(32, allTx2.size)
        assertEquals(3, commitments2.size)
        assertEquals(0, result2.inserted)
        assertEquals(32, result2.skipped)
    }

    @Test
    fun testDistinctDescriptionsOnSameAccountAndAmountAreNotDuplicates() = runBlocking {
        val csv = """
Date,Type,Amount,FromAccount,ToAccount,Category,Description,Notes
2026-09-26 19:05,EXPENSE,100.00,Cash,,Transport & Fuel,Jupiter 125CC,
2026-09-26 19:05,EXPENSE,100.00,Cash,,Beverages/Snacks,Ice Cream,
2026-09-26 19:06,EXPENSE,100.00,Cash,,Dad's Expense,Dad's Expenses,

#ACCOUNTS
Name,Type,Opening,Archived
Cash,CASH,9000.00,false
        """.trimIndent()

        val stream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val res = UnifiedCsvStorage.restoreFromInputStream(repository, stream, clearExisting = true)
        assertEquals(3, res.inserted)
        assertEquals(3, repository.allTransactions.first().size)
    }

    @Test
    fun testInvestigateUserReportedIssues() = runBlocking {
        val stream = ByteArrayInputStream(userCsv.toByteArray(Charsets.UTF_8))
        UnifiedCsvStorage.restoreFromInputStream(repository, stream, clearExisting = true)
        val allTx = repository.allTransactions.first()
        val allAccounts = repository.allAccounts.first()
        val allCategories = repository.allCategories.first()

        // 1. Check Oct 1st KPIs:
        val oct1Cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 1, 10, 0, 0)
        }
        val oct1Kpis = LedgerCalculator.computeDashboardKpis(
            accounts = allAccounts,
            transactions = allTx,
            monthStartDay = 1,
            now = oct1Cal.timeInMillis
        )
        // Assert card spend last month is 6678.75 (not 0.0)
        assertEquals(6678.75, oct1Kpis.ccSpendLastMonth, 0.01)
        assertEquals(6678.75, oct1Kpis.totalOwed, 0.01)

        // 2. Check Sept KPIs (when Sept clicked):
        val sept30Cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 30, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val septKpis = LedgerCalculator.computeDashboardKpis(
            accounts = allAccounts,
            transactions = allTx,
            monthStartDay = 1,
            now = sept30Cal.timeInMillis
        )
        assertEquals(6678.75, septKpis.ccSpendThisMonth, 0.01)
        assertEquals(6678.75, septKpis.totalOwed, 0.01)

        // 3. Check Budgets when Sept is selected:
        val budget = com.example.data.model.BudgetEntity(
            id = 1L,
            name = "ICICI Credit Limit",
            scopeType = "ACCOUNT",
            targetName = "ICICI Credit",
            limitAmount = 6000.0,
            active = true
        )
        val budgetsSept = com.example.domain.budget.BudgetCalculator.computeBudgets(
            budgets = listOf(budget),
            transactions = allTx,
            accounts = allAccounts,
            categories = allCategories,
            monthStartDay = 1,
            referenceTime = sept30Cal.timeInMillis
        )
        assertEquals(1, budgetsSept.size)
        val b = budgetsSept.first()
        assertEquals(6678.75, b.spent, 0.01)
        assertEquals(6000.0, b.limit, 0.01)
        assertEquals(true, b.isOverLimit)
    }
}
