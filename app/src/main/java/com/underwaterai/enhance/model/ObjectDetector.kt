package com.underwaterai.enhance.model

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class DetectionResult(
    val labelIndex: Int,
    val labelName: String,
    val score: Float,
    val box: FloatArray // [left, top, right, bottom]
)

class ObjectDetector(private val context: Context) {

    private var module: Module? = null
    private var labels = emptyArray<String>()

    fun loadModel() {
            org.pytorch.PyTorchAndroid.setNumThreads(Runtime.getRuntime().availableProcessors())
        
        val modelPath = assetFilePath(context, "models/detector_ssdlite.ptl")
        module = org.pytorch.Module.load(modelPath)
        
        val labelsPath = assetFilePath(context, "models/coco_classes.txt")
        labels = File(labelsPath).readLines().toTypedArray()
    }

    suspend fun detect(bitmap: Bitmap, threshold: Float = 0.5f): List<DetectionResult> = withContext(Dispatchers.Default) {
        val mod = module ?: throw IllegalStateException("Model is not loaded")

        val targetSize = 320
        // Calculate scaling factor to maintain aspect ratio
        val scale = Math.min(targetSize.toFloat() / bitmap.width, targetSize.toFloat() / bitmap.height)
        val scaledWidth = Math.max(1, Math.round(bitmap.width * scale))
        val scaledHeight = Math.max(1, Math.round(bitmap.height * scale))

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        
        // Create a 320x320 bitmap (letterbox) to pad the resized image
        val paddedBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(paddedBitmap)
        val leftPad = (targetSize - scaledWidth) / 2f
        val topPad = (targetSize - scaledHeight) / 2f
        canvas.drawBitmap(resizedBitmap, leftPad, topPad, null)

        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            paddedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )

        // The model expects a List of 3D Tensors: List[Tensor[C, H, W]]
        val tensor3d = org.pytorch.Tensor.fromBlob(
            inputTensor.dataAsFloatArray,
            longArrayOf(3, targetSize.toLong(), targetSize.toLong())
        )
        val inputs = IValue.listFrom(tensor3d)
        val outputTuple = mod.forward(inputs).toTuple()

        // SSDLite TorchScript eval output is a tuple: (Dict[str, Tensor], List[Dict[str, Tensor]])
        val outputList = outputTuple[1].toList()
        val resultDict = outputList[0].toDictStringKey()

        val boxesTensor = resultDict["boxes"]?.toTensor() ?: return@withContext emptyList()
        val scoresTensor = resultDict["scores"]?.toTensor() ?: return@withContext emptyList()
        val labelsTensor = resultDict["labels"]?.toTensor() ?: return@withContext emptyList()
        
        val boxes = boxesTensor.dataAsFloatArray
        val scores = scoresTensor.dataAsFloatArray
        val labelsIdx = labelsTensor.dataAsLongArray

        val results = mutableListOf<DetectionResult>()

        for (i in scores.indices) {
            if (scores[i] >= threshold) {
                // Adjust coordinates back regarding padding and scale
                val left = Math.max(0f, (boxes[4 * i] - leftPad) / scale)
                val top = Math.max(0f, (boxes[4 * i + 1] - topPad) / scale)
                val right = Math.min(bitmap.width.toFloat(), (boxes[4 * i + 2] - leftPad) / scale)
                val bottom = Math.min(bitmap.height.toFloat(), (boxes[4 * i + 3] - topPad) / scale)
                
                val labelIndex = labelsIdx[i].toInt()
                val labelName = if (labelIndex >= 0 && labelIndex < labels.size) labels[labelIndex] else "Unknown"
                
                results.add(
                    DetectionResult(
                        labelIndex = labelIndex,
                        labelName = labelName,
                        score = scores[i],
                        box = floatArrayOf(left, top, right, bottom)
                    )
                )
            }
        }

        return@withContext results
    }

    @Throws(IOException::class)
    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName.split("/").last())
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
        }
        return file.absolutePath
    }
}
