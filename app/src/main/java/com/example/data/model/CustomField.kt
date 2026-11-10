package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_fields")
data class CustomField(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // TEXT, NUMBER, DROPDOWN, DATE
    val options: String = "", // Comma-separated or JSON list for DROPDOWN
    val appliesToType: String? = null, // INCOME, EXPENSE, TRANSFER, or null for all
    val sortOrder: Int = 0
)
