package com.example.importbackup

import androidx.room.withTransaction
import android.content.Context
import com.example.data.AppDatabase
import com.example.data.datastore.AppSettings
import com.example.data.datastore.AppSettingsRepository
import com.example.data.model.Account
import com.example.data.model.Category
import com.example.data.model.CustomField
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

object BackupManager {

    suspend fun createBackupJson(
        database: AppDatabase,
        settingsRepository: AppSettingsRepository
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        // Accounts
        val accounts = database.accountDao().getAllAccounts().first()
        val accountsArray = JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject().apply {
                put("id", acc.id)
                put("name", acc.name)
                put("openingBalance", acc.openingBalance)
                put("archived", acc.archived)
                put("sortOrder", acc.sortOrder)
                put("accountType", acc.accountType)
            }
            accountsArray.put(obj)
        }
        root.put("accounts", accountsArray)

        // Categories
        val categories = database.categoryDao().getAllCategories().first()
        val categoriesArray = JSONArray()
        categories.forEach { cat ->
            val obj = JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("color", cat.color)
                put("archived", cat.archived)
                put("sortOrder", cat.sortOrder)
            }
            categoriesArray.put(obj)
        }
        root.put("categories", categoriesArray)

        // Custom Fields
        val fields = database.customFieldDao().getAllCustomFields().first()
        val fieldsArray = JSONArray()
        fields.forEach { cf ->
            val obj = JSONObject().apply {
                put("id", cf.id)
                put("name", cf.name)
                put("type", cf.type)
                put("options", cf.options)
                put("appliesToType", cf.appliesToType ?: JSONObject.NULL)
                put("sortOrder", cf.sortOrder)
            }
            fieldsArray.put(obj)
        }
        root.put("customFields", fieldsArray)

        // Transactions
        val transactions = database.transactionDao().getAllTransactions().first()
        val txArray = JSONArray()
        transactions.forEach { tx ->
            val obj = JSONObject().apply {
                put("id", tx.id)
                put("date", tx.date)
                put("type", tx.type)
                put("amount", tx.amount)
                put("fromAccountId", tx.fromAccountId ?: JSONObject.NULL)
                put("toAccountId", tx.toAccountId ?: JSONObject.NULL)
                put("categoryId", tx.categoryId ?: JSONObject.NULL)
                put("description", tx.description)
                put("notes", tx.notes)
                put("customValues", tx.customValues)
                put("createdAt", tx.createdAt)
                put("updatedAt", tx.updatedAt)
            }
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        // Settings (1.2: never write pinHash or any PIN material to backups)
        val settings = settingsRepository.settingsFlow.first()
        val settingsObj = JSONObject().apply {
            put("currencySymbol", settings.currencySymbol)
            put("monthStartDay", settings.monthStartDay)
            put("theme", settings.theme)
            put("hideBalances", settings.hideBalances)
            put("soundHapticsEnabled", settings.soundHapticsEnabled)
        }
        root.put("settings", settingsObj)

        return root.toString(2)
    }

    suspend fun exportBackup(
        database: AppDatabase,
        settingsRepository: AppSettingsRepository,
        outputStream: OutputStream
    ) {
        val json = createBackupJson(database, settingsRepository)
        outputStream.bufferedWriter().use { it.write(json) }
    }

    suspend fun restoreBackup(
        database: AppDatabase,
        settingsRepository: AppSettingsRepository,
        inputStream: InputStream
    ): Boolean {
        val jsonStr = inputStream.bufferedReader().use { it.readText() }
        val root = JSONObject(jsonStr)

        val accountDao = database.accountDao()
        val categoryDao = database.categoryDao()
        val customFieldDao = database.customFieldDao()
        val transactionDao = database.transactionDao()

        // Clear existing
        database.withTransaction {
        transactionDao.clearAll()
        accountDao.clearAll()
        categoryDao.clearAll()
        customFieldDao.clearAll()

        // Restore Accounts
        if (root.has("accounts")) {
            val arr = root.getJSONArray("accounts")
            val list = mutableListOf<Account>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val rawType = obj.optString("accountType", "")
                val name = obj.getString("name")
                val resolvedType = if (rawType.isNotBlank() && (rawType == "BANK" || rawType == "CASH" || rawType == "CREDIT_CARD")) {
                    rawType
                } else {
                    val lower = name.lowercase()
                    if (lower.contains("credit") || lower.contains("card")) "CREDIT_CARD" else "BANK"
                }
                list.add(
                    Account(
                        id = obj.optLong("id", 0),
                        name = name,
                        openingBalance = obj.optDouble("openingBalance", 0.0),
                        archived = obj.optBoolean("archived", false),
                        sortOrder = obj.optInt("sortOrder", 0),
                        accountType = resolvedType
                    )
                )
            }
            accountDao.insertAll(list)
        }

        // Restore Categories
        if (root.has("categories")) {
            val arr = root.getJSONArray("categories")
            val list = mutableListOf<Category>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Category(
                        id = obj.optLong("id", 0),
                        name = obj.getString("name"),
                        color = obj.optString("color", "#F59E0B"),
                        archived = obj.optBoolean("archived", false),
                        sortOrder = obj.optInt("sortOrder", 0)
                    )
                )
            }
            categoryDao.insertAll(list)
        }

        // Restore Custom Fields
        if (root.has("customFields")) {
            val arr = root.getJSONArray("customFields")
            val list = mutableListOf<CustomField>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CustomField(
                        id = obj.optLong("id", 0),
                        name = obj.getString("name"),
                        type = obj.getString("type"),
                        options = obj.optString("options", ""),
                        appliesToType = if (obj.isNull("appliesToType")) null else obj.getString("appliesToType"),
                        sortOrder = obj.optInt("sortOrder", 0)
                    )
                )
            }
            customFieldDao.insertAll(list)
        }

        // Restore Transactions
        if (root.has("transactions")) {
            val arr = root.getJSONArray("transactions")
            val list = mutableListOf<TransactionEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TransactionEntity(
                        id = obj.optLong("id", 0),
                        date = obj.getLong("date"),
                        type = obj.getString("type"),
                        amount = obj.getDouble("amount"),
                        fromAccountId = if (obj.isNull("fromAccountId")) null else obj.getLong("fromAccountId"),
                        toAccountId = if (obj.isNull("toAccountId")) null else obj.getLong("toAccountId"),
                        categoryId = if (obj.isNull("categoryId")) null else obj.getLong("categoryId"),
                        description = obj.optString("description", ""),
                        notes = obj.optString("notes", ""),
                        customValues = obj.optString("customValues", "{}"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            transactionDao.insertAll(list)
        }

        // Restore Settings
        }
        if (root.has("settings")) {
            val sObj = root.getJSONObject("settings")
            if (sObj.has("currencySymbol")) settingsRepository.setCurrency(sObj.getString("currencySymbol"))
            if (sObj.has("monthStartDay")) settingsRepository.setMonthStartDay(sObj.getInt("monthStartDay"))
            if (sObj.has("theme")) settingsRepository.setTheme(sObj.getString("theme"))
            // 1.2: Ignore pinHash (and do not overwrite current PIN or lock state from backup)
            if (sObj.has("hideBalances")) settingsRepository.setHideBalances(sObj.getBoolean("hideBalances"))
            if (sObj.has("soundHapticsEnabled")) settingsRepository.setSoundHapticsEnabled(sObj.getBoolean("soundHapticsEnabled"))
        }

        return true
    }
}
