package com.nammashaale.inventory.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM assets ORDER BY lastCheckedAt ASC")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM issue_logs ORDER BY loggedAt DESC")
    fun observeIssues(): Flow<List<IssueLogEntity>>

    @Query("SELECT COUNT(*) FROM assets")
    fun observeTotalAssets(): Flow<Int>

    @Query("SELECT COUNT(*) FROM assets WHERE condition IN ('NeedsRepair', 'Broken')")
    fun observeNeedsRepairCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM assets")
    suspend fun assetCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntity): Long

    @Insert
    suspend fun insertHealthCheck(check: HealthCheckEntity)

    @Insert
    suspend fun insertIssue(issue: IssueLogEntity)

    @Update
    suspend fun updateAsset(asset: AssetEntity)
}
