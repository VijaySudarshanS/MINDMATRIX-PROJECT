package com.nammashaale.inventory

import android.Manifest
import android.annotation.SuppressLint
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import com.nammashaale.inventory.data.AssetCondition
import com.nammashaale.inventory.data.AssetEntity
import com.nammashaale.inventory.data.IssueLogEntity
import com.nammashaale.inventory.data.InventoryRepository
import com.nammashaale.inventory.data.generatedTagCode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as NammaShaaleApp).repository
        val factory = InventoryViewModel.factory(application as NammaShaaleApp, repository)
        setContent {
            val viewModel: InventoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
            InventoryTheme {
                InventoryApp(viewModel)
            }
        }
    }
}

data class InventoryUiState(
    val assets: List<AssetEntity> = emptyList(),
    val issues: List<IssueLogEntity> = emptyList(),
    val totalAssets: Int = 0,
    val needsRepair: Int = 0
)

class InventoryViewModel(
    app: NammaShaaleApp,
    private val repository: InventoryRepository
) : AndroidViewModel(app) {
    val uiState: StateFlow<InventoryUiState> = combine(
        repository.assets,
        repository.issues,
        repository.totalAssets,
        repository.needsRepair
    ) { assets, issues, total, repair ->
        InventoryUiState(assets, issues, total, repair)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
        }
    }

    fun addAsset(name: String, serial: String, tagCode: String, category: String, location: String, photoUri: String?) {
        if (name.isBlank() || serial.isBlank()) return
        viewModelScope.launch { repository.addAsset(name, serial, tagCode, category.ifBlank { "General" }, location.ifBlank { "School" }, photoUri) }
    }

    fun markWorking(assets: List<AssetEntity>) {
        viewModelScope.launch {
            assets.forEach { repository.updateCondition(it, AssetCondition.Working, "Fast monthly check: working") }
        }
    }

    fun updateCondition(asset: AssetEntity, condition: AssetCondition, note: String = "") {
        viewModelScope.launch { repository.updateCondition(asset, condition, note) }
    }

    fun logIssue(asset: AssetEntity, reason: String) {
        if (reason.isBlank()) return
        viewModelScope.launch { repository.logIssue(asset, reason) }
    }

    companion object {
        fun factory(app: NammaShaaleApp, repository: InventoryRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return InventoryViewModel(app, repository) as T
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryApp(viewModel: InventoryViewModel) {
    val state by viewModel.uiState.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var showAddAsset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tabs = listOf("Home", "Check", "Scan", "Issues", "Repair")

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandTeal,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = {
                    Column {
                        Text("Namma-Shaale Inventory", fontWeight = FontWeight.Bold)
                        Text("Digital Asset Auditor", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE0F2FE))
                    }
                },
                actions = {
                    TextButton(onClick = { context.shareReport(state) }) { Text("Share Report", color = Color.White) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(PageBg)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> DashboardScreen(state, onAddAsset = { showAddAsset = true })
                1 -> HealthCheckScreen(state.assets, onUpdate = viewModel::updateCondition, onMarkWorking = viewModel::markWorking)
                2 -> TagLookupScreen(state.assets, onUpdate = viewModel::updateCondition, onLogIssue = viewModel::logIssue)
                3 -> IssuesScreen(state.assets, state.issues, onLogIssue = viewModel::logIssue)
                4 -> RepairScreen(state.assets.filter { it.condition == AssetCondition.NeedsRepair || it.condition == AssetCondition.Broken || it.condition == AssetCondition.Lost })
            }
        }
    }

    if (showAddAsset) {
        AddAssetDialog(
            onDismiss = { showAddAsset = false },
            onSave = { name, serial, tagCode, category, location, photoUri ->
                viewModel.addAsset(name, serial, tagCode, category, location, photoUri)
                showAddAsset = false
            }
        )
    }
}

