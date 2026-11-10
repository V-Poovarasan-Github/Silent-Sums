package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AccountDao
import com.example.data.dao.BudgetDao
import com.example.data.dao.CategoryDao
import com.example.data.dao.CommitmentDao
import com.example.data.dao.CustomFieldDao
import com.example.data.dao.MediaAttachmentDao
import com.example.data.dao.TransactionDao
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.model.CustomField
import com.example.data.model.MediaAttachment
import com.example.data.model.TransactionEntity

@Database(
    entities = [
        Account::class,
        Category::class,
        CustomField::class,
        TransactionEntity::class,
        Commitment::class,
        BudgetEntity::class,
        MediaAttachment::class
    ],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun transactionDao(): TransactionDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun budgetDao(): BudgetDao
    abstract fun mediaAttachmentDao(): MediaAttachmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `accountType` TEXT NOT NULL DEFAULT 'BANK'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN icon_text TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `commitments` ADD COLUMN `description` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `commitments` ADD COLUMN `paid_so_far` REAL NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `media_attachments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `txnId` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_attachments_txnId` ON `media_attachments` (`txnId`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursorCol = db.query("PRAGMA table_info(`media_attachments`)")
                var hasMediaRef = false
                while (cursorCol.moveToNext()) {
                    val name = cursorCol.getString(cursorCol.getColumnIndexOrThrow("name"))
                    if (name.equals("mediaRef", ignoreCase = true)) {
                        hasMediaRef = true
                        break
                    }
                }
                cursorCol.close()
                if (!hasMediaRef) {
                    db.execSQL("ALTER TABLE `media_attachments` ADD COLUMN `mediaRef` TEXT DEFAULT NULL")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_attachments_mediaRef` ON `media_attachments` (`mediaRef`)")

                val cursor = db.query("SELECT DISTINCT txnId FROM media_attachments WHERE mediaRef IS NULL")
                val txnIds = mutableListOf<Long>()
                while (cursor.moveToNext()) {
                    txnIds.add(cursor.getLong(0))
                }
                cursor.close()

                for (txnId in txnIds) {
                    val ref = "m_" + java.util.UUID.randomUUID().toString().replace("-", "").take(10)
                    db.execSQL("UPDATE media_attachments SET mediaRef = ? WHERE txnId = ?", arrayOf<Any?>(ref, txnId))
                }
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursorCol = db.query("PRAGMA table_info(`transactions`)")
                var hasGroupId = false
                while (cursorCol.moveToNext()) {
                    val name = cursorCol.getString(cursorCol.getColumnIndexOrThrow("name"))
                    if (name.equals("groupId", ignoreCase = true)) {
                        hasGroupId = true
                        break
                    }
                }
                cursorCol.close()
                if (!hasGroupId) {
                    db.execSQL("ALTER TABLE `transactions` ADD COLUMN `groupId` TEXT DEFAULT NULL")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_groupId` ON `transactions` (`groupId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return DatabaseProvider.getDatabase(context)
        }

        fun resetInstance() {
            DatabaseProvider.resetInstance()
        }
    }
}
