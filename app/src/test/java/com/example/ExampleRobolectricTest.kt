package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.example.data.ExifExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ExifLens", appName)
  }

  @Test
  fun `extract exif metadata from jpeg file`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempFile = File(context.cacheDir, "sample_exif_test.jpg")
    val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
    FileOutputStream(tempFile).use { out ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }

    val exif = ExifInterface(tempFile.absolutePath)
    exif.setAttribute(ExifInterface.TAG_MAKE, "Fujifilm")
    exif.setAttribute(ExifInterface.TAG_MODEL, "X-T5")
    exif.setAttribute(ExifInterface.TAG_LENS_MODEL, "XF 23mm F1.4 R LM WR")
    exif.setAttribute(ExifInterface.TAG_F_NUMBER, "2.8")
    exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "0.004")
    exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "320")
    exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "23/1")
    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:15 14:22:10")
    exif.setLatLong(37.5665, 126.9780)
    exif.saveAttributes()

    val extractor = ExifExtractor(context)
    val result = extractor.extract(Uri.fromFile(tempFile))

    assertTrue(result.isSuccess)
    val report = result.getOrThrow()
    assertEquals("Fujifilm X-T5", report.cameraSummary.displayDeviceTitle)
    assertEquals("XF 23mm F1.4 R LM WR", report.cameraSummary.displayLensTitle)
    assertEquals("f/2.8", report.exposureSummary.apertureDisplay)
    assertEquals("1/250s", report.exposureSummary.shutterSpeedDisplay)
    assertEquals("ISO 320", report.exposureSummary.isoDisplay)
    assertEquals("23mm", report.exposureSummary.focalLengthDisplay)
    assertEquals("2026.10.15 14:22:10", report.capturedDateDisplay)
    assertNotNull(report.locationSummary)
    assertEquals(37.5665, report.locationSummary!!.latitude, 0.001)
    assertEquals(126.9780, report.locationSummary!!.longitude, 0.001)
    assertTrue(report.rawTags.isNotEmpty())

    tempFile.delete()
  }
}