@Composable
fun DashboardScreen(state: InventoryUiState, onAddAsset: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Total Assets", state.totalAssets.toString(), BrandTeal, Modifier.weight(1f), Color(0xFFE0F2F1))
                MetricCard("Needs Repair", state.needsRepair.toString(), WarmAmber, Modifier.weight(1f), Color(0xFFFFF7ED))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Working", state.assets.count { it.condition == AssetCondition.Working }.toString(), FreshGreen, Modifier.weight(1f), Color(0xFFECFDF5))
                MetricCard("Broken/Lost", state.assets.count { it.condition == AssetCondition.Broken || it.condition == AssetCondition.Lost }.toString(), AlertRed, Modifier.weight(1f), Color(0xFFFFF1F2))
            }
        }
        item {
            Button(onClick = onAddAsset, modifier = Modifier.fillMaxWidth()) { Text("Add Asset with Photo") }
        }
        if (state.assets.isEmpty()) {
            item { EmptyState("No assets added yet") }
        } else {
            items(state.assets) { asset ->
                AssetCard(asset)
            }
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier, background: Color = PanelBg) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = background), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = InkMuted, style = MaterialTheme.typography.labelLarge)
            Text(value, color = color, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HealthCheckScreen(assets: List<AssetEntity>, onUpdate: (AssetEntity, AssetCondition, String) -> Unit, onMarkWorking: (List<AssetEntity>) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Monthly Health Check", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Oldest unchecked items appear first for a fast audit.", color = InkMuted)
            Spacer(Modifier.height(8.dp))
            AuditProgressCard(assets)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onMarkWorking(assets.take(10)) }, enabled = assets.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Mark Next 10 as Working")
            }
        }
        items(assets) { asset ->
            Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssetHeader(asset)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatusButton("Green", AssetCondition.Working, asset.condition, onClick = { onUpdate(asset, AssetCondition.Working, "Monthly check: working") })
                        StatusButton("Yellow", AssetCondition.NeedsRepair, asset.condition, onClick = { onUpdate(asset, AssetCondition.NeedsRepair, "Monthly check: needs repair") })
                        StatusButton("Red", AssetCondition.Broken, asset.condition, onClick = { onUpdate(asset, AssetCondition.Broken, "Monthly check: broken") })
                    }
                }
            }
        }
    }
}

@Composable
fun IssuesScreen(assets: List<AssetEntity>, issues: List<IssueLogEntity>, onLogIssue: (AssetEntity, String) -> Unit) {
    var selectedAsset by remember { mutableStateOf<AssetEntity?>(null) }
    var reason by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Issue Log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Record loss, damage, or missing accessories with a reason.", color = InkMuted)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New Issue", fontWeight = FontWeight.SemiBold)
                    FlowAssetChips(assets, selectedAsset) { selectedAsset = it }
                    OutlinedTextField(reason, { reason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            selectedAsset?.let { onLogIssue(it, reason) }
                            reason = ""
                        },
                        enabled = selectedAsset != null && reason.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Log Issue") }
                }
            }
        }
        if (issues.isEmpty()) {
            item { EmptyState("No issues logged") }
        } else {
            items(issues) { issue ->
            val asset = assets.firstOrNull { it.id == issue.assetId }
            Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(asset?.name ?: "Asset #${issue.assetId}", fontWeight = FontWeight.SemiBold)
                    Text(issue.reason, color = InkMuted)
                    Text(formatDate(issue.loggedAt), style = MaterialTheme.typography.labelSmall, color = InkMuted)
                }
            }
        }
        }
    }
}

@Composable
fun FlowAssetChips(assets: List<AssetEntity>, selected: AssetEntity?, onSelect: (AssetEntity) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        assets.chunked(2).forEach { rowAssets ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowAssets.forEach { asset ->
                    FilterChip(selected = selected?.id == asset.id, onClick = { onSelect(asset) }, label = { Text(asset.name) })
                }
            }
        }
    }
}

@Composable
fun RepairScreen(repairAssets: List<AssetEntity>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("SDMC Attention", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${repairAssets.size} items require SDMC attention.", color = InkMuted)
        }
        if (repairAssets.isEmpty()) {
            item { EmptyState("No repair requests right now") }
        } else {
            items(repairAssets) { asset ->
                AssetCard(asset)
            }
        }
    }
}

