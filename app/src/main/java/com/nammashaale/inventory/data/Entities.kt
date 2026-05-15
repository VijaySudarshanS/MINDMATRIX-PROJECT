package com.nammashaale.inventory.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AssetCondition {
    Working,
    NeedsRepair,
    Broken,
    Lost
}

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val serialNumber: String,
    val tagCode: String = "",
    val category: String,
    val location: String,
    val condition: AssetCondition,
    val photoUri: String? = null,
    val lastCheckedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "health_checks")
data class HealthCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val condition: AssetCondition,
    val note: String,
    val checkedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "issue_logs")
data class IssueLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val reason: String,
    val loggedAt: Long = System.currentTimeMillis()
)