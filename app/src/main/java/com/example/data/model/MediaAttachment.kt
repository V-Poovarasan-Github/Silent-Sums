package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_attachments",
    indices = [
        Index(value = ["txnId"]),
        Index(value = ["mediaRef"])
    ]
)
data class MediaAttachment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val txnId: Long,
    val kind: String, // "PHOTO" or "VOICE"
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val mediaRef: String? = null
)
