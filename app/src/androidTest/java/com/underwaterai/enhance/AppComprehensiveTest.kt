package com.underwaterai.enhance

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.underwaterai.enhance.model.ObjectDetector
import com.underwaterai.enhance.model.ImageClassifier
import com.underwaterai.enhance.model.ImageEnhancer
import com.underwaterai.enhance.model.ModelType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.pytorch.IValue
import org.pytorch.Tensor
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

@RunWith(AndroidJUnit4::class)
class AppComprehensiveTest {

    @Test
    fun testModelsLoading() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        val detector = ObjectDetector(context)
        detector.loadModelIfNeeded()
        
        val classifier = ImageClassifier(context)
        classifier.loadModelIfNeeded()
        
        // If no exception, models loaded successfully.
        assertTrue(true)
    }

    @Test
    fun testObjectDetector() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = ObjectDetector(context)
        detector.loadModelIfNeeded()

        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val detections = detector.detect(bitmap, 0.1f)
        
        assertNotNull(detections)
    }

    @Test
    fun testImageClassifier() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val classifier = ImageClassifier(context)
        classifier.loadModelIfNeeded()

        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val matches = classifier.classify(bitmap)
        
        assertNotNull(matches)
        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun testDeviceConcurrencyBoundaries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = ObjectDetector(context)
        detector.loadModelIfNeeded()
        
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        
        // We verify that the model does not crash when called from a single-thread executor
        // which represents the new architectural fix for POCO threading.
        val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        withContext(singleThreadDispatcher) {
            val results = detector.detect(bitmap, 0.5f)
            assertNotNull(results)
        }
    }
}
