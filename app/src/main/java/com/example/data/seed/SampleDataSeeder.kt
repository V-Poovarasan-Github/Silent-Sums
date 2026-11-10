package com.example.data.seed

import com.example.data.AppDatabase
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.model.CustomField
import com.example.data.model.TransactionEntity
import java.util.Calendar

object SampleDataSeeder {

    suspend fun seedCleanDefaults(database: AppDatabase) {
        val accountDao = database.accountDao()
        val categoryDao = database.categoryDao()
        val customFieldDao = database.customFieldDao()
        val transactionDao = database.transactionDao()
        val commitmentDao = database.commitmentDao()
        val budgetDao = database.budgetDao()

        // Clear all dummy transactions and custom fields
        transactionDao.clearAll()
        customFieldDao.clearAll()
        accountDao.clearAll()
        categoryDao.clearAll()
        commitmentDao.clearAll()
        budgetDao.clearAll()

        // 1. Seed Clean Starter Accounts with 0 balance
        accountDao.insert(Account(name = "Bank Account", openingBalance = 0.0, sortOrder = 0, accountType = "BANK"))
        accountDao.insert(Account(name = "Cash", openingBalance = 0.0, sortOrder = 1, accountType = "CASH"))

        // 2. Seed Clean Standard Categories
        categoryDao.insert(Category(name = "Food & Dining", color = "#EF4444", iconText = "🍽️", sortOrder = 0))
        categoryDao.insert(Category(name = "Groceries & Shopping", color = "#F59E0B", iconText = "🛒", sortOrder = 1))
        categoryDao.insert(Category(name = "Transport & Fuel", color = "#3B82F6", iconText = "🚗", sortOrder = 2))
        categoryDao.insert(Category(name = "Bills & Utilities", color = "#8B5CF6", iconText = "⚡", sortOrder = 3))
        categoryDao.insert(Category(name = "Entertainment", color = "#EC4899", iconText = "🎬", sortOrder = 4))
        categoryDao.insert(Category(name = "Health & Wellness", color = "#06B6D4", iconText = "🏥", sortOrder = 5))
        categoryDao.insert(Category(name = "Salary & Income", color = "#10B981", iconText = "💰", sortOrder = 6))
        categoryDao.insert(Category(name = "Investments", color = "#059669", iconText = "📈", sortOrder = 7))
        categoryDao.insert(Category(name = "Other", color = "#64748B", iconText = null, sortOrder = 8))

        // 0 Transactions seeded. 100% clean ledger ready for user.
    }

    suspend fun seedSampleData(database: AppDatabase) {
        val dbName = database.openHelper.databaseName
        if (dbName == com.example.data.StorageConstants.REAL_DATABASE_NAME) {
            throw IllegalStateException("SampleDataSeeder.seedSampleData refused: cannot seed real database '$dbName'")
        }
        val accountDao = database.accountDao()
        val categoryDao = database.categoryDao()
        val customFieldDao = database.customFieldDao()
        val transactionDao = database.transactionDao()
        val commitmentDao = database.commitmentDao()
        val budgetDao = database.budgetDao()

        // Clear existing to avoid duplicates when user toggles sample data
        transactionDao.clearAll()
        accountDao.clearAll()
        categoryDao.clearAll()
        customFieldDao.clearAll()
        commitmentDao.clearAll()
        budgetDao.clearAll()

        // 1. Seed Accounts (Bank, Cash, and Credit Card)
        val salaryAcId = accountDao.insert(
            Account(name = "Salary A/C", openingBalance = 0.0, sortOrder = 0, accountType = "BANK")
        )
        val personalId = accountDao.insert(
            Account(name = "Personal", openingBalance = 6240.24, sortOrder = 1, accountType = "BANK")
        )
        val momsAcId = accountDao.insert(
            Account(name = "Mom's A/C", openingBalance = 10000.0, sortOrder = 2, accountType = "BANK")
        )
        val dadsAcId = accountDao.insert(
            Account(name = "Dad's A/C", openingBalance = 16600.50, sortOrder = 3, accountType = "BANK")
        )
        val cashHomeId = accountDao.insert(
            Account(name = "Cash at Home", openingBalance = 4040.0, sortOrder = 4, accountType = "CASH")
        )
        val creditCardId = accountDao.insert(
            Account(name = "HDFC Credit Card", openingBalance = 0.0, sortOrder = 5, accountType = "CREDIT_CARD")
        )

        // 2. Seed Categories
        val foodCatId = categoryDao.insert(
            Category(name = "Food & Dining", color = "#EF4444", iconText = "🍽️", sortOrder = 0)
        )
        val groceriesCatId = categoryDao.insert(
            Category(name = "Groceries & Household", color = "#F59E0B", iconText = "🛒", sortOrder = 1)
        )
        val transportCatId = categoryDao.insert(
            Category(name = "Transport & Fuel", color = "#3B82F6", iconText = "🚗", sortOrder = 2)
        )
        val utilitiesCatId = categoryDao.insert(
            Category(name = "Bills & Utilities", color = "#8B5CF6", iconText = "🧾", sortOrder = 3)
        )
        val shoppingCatId = categoryDao.insert(
            Category(name = "Shopping & Lifestyle", color = "#EC4899", iconText = "🛍️", sortOrder = 4)
        )
        val travelCatId = categoryDao.insert(
            Category(name = "Travel & Trips", color = "#06B6D4", iconText = "✈️", sortOrder = 5)
        )
        val loanCatId = categoryDao.insert(
            Category(name = "Loan & EMI Repayments", color = "#D97706", iconText = "💰", sortOrder = 6)
        )
        val healthCatId = categoryDao.insert(
            Category(name = "Healthcare & Pharmacy", color = "#14B8A6", iconText = "🏥", sortOrder = 7)
        )
        val subscriptionsCatId = categoryDao.insert(
            Category(name = "Subscriptions & Media", color = "#6366F1", iconText = "🔁", sortOrder = 8)
        )
        val salaryCatId = categoryDao.insert(
            Category(name = "Salary Credit", color = "#10B981", iconText = "💰", sortOrder = 9)
        )
        val freelanceIncomeCatId = categoryDao.insert(
            Category(name = "Freelance & Consulting", color = "#059669", iconText = "💰", sortOrder = 10)
        )
        val rentalIncomeCatId = categoryDao.insert(
            Category(name = "Rental Income", color = "#047857", iconText = "🏠", sortOrder = 11)
        )
        val miscCatId = categoryDao.insert(
            Category(name = "Miscellaneous", color = "#64748B", iconText = null, sortOrder = 12)
        )

        // Archived Categories
        categoryDao.insert(
            Category(name = "Gym Membership 2024", color = "#94A3B8", iconText = "💪", archived = true, sortOrder = 13)
        )
        categoryDao.insert(
            Category(name = "College Tuition Archive", color = "#94A3B8", iconText = "🎓", archived = true, sortOrder = 14)
        )

        // 3. Seed Custom Fields
        customFieldDao.insert(
            CustomField(
                name = "Payment Mode",
                type = "DROPDOWN",
                options = "Paid via: UPI,Card,Cash,Net Banking",
                sortOrder = 0
            )
        )
        customFieldDao.insert(
            CustomField(
                name = "Vendor GSTIN",
                type = "TEXT",
                options = "",
                sortOrder = 1
            )
        )
        customFieldDao.insert(
            CustomField(
                name = "Receipt Attachment",
                type = "TEXT",
                options = "",
                sortOrder = 2
            )
        )
        customFieldDao.insert(
            CustomField(
                name = "Tax Deductible (80C/80D)",
                type = "DROPDOWN",
                options = "Yes,No,Pending Review",
                sortOrder = 3
            )
        )

        // 4. Seed Budgets (Across multiple scenarios: All spend, Category spend, Account spend, Over budget, Under budget)
        budgetDao.insert(
            BudgetEntity(
                name = "Monthly Total Spend Limit",
                scopeType = "ALL",
                targetName = "",
                limitAmount = 60000.0,
                active = true
            )
        )
        budgetDao.insert(
            BudgetEntity(
                name = "Food & Dining Budget",
                scopeType = "CATEGORY",
                targetName = "Food & Dining",
                limitAmount = 15000.0,
                active = true
            )
        )
        budgetDao.insert(
            BudgetEntity(
                name = "Transport & Fuel Cap",
                scopeType = "CATEGORY",
                targetName = "Transport & Fuel",
                limitAmount = 5000.0,
                active = true
            )
        )
        budgetDao.insert(
            BudgetEntity(
                name = "Shopping & Lifestyle Alert",
                scopeType = "CATEGORY",
                targetName = "Shopping & Lifestyle",
                limitAmount = 8000.0,
                active = true
            )
        )
        budgetDao.insert(
            BudgetEntity(
                name = "Personal Account Allowance",
                scopeType = "ACCOUNT",
                targetName = "Personal",
                limitAmount = 25000.0,
                active = true
            )
        )

        // 5. Seed Commitments (Recurring Dues, Upcoming Bills, Incoming Rent, IOUs/Lending)
        val nowMs = System.currentTimeMillis()
        val calNow = Calendar.getInstance()
        val currentDayOfMonth = calNow.get(Calendar.DAY_OF_MONTH)

        // Recurring Outgoing Commitments
        commitmentDao.insert(
            Commitment(
                type = "RECURRING",
                name = "Broadband Fiber Internet",
                amount = 1199.0,
                direction = "OUTGOING",
                dayOfMonth = 12,
                accountId = personalId,
                categoryId = utilitiesCatId,
                active = true,
                settled = false
            )
        )
        commitmentDao.insert(
            Commitment(
                type = "RECURRING",
                name = "Netflix & Spotify Family",
                amount = 799.0,
                direction = "OUTGOING",
                dayOfMonth = 15,
                accountId = personalId,
                categoryId = subscriptionsCatId,
                active = true,
                settled = false
            )
        )
        commitmentDao.insert(
            Commitment(
                type = "RECURRING",
                name = "Car Loan EMI",
                amount = 8500.0,
                direction = "OUTGOING",
                dayOfMonth = 7,
                accountId = dadsAcId,
                categoryId = loanCatId,
                active = true,
                settled = false
            )
        )
        commitmentDao.insert(
            Commitment(
                type = "RECURRING",
                name = "Apartment Society Maintenance",
                amount = 3200.0,
                direction = "OUTGOING",
                dayOfMonth = 20,
                accountId = momsAcId,
                categoryId = groceriesCatId,
                active = true,
                settled = false
            )
        )
        commitmentDao.insert(
            Commitment(
                type = "RECURRING",
                name = "Electricity & Utility Bill",
                amount = 2450.0,
                direction = "OUTGOING",
                dayOfMonth = 25,
                accountId = momsAcId,
                categoryId = utilitiesCatId,
                active = true,
                settled = false
            )
        )

        // Recurring Incoming Commitments
        commitmentDao.insert(
            Commitment(
                type = "RECURRING",
                name = "Flat Rental Income",
                amount = 18000.0,
                direction = "INCOMING",
                dayOfMonth = 5,
                accountId = dadsAcId,
                categoryId = rentalIncomeCatId,
                active = true,
                settled = false
            )
        )

        // One-time Dues / IOUs (Incoming and Outgoing)
        val threeDaysFromNow = nowMs + (3L * 24 * 60 * 60 * 1000)
        val sixDaysFromNow = nowMs + (6L * 24 * 60 * 60 * 1000)
        val pastTwoDays = nowMs - (2L * 24 * 60 * 60 * 1000)
        val pastWeek = nowMs - (7L * 24 * 60 * 60 * 1000)

        commitmentDao.insert(
            Commitment(
                type = "IOU",
                name = "Lent to Amit for Concert Booking",
                amount = 3500.0,
                direction = "INCOMING",
                dueDate = threeDaysFromNow,
                accountId = personalId,
                categoryId = shoppingCatId,
                active = true,
                settled = false
            )
        )
        commitmentDao.insert(
            Commitment(
                type = "IOU",
                name = "Refund due from Amazon Return",
                amount = 1850.0,
                direction = "INCOMING",
                dueDate = sixDaysFromNow,
                accountId = personalId,
                categoryId = shoppingCatId,
                active = true,
                settled = false
            )
        )
        commitmentDao.insert(
            Commitment(
                type = "IOU",
                name = "Dental Treatment Final Payment",
                amount = 2500.0,
                direction = "OUTGOING",
                dueDate = threeDaysFromNow,
                accountId = personalId,
                categoryId = healthCatId,
                active = true,
                settled = false
            )
        )
        commitmentDao.insert(
            Commitment(
                type = "IOU",
                name = "Settled: Office Team Lunch reimbursement",
                amount = 1400.0,
                direction = "INCOMING",
                dueDate = pastTwoDays,
                accountId = personalId,
                categoryId = foodCatId,
                active = false,
                settled = true,
                settledAt = pastTwoDays
            )
        )

        // 6. Seed Rich Transactions across historical months & current cycle
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) // 0-based

