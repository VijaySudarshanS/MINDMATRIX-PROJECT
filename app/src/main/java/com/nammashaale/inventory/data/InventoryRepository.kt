package com.nammashaale.inventory.data

class InventoryRepository(private val dao: InventoryDao) {
    val assets = dao.observeAssets()
    val issues = dao.observeIssues()
    val totalAssets = dao.observeTotalAssets()
    val needsRepair = dao.observeNeedsRepairCount()

    suspend fun seedIfEmpty() {
        if (dao.assetCount() > 0) return
        listOf(
            AssetEntity(name = "Microscope", serialNumber = "LAB-MIC-104", tagCode = "NSI-LAB-104", category = "Lab", location = "Science Lab", condition = AssetCondition.Working),
            AssetEntity(name = "Tablet", serialNumber = "TAB-SSA-221", tagCode = "NSI-TAB-221", category = "Digital", location = "Class 8", condition = AssetCondition.NeedsRepair),
            AssetEntity(name = "Football", serialNumber = "SPORT-FB-018", tagCode = "NSI-SPT-018", category = "Sports", location = "Sports Room", condition = AssetCondition.Working),
            AssetEntity(name = "Chemistry Kit", serialNumber = "LAB-CHK-012", tagCode = "NSI-LAB-012", category = "Lab", location = "Science Lab", condition = AssetCondition.Broken),
            AssetEntity(name = "Projector", serialNumber = "DIG-PRO-009", tagCode = "NSI-DIG-009", category = "Digital", location = "Smart Class", condition = AssetCondition.Working)
        ).forEach { dao.insertAsset(it) }
    }

    suspend fun addAsset(name: String, serial: String, tagCode: String, category: String, location: String, photoUri: String?) {
        dao.insertAsset(
            AssetEntity(
                name = name.trim(),
                serialNumber = serial.trim(),
                tagCode = tagCode.trim().ifBlank { generatedTagCode(serial) },
                category = category.trim(),
                location = location.trim(),
                condition = AssetCondition.Working,
                photoUri = photoUri
            )
        )
    }

    suspend fun updateCondition(asset: AssetEntity, condition: AssetCondition, note: String) {
        dao.updateAsset(asset.copy(condition = condition, lastCheckedAt = System.currentTimeMillis()))
        dao.insertHealthCheck(HealthCheckEntity(assetId = asset.id, condition = condition, note = note.trim()))
    }

    suspend fun logIssue(asset: AssetEntity, reason: String) {
        dao.insertIssue(IssueLogEntity(assetId = asset.id, reason = reason.trim()))
        val condition = if (reason.contains("lost", ignoreCase = true) || reason.contains("missing", ignoreCase = true)) {
            AssetCondition.Lost
        } else {
            AssetCondition.NeedsRepair
        }
        dao.updateAsset(asset.copy(condition = condition, lastCheckedAt = System.currentTimeMillis()))
    }
}

fun generatedTagCode(seed: String = ""): String {
    val suffix = seed.filter { it.isLetterOrDigit() }.takeLast(4).uppercase().ifBlank {
        System.currentTimeMillis().toString().takeLast(4)
    }
    return "NSI-$suffix"
}