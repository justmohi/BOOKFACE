package com.mohi.bookface

import android.os.Bundle
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mohi.bookface.data.FilterPreferences
import com.mohi.bookface.databinding.ActivitySettingsBinding
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

  private lateinit var binding: ActivitySettingsBinding
  private lateinit var preferences: FilterPreferences

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivitySettingsBinding.inflate(layoutInflater)
    setContentView(binding.root)

    preferences = FilterPreferences.getInstance(this)

    setupToolbar()
    setupFilterSwitch()
    updateStatisticsUI()
    setupActionButtons()
  }

  private fun setupToolbar() {
    setSupportActionBar(binding.settingsToolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    supportActionBar?.setDisplayShowHomeEnabled(true)
    binding.settingsToolbar.setNavigationOnClickListener {
      finish()
    }
  }

  private fun setupFilterSwitch() {
    val isEnabled = preferences.isFilteringEnabled
    binding.switchFilter.isChecked = isEnabled
    updateProtectionStatus(isEnabled)

    binding.switchFilter.setOnCheckedChangeListener { _, isChecked ->
      preferences.isFilteringEnabled = isChecked
      updateProtectionStatus(isChecked)
    }
  }

  private fun updateProtectionStatus(isActive: Boolean) {
    if (isActive) {
      binding.tvProtectionStatus.text = "Protection Active"
      binding.tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.bookface_shield_active))
      binding.tvProtectionStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_shield, 0, 0, 0)
    } else {
      binding.tvProtectionStatus.text = "Protection Paused"
      binding.tvProtectionStatus.setTextColor(ContextCompat.getColor(this, R.color.bookface_text_secondary))
      binding.tvProtectionStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_shield, 0, 0, 0)
    }
  }

  private fun updateStatisticsUI() {
    val blocked = preferences.blockedRequestsCount
    val observed = preferences.totalObservedRequestsCount

    binding.tvBlockedCount.text = String.format(Locale.US, "%,d", blocked)
    binding.tvObservedCount.text = String.format(Locale.US, "%,d", observed)

    val rate = if (observed > 0L) {
      (blocked.toDouble() / observed.toDouble()) * 100.0
    } else {
      0.0
    }
    binding.tvRatePercent.text = String.format(Locale.US, "%.1f%%", rate)
  }

  private fun setupActionButtons() {
    binding.btnClearStats.setOnClickListener {
      preferences.resetStatistics()
      updateStatisticsUI()
      Toast.makeText(this, getString(R.string.clear_stats_confirm), Toast.LENGTH_SHORT).show()
    }

    binding.btnClearCache.setOnClickListener {
      try {
        val tempWebView = WebView(this)
        tempWebView.clearCache(true)
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        Toast.makeText(this, "WebView cache and temporary data cleared", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {
        Toast.makeText(this, "Cleared temporary cache", Toast.LENGTH_SHORT).show()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    updateStatisticsUI()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }
}
