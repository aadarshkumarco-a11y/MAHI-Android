package com.mahi.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE timestamp >= :fromTimestamp ORDER BY timestamp ASC")
    suspend fun getSince(fromTimestamp: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlder(beforeTimestamp: Long): Int

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int
}
