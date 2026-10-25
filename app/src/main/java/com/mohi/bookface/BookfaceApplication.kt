package com.mohi.bookface

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import java.io.File
import java.util.concurrent.Executor

class BookfaceApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    purgeInvalidVariationsSeeds(this)
  }

  override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
    if (isVariationsSeedService(intent)) {
      // Suppress binding to VariationsSeedServer which supplies unsigned seeds on virtualized devices
      return true
    }
    return super.bindService(intent, conn, flags)
  }

  override fun bindService(
    intent: Intent,
    flags: Int,
    executor: Executor,
    conn: ServiceConnection
  ): Boolean {
    if (isVariationsSeedService(intent)) {
      return true
    }
    return super.bindService(intent, flags, executor, conn)
  }

  private fun isVariationsSeedService(intent: Intent?): Boolean {
    val className = intent?.component?.className ?: return false
    return className.contains("VariationsSeedServer", ignoreCase = true) ||
      className.contains("Variations", ignoreCase = true)
  }

  companion object {
    fun purgeInvalidVariationsSeeds(context: Context) {
      try {
        val webviewDir = context.getDir("webview", Context.MODE_PRIVATE)
        val seedFiles = listOf(
          "variations_seed",
          "variations_seed_new",
          "variations_stamp"
        )
        for (fileName in seedFiles) {
          val file = File(webviewDir, fileName)
          if (file.exists()) {
            file.delete()
          }
        }
      } catch (_: Exception) {
        // Ignore file cleanup exceptions
      }
    }
  }
}
