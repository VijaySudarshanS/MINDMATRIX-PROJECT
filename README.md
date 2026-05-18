# Namma-Shaale Inventory

Namma-Shaale Inventory is a digital asset auditor for primary and secondary schools. It helps teachers register, tag, check, and report the condition of school resources such as sports kits, lab equipment, tablets, projectors, and other government-funded assets.

## Problem Statement

Schools receive useful resources, but there is often no simple way to track whether each item is working, broken, lost, or needs repair. A damaged tablet or missing football may be discovered months later. This app gives teachers a quick mobile workflow to audit assets regularly and maintain accountability.

## Project Vision

The app works as a simplified school inventory and health-check system. Teachers can add assets, attach photos, assign tag codes, scan QR/barcodes, update monthly condition status, log issues, and share a summary report with school authorities or the SDMC.

## Key Features

- Asset register with item name, serial number, tag code, category, location, and photo
- Monthly health check using Green, Yellow, and Red condition updates
- Fast audit action to update the next 10 items quickly
- Issue log with date and reason, such as lost or damaged items
- Repair/SDMC attention list for broken, lost, or repair-needed assets
- Dashboard showing total assets, working assets, needs repair, and broken/lost counts
- CameraX photo capture for documenting high-value items
- QR/barcode scanning using ML Kit for fast tag lookup
- Shareable summary report through Android share sheet
- Local Room database for offline storage
- Professional organized UI with color-coded status cards

## Tech Stack

- Kotlin
- Android Jetpack Compose
- Room Database
- CameraX
- Google ML Kit Barcode Scanning
- Gradle Kotlin DSL
- Android Studio

## Database

The app uses Room DB to store school inventory records locally on the device.

Main database file:

```text
app/src/main/java/com/nammashaale/inventory/data/InventoryDatabase.kt
```

Tables/entities:

- `AssetEntity`: item name, serial number, tag code, category, location, condition, photo URI, last checked time
- `HealthCheckEntity`: asset ID, condition, note, checked date/time
- `IssueLogEntity`: asset ID, issue reason, logged date/time

Data layer files:

```text
app/src/main/java/com/nammashaale/inventory/data/Entities.kt
app/src/main/java/com/nammashaale/inventory/data/InventoryDao.kt
app/src/main/java/com/nammashaale/inventory/data/InventoryDatabase.kt
app/src/main/java/com/nammashaale/inventory/data/InventoryRepository.kt
```

## App Flow

1. Open the app and view the dashboard.
2. Add an asset with name, serial number, tag code, category, location, and photo.
3. Use Monthly Health Check to mark items as:
   - Green: Working
   - Yellow: Needs Repair
   - Red: Broken
4. Use Scan to scan or search by tag/serial/name.
5. Log issues with reason and date.
6. View repair/SDMC attention list.
7. Share the summary report.

## Success Criteria Coverage

| Requirement | Status |
|---|---|
| Asset register with item, serial number, and photo | Done |
| Monthly health check with Green/Yellow/Red status | Done |
| Update 10 items in under 2 minutes | Done |
| Issue log with date and reason | Done |
| Repair request list for SDMC attention | Done |
| Room DB for asset list and health history | Done |
| Dashboard with asset counts | Done |
| CameraX photo documentation | Done |
| Scan or tag assets | Done |
| Shareable summary report | Done |
| Professional organized UI | Done |

## How to Run

1. Open Android Studio.
2. Click `File > Open`.
3. Select the project folder:

```text
05_NammaShaaleInventory
```

4. Wait for Gradle sync to finish.
5. Connect an Android phone or start an emulator.
6. Click Run.

## Build Command

From the project root:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```text
app/
  src/main/
    AndroidManifest.xml
    java/com/nammashaale/inventory/
      MainActivity.kt
      NammaShaaleApp.kt
      data/
        Entities.kt
        InventoryDao.kt
        InventoryDatabase.kt
        InventoryRepository.kt
    res/
      drawable/
      values/
gradle/
build.gradle.kts
settings.gradle.kts
```

## Impact

- Resource optimization: keeps taxpayer-funded school assets tracked and maintained
- Educational quality: helps keep labs, sports rooms, and digital classrooms functional
- Accountability: creates a regular asset-care habit in the school

## Notes

This project is designed as a student MVP/demo. It uses local offline storage with Room DB. Cloud backup, login/authentication, and an admin web dashboard are optional future enhancements, not mandatory for the given problem statement.