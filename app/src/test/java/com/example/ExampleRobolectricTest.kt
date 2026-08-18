package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.formatDuration
import com.example.model.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Video Compositor", appName)
  }

  @Test
  fun `test format duration`() {
    assertEquals("00:00", formatDuration(0L))
    assertEquals("00:05", formatDuration(5000L))
    assertEquals("01:30", formatDuration(90000L))
  }

  @Test
  fun `test format file size`() {
    assertEquals("0 B", formatFileSize(0L))
    assertEquals("1.0 MB", formatFileSize(1024 * 1024L))
  }
}

