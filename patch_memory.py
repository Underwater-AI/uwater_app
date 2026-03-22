import re

file_path = "app/src/main/java/com/underwaterai/enhance/model/ImageEnhancer.kt"
with open(file_path, "r") as f:
    text = f.read()

# Replace bitmapToTensor
old_b2t = """    private fun bitmapToTensor(bitmap: Bitmap): Tensor {
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height
        val pixels = IntArray(pixelCount)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Build planar [1, 3, H, W] float array directly.
        // Inline bit-shift extraction + reciprocal multiply for throughput.
        // Avoids 3 Color.red/green/blue method calls per pixel.
        val floats = FloatArray(3 * pixelCount)
        val inv255 = 1.0f / 255.0f
        val planeG = pixelCount
        val planeB = pixelCount shl 1
        for (i in 0 until pixelCount) {
            val px = pixels[i]
            floats[i]          = ((px shr 16) and 0xFF) * inv255  // R plane
            floats[planeG + i] = ((px shr 8)  and 0xFF) * inv255  // G plane
            floats[planeB + i] = ( px          and 0xFF) * inv255  // B plane
        }

        return Tensor.fromBlob(floats, longArrayOf(1, 3, height.toLong(), width.toLong()))
    }"""

new_b2t = """    private fun bitmapToTensor(bitmap: Bitmap): Tensor {
        val width = bitmap.width
        val height = bitmap.height
        val pixelCount = width * height
        
        val floats = FloatArray(3 * pixelCount)
        val inv255 = 1.0f / 255.0f
        val planeG = pixelCount
        val planeB = pixelCount shl 1
        
        val rowPixels = IntArray(width)
        var offset = 0
        for (y in 0 until height) {
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val px = rowPixels[x]
                floats[offset]          = ((px shr 16) and 0xFF) * inv255  // R plane
                floats[planeG + offset] = ((px shr 8)  and 0xFF) * inv255  // G plane
                floats[planeB + offset] = ( px          and 0xFF) * inv255  // B plane
                offset++
            }
        }

        return Tensor.fromBlob(floats, longArrayOf(1, 3, height.toLong(), width.toLong()))
    }"""

# Replace tensorToBitmap
old_t2b = """    private fun tensorToBitmap(tensor: Tensor): Bitmap {
        val shape = tensor.shape()
        // Handle both [1, 3, H, W] and [3, H, W]
        val channels: Int
        val height: Int
        val width: Int
        if (shape.size == 4) {
            channels = shape[1].toInt()
            height = shape[2].toInt()
            width = shape[3].toInt()
        } else if (shape.size == 3) {
            channels = shape[0].toInt()
            height = shape[1].toInt()
            width = shape[2].toInt()
        } else {
            throw RuntimeException("Unexpected output tensor rank: ${shape.size}, shape: [${shape.joinToString()}]")
        }

        val data = tensor.dataAsFloatArray
        val planeSize = height * width
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(planeSize)

        // Inline ARGB packing with proper rounding (+0.5f) for accuracy.
        // Avoids Color.argb() call overhead per pixel.
        val alphaFull = 0xFF shl 24
        if (channels >= 3) {
            val pG = planeSize
            val pB = planeSize shl 1
            for (i in 0 until planeSize) {
                val r = (data[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val g = (data[pG + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                val b = (data[pB + i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                pixels[i] = alphaFull or (r shl 16) or (g shl 8) or b
            }
        } else {
            // Fallback for grayscale
            for (i in 0 until planeSize) {
                val v = (data[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                pixels[i] = alphaFull or (v shl 16) or (v shl 8) or v
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }"""


new_t2b = """    private fun tensorToBitmap(tensor: Tensor): Bitmap {
        val shape = tensor.shape()
        // Handle both [1, 3, H, W] and [3, H, W]
        val channels: Int
        val height: Int
        val width: Int
        if (shape.size == 4) {
            channels = shape[1].toInt()
            height = shape[2].toInt()
            width = shape[3].toInt()
        } else if (shape.size == 3) {
            channels = shape[0].toInt()
            height = shape[1].toInt()
            width = shape[2].toInt()
        } else {
            throw RuntimeException("Unexpected output tensor rank: ${shape.size}, shape: [${shape.joinToString()}]")
        }

        val data = tensor.dataAsFloatArray
        val planeSize = height * width
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val alphaFull = 0xFF shl 24
        val rowPixels = IntArray(width)
        
        var offset = 0
        if (channels >= 3) {
            val pG = planeSize
            val pB = planeSize shl 1
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val r = (data[offset].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val g = (data[pG + offset].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val b = (data[pB + offset].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    rowPixels[x] = alphaFull or (r shl 16) or (g shl 8) or b
                    offset++
                }
                bitmap.setPixels(rowPixels, 0, width, 0, y, width, 1)
            }
        } else {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val v = (data[offset].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    rowPixels[x] = alphaFull or (v shl 16) or (v shl 8) or v
                    offset++
                }
                bitmap.setPixels(rowPixels, 0, width, 0, y, width, 1)
            }
        }
        return bitmap
    }"""
text = text.replace(old_b2t, new_b2t)
text = text.replace(old_t2b, new_t2b)

with open(file_path, "w") as f:
    f.write(text)
