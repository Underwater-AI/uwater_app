package com.underwaterai.enhance.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.underwaterai.enhance.model.EnhanceViewModel
import com.underwaterai.enhance.model.ModelType
import com.underwaterai.enhance.ui.components.ImageComparisonSlider
import com.underwaterai.enhance.ui.theme.*
import com.underwaterai.enhance.utils.AppLogger
import com.underwaterai.enhance.utils.PerformanceMetrics
import kotlinx.coroutines.launch
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: EnhanceViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showLogSheet by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val themeMode by ThemePreference.themeFlow(context)
        .collectAsState(initial = ThemeMode.SYSTEM)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.loadImage(it) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(AccentCyan, Color(0xFF0077B6))
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.FlashOn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "UnderwaterAI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(onClick = { showLogSheet = true }) {
                        Icon(
                            Icons.Outlined.Terminal,
                            contentDescription = "Logs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Theme toggle
                    Box {
                        IconButton(onClick = { showThemeMenu = true }) {
                            Icon(
                                imageVector = when (themeMode) {
                                    ThemeMode.DARK -> Icons.Filled.DarkMode
                                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                                    ThemeMode.SYSTEM -> Icons.Outlined.Contrast
                                },
                                contentDescription = "Theme",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Light") },
                                onClick = {
                                    scope.launch { ThemePreference.setTheme(context, ThemeMode.LIGHT) }
                                    showThemeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.LightMode, null) },
                                trailingIcon = {
                                    if (themeMode == ThemeMode.LIGHT)
                                        Icon(Icons.Filled.Check, null, tint = AccentCyan)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Dark") },
                                onClick = {
                                    scope.launch { ThemePreference.setTheme(context, ThemeMode.DARK) }
                                    showThemeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.DarkMode, null) },
                                trailingIcon = {
                                    if (themeMode == ThemeMode.DARK)
                                        Icon(Icons.Filled.Check, null, tint = AccentCyan)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("System") },
                                onClick = {
                                    scope.launch { ThemePreference.setTheme(context, ThemeMode.SYSTEM) }
                                    showThemeMenu = false
                                },
                                leadingIcon = { Icon(Icons.Outlined.Contrast, null) },
                                trailingIcon = {
                                    if (themeMode == ThemeMode.SYSTEM)
                                        Icon(Icons.Filled.Check, null, tint = AccentCyan)
                                }
                            )
                        }
                    }

                    if (uiState.originalBitmap != null) {
                        IconButton(onClick = { viewModel.clearAll() }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Reset",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Model Selector
            ModelSelector(
                selectedModel = uiState.selectedModel,
                onModelSelected = { viewModel.selectModel(it) },
                enabled = !uiState.isProcessing
            )

            // Image Area
            if (uiState.originalBitmap == null) {
                ImagePickerPlaceholder(
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                // Show result or original
                if (uiState.showResult && uiState.enhancedBitmap != null) {
                    ResultView(
                        original = uiState.originalBitmap!!,
                        enhanced = uiState.enhancedBitmap!!,
                        metrics = uiState.metrics,
                        onSave = { bitmap ->
                            saveBitmapToGallery(context, bitmap)
                        },
                        onBack = { viewModel.clearResult() }
                    )
                } else {
                    OriginalImageView(
                        bitmap = uiState.originalBitmap!!,
                        onChangeImage = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    // Enhance Button
                    EnhanceButton(
                        isProcessing = uiState.isProcessing,
                        modelName = uiState.selectedModel.displayName,
                        onClick = { viewModel.enhanceImage() }
                    )
                }
            }

            // Error Message
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = ErrorRed.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = ErrorRed
                        )
                        Text(
                            text = error,
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // Log viewer bottom sheet
    if (showLogSheet) {
        LogViewerSheet(
            onDismiss = { showLogSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var logText by remember { mutableStateOf(AppLogger.getRecentLogs(200)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0A1020),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AccentCyan.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Terminal,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Application Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { logText = AppLogger.getRecentLogs(200) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { shareLogFile(context) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Share logs",
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log file path
            val logPath = AppLogger.getLogFile()?.absolutePath ?: "N/A"
            Text(
                text = logPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Log content
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF050D18))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = logText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = Color(0xFF8BC34A)
                    )
                }
            }
        }
    }
}

fun shareLogFile(context: Context) {
    val logFile = AppLogger.getLogFile()
    if (logFile == null || !logFile.exists()) {
        Toast.makeText(context, "No log file found", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "UnderwaterAI Logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Logs"))
    } catch (e: Exception) {
        AppLogger.e("LogShare", "Failed to share log file", e)
        Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ModelSelector(
    selectedModel: ModelType,
    onModelSelected: (ModelType) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Select Enhancement Model",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModelType.entries.forEach { model ->
                val isSelected = model == selectedModel
                val accentColor = modelAccentColor(model)
                val borderColor = if (isSelected) accentColor
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                val bgColor = if (isSelected) accentColor.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

                Card(
                    modifier = Modifier
                        .width(170.dp)
                        .clickable(enabled = enabled) { onModelSelected(model) }
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (isSelected) accentColor else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = borderColor,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = model.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = model.bestFor,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${model.scaleFactor}x upscale",
                            style = MaterialTheme.typography.labelSmall,
                            color = borderColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun modelAccentColor(model: ModelType): Color {
    return when (model) {
        ModelType.MODEL_1 -> AccentCyan
        ModelType.MODEL_2 -> EmeraldAccent
        ModelType.MODEL_3 -> Color(0xFFF59E0B) // Amber
        ModelType.MODEL_4 -> Color(0xFFA78BFA) // Purple
        ModelType.MODEL_5 -> Color(0xFFEF4444) // Red
    }
}

@Composable
fun ImagePickerPlaceholder(onPickImage: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clickable { onPickImage() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                colors = listOf(AccentCyan.copy(alpha = 0.4f), EmeraldAccent.copy(alpha = 0.4f))
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = AccentCyan.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = "Pick image",
                    modifier = Modifier.size(36.dp),
                    tint = AccentCyan
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tap to select an image",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose an underwater photo to enhance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OriginalImageView(bitmap: Bitmap, onChangeImage: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Original Image",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onChangeImage) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Change")
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Original image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
        }

        Text(
            text = "${bitmap.width} x ${bitmap.height} px",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EnhanceButton(isProcessing: Boolean, modelName: String, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Button(
        onClick = onClick,
        enabled = !isProcessing,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentCyan,
            contentColor = DeepOcean,
            disabledContainerColor = AccentCyan.copy(alpha = if (isProcessing) pulseAlpha else 0.4f),
            disabledContentColor = DeepOcean.copy(alpha = 0.7f)
        )
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = DeepOcean,
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Enhancing...",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        } else {
            Icon(
                Icons.Filled.AutoFixHigh,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Enhance with $modelName",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultView(
    original: Bitmap,
    enhanced: Bitmap,
    metrics: PerformanceMetrics?,
    onSave: (Bitmap) -> Unit,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Comparison header
        Text(
            text = "Before / After Comparison",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AccentCyan
        )

        // Before/After slider with pinch-to-zoom
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            ImageComparisonSlider(
                originalBitmap = original,
                enhancedBitmap = enhanced,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 250.dp, max = 400.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        // Performance Metrics
        metrics?.let { m ->
            PerformanceCard(m)
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Try Again")
            }
            Button(
                onClick = { onSave(enhanced) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldAccent,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.SaveAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Image")
            }
        }
    }
}

// ────────────────────────────────────────────────
// Performance & Hardware Analytics Card
// ────────────────────────────────────────────────

@Composable
fun PerformanceCard(metrics: PerformanceMetrics) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Speed,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Performance Analytics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Key metrics row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricItem("Inference", "${metrics.inferenceTimeMs}ms", AccentCyan)
                MetricItem("Total", "${metrics.totalTimeMs}ms", EmeraldAccent)
                MetricItem("Upscale", metrics.upscaleFactor, Color(0xFFF59E0B))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricItem("Input", "${metrics.inputWidth}x${metrics.inputHeight}", MaterialTheme.colorScheme.onSurfaceVariant)
                MetricItem("Output", "${metrics.outputWidth}x${metrics.outputHeight}", MaterialTheme.colorScheme.onSurfaceVariant)
                MetricItem("Throughput", "${metrics.pixelsPerSecond / 1_000}K px/s", Color(0xFFA78BFA))
            }

            // Timing breakdown
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimeBar("Model Load", metrics.modelLoadTimeMs, metrics.totalTimeMs, Color(0xFF8B5CF6))
                    TimeBar("Preprocess", metrics.preprocessTimeMs, metrics.totalTimeMs, Color(0xFFF59E0B))
                    TimeBar("Inference", metrics.inferenceTimeMs, metrics.totalTimeMs, AccentCyan)
                    TimeBar("Postprocess", metrics.postprocessTimeMs, metrics.totalTimeMs, EmeraldAccent)
                }
            }

            // Expandable hardware details
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // CPU Section
                    HardwareSectionHeader("CPU", Icons.Filled.Memory)
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HardwareRow("Architecture", metrics.cpuArch)
                            HardwareRow("Cores", "${metrics.cpuCoreCount} (all used for inference)")
                            HardwareRow("Threads", "${metrics.threadsUsed}")

                            if (metrics.cpuCoreFrequencies.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Per-Core Frequencies",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                metrics.cpuCoreFrequencies.forEach { core ->
                                    CoreFrequencyBar(core.coreIndex, core.currentFreqMhz, core.maxFreqMhz)
                                }
                            }
                        }
                    }

                    // GPU Section
                    HardwareSectionHeader("GPU", Icons.Filled.Tv)
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HardwareRow("Renderer", metrics.gpuRenderer)
                            HardwareRow("Vendor", metrics.gpuVendor)
                            HardwareRow("OpenGL ES", metrics.gpuGlVersion)
                            HardwareRow("Available", if (metrics.gpuAvailable) "Yes" else "No")
                            HardwareRow(
                                "Used for Inference",
                                if (metrics.gpuUsedForInference) "Yes (Vulkan)" else "No (CPU optimized)"
                            )
                        }
                    }

                    // Memory Section
                    HardwareSectionHeader("Memory", Icons.Filled.Storage)
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HardwareRow("System RAM", "${metrics.totalRamMb} MB total")
                            HardwareRow("RAM Available", "${metrics.availableRamMb} MB")
                            HardwareRow("Heap Max", "${metrics.heapMaxMb} MB")
                            HardwareRow("Heap Before", "${metrics.heapUsedBeforeMb} MB")
                            HardwareRow("Heap After", "${metrics.heapUsedAfterMb} MB")
                            val heapDelta = metrics.heapUsedAfterMb - metrics.heapUsedBeforeMb
                            val deltaColor = if (heapDelta > 0) Color(0xFFF59E0B) else EmeraldAccent
                            HardwareRow(
                                "Heap Delta",
                                "${if (heapDelta >= 0) "+" else ""}${heapDelta} MB",
                                valueColor = deltaColor
                            )
                        }
                    }

                    // Device Section
                    HardwareSectionHeader("Device", Icons.Filled.PhoneAndroid)
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HardwareRow("Model", metrics.deviceModel)
                            HardwareRow("Android API", "${metrics.androidApi}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HardwareSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = AccentCyan
        )
    }
}

