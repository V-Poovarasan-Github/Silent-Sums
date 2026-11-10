package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Commitment
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentDao {

    @Query("SELECT * FROM commitments WHERE active = 1 AND settled = 0 ORDER BY createdAt DESC")
    fun getActiveCommitments(): Flow<List<Commitment>>

    @Query("SELECT * FROM commitments ORDER BY createdAt DESC")
    fun getAllCommitments(): Flow<List<Commitment>>

    @Query("SELECT * FROM commitments WHERE active = 1 AND settled = 0")
    suspend fun getActiveCommitmentsList(): List<Commitment>

    @Query("SELECT * FROM commitments")
    suspend fun getAllCommitmentsList(): List<Commitment>

    @Query("SELECT * FROM commitments WHERE id = :id")
    suspend fun getCommitmentById(id: Long): Commitment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(commitment: Commitment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(commitments: List<Commitment>)

    @Update
    suspend fun update(commitment: Commitment)

    @Delete
    suspend fun delete(commitment: Commitment)

    @Query("DELETE FROM commitments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM commitments")
    suspend fun clearAll()
}
