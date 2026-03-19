package com.underwaterai.enhance.model

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.util.PriorityQueue

class ImageClassifier(private val context: Context) {
    companion object {
        private const val TAG = "UnderwaterAI.Classifier"
        private const val MODEL_FILENAME = "classifier_mobilenet_v3.ptl"
        private const val LABELS_FILENAME = "imagenet_classes.txt"
        private const val INPUT_SIZE = 224
    }

    private var module: Module? = null
    private var labels: List<String>? = null

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelFile = assetFilePath(context, MODEL_FILENAME)
            org.pytorch.PyTorchAndroid.setNumThreads(Runtime.getRuntime().availableProcessors())
            module = Module.load(modelFile)
            labels = loadLabels(context, LABELS_FILENAME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
        }
    }

    suspend fun classify(bitmap: Bitmap): List<Prediction>? = withContext(Dispatchers.Default) {
        val mod = module ?: return@withContext null
        val labs = labels ?: return@withContext null

        val scale = Math.max(INPUT_SIZE.toFloat() / bitmap.width, INPUT_SIZE.toFloat() / bitmap.height)
        val scaledWidth = Math.max(1, Math.round(bitmap.width * scale))
        val scaledHeight = Math.max(1, Math.round(bitmap.height * scale))
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val startX = Math.max(0, (scaledWidth - INPUT_SIZE) / 2)
        val startY = Math.max(0, (scaledHeight - INPUT_SIZE) / 2)
        val finalCrop = Bitmap.createBitmap(scaledBitmap, startX, startY, INPUT_SIZE, INPUT_SIZE)

        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            finalCrop,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )
        
        val outputTensor = mod.forward(IValue.from(inputTensor)).toTensor()
        val scores = outputTensor.dataAsFloatArray
        
        var expSum = 0.0f
        val expScores = FloatArray(scores.size)
        for (i in scores.indices) {
            val exp = Math.exp(scores[i].toDouble()).toFloat()
            expScores[i] = exp
            expSum += exp
        }
        
        val pq = PriorityQueue<Prediction>(3) { p1, p2 -> java.lang.Float.compare(p2.score, p1.score) }
        for (i in expScores.indices) {
            val score = expScores[i] / expSum
            val label = labs[i]
            // Added Invasive Species check functionality
            val invasive = FeatureExtensions.checkInvasiveSpecies(label)
            pq.add(Prediction(label, score, invasive))
        }
        
        val top3 = mutableListOf<Prediction>()
        val n = Math.min(3, pq.size)
        for (i in 0 until n) {
            pq.poll()?.let { top3.add(it) }
        }
        return@withContext top3
    }
    
    data class Prediction(val label: String, val score: Float, val isInvasive: Boolean = false)

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }

        context.assets.open("models/$assetName").use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
        return file.absolutePath
    }

    private fun loadLabels(context: Context, fileName: String): List<String> {
        val labels = mutableListOf<String>()
        try {
            context.assets.open("models/$fileName").bufferedReader().useLines { lines ->
                labels.addAll(lines)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load labels", e)
        }
        return labels
    }
}
