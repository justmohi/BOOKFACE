package com.mohi.bookface.data

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicLong

class FilterPreferences private constructor(context: Context) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("bookface_filter_prefs", Context.MODE_PRIVATE)

  private val cachedBlocked = AtomicLong(prefs.getLong(KEY_BLOCKED_REQUESTS, 0L))
  private val cachedObserved = AtomicLong(prefs.getLong(KEY_OBSERVED_REQUESTS, 0L))

  companion object {
    private const val KEY_FILTERING_ENABLED = "filtering_enabled"
    private const val KEY_BLOCKED_REQUESTS = "blocked_requests_count"
    private const val KEY_OBSERVED_REQUESTS = "observed_requests_count"

    @Volatile
    private var INSTANCE: FilterPreferences? = null

    fun getInstance(context: Context): FilterPreferences {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: FilterPreferences(context).also { INSTANCE = it }
      }
    }
  }

  var isFilteringEnabled: Boolean
    get() = prefs.getBoolean(KEY_FILTERING_ENABLED, true)
    set(value) {
      prefs.edit().putBoolean(KEY_FILTERING_ENABLED, value).apply()
    }

  val blockedRequestsCount: Long
    get() = cachedBlocked.get()

  val totalObservedRequestsCount: Long
    get() = cachedObserved.get()

  fun incrementObserved() {
    val newCount = cachedObserved.incrementAndGet()
    if (newCount % 20L == 0L) { // Batch disk writes to reduce I/O overhead
      prefs.edit().putLong(KEY_OBSERVED_REQUESTS, newCount).apply()
    }
  }

  fun incrementBlocked() {
    val newCount = cachedBlocked.incrementAndGet()
    prefs.edit().putLong(KEY_BLOCKED_REQUESTS, newCount).apply()
  }

  fun syncCountsToDisk() {
    prefs.edit()
      .putLong(KEY_BLOCKED_REQUESTS, cachedBlocked.get())
      .putLong(KEY_OBSERVED_REQUESTS, cachedObserved.get())
      .apply()
  }

  fun resetStatistics() {
    cachedBlocked.set(0L)
    cachedObserved.set(0L)
    prefs.edit()
      .putLong(KEY_BLOCKED_REQUESTS, 0L)
      .putLong(KEY_OBSERVED_REQUESTS, 0L)
      .apply()
  }
}
