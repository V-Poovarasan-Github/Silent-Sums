package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CustomField
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFieldDao {
    @Query("SELECT * FROM custom_fields ORDER BY sortOrder ASC, name ASC")
    fun getAllCustomFields(): Flow<List<CustomField>>

    @Query("SELECT * FROM custom_fields WHERE appliesToType IS NULL OR appliesToType = :type ORDER BY sortOrder ASC")
    fun getFieldsForType(type: String): Flow<List<CustomField>>

    @Query("SELECT * FROM custom_fields WHERE id = :id")
    suspend fun getCustomFieldById(id: Long): CustomField?

    @Query("SELECT * FROM custom_fields WHERE name = :name LIMIT 1")
    suspend fun getCustomFieldByName(name: String): CustomField?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customField: CustomField): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fields: List<CustomField>)

    @Update
    suspend fun update(customField: CustomField)

    @Delete
    suspend fun delete(customField: CustomField)

    @Query("DELETE FROM custom_fields")
    suspend fun clearAll()
}
