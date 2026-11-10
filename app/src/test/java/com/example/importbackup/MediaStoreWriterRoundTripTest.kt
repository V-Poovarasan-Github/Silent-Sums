package com.example.importbackup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.datastore.AppSettings
import com.example.data.model.Account
import com.example.data.model.BudgetEntity
import com.example.data.model.Category
import com.example.data.model.Commitment
import com.example.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStoreWriterRoundTripTest {

    @Test
    fun testMediaStoreWriterRoundTrip_assertsParadiseRowAndFormat2() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val acc = Account(id = 1L, name = "ICICI Credit", accountType = "CREDIT_CARD", openingBalance = 0.0)
        val cat = Category(id = 1L, name = "Entertainment", color = "#EC4899")
        val tx = TransactionEntity(
            id = 1L,
            date = 1727444640000L,
            type = "EXPENSE",
            amount = 1092.34,
            fromAccountId = 1L,
            categoryId = 1L,
            description = "Paradise * 4",
            notes = "Dinner"
        )
        val com = Commitment(
            id = 1L,
            type = "RECURRING",
            name = "House Loan",
            amount = 25000.0,
            direction = "OUTGOING",
            dayOfMonth = 4,
            accountId = 1L,
            categoryId = 1L
        )
        val budget = BudgetEntity(
            id = 1L,
            name = "Entertainment Cap",
            scopeType = "CATEGORY",
            targetName = "Entertainment",
            limitAmount = 5000.0,
            active = true
        )

        // 1. Build CSV content
        val csvContent = CsvBackupManager.buildCsv(
            transactions = listOf(tx),
            accounts = listOf(acc),
            categories = listOf(cat),
            settings = AppSettings(),
            commitments = listOf(com),
            budgets = listOf(budget)
        )

        // Verify #FORMAT=2 is at line 1
        assertTrue("CSV must start with #FORMAT=2", csvContent.startsWith("#FORMAT=2"))

        // 2. Write via MediaStoreWriter to shared storage
        val targetUri = MediaStoreWriter.writeToSharedDownloads(context, "", "test_backup.csv", csvContent)
        assertNotNull("Target URI must not be null", targetUri)

        // Assert target is a content URI, never a raw File in filesDir or getExternalFilesDir
        val uriString = targetUri.toString()
        assertTrue("Target must be a content URI on API 30+", uriString.startsWith("content://"))
        assertFalse("Target must not resolve to filesDir", uriString.contains(context.filesDir.path))
        val extFilesDir = context.getExternalFilesDir(null)
        if (extFilesDir != null) {
            assertFalse("Target must not resolve to getExternalFilesDir", uriString.contains(extFilesDir.path))
        }

        // 3. Read back from stream and parse
        val inputStream = context.contentResolver.openInputStream(targetUri!!)
        assertNotNull("InputStream from content URI must not be null", inputStream)

        val parsed = CsvBackupManager.parseCsvFull(inputStream!!)
        assertEquals("Format version must be 2", 2, parsed.formatVersion)
        assertEquals("Transaction count must be 1", 1, parsed.transactions.size)

        val readTx = parsed.transactions.first()
        assertEquals("Paradise * 4", readTx.description)
        assertEquals(1092.34, readTx.amount, 0.001)
        assertEquals("ICICI Credit", readTx.fromAccount)
        assertEquals("Entertainment", readTx.category)

        assertEquals(1, parsed.accounts.size)
        assertEquals("ICICI Credit", parsed.accounts.first().name)

        assertEquals(1, parsed.categories.size)
        assertEquals("Entertainment", parsed.categories.first().name)

        assertEquals(1, parsed.dues.size)
        assertEquals("House Loan", parsed.dues.first().name)

        assertEquals(1, parsed.budgets.size)
        assertEquals("Entertainment Cap", parsed.budgets.first().name)
        assertEquals("CATEGORY", parsed.budgets.first().scopeType)
        assertEquals("Entertainment", parsed.budgets.first().targetName)
        assertEquals(5000.0, parsed.budgets.first().limitAmount, 0.001)
        assertTrue(parsed.budgets.first().active)
    }

    @Test
    fun testRollingAutoSaveWriter_resolvesToContentUriInAutoSubfolder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val content = "#FORMAT=2\nDate,Type,Amount,FromAccount,ToAccount,Category,Description,Notes\n"
        val (uri, usedSaf) = MediaStoreWriter.writeRollingAutoSave(context, content, null)
        assertNotNull(uri)
        assertFalse(usedSaf)
        val internalFile = java.io.File(context.filesDir, "backups/auto_backup.csv")
        assertTrue("Internal safety-net backup must exist", internalFile.exists() && internalFile.length() > 0)
    }
}
