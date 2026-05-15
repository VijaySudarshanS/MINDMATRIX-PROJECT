package com.nammashaale.inventory

import android.app.Application
import com.nammashaale.inventory.data.InventoryDatabase
import com.nammashaale.inventory.data.InventoryRepository

class NammaShaaleApp : Application() {
    val repository by lazy {
        InventoryRepository(InventoryDatabase.getDatabase(this).inventoryDao())
    }
}
