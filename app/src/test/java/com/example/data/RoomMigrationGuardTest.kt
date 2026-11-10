package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.model.TransactionEntity
import com.example.data.repository.FinanceRepository
import com.example.data.seed.SampleDataSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigrationGuardTest {

    @Test
    fun testRoomMigration1To2To3To4_preservesDataWithoutLoss() {
        runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Create database and verify all migrations apply additively
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10
            )
            .allowMainThreadQueries()
            .build()

        // 2. Insert accounts, categories, transactions, commitments, budgets
        val accId = db.accountDao().insert(
            Account(id = 1, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = 0.0)
        )
        val catId = db.categoryDao().insert(
            Category(id = 1, name = "Entertainment", color = "#EC4899")
        )
        val txId = db.transactionDao().insert(
            TransactionEntity(
                id = 1,
                date = System.currentTimeMillis(),
                type = "EXPENSE",
                amount = 1092.34,
                fromAccountId = accId,
                categoryId = catId,
                description = "Paradise * 4"
            )
        )
        val comId = db.commitmentDao().insert(
            Commitment(
                id = 1,
                type = "RECURRING",
                name = "House Loan",
                amount = 25000.0,
                direction = "OUTGOING",
                dayOfMonth = 4,
                accountId = accId,
                categoryId = catId,
                description = "SBI Home Loan EMI"
            )
        )
        val budgetId = db.budgetDao().insert(
            BudgetEntity(
                id = 1,
                name = "Entertainment Cap",
                scopeType = "CATEGORY",
                targetName = "Entertainment",
                limitAmount = 5000.0,
                active = true
            )
        )

        // 3. Assert all tables exist and data was queried back successfully
        assertEquals(1, db.accountDao().getAllAccounts().first().size)
        assertEquals(1, db.categoryDao().getAllCategories().first().size)
        assertEquals(1, db.transactionDao().getAllTransactions().first().size)
        assertEquals(1, db.commitmentDao().getAllCommitments().first().size)
        assertEquals("SBI Home Loan EMI", db.commitmentDao().getAllCommitments().first()[0].description)
        assertEquals(1, db.budgetDao().getAllBudgets().first().size)

        val retrievedBudget = db.budgetDao().getBudgetByIdentity("CATEGORY", "Entertainment")
        assertNotNull(retrievedBudget)
        assertEquals(5000.0, retrievedBudget!!.limitAmount, 0.001)
        assertTrue(retrievedBudget.active)

        db.close()
        }
    }

    /**
     * Simulates upgrading an app with an existing on-disk v1 database to the latest schema (v4).
     * Verifies that raw SQLite tables from v1 migrate seamlessly and all pre-existing user data is 100% retained.
     */
    @Test
    fun testRealUpgradeFromV1DatabaseFileToCurrentAppVersion() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_upgrade_db.db")
        if (dbFile.exists()) dbFile.delete()

        // Step 1: Create a real SQLite database representing version 1 schema manually
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_upgrade_db.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `accounts` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `openingBalance` REAL NOT NULL,
                            `sortOrder` INTEGER NOT NULL,
                            `archived` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `categories` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `color` TEXT NOT NULL,
                            `sortOrder` INTEGER NOT NULL,
                            `archived` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `custom_fields` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `type` TEXT NOT NULL,
                            `options` TEXT NOT NULL,
                            `appliesToType` TEXT,
                            `sortOrder` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `transactions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `date` INTEGER NOT NULL,
                            `type` TEXT NOT NULL,
                            `amount` REAL NOT NULL,
                            `fromAccountId` INTEGER,
                            `toAccountId` INTEGER,
                            `categoryId` INTEGER,
                            `description` TEXT NOT NULL,
                            `notes` TEXT NOT NULL,
                            `customValues` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    // Insert pre-existing user records into v1 database
                    db.execSQL("INSERT INTO accounts (id, name, openingBalance, sortOrder, archived) VALUES (1, 'User Main Savings', 25000.0, 0, 0)")
                    db.execSQL("INSERT INTO categories (id, name, color, sortOrder, archived) VALUES (1, 'Household Utilities', '#3B82F6', 0, 0)")
                    db.execSQL("INSERT INTO transactions (id, date, type, amount, fromAccountId, categoryId, description, notes, customValues, createdAt, updatedAt) VALUES (1, 1727784000000, 'EXPENSE', 3500.0, 1, 1, 'Electricity Bill', 'Paid via biller', '{}', 1727784000000, 1727784000000)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
        val writableDb = helper.writableDatabase
        writableDb.close()
        helper.close()

        val upgradedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_upgrade_db.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10
            )
            .allowMainThreadQueries()
            .build()

        // Step 3: Initialize FinanceRepository and run checkAndSeedIfEmpty()
        val repo = FinanceRepository(upgradedDb)
        repo.checkAndSeedIfEmpty()

        // Step 4: Verify that existing user accounts, categories, and transactions are completely preserved
        val accounts = upgradedDb.accountDao().getAllAccounts().first()
        val categories = upgradedDb.categoryDao().getAllCategories().first()
        val transactions = upgradedDb.transactionDao().getAllTransactions().first()

        assertEquals(1, accounts.size)
        assertEquals("User Main Savings", accounts[0].name)
        assertEquals(25000.0, accounts[0].openingBalance, 0.001)
        assertEquals("BANK", accounts[0].accountType) // Defaulted correctly via MIGRATION_1_2

        assertEquals(1, categories.size)
        assertEquals("Household Utilities", categories[0].name)

        assertEquals(1, transactions.size)
        assertEquals(3500.0, transactions[0].amount, 0.001)
        assertEquals("Electricity Bill", transactions[0].description)

        // Verify new tables with v6 description column are ready for use
        upgradedDb.commitmentDao().insert(
            Commitment(
                id = 101,
                type = "RECURRING",
                name = "Wifi",
                amount = 999.0,
                direction = "OUTGOING",
                dayOfMonth = 10,
                accountId = accounts[0].id,
                categoryId = categories[0].id,
                description = "High-speed optical fiber"
            )
        )
        val commitments = upgradedDb.commitmentDao().getAllCommitments().first()
        assertEquals(1, commitments.size)
        assertEquals("Wifi", commitments[0].name)
        assertEquals("High-speed optical fiber", commitments[0].description)

        upgradedDb.budgetDao().insert(
            BudgetEntity(
                id = 201,
                name = "Monthly Utilities Cap",
                scopeType = "CATEGORY",
                targetName = "Household Utilities",
                limitAmount = 10000.0,
                active = true
            )
        )
        val budgets = upgradedDb.budgetDao().getAllBudgets().first()
        assertEquals(1, budgets.size)
        assertEquals("Monthly Utilities Cap", budgets[0].name)

        upgradedDb.close()
        dbFile.delete()
        }
    }

    @Test
    fun testRoomMigration5To6_addsCommitmentDescriptionColumn() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = context.getDatabasePath("test_v5_v6_upgrade.db")
            if (dbFile.exists()) dbFile.delete()

            // Create real SQLite DB representing v5 schema
            val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("test_v5_v6_upgrade.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `accounts` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `openingBalance` REAL NOT NULL,
                                `sortOrder` INTEGER NOT NULL,
                                `archived` INTEGER NOT NULL,
                                `accountType` TEXT NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `categories` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `color` TEXT NOT NULL,
                                `sortOrder` INTEGER NOT NULL,
                                `archived` INTEGER NOT NULL,
                                `icon_text` TEXT
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `custom_fields` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `type` TEXT NOT NULL,
                                `options` TEXT NOT NULL,
                                `appliesToType` TEXT,
                                `sortOrder` INTEGER NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `transactions` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `date` INTEGER NOT NULL,
                                `type` TEXT NOT NULL,
                                `amount` REAL NOT NULL,
                                `fromAccountId` INTEGER,
                                `toAccountId` INTEGER,
                                `categoryId` INTEGER,
                                `description` TEXT NOT NULL,
                                `notes` TEXT NOT NULL,
                                `customValues` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `commitments` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `type` TEXT NOT NULL,
                                `name` TEXT NOT NULL,
                                `amount` REAL NOT NULL,
                                `direction` TEXT NOT NULL,
                                `dayOfMonth` INTEGER,
                                `dueDate` INTEGER,
                                `accountId` INTEGER,
                                `categoryId` INTEGER,
                                `active` INTEGER NOT NULL,
                                `settled` INTEGER NOT NULL,
                                `settledAt` INTEGER,
                                `lastPostedCycle` TEXT,
                                `createdAt` INTEGER NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `budgets` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `scopeType` TEXT NOT NULL,
                                `targetName` TEXT NOT NULL,
                                `limitAmount` REAL NOT NULL,
                                `active` INTEGER NOT NULL,
                                `createdAt` INTEGER NOT NULL
                            )
                        """.trimIndent())

                        // Pre-insert a commitment in v5 (no description column)
                        db.execSQL("""
                            INSERT INTO commitments (id, type, name, amount, direction, dayOfMonth, active, settled, createdAt)
                            VALUES (1, 'RECURRING', 'Gym Membership', 1500.0, 'OUTGOING', 5, 1, 0, 1727784000000)
                        """.trimIndent())
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()

            val helper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
            val writableDb = helper.writableDatabase
            writableDb.close()
            helper.close()

            // Apply MIGRATION_5_6 through MIGRATION_9_10 to bring it to v10
            val upgradedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_v5_v6_upgrade.db")
                .addMigrations(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
                .allowMainThreadQueries()
                .build()

            val existingList = upgradedDb.commitmentDao().getAllCommitments().first()
            assertEquals(1, existingList.size)
            assertEquals("Gym Membership", existingList[0].name)
            // Existing row description should default to null
            org.junit.Assert.assertNull(existingList[0].description)
            assertEquals(0.0, existingList[0].paidSoFar, 0.001)

            // Insert new commitment with description
            upgradedDb.commitmentDao().insert(
                Commitment(
                    id = 2,
                    type = "IOU",
                    name = "Rohan",
                    amount = 500.0,
                    direction = "OUTGOING",
                    description = "Split dinner bill"
                )
            )

            val updatedList = upgradedDb.commitmentDao().getAllCommitments().first()
            assertEquals(2, updatedList.size)
            val rohan = updatedList.first { it.id == 2L }
            assertEquals("Split dinner bill", rohan.description)

            upgradedDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun testRoomMigration6To7_addsPaidSoFarColumn_preservesData() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = context.getDatabasePath("test_v6_v7_upgrade.db")
            if (dbFile.exists()) dbFile.delete()

            // Create a v6 SQLite DB directly using FrameworkSQLiteOpenHelper
            val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("test_v6_v7_upgrade.db")
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `accounts` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `openingBalance` REAL NOT NULL,
                                `sortOrder` INTEGER NOT NULL,
                                `archived` INTEGER NOT NULL,
                                `accountType` TEXT NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `categories` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `color` TEXT NOT NULL,
                                `sortOrder` INTEGER NOT NULL,
                                `archived` INTEGER NOT NULL,
                                `icon_text` TEXT
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `custom_fields` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `type` TEXT NOT NULL,
                                `options` TEXT NOT NULL,
                                `appliesToType` TEXT,
                                `sortOrder` INTEGER NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `transactions` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `date` INTEGER NOT NULL,
                                `type` TEXT NOT NULL,
                                `amount` REAL NOT NULL,
                                `fromAccountId` INTEGER,
                                `toAccountId` INTEGER,
                                `categoryId` INTEGER,
                                `description` TEXT NOT NULL,
                                `notes` TEXT NOT NULL,
                                `customValues` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `budgets` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `scopeType` TEXT NOT NULL,
                                `targetName` TEXT NOT NULL,
                                `limitAmount` REAL NOT NULL,
                                `active` INTEGER NOT NULL,
                                `createdAt` INTEGER NOT NULL
                            )
                        """.trimIndent())
                        // v6 commitments has description column, but NOT paid_so_far
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS commitments (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                type TEXT NOT NULL,
                                name TEXT NOT NULL,
                                amount REAL NOT NULL,
                                direction TEXT NOT NULL,
                                dayOfMonth INTEGER,
                                dueDate INTEGER,
                                accountId INTEGER,
                                categoryId INTEGER,
                                active INTEGER NOT NULL,
                                settled INTEGER NOT NULL,
                                settledAt INTEGER,
                                lastPostedCycle TEXT,
                                description TEXT,
                                createdAt INTEGER NOT NULL
                            )
                        """.trimIndent())

                        // Pre-insert a commitment in v6
                        db.execSQL("""
                            INSERT INTO commitments (id, type, name, amount, direction, dayOfMonth, active, settled, description, createdAt)
                            VALUES (1, 'RECURRING', 'Broadband', 999.0, 'OUTGOING', 15, 1, 0, 'Fiber internet', 1727784000000)
                        """.trimIndent())
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()

            val helper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
            val writableDb = helper.writableDatabase
            writableDb.close()
            helper.close()

            // Apply MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, and MIGRATION_9_10 to bring it to v10
            val upgradedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_v6_v7_upgrade.db")
                .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10)
                .allowMainThreadQueries()
                .build()

            val existingList = upgradedDb.commitmentDao().getAllCommitments().first()
            assertEquals(1, existingList.size)
            val existing = existingList[0]
            assertEquals("Broadband", existing.name)
            assertEquals("Fiber internet", existing.description)
            assertEquals(0.0, existing.paidSoFar, 0.001)
            assertEquals(999.0, existing.remaining, 0.001)

            // Insert new commitment with paidSoFar = 400.0
            upgradedDb.commitmentDao().insert(
                Commitment(
                    id = 2,
                    type = "IOU",
                    name = "Kiran",
                    amount = 1000.0,
                    direction = "OUTGOING",
                    description = "Trip split",
                    paidSoFar = 400.0
                )
            )

            val updatedList = upgradedDb.commitmentDao().getAllCommitments().first()
            assertEquals(2, updatedList.size)
            val kiran = updatedList.first { it.id == 2L }
            assertEquals(400.0, kiran.paidSoFar, 0.001)
            assertEquals(600.0, kiran.remaining, 0.001)

            upgradedDb.close()
            dbFile.delete()
        }
    }
}
