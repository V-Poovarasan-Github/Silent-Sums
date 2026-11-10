package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["groupId"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long, // Epoch timestamp in milliseconds
    val type: String, // INCOME, EXPENSE, TRANSFER
    val amount: Double,
    val fromAccountId: Long? = null,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val description: String = "",
    val notes: String = "",
    val customValues: String = "{}", // JSON map e.g. {"Payment Mode": "UPI"}
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val groupId: String? = null
)
