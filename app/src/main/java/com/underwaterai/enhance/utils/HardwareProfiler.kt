package com.underwaterai.enhance.utils

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import java.io.File

/**
 * Device performance tier used to adapt tile sizes, timeouts,
 * and model recommendations for the detected hardware.
 */
enum class DeviceTier {
    /** ≥6 GB RAM, ≥4 big cores above 2 GHz */
    HIGH,
    /** 4–6 GB RAM, ≥2 big cores above 1.8 GHz */
    MEDIUM,
    /** <4 GB RAM or weak CPU — needs reduced tile sizes and timeouts */
    LOW
}

/**
 * Profiles the device hardware: CPU cores, frequencies, GPU info,
 * and memory. Used to document what resources the enhancement used.
 */
object HardwareProfiler {

    data class CpuCoreInfo(
        val coreIndex: Int,
        val currentFreqMhz: Long,
        val maxFreqMhz: Long
    )

    data class GpuInfo(
        val renderer: String,
        val vendor: String,
        val glVersion: String,
        val available: Boolean
    )

    data class HardwareSnapshot(
        val cpuCores: Int,
        val cpuArch: String,
        val cpuCoreFrequencies: List<CpuCoreInfo>,
        val gpuInfo: GpuInfo,
        val totalRamMb: Long,
        val availableRamMb: Long,
        val heapMaxMb: Long,
        val heapUsedMb: Long,
        val deviceModel: String,
        val androidApi: Int
    ) {
        fun toReportString(): String = buildString {
            appendLine("--- Hardware Profile ---")
            appendLine("Device: $deviceModel (API $androidApi)")
            appendLine("CPU: $cpuArch, $cpuCores cores")
            cpuCoreFrequencies.forEach { core ->
                appendLine("  Core ${core.coreIndex}: ${core.currentFreqMhz}MHz / ${core.maxFreqMhz}MHz max")
            }
            appendLine("GPU: ${gpuInfo.renderer} (${gpuInfo.vendor})")
            appendLine("  OpenGL ES: ${gpuInfo.glVersion}")
            appendLine("  Available: ${gpuInfo.available}")
            appendLine("RAM: ${availableRamMb}MB available / ${totalRamMb}MB total")
            appendLine("Heap: ${heapUsedMb}MB used / ${heapMaxMb}MB max")
            appendLine("------------------------")
        }
    }

    /**
     * Snapshot current hardware state. Call from background thread.
     */
    fun snapshot(context: Context): HardwareSnapshot {
        val runtime = Runtime.getRuntime()
        val cores = runtime.availableProcessors()
        val coreFreqs = readCpuFrequencies(cores)
        val gpuInfo = probeGpuInfo()
        val memInfo = getMemoryInfo(context)

        return HardwareSnapshot(
            cpuCores = cores,
            cpuArch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            cpuCoreFrequencies = coreFreqs,
            gpuInfo = gpuInfo,
            totalRamMb = memInfo.first,
            availableRamMb = memInfo.second,
            heapMaxMb = runtime.maxMemory() / (1024 * 1024),
            heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidApi = Build.VERSION.SDK_INT
        )
    }

    /**
     * Read per-core CPU frequencies from sysfs.
     */
    private fun readCpuFrequencies(coreCount: Int): List<CpuCoreInfo> {
        return (0 until coreCount).map { i ->
            val curFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val maxFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")

            val curKhz = try { curFile.readText().trim().toLongOrNull() ?: 0L } catch (_: Exception) { 0L }
            val maxKhz = try { maxFile.readText().trim().toLongOrNull() ?: 0L } catch (_: Exception) { 0L }

            CpuCoreInfo(
                coreIndex = i,
                currentFreqMhz = curKhz / 1000,
                maxFreqMhz = maxKhz / 1000
            )
        }
    }

    /**
     * Probe GPU information via EGL/OpenGL ES.
     * Creates a minimal EGL context to query the renderer string.
     */
    private fun probeGpuInfo(): GpuInfo {
        try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                return GpuInfo("N/A", "N/A", "N/A", false)
            }

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                return GpuInfo("N/A", "N/A", "N/A", false)
            }

            val attribList = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)

            if (numConfigs[0] == 0) {
                EGL14.eglTerminate(display)
                return GpuInfo("N/A", "N/A", "N/A", false)
            }

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val eglContext = EGL14.eglCreateContext(display, configs[0]!!, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0]!!, surfaceAttribs, 0)

            EGL14.eglMakeCurrent(display, surface, surface, eglContext)

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
            val glVer = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"

            // Cleanup
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)

            return GpuInfo(renderer, vendor, glVer, true)
        } catch (e: Exception) {
            return GpuInfo("Error: ${e.message}", "N/A", "N/A", false)
        }
    }

    /**
     * Get system RAM info from ActivityManager.
     * Returns (totalMb, availableMb).
     */
    private fun getMemoryInfo(context: Context): Pair<Long, Long> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val totalMb = mi.totalMem / (1024 * 1024)
            val availMb = mi.availMem / (1024 * 1024)
            Pair(totalMb, availMb)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    // ── Device-tier & adaptive sizing helpers ───────────────────────────

    /**
     * Classify the device into HIGH / MEDIUM / LOW tier based on
     * total RAM and CPU performance-core frequency.
     */
    fun classifyDevice(context: Context): DeviceTier {
        val memInfo = getMemoryInfo(context)
        val totalRamMb = memInfo.first
        
        // Count performance cores
        val runtime = Runtime.getRuntime()
        val cores = runtime.availableProcessors()
        val coreFreqs = readCpuFrequencies(cores)
        val bigCores = countPerformanceCores(coreFreqs)
        
        return when {
            totalRamMb >= 5800 && bigCores >= 4 -> DeviceTier.HIGH
            totalRamMb >= 3800 && bigCores >= 2 -> DeviceTier.MEDIUM
            else -> DeviceTier.LOW
        }
    }

    /**
     * Return the number of "performance" (big) cores.
     *
     * On ARM big.LITTLE SoCs the big cores have a noticeably higher
     * max frequency than the efficiency cores. We use a simple
     * heuristic: any core whose max frequency is ≥80 % of the
     * highest frequency across all cores is a "big" core.
     *
     * On symmetric CPUs (e.g. desktop-class SoCs or emulators)
     * all cores count as big.
     */
    fun countPerformanceCores(cores: List<CpuCoreInfo>): Int {
        if (cores.isEmpty()) return Runtime.getRuntime().availableProcessors()
        val maxFreq = cores.maxOfOrNull { it.maxFreqMhz } ?: return Runtime.getRuntime().availableProcessors()
        if (maxFreq <= 0) return Runtime.getRuntime().availableProcessors()
        
        return cores.count { it.maxFreqMhz >= maxFreq * 0.8 }
    }

    /**
     * Return the recommended tile size for inference based on
     * available heap memory and device tier.
     *
     * ESRGAN models produce 4× output, so a 512 px tile → 2048 px
     * output tile ≈ 16 MB of pixel data in ARGB_8888. Plus the
     * float tensor and accumulator arrays, a single tile pass needs
     * roughly 60–80 MB of transient heap. On LOW-tier devices we
     * drop to 256 px tiles (≈ 20 MB transient) to avoid OOM.
     */
    fun recommendedTileSize(tier: DeviceTier): Int = when(tier) {
        DeviceTier.HIGH -> 512
        DeviceTier.MEDIUM -> 384
        DeviceTier.LOW -> 256
    }

    /**
     * Maximum total processing timeout in milliseconds.
     * LOW-tier devices get more time because smaller tiles are slower.
     */
    fun recommendedTimeoutMs(tier: DeviceTier): Long = when(tier) {
        DeviceTier.HIGH -> 60_000L      // 60 seconds
        DeviceTier.MEDIUM -> 120_000L   // 120 seconds
        DeviceTier.LOW -> 180_000L      // 180 seconds
    }

    /**
     * Per-tile timeout in milliseconds. If a single tile takes
     * longer than this the job is aborted — it means the device
     * simply cannot handle the model.
     */
    fun recommendedPerTileTimeoutMs(tier: DeviceTier): Long = when(tier) {
        DeviceTier.HIGH -> 10_000L       // 10 seconds
        DeviceTier.MEDIUM -> 20_000L     // 20 seconds
        DeviceTier.LOW -> 30_000L        // 30 seconds
    }
}
