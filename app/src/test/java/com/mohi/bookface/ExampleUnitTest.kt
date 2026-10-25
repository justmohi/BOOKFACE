package com.mohi.bookface

import com.mohi.bookface.filter.FilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  private val filterEngine = FilterEngine()

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun filterEngine_blocksFacebookPixel() {
    val result = filterEngine.evaluateUrl("https://www.facebook.com/tr/?id=123456789&ev=PageView")
    assertNotNull(result)
    assertTrue(result.shouldBlock)
    assertEquals("FB Telemetry", result.ruleName)
  }

  @Test
  fun filterEngine_blocksKnownAdNetworks() {
    val result = filterEngine.evaluateUrl("https://pagead2.googlesyndication.com/pagead/js/ads.js")
    assertNotNull(result)
    assertTrue(result.shouldBlock)
    assertEquals("Third-Party Ad/Tracker", result.ruleName)
  }

  @Test
  fun filterEngine_preservesLoginAndAuth() {
    val result = filterEngine.evaluateUrl("https://m.facebook.com/login/device-based/regular/login/")
    assertNotNull(result)
    assertFalse(result.shouldBlock)
  }

  @Test
  fun filterEngine_preservesMediaAndVideoStreamDelivery() {
    val result = filterEngine.evaluateUrl("https://video.xx.fbcdn.net/v/t39.1234/stream.mp4")
    assertNotNull(result)
    assertFalse(result.shouldBlock)
  }

  @Test
  fun filterEngine_allowsNormalNavigation() {
    val result = filterEngine.evaluateUrl("https://m.facebook.com/messages")
    assertNotNull(result)
    assertFalse(result.shouldBlock)
  }
}
