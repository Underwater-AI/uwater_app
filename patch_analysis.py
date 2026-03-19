import re

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'r') as f:
    text = f.read()

# Add bitmap for annotated image and graph
text = text.replace(
    'val analysisReport: String? = null',
    'val analysisReport: String? = null,\n    val annotatedBitmap: Bitmap? = null,\n    val histogramBitmap: Bitmap? = null,\n    val cpuStats: String? = null'
)

# Insert the drawing and sys/freq helpers inside EnhanceViewModel or companion
HELPERS = """
    private fun drawDetections(original: Bitmap, detections: List<DetectionResult>): Bitmap {
        val bitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(bitmap)
        val paintBox = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
        }
        val paintText = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 40f
            isFakeBoldText = true
            setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
        }
        for (det in detections) {
            val left = det.box[0]
            val top = det.box[1]
            val right = det.box[2]
            val bottom = det.box[3]
            canvas.drawRect(left, top, right, bottom, paintBox)
            canvas.drawText("${det.labelName} ${(det.score * 100).toInt()}%", left, top - 10f, paintText)
        }
        return bitmap
    }

    private fun generateHistogram(bitmap: Bitmap): Bitmap {
        val histBitmap = Bitmap.createBitmap(256, 150, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(histBitmap)
        canvas.drawColor(android.graphics.Color.DKGRAY)
        
        val rCount = IntArray(256)
        val gCount = IntArray(256)
        val bCount = IntArray(256)
        
        // Sampling to keep it fast
        val step = kotlin.math.max(1, bitmap.width * bitmap.height / 10000)
        var pxVisited = 0
        for (y in 0 until bitmap.height step kotlin.math.max(1, bitmap.height / 100)) {
            for (x in 0 until bitmap.width step kotlin.math.max(1, bitmap.width / 100)) {
                val p = bitmap.getPixel(x, y)
                rCount[android.graphics.Color.red(p)]++
                gCount[android.graphics.Color.green(p)]++
                bCount[android.graphics.Color.blue(p)]++
                pxVisited++
            }
        }
        
        val maxCount = maxOf(rCount.maxOrNull() ?: 1, gCount.maxOrNull() ?: 1, bCount.maxOrNull() ?: 1).toFloat()
        
        val paintR = android.graphics.Paint().apply { color = android.graphics.Color.RED; strokeWidth=1f }
        val paintG = android.graphics.Paint().apply { color = android.graphics.Color.GREEN; strokeWidth=1f }
        val paintB = android.graphics.Paint().apply { color = android.graphics.Color.BLUE; strokeWidth=1f }
        
        for (i in 0..255) {
            canvas.drawLine(i.toFloat(), 150f, i.toFloat(), 150f - (rCount[i] / maxCount * 150f), paintR)
            canvas.drawLine(i.toFloat(), 150f, i.toFloat(), 150f - (gCount[i] / maxCount * 150f), paintG)
            canvas.drawLine(i.toFloat(), 150f, i.toFloat(), 150f - (bCount[i] / maxCount * 150f), paintB)
        }
        return histBitmap
    }
    
    private fun getCpuStats(): String {
        val sb = StringBuilder()
        sb.append("CPU Core Frequencies:\\n")
        try {
            val numCores = Runtime.getRuntime().availableProcessors()
            for (i in 0 until numCores) {
                val curFreqFile = java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                val maxFreqFile = java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (curFreqFile.exists() && maxFreqFile.exists()) {
                    val cur = curFreqFile.readText().trim().toLongOrNull() ?: 0L
                    val max = maxFreqFile.readText().trim().toLongOrNull() ?: 0L
                    sb.append("Core $i: ${cur / 1000} MHz / ${max / 1000} MHz\\n")
                } else {
                    sb.append("Core $i: offline or isolated\\n")
                }
            }
        } catch (e: Exception) {
            sb.append("Failed to read CPU stats: ${e.message}\\n")
        }
        return sb.toString()
    }
"""

text = text.replace('    fun clearAnalysis() {', HELPERS + '\n    fun clearAnalysis() {')

text = text.replace(
    '_uiState.value = _uiState.value.copy(isProcessing = true, analysisReport = null)',
    '_uiState.value = _uiState.value.copy(isProcessing = true, analysisReport = null, annotatedBitmap = null, histogramBitmap = null, cpuStats = null)'
)

# Within runMarineAnalysis, modify to save annotated UI and stats
text = text.replace(
    'val report = """',
    """val stats = getCpuStats()
                val annotatedUi = drawDetections(original, detections)
                val histUi = generateHistogram(original)
                val report = \"\"\""""
)

text = text.replace(
    'analysisReport = report',
    'analysisReport = report,\n                        annotatedBitmap = annotatedUi,\n                        histogramBitmap = histUi,\n                        cpuStats = stats'
)

text = text.replace(
    '_uiState.value.copy(analysisReport = null)',
    '_uiState.value.copy(analysisReport = null, annotatedBitmap = null, histogramBitmap = null, cpuStats = null)'
)

with open('app/src/main/java/com/underwaterai/enhance/model/EnhanceViewModel.kt', 'w') as f:
    f.write(text)
