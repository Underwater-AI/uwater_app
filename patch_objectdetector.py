import re

with open('app/src/main/java/com/underwaterai/enhance/model/ObjectDetector.kt', 'r') as f:
    content = f.read()

# Add kotlinx.coroutines.Dispatchers and withContext
if 'import kotlinx.coroutines.Dispatchers' not in content:
    content = content.replace('import org.pytorch.IValue', 'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\nimport org.pytorch.IValue')

# Change detect to suspend
old_detect = r'fun detect\(bitmap: Bitmap, threshold: Float = 0.5f\): List<DetectionResult> \{'
new_detect = r'suspend fun detect(bitmap: Bitmap, threshold: Float = 0.5f): List<DetectionResult> = withContext(Dispatchers.Default) {'

content = re.sub(old_detect, new_detect, content)

old_body = r"""        val mod = module \?\: throw IllegalStateException\("Model is not loaded"\)

        // Resize to 320x320 as expected by SSDLite
        val resizedBitmap = Bitmap\.createScaledBitmap\(bitmap, 320, 320, true\)

        val inputTensor = TensorImageUtils\.bitmapToFloat32Tensor\(
            resizedBitmap,
            TensorImageUtils\.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils\.TORCHVISION_NORM_STD_RGB
        \)"""

new_body = r"""        val mod = module ?: throw IllegalStateException("Model is not loaded")

        // Resize retaining aspect ratio
        val targetSize = 320
        val scale = Math.min(targetSize.toFloat() / bitmap.width, targetSize.toFloat() / bitmap.height)
        val scaledWidth = Math.max(1, Math.round(bitmap.width * scale))
        val scaledHeight = Math.max(1, Math.round(bitmap.height * scale))
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val paddedBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(paddedBitmap)
        val leftPad = (targetSize - scaledWidth) / 2f
        val topPad = (targetSize - scaledHeight) / 2f
        canvas.drawBitmap(resizedBitmap, leftPad, topPad, null)

        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            paddedBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )"""

content = re.sub(old_body, new_body, content)

old_loop_prep = r"""        // Output boxes are for 320x320. We need to scale them back to original 
bitmap size.                                                                            val scaleX = bitmap.width.toFloat\(\) / 320f
        val scaleY = bitmap.height.toFloat\(\) / 320f

        for \(i in scores.indices\) \{
            if \(scores\[i\] >= threshold\) \{
                val left = boxes\[4 \* i\] \* scaleX
                val top = boxes\[4 \* i \+ 1\] \* scaleY
                val right = boxes\[4 \* i \+ 2\] \* scaleX
                val bottom = boxes\[4 \* i \+ 3\] \* scaleY"""

# Note, the scaling back logic has to account for the padding and the uniform scale!
new_loop_prep = r"""        for (i in scores.indices) {
            if (scores[i] >= threshold) {
                val left = Math.max(0f, (boxes[4 * i] - leftPad) / scale)
                val top = Math.max(0f, (boxes[4 * i + 1] - topPad) / scale)
                val right = Math.min(bitmap.width.toFloat(), (boxes[4 * i + 2] - leftPad) / scale)
                val bottom = Math.min(bitmap.height.toFloat(), (boxes[4 * i + 3] - topPad) / scale)"""

# In the actual string, there are weird newlines and wraps from ad-hoc copying.
