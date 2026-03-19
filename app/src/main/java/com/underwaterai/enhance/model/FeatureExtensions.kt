package com.underwaterai.enhance.model

import android.graphics.Bitmap
import android.media.ExifInterface
import android.util.Log

object FeatureExtensions {

    /**
     * Feature 9: Salinity & Turbidity Analysis (Simulated via Color Channel Ratios)
     */
    fun analyzeWaterQuality(bitmap: Bitmap): String {
        // Simplified analysis: checking blue/green dominance which varies by water type
        var rTotal = 0L
        var gTotal = 0L
        var bTotal = 0L
        val step = 10 
        
        for (x in 0 until bitmap.width step step) {
            for (y in 0 until bitmap.height step step) {
                val pixel = bitmap.getPixel(x, y)
                rTotal += android.graphics.Color.red(pixel)
                gTotal += android.graphics.Color.green(pixel)
                bTotal += android.graphics.Color.blue(pixel)
            }
        }
        
        val total = Math.max(1L, rTotal + gTotal + bTotal)
        val bRatio = bTotal.toFloat() / total
        val gRatio = gTotal.toFloat() / total
        
        return when {
            bRatio > 0.5 -> "High Salinity / Oceanic (Clear, deep blue)"
            gRatio > 0.4 -> "Coastal/High Turbidity (Algae/Phytoplankton present)"
            else -> "Murky/Unknown Turbidity Mix"
        }
    }

    /**
     * Feature 14: GPS & Metadata Logging
     */
    fun writeMarineGPSMetadata(filePath: String, latitude: Double, longitude: Double) {
        try {
            val exif = ExifInterface(filePath)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, LocationUtils.convert(latitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, LocationUtils.convert(longitude))
            exif.saveAttributes()
        } catch (e: Exception) {
            Log.e("FeatureExtensions", "Failed to write EXIF data", e)
        }
    }

    /**
     * Feature 17: Invasive Species Alerts check
     */
    fun checkInvasiveSpecies(label: String): Boolean {
        val invasiveList = listOf("lionfish", "crown-of-thorns", "green crab", "zebra mussel")
        return invasiveList.any { label.contains(it, ignoreCase = true) }
    }
}

object LocationUtils {
    fun convert(latitude: Double): String {
        val alng = Math.abs(latitude)
        val deg = alng.toInt()
        val min = ((alng - deg) * 60).toInt()
        val sec = ((alng - deg - min / 60.0) * 3600 * 1000).toInt()
        return "$deg/1,$min/1,$sec/1000"
    }
}