        fun dateFor(mOffset: Int, day: Int, hour: Int = 12, min: Int = 30): Long {
            return Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                add(Calendar.MONTH, mOffset)
                set(Calendar.DAY_OF_MONTH, day.coerceIn(1, 28))
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val transactions = mutableListOf<TransactionEntity>()

        // Historical Month -5
        transactions.add(TransactionEntity(date = dateFor(-5, 5, 9), type = "INCOME", amount = 45000.0, toAccountId = salaryAcId, categoryId = salaryCatId, description = "Monthly Salary Credit"))
        transactions.add(TransactionEntity(date = dateFor(-5, 6, 10), type = "TRANSFER", amount = 18000.0, fromAccountId = salaryAcId, toAccountId = personalId, description = "Monthly Living Expenses Transfer"))
        transactions.add(TransactionEntity(date = dateFor(-5, 6, 11), type = "TRANSFER", amount = 12000.0, fromAccountId = salaryAcId, toAccountId = momsAcId, description = "Household Allowance"))
        transactions.add(TransactionEntity(date = dateFor(-5, 6, 12), type = "TRANSFER", amount = 10000.0, fromAccountId = salaryAcId, toAccountId = dadsAcId, description = "Dad's Monthly Allocation"))
        transactions.add(TransactionEntity(date = dateFor(-5, 10, 14), type = "EXPENSE", amount = 6500.0, fromAccountId = personalId, categoryId = foodCatId, description = "Supermarket Grocery Basket", customValues = "{\"Payment Mode\": \"Paid via: UPI\"}"))
        transactions.add(TransactionEntity(date = dateFor(-5, 15, 16), type = "EXPENSE", amount = 3200.0, fromAccountId = personalId, categoryId = transportCatId, description = "Fuel & Cab Rides", customValues = "{\"Payment Mode\": \"Paid via: UPI\"}"))
        transactions.add(TransactionEntity(date = dateFor(-5, 18, 11), type = "EXPENSE", amount = 8500.0, fromAccountId = dadsAcId, categoryId = loanCatId, description = "Vehicle Loan EMI", customValues = "{\"Payment Mode\": \"Paid via: Net Banking\"}"))
        transactions.add(TransactionEntity(date = dateFor(-5, 22, 18), type = "EXPENSE", amount = 7800.0, fromAccountId = momsAcId, categoryId = groceriesCatId, description = "Monthly Provisions & Milk", customValues = "{\"Payment Mode\": \"Paid via: Card\"}"))

        // Historical Month -4
        transactions.add(TransactionEntity(date = dateFor(-4, 4, 9), type = "INCOME", amount = 48000.0, toAccountId = salaryAcId, categoryId = salaryCatId, description = "Monthly Salary Credit"))
        transactions.add(TransactionEntity(date = dateFor(-4, 5, 10), type = "TRANSFER", amount = 20000.0, fromAccountId = salaryAcId, toAccountId = personalId, description = "Personal Account Funding"))
        transactions.add(TransactionEntity(date = dateFor(-4, 5, 11), type = "TRANSFER", amount = 12000.0, fromAccountId = salaryAcId, toAccountId = momsAcId, description = "Family Expenses Fund"))
        transactions.add(TransactionEntity(date = dateFor(-4, 5, 12), type = "TRANSFER", amount = 10000.0, fromAccountId = salaryAcId, toAccountId = dadsAcId, description = "Dad's Account Fund"))
        transactions.add(TransactionEntity(date = dateFor(-4, 8, 12), type = "EXPENSE", amount = 5400.0, fromAccountId = personalId, categoryId = foodCatId, description = "Weekend Family Dining", customValues = "{\"Payment Mode\": \"Paid via: Card\"}"))
        transactions.add(TransactionEntity(date = dateFor(-4, 12, 15), type = "EXPENSE", amount = 4600.0, fromAccountId = personalId, categoryId = shoppingCatId, description = "Winter Clothes & Footwear", customValues = "{\"Payment Mode\": \"Paid via: UPI\"}"))
        transactions.add(TransactionEntity(date = dateFor(-4, 18, 10), type = "EXPENSE", amount = 8500.0, fromAccountId = dadsAcId, categoryId = loanCatId, description = "Vehicle Loan EMI", customValues = "{\"Payment Mode\": \"Paid via: Net Banking\"}"))
        transactions.add(TransactionEntity(date = dateFor(-4, 24, 17), type = "EXPENSE", amount = 6200.0, fromAccountId = momsAcId, categoryId = groceriesCatId, description = "Organic Vegetables & Ration", customValues = "{\"Payment Mode\": \"Paid via: UPI\"}"))

        // Historical Month -3
        transactions.add(TransactionEntity(date = dateFor(-3, 3, 9), type = "INCOME", amount = 52000.0, toAccountId = salaryAcId, categoryId = salaryCatId, description = "Monthly Salary & Performance Bonus"))
        transactions.add(TransactionEntity(date = dateFor(-3, 4, 10), type = "TRANSFER", amount = 22000.0, fromAccountId = salaryAcId, toAccountId = personalId, description = "Salary Allocation to Personal"))
        transactions.add(TransactionEntity(date = dateFor(-3, 4, 11), type = "TRANSFER", amount = 12000.0, fromAccountId = salaryAcId, toAccountId = momsAcId, description = "Allowance for Mom's A/C"))
        transactions.add(TransactionEntity(date = dateFor(-3, 4, 12), type = "TRANSFER", amount = 12000.0, fromAccountId = salaryAcId, toAccountId = dadsAcId, description = "Allocation for Dad's A/C"))
        transactions.add(TransactionEntity(date = dateFor(-3, 10, 19), type = "EXPENSE", amount = 11500.0, fromAccountId = personalId, categoryId = travelCatId, description = "Hills Resort Weekend Trip", customValues = "{\"Payment Mode\": \"Paid via: UPI\", \"Receipt Attachment\": \"Hotel_Booking_Oct.pdf\"}"))
        transactions.add(TransactionEntity(date = dateFor(-3, 16, 14), type = "EXPENSE", amount = 3400.0, fromAccountId = personalId, categoryId = healthCatId, description = "Quarterly Health Checkup & Meds", customValues = "{\"Payment Mode\": \"Paid via: Card\", \"Tax Deductible (80C/80D)\": \"Yes\"}"))
        transactions.add(TransactionEntity(date = dateFor(-3, 20, 11), type = "EXPENSE", amount = 8500.0, fromAccountId = dadsAcId, categoryId = loanCatId, description = "Vehicle Loan EMI", customValues = "{\"Payment Mode\": \"Paid via: Net Banking\"}"))
        transactions.add(TransactionEntity(date = dateFor(-3, 22, 17), type = "EXPENSE", amount = 7100.0, fromAccountId = momsAcId, categoryId = groceriesCatId, description = "Festival Sweets & Provisions", customValues = "{\"Payment Mode\": \"Paid via: Cash\"}"))

        // Historical Month -2
        transactions.add(TransactionEntity(date = dateFor(-2, 5, 9), type = "INCOME", amount = 48000.0, toAccountId = salaryAcId, categoryId = salaryCatId, description = "Monthly Salary Credit"))
        transactions.add(TransactionEntity(date = dateFor(-2, 6, 10), type = "TRANSFER", amount = 20000.0, fromAccountId = salaryAcId, toAccountId = personalId, description = "Personal Allocation"))
        transactions.add(TransactionEntity(date = dateFor(-2, 6, 11), type = "TRANSFER", amount = 12000.0, fromAccountId = salaryAcId, toAccountId = momsAcId, description = "Monthly House Budget"))
        transactions.add(TransactionEntity(date = dateFor(-2, 6, 12), type = "TRANSFER", amount = 10000.0, fromAccountId = salaryAcId, toAccountId = dadsAcId, description = "Dad's Monthly Allowance"))
        transactions.add(TransactionEntity(date = dateFor(-2, 10, 13), type = "EXPENSE", amount = 7200.0, fromAccountId = personalId, categoryId = foodCatId, description = "Fine Dining & Food Deliveries", customValues = "{\"Payment Mode\": \"Paid via: UPI\"}"))
        transactions.add(TransactionEntity(date = dateFor(-2, 14, 16), type = "EXPENSE", amount = 4800.0, fromAccountId = personalId, categoryId = shoppingCatId, description = "Electronics & Headphones", customValues = "{\"Payment Mode\": \"Paid via: Card\"}"))
        transactions.add(TransactionEntity(date = dateFor(-2, 19, 11), type = "EXPENSE", amount = 8500.0, fromAccountId = dadsAcId, categoryId = loanCatId, description = "Vehicle Loan EMI", customValues = "{\"Payment Mode\": \"Paid via: Net Banking\"}"))
        transactions.add(TransactionEntity(date = dateFor(-2, 23, 15), type = "EXPENSE", amount = 6800.0, fromAccountId = momsAcId, categoryId = groceriesCatId, description = "Home Appliances & Groceries", customValues = "{\"Payment Mode\": \"Paid via: Card\"}"))

        // Historical Month -1 (Last Month)
        transactions.add(TransactionEntity(date = dateFor(-1, 5, 9), type = "INCOME", amount = 50000.0, toAccountId = salaryAcId, categoryId = salaryCatId, description = "Monthly Salary Credit"))
        transactions.add(TransactionEntity(date = dateFor(-1, 6, 10), type = "TRANSFER", amount = 18000.0, fromAccountId = salaryAcId, toAccountId = personalId, description = "Salary Allocation to Personal"))
        transactions.add(TransactionEntity(date = dateFor(-1, 6, 11), type = "TRANSFER", amount = 10000.0, fromAccountId = salaryAcId, toAccountId = momsAcId, description = "Allowance for Mom's A/C"))
        transactions.add(TransactionEntity(date = dateFor(-1, 6, 12), type = "TRANSFER", amount = 12000.0, fromAccountId = salaryAcId, toAccountId = dadsAcId, description = "Allocation to Dad's A/C"))
        transactions.add(TransactionEntity(date = dateFor(-1, 8, 14), type = "EXPENSE", amount = 3800.0, fromAccountId = personalId, categoryId = foodCatId, description = "Team Lunch & Cafes", customValues = "{\"Payment Mode\": \"Paid via: UPI\"}"))
        transactions.add(TransactionEntity(date = dateFor(-1, 12, 11), type = "EXPENSE", amount = 2834.40, fromAccountId = momsAcId, categoryId = travelCatId, description = "Train Booking for Family Trip", customValues = "{\"Payment Mode\": \"Paid via: UPI\"}"))
        transactions.add(TransactionEntity(date = dateFor(-1, 18, 10), type = "EXPENSE", amount = 8500.0, fromAccountId = dadsAcId, categoryId = loanCatId, description = "Vehicle Loan EMI", customValues = "{\"Payment Mode\": \"Paid via: Net Banking\"}"))
        transactions.add(TransactionEntity(date = dateFor(-1, 22, 17), type = "EXPENSE", amount = 4500.0, fromAccountId = personalId, categoryId = transportCatId, description = "Monthly Car Fuel & Toll Pass", customValues = "{\"Payment Mode\": \"Paid via: Card\"}"))

        // Current Month (Active cycle)
        // Day 2: Freelance consulting payout
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 2, 11),
                type = "INCOME",
                amount = 15000.0,
                toAccountId = personalId,
                categoryId = freelanceIncomeCatId,
                description = "Client UX Audit Consulting Fee",
                notes = "Freelance project milestone payout",
                customValues = "{\"Payment Mode\": \"Paid via: Net Banking\", \"Vendor GSTIN\": \"27AABCU9603R1ZM\"}"
            )
        )

        // Day 5: Monthly Salary Credit
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 5, 9),
                type = "INCOME",
                amount = 65000.0,
                toAccountId = salaryAcId,
                categoryId = salaryCatId,
                description = "Monthly Base Salary & Bonus",
                notes = "Direct corporate payroll deposit",
                customValues = "{\"Payment Mode\": \"Paid via: Net Banking\"}"
            )
        )

        // Day 6: Monthly Inter-Account Allocations
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 6, 10),
                type = "TRANSFER",
                amount = 20000.0,
                fromAccountId = salaryAcId,
                toAccountId = personalId,
                description = "Monthly Living Expenses Transfer",
                notes = "Personal expense allocation"
            )
        )
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 6, 11),
                type = "TRANSFER",
                amount = 12000.0,
                fromAccountId = salaryAcId,
                toAccountId = momsAcId,
                description = "Monthly House Budget Allowance",
                notes = "Mom's account household fund"
            )
        )
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 6, 12),
                type = "TRANSFER",
                amount = 12000.0,
                fromAccountId = salaryAcId,
                toAccountId = dadsAcId,
                description = "Dad's Monthly Expense Allocation",
                notes = "Dad's account allocation"
            )
        )

        // Day 7: Dad's Vehicle EMI
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 7, 10),
                type = "EXPENSE",
                amount = 8500.0,
                fromAccountId = dadsAcId,
                categoryId = loanCatId,
                description = "Car Loan EMI Repayment",
                notes = "Monthly auto-debit vehicle loan installment",
                customValues = "{\"Payment Mode\": \"Paid via: Net Banking\"}"
            )
        )

        // Day 8: Travel cab
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 8, 14),
                type = "EXPENSE",
                amount = 520.0,
                fromAccountId = personalId,
                categoryId = travelCatId,
                description = "Airport Cab Booking",
                notes = "Fast-track airport terminal ride",
                customValues = "{\"Payment Mode\": \"Paid via: UPI\"}"
            )
        )

        // Day 10: Groceries & Provisions
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 10, 11),
                type = "EXPENSE",
                amount = 1500.0,
                fromAccountId = momsAcId,
                categoryId = groceriesCatId,
                description = "Weekly Supermarket Groceries",
                notes = "Fresh dairy, pantry staples and fruit",
                customValues = "{\"Payment Mode\": \"Paid via: Card\"}"
            )
        )

        // Day 10: Commute
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 10, 16),
                type = "EXPENSE",
                amount = 560.0,
                fromAccountId = personalId,
                categoryId = transportCatId,
                description = "Metro Smartcard Auto-Recharge & Uber",
                notes = "Work commute transit",
                customValues = "{\"Payment Mode\": \"Paid via: UPI\"}"
            )
        )

        // Day 10: Swiggy Dinner Celebration
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 10, 20),
                type = "EXPENSE",
                amount = 1240.0,
                fromAccountId = personalId,
                categoryId = foodCatId,
                description = "Swiggy Gourmet Dinner",
                notes = "Celebration dinner for project milestone",
                customValues = "{\"Payment Mode\": \"Paid via: UPI\", \"Receipt Attachment\": \"Swiggy_Bill_1012.pdf\"}"
            )
        )

        // Credit Card spend sample
        transactions.add(
            TransactionEntity(
                date = dateFor(0, 11, 15),
                type = "EXPENSE",
                amount = 3499.0,
                fromAccountId = creditCardId,
                categoryId = shoppingCatId,
                description = "Noise Cancelling Earbuds (Amazon)",
                notes = "HDFC Credit Card EMI cashback purchase",
                customValues = "{\"Payment Mode\": \"Paid via: Card\", \"Vendor GSTIN\": \"29AAACH7409R1ZZ\", \"Receipt Attachment\": \"Amazon_Invoice_3499.pdf\"}"
            )
        )

        // Seed exactly one 3-line split in demo seeder (S7)
        val demoSplitGroupId = "demo_split_superstore_001"
        val splitDate = dateFor(0, 12, 18)
        transactions.add(
            TransactionEntity(
                date = splitDate,
                type = "EXPENSE",
                amount = 1850.0,
                fromAccountId = personalId,
                categoryId = groceriesCatId,
                description = "Hypermarket Superstore Run",
                notes = "Split Line 1: Fresh groceries and weekly household staples",
                customValues = "{\"Payment Mode\": \"Paid via: UPI\"}",
                groupId = demoSplitGroupId
            )
        )
        transactions.add(
            TransactionEntity(
                date = splitDate,
                type = "EXPENSE",
                amount = 650.0,
                fromAccountId = personalId,
                categoryId = foodCatId,
                description = "Hypermarket Superstore Run",
                notes = "Split Line 2: Food court bakery snacks & juice",
                customValues = "{\"Payment Mode\": \"Paid via: UPI\"}",
                groupId = demoSplitGroupId
            )
        )
        transactions.add(
            TransactionEntity(
                date = splitDate,
                type = "EXPENSE",
                amount = 500.0,
                fromAccountId = personalId,
                categoryId = healthCatId,
                description = "Hypermarket Superstore Run",
                notes = "Split Line 3: Pharmacy first aid kit & vitamins",
                customValues = "{\"Payment Mode\": \"Paid via: UPI\"}",
                groupId = demoSplitGroupId
            )
        )

        transactionDao.insertAll(transactions)
    }

    suspend fun seedDemoTransactions(database: AppDatabase) {
        val dbName = database.openHelper.databaseName
        if (dbName == com.example.data.StorageConstants.REAL_DATABASE_NAME) {
            throw IllegalStateException("SampleDataSeeder.seedDemoTransactions refused: cannot seed real database '$dbName'")
        }
        // Unified Phase 2.9: exactly one rich dataset for both entry points (replace-mode)
        seedSampleData(database)
    }

    suspend fun clearDemoTransactions(database: AppDatabase) {
        val dbName = database.openHelper.databaseName
        if (dbName == com.example.data.StorageConstants.REAL_DATABASE_NAME) {
            throw IllegalStateException("SampleDataSeeder.clearDemoTransactions refused: cannot run against real database '$dbName'")
        }
        seedCleanDefaults(database)
    }
}
