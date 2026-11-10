package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.MediaAttachment
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaAttachmentDao {
    @Query("SELECT * FROM media_attachments WHERE txnId = :txnId ORDER BY id ASC")
    fun getAttachmentsForTxn(txnId: Long): Flow<List<MediaAttachment>>

    @Query("SELECT * FROM media_attachments WHERE txnId = :txnId ORDER BY id ASC")
    suspend fun getAttachmentsForTxnSync(txnId: Long): List<MediaAttachment>

    @Query("SELECT * FROM media_attachments ORDER BY id ASC")
    fun getAllAttachments(): Flow<List<MediaAttachment>>

    @Query("SELECT * FROM media_attachments ORDER BY id ASC")
    suspend fun getAllAttachmentsSync(): List<MediaAttachment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: MediaAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<MediaAttachment>): List<Long>

    @Query("DELETE FROM media_attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM media_attachments WHERE txnId = :txnId")
    suspend fun deleteByTxnId(txnId: Long)

    @Query("DELETE FROM media_attachments")
    suspend fun clearAll()

    @Query("SELECT * FROM media_attachments WHERE mediaRef = :mediaRef ORDER BY id ASC")
    suspend fun getAttachmentsByMediaRef(mediaRef: String): List<MediaAttachment>

    @Query("UPDATE media_attachments SET txnId = :newTxnId WHERE mediaRef = :mediaRef")
    suspend fun updateTxnIdForMediaRef(mediaRef: String, newTxnId: Long)

    @Query("SELECT mediaRef FROM media_attachments WHERE txnId = :txnId AND mediaRef IS NOT NULL LIMIT 1")
    suspend fun getMediaRefForTxn(txnId: Long): String?

    @Query("SELECT * FROM media_attachments WHERE mediaRef IS NOT NULL")
    suspend fun getAllWithMediaRef(): List<MediaAttachment>
}
