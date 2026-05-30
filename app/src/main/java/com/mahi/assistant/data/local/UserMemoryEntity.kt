package com.mahi.assistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,  // "name", "preference", "fact", "location", "contact", "custom"
    val key: String,       // e.g., "user_name", "home_city"
    val value: String,     // e.g., "Aadarsh", "Patna"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface UserMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: UserMemoryEntity): Long

    @Query("SELECT * FROM user_memories ORDER BY timestamp DESC")
    suspend fun getAll(): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getByCategory(category: String): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memories WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): UserMemoryEntity?

    @Query("SELECT * FROM user_memories WHERE `value` LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun search(query: String): List<UserMemoryEntity>

    @Query("DELETE FROM user_memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM user_memories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM user_memories")
    suspend fun count(): Int
}