@Composable
fun HardwareRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CoreFrequencyBar(coreIndex: Int, currentMhz: Long, maxMhz: Long) {
    val fraction = if (maxMhz > 0) (currentMhz.toFloat() / maxMhz).coerceIn(0f, 1f) else 0f
    val color = when {
        fraction > 0.8f -> Color(0xFFEF4444)
        fraction > 0.5f -> Color(0xFFF59E0B)
        else -> EmeraldAccent
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Core $coreIndex",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Text(
            text = "${currentMhz}MHz",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.width(62.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun MetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TimeBar(label: String, timeMs: Long, totalMs: Long, color: Color) {
    val fraction = if (totalMs > 0) (timeMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
        Text(
            text = "${timeMs}ms",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.width(50.dp),
            textAlign = TextAlign.End
        )
    }
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    try {
        val filename = "UnderwaterAI_${System.currentTimeMillis()}.png"
        val outputStream: OutputStream?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/UnderwaterAI")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )
            outputStream = uri?.let { context.contentResolver.openOutputStream(it) }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val file = java.io.File(dir, filename)
            outputStream = java.io.FileOutputStream(file)
        }

        outputStream?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        AppLogger.i("SaveImage", "Image saved: $filename")
        Toast.makeText(context, "Saved to Pictures/UnderwaterAI", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        AppLogger.e("SaveImage", "Save failed", e)
        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
