package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val openingBalance: Double = 0.0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val accountType: String = "BANK" // "BANK", "CASH", "CREDIT_CARD"
)
