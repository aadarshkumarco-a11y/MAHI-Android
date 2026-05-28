package com.mahi.assistant.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RoutineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: RoutineEntity): Long

    @Update
    suspend fun update(routine: RoutineEntity)

    @Delete
    suspend fun delete(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    suspend fun getAll(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE isActive = 1 ORDER BY createdAt DESC")
    suspend fun getActiveRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: Long): RoutineEntity?

    @Query("SELECT * FROM routines WHERE triggerType = :triggerType AND triggerValue = :triggerValue AND isActive = 1")
    suspend fun getByTrigger(triggerType: String, triggerValue: String): List<RoutineEntity>

    @Query("UPDATE routines SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun count(): Int
}
