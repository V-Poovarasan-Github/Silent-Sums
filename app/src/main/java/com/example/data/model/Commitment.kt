package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "commitments")
data class Commitment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "RECURRING" or "IOU"
    val name: String,
    val amount: Double,
    val direction: String, // "OUTGOING" or "INCOMING"
    val dayOfMonth: Int? = null, // 1-31, for RECURRING
    val dueDate: Long? = null, // for IOU, optional
    val accountId: Long? = null, // paying/receiving account
    val categoryId: Long? = null, // optional posting category
    val active: Boolean = true,
    val settled: Boolean = false,
    val settledAt: Long? = null,
    val lastPostedCycle: String? = null, // e.g. "2026-09"
    val description: String? = null,
    @ColumnInfo(name = "paid_so_far")
    val paidSoFar: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val remaining: Double
        get() = maxOf(0.0, amount - paidSoFar)
}