@Composable
fun AssetCard(asset: AssetEntity) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (asset.photoUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(asset.photoUri),
                        contentDescription = asset.name,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                }
                AssetHeader(asset)
            }
            Divider()
            Text("${asset.category} | ${asset.location} | ${asset.displayTag()} | Last checked ${formatDate(asset.lastCheckedAt)}", color = InkMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AssetHeader(asset: AssetEntity) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(asset.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            ConditionBadge(asset.condition)
        }
        Text("Serial: ${asset.serialNumber}  Tag: ${asset.displayTag()}", color = InkMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ConditionBadge(condition: AssetCondition) {
    val color = when (condition) {
        AssetCondition.Working -> FreshGreen
        AssetCondition.NeedsRepair -> WarmAmber
        AssetCondition.Broken, AssetCondition.Lost -> AlertRed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(condition.label(), color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun RowScope.StatusButton(label: String, condition: AssetCondition, current: AssetCondition, onClick: () -> Unit) {
    val selected = condition == current
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label) }
    }
}

@Composable
fun AuditProgressCard(assets: List<AssetEntity>) {
    val nextTen = assets.take(10)
    val needsAttention = nextTen.count { it.condition != AssetCondition.Working }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Fast audit batch", fontWeight = FontWeight.SemiBold)
                Text("Next ${nextTen.size} items ready", color = InkMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text("$needsAttention flagged", color = Indigo, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TagLookupScreen(
    assets: List<AssetEntity>,
    onUpdate: (AssetEntity, AssetCondition, String) -> Unit,
    onLogIssue: (AssetEntity, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var issueReason by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    val matches = assets.filter { asset ->
        val value = query.trim()
        value.isNotBlank() && (
            asset.displayTag().contains(value, ignoreCase = true) ||
                asset.serialNumber.contains(value, ignoreCase = true) ||
                asset.name.contains(value, ignoreCase = true)
            )
    }
    val selected = matches.firstOrNull()

    if (showScanner) {
        BarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onCode = { code ->
                query = code.uppercase()
                showScanner = false
            }
        )
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Scan or Tag Lookup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Enter the tag printed on the item label, or search by serial/name.", color = InkMuted)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(query, { query = it.uppercase() }, label = { Text("Tag / Serial / Name") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { showScanner = true }, modifier = Modifier.fillMaxWidth()) { Text("Open QR / Barcode Scanner") }
                    selected?.let { asset ->
                        AssetCard(asset)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            StatusButton("Green", AssetCondition.Working, asset.condition) { onUpdate(asset, AssetCondition.Working, "Tag audit: working") }
                            StatusButton("Yellow", AssetCondition.NeedsRepair, asset.condition) { onUpdate(asset, AssetCondition.NeedsRepair, "Tag audit: needs repair") }
                            StatusButton("Red", AssetCondition.Broken, asset.condition) { onUpdate(asset, AssetCondition.Broken, "Tag audit: broken") }
                        }
                        OutlinedTextField(issueReason, { issueReason = it }, label = { Text("Issue note") }, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(
                            onClick = {
                                onLogIssue(asset, issueReason)
                                issueReason = ""
                            },
                            enabled = issueReason.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Log Issue for This Tag") }
                    } ?: EmptyState(if (query.isBlank()) "No tag entered" else "No matching asset found")
                }
            }
        }
    }
}

@Composable
fun BarcodeScannerDialog(onDismiss: () -> Unit, onCode: (String) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Asset Tag") },
        text = {
            if (hasPermission) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Point the camera at the QR/barcode label.", color = InkMuted)
                    BarcodeCameraPreview(onCode = onCode)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Camera permission is needed to scan asset tags.")
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow Camera") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeCameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8
            )
            .build()
        BarcodeScanning.getClient(options)
    }
    var scanned by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(8.dp)),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val executor = Executors.newSingleThreadExecutor()
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && !scanned) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                if (!value.isNullOrBlank() && !scanned) {
                                    scanned = true
                                    onCode(value)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(context))
            previewView
        }
    )
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

