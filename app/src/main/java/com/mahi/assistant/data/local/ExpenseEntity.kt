package com.mahi.assistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String,  // "food", "transport", "shopping", "bills", "entertainment", "other"
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE timestamp >= :fromTimestamp ORDER BY timestamp DESC")
    suspend fun getSince(fromTimestamp: Long): List<ExpenseEntity>

    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE timestamp >= :fromTimestamp GROUP BY category ORDER BY total DESC")
    suspend fun getTotalByCategorySince(fromTimestamp: Long): List<CategoryTotal>

    @Query("SELECT SUM(amount) FROM expenses WHERE timestamp >= :fromTimestamp")
    suspend fun getTotalSince(fromTimestamp: Long): Double?

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    data class CategoryTotal(val category: String, val total: Double)
}
