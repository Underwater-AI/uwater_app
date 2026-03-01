package com.underwaterai.enhance.utils

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class PerformanceMetrics(
    val modelName: String,
    val modelLoadTimeMs: Long,
    val preprocessTimeMs: Long,
    val inferenceTimeMs: Long,
    val postprocessTimeMs: Long,
    val totalTimeMs: Long,
    val inputWidth: Int,
    val inputHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val threadsUsed: Int,
    val memoryUsedMb: Long,
    // Hardware profile fields
    val cpuArch: String = "",
    val cpuCoreCount: Int = 0,
    val cpuCoreFrequencies: List<HardwareProfiler.CpuCoreInfo> = emptyList(),
    val gpuRenderer: String = "",
    val gpuVendor: String = "",
    val gpuGlVersion: String = "",
    val gpuAvailable: Boolean = false,
    val gpuUsedForInference: Boolean = false,
    val totalRamMb: Long = 0,
    val availableRamMb: Long = 0,
    val heapMaxMb: Long = 0,
    val heapUsedBeforeMb: Long = 0,
    val heapUsedAfterMb: Long = 0,
    val deviceModel: String = "",
    val androidApi: Int = 0
) {
    val upscaleFactor: String
        get() {
            if (inputWidth == 0 || inputHeight == 0) return "N/A"
            val fx = outputWidth.toFloat() / inputWidth
            val fy = outputHeight.toFloat() / inputHeight
            return "%.1fx".format((fx + fy) / 2)
        }

    val pixelsPerSecond: Long
        get() {
            if (inferenceTimeMs <= 0) return 0
            return (outputWidth.toLong() * outputHeight * 1000) / inferenceTimeMs
        }

    fun toReportString(): String = buildString {
        appendLine("=== Performance Report: $modelName ===")
        appendLine("Device:       $deviceModel (API $androidApi)")
        appendLine("CPU:          $cpuArch, $cpuCoreCount cores")
        cpuCoreFrequencies.forEach { core ->
            appendLine("  Core ${core.coreIndex}: ${core.currentFreqMhz}MHz / ${core.maxFreqMhz}MHz")
        }
        appendLine("GPU:          $gpuRenderer ($gpuVendor)")
        appendLine("  OpenGL ES:  $gpuGlVersion")
        appendLine("  GPU avail:  $gpuAvailable | Used: $gpuUsedForInference")
        appendLine("Threads:      $threadsUsed")
        appendLine("RAM:          ${availableRamMb}MB free / ${totalRamMb}MB total")
        appendLine("Heap before:  ${heapUsedBeforeMb}MB | after: ${heapUsedAfterMb}MB / ${heapMaxMb}MB max")
        appendLine("---")
        appendLine("Model load:   ${modelLoadTimeMs}ms")
        appendLine("Preprocess:   ${preprocessTimeMs}ms")
        appendLine("Inference:    ${inferenceTimeMs}ms")
        appendLine("Postprocess:  ${postprocessTimeMs}ms")
        appendLine("Total:        ${totalTimeMs}ms")
        appendLine("Input:        ${inputWidth}x${inputHeight}")
        appendLine("Output:       ${outputWidth}x${outputHeight}")
        appendLine("Upscale:      $upscaleFactor")
        appendLine("Throughput:   ${pixelsPerSecond / 1_000}K px/sec")
        appendLine("========================================")
    }

    override fun toString(): String = toReportString()
}

/**
 * Thread-safe performance logger with file persistence.
 * Each phase gets its own start timestamp to avoid overwrite races.
 */
class PerformanceLogger(private val tag: String = "UnderwaterAI.Perf") {

    private val phaseStarts = ConcurrentHashMap<String, Long>()
    private val phaseResults = ConcurrentHashMap<String, Long>()

    fun startPhase(phaseName: String) {
        phaseStarts[phaseName] = SystemClock.elapsedRealtime()
        Log.d(tag, "[$phaseName] started")
    }

    fun endPhase(phaseName: String): Long {
        val start = phaseStarts[phaseName] ?: SystemClock.elapsedRealtime()
        val elapsed = SystemClock.elapsedRealtime() - start
        phaseResults[phaseName] = elapsed
        Log.d(tag, "[$phaseName] completed in ${elapsed}ms")
        return elapsed
    }

    fun getPhase(phaseName: String): Long = phaseResults[phaseName] ?: 0L

    fun getMemoryUsageMb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    fun logSystemInfo() {
        val runtime = Runtime.getRuntime()
        val cores = runtime.availableProcessors()
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val msg = "CPU Cores: $cores | Max Memory: ${maxMem}MB | Allocated: ${totalMem}MB | Free: ${freeMem}MB"
        Log.i(tag, msg)
        AppLogger.i(tag, msg)
    }

    fun reset() {
        phaseStarts.clear()
        phaseResults.clear()
    }
}

/**
 * Application-wide file logger. Writes all logs to a persistent file
 * that can be viewed and shared from within the app.
 */
object AppLogger {

    private const val TAG = "UnderwaterAI.Logger"
    private const val LOG_FILE_NAME = "underwater_ai_log.txt"
    private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024 // 2MB

    private var logFile: File? = null
    private var writer: PrintWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            logFile = File(dir, LOG_FILE_NAME)

            // Rotate if too large
            logFile?.let { file ->
                if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                    val backup = File(dir, "underwater_ai_log_prev.txt")
                    backup.delete()
                    file.renameTo(backup)
                }
            }

            writer = PrintWriter(FileWriter(logFile, true), true)
            initialized = true

            val separator = "=".repeat(60)
            write("I", TAG, separator)
            write("I", TAG, "Session started at ${dateFormat.format(Date())}")
            write("I", TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            write("I", TAG, "ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            write("I", TAG, "Cores: ${Runtime.getRuntime().availableProcessors()}")
            write("I", TAG, "Max heap: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB")
            write("I", TAG, separator)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize file logger", e)
        }
    }

    @Synchronized
    private fun write(level: String, tag: String, message: String) {
        if (!initialized) return
        try {
            val timestamp = dateFormat.format(Date())
            val line = "$timestamp $level/$tag: $message"
            writer?.println(line)
        } catch (_: Exception) {
            // Don't log errors from the logger itself to avoid loops
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        write("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("I", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        write("W", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        write("E", tag, message)
        throwable?.let {
            write("E", tag, "  Caused by: ${it.javaClass.simpleName}: ${it.message}")
            it.stackTrace.take(10).forEach { frame ->
                write("E", tag, "    at $frame")
            }
        }
    }

    fun logMetrics(metrics: PerformanceMetrics) {
        metrics.toReportString().lines().forEach { line ->
            if (line.isNotBlank()) write("I", "UnderwaterAI.Metrics", line)
        }
    }

    fun getLogFile(): File? = logFile

    fun getRecentLogs(lineCount: Int = 100): String {
        val file = logFile ?: return "(logger not initialized)"
        if (!file.exists()) return "(no logs yet)"
        return try {
            val lines = file.readLines()
            lines.takeLast(lineCount).joinToString("\n")
        } catch (e: Exception) {
            "(error reading logs: ${e.message})"
        }
    }

    @Synchronized
    fun flush() {
        writer?.flush()
    }
}