fun AssetEntity.displayTag(): String = tagCode.ifBlank { "NSI-${id.toString().padStart(3, '0')}" }

@Composable
fun AddAssetDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var tagCode by remember { mutableStateOf(generatedTagCode()) }
    var category by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var showCamera by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add School Asset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Item name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(serial, { serial = it }, label = { Text("Serial number") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tagCode, { tagCode = it.uppercase() }, label = { Text("Asset tag code") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(location, { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { showCamera = true }, modifier = Modifier.fillMaxWidth()) { Text(if (photoUri == null) "Capture Photo" else "Retake Photo") }
                photoUri?.let {
                    Image(
                        painter = rememberAsyncImagePainter(it),
                        contentDescription = "Asset photo",
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, serial, tagCode, category, location, photoUri) }, enabled = name.isNotBlank() && serial.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showCamera) {
        CameraDialog(onDismiss = { showCamera = false }, onPhoto = {
            photoUri = it
            showCamera = false
        })
    }
}

@Composable
fun CameraDialog(onDismiss: () -> Unit, onPhoto: (String) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Asset Photo") },
        text = {
            if (hasPermission) {
                CameraPreview(onImageCapture = { imageCapture = it })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Camera permission is needed to document asset condition.")
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow Camera") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val capture = imageCapture ?: return@Button
                    scope.launch { capturePhoto(context, capture, onPhoto) }
                },
                enabled = hasPermission
            ) { Text("Capture") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun CameraPreview(onImageCapture: (ImageCapture) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(8.dp)),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val capture = ImageCapture.Builder().build()
                onImageCapture(capture)
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }, ContextCompat.getMainExecutor(context))
            previewView
        }
    )
}

fun capturePhoto(context: Context, imageCapture: ImageCapture, onPhoto: (String) -> Unit) {
    val dir = File(context.filesDir, "asset_photos").apply { mkdirs() }
    val file = File(dir, "asset_${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onPhoto(Uri.fromFile(file).toString())
            }

            override fun onError(exception: ImageCaptureException) = Unit
        }
    )
}

fun Context.shareReport(state: InventoryUiState) {
    val report = buildString {
        appendLine("Namma-Shaale Inventory Summary")
        appendLine("Generated: ${formatDate(System.currentTimeMillis())}")
        appendLine("Total Assets: ${state.totalAssets}")
        appendLine("Needs Repair: ${state.needsRepair}")
        appendLine("Working: ${state.assets.count { it.condition == AssetCondition.Working }}")
        appendLine()
        appendLine("Repair Request")
        state.assets.filter { it.condition == AssetCondition.NeedsRepair || it.condition == AssetCondition.Broken || it.condition == AssetCondition.Lost }.forEach {
            appendLine("- ${it.name} [${it.displayTag()}] (${it.serialNumber}) - ${it.condition.label()} at ${it.location}")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Namma-Shaale Inventory Summary")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    startActivity(Intent.createChooser(intent, "Share Summary Report"))
}

fun AssetCondition.label(): String = when (this) {
    AssetCondition.Working -> "Working"
    AssetCondition.NeedsRepair -> "Needs Repair"
    AssetCondition.Broken -> "Broken"
    AssetCondition.Lost -> "Lost"
}

fun formatDate(value: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(value))

private val PageBg = Color(0xFFF5F7EE)
private val PanelBg = Color(0xFFFFFEF8)
private val BrandTeal = Color(0xFF0F766E)
private val Indigo = Color(0xFF4338CA)
private val WarmAmber = Color(0xFFD97706)
private val FreshGreen = Color(0xFF15803D)
private val AlertRed = Color(0xFFDC2626)
private val InkMuted = Color(0xFF5B6472)

@Composable
fun InventoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = BrandTeal,
            secondary = Indigo,
            tertiary = WarmAmber,
            surface = PageBg,
            background = PageBg
        ),
        content = {
            Surface(color = PageBg, content = content)
        }
    )
}
