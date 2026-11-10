package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val scopeType: String, // "ALL", "CATEGORY", "ACCOUNT"
    val targetName: String, // "" for ALL, Category name for CATEGORY, Account name for ACCOUNT
    val limitAmount: Double,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
