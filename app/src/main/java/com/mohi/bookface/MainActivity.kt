package com.mohi.bookface

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mohi.bookface.data.FilterPreferences
import com.mohi.bookface.databinding.ActivityMainBinding
import com.mohi.bookface.filter.FilterEngine
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {

  companion object {
    private const val DEFAULT_URL = "https://m.facebook.com/"
    private const val BASIC_FALLBACK_URL = "https://mbasic.facebook.com/"
    private const val STATE_KEY_WEBVIEW = "bookface_webview_state"
  }

  private lateinit var binding: ActivityMainBinding
  private lateinit var preferences: FilterPreferences
  private val filterEngine = FilterEngine()

  private var hasEncounteredError = false
  private val seenRequestsInPage = ConcurrentHashMap.newKeySet<String>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    BookfaceApplication.purgeInvalidVariationsSeeds(this)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    preferences = FilterPreferences.getInstance(this)

    setupToolbar()
    setupSwipeRefresh()
    setupErrorView()
    setupBackNavigation()
    setupWebView(savedInstanceState)
  }

  private fun setupToolbar() {
    setSupportActionBar(binding.mainToolbar)
    supportActionBar?.setDisplayShowTitleEnabled(false)

    binding.btnRefresh.setOnClickListener {
      hideError()
      binding.facebookWebView.reload()
    }

    binding.btnSettings.setOnClickListener {
      startActivity(Intent(this, SettingsActivity::class.java))
    }

    binding.btnShieldStats.setOnClickListener {
      startActivity(Intent(this, SettingsActivity::class.java))
    }

    updateToolbarBadge()
  }

  private fun updateToolbarBadge() {
    val isEnabled = preferences.isFilteringEnabled
    val blockedCount = preferences.blockedRequestsCount

    if (isEnabled) {
      binding.statusSubtitle.text = "Shield Active"
      binding.statusSubtitle.setTextColor(ContextCompat.getColor(this, R.color.bookface_shield_active))
      binding.ivShieldIcon.setColorFilter(ContextCompat.getColor(this, R.color.bookface_shield_active))
    } else {
      binding.statusSubtitle.text = "Shield Paused"
      binding.statusSubtitle.setTextColor(ContextCompat.getColor(this, R.color.bookface_text_secondary))
      binding.ivShieldIcon.setColorFilter(ContextCompat.getColor(this, R.color.bookface_shield_inactive))
    }

    if (blockedCount > 0L && isEnabled) {
      binding.tvBlockedBadge.visibility = View.VISIBLE
      binding.tvBlockedBadge.text = if (blockedCount > 99L) "99+" else blockedCount.toString()
    } else {
      binding.tvBlockedBadge.visibility = View.GONE
    }
  }

  private fun setupSwipeRefresh() {
    binding.swipeRefreshLayout.setColorSchemeColors(
      ContextCompat.getColor(this, R.color.bookface_orange)
    )
    binding.swipeRefreshLayout.setProgressBackgroundColorSchemeColor(
      ContextCompat.getColor(this, R.color.bookface_surface_elevated)
    )
    binding.swipeRefreshLayout.setOnRefreshListener {
      hideError()
      binding.facebookWebView.reload()
    }
  }

  private fun setupErrorView() {
    binding.btnRetry.setOnClickListener {
      hideError()
      binding.facebookWebView.reload()
    }

    binding.btnBasicFallback.setOnClickListener {
      hideError()
      binding.facebookWebView.loadUrl(BASIC_FALLBACK_URL)
    }
  }

  private fun setupBackNavigation() {
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (binding.facebookWebView.canGoBack()) {
          hideError()
          binding.facebookWebView.goBack()
        } else {
          isEnabled = false
          onBackPressedDispatcher.onBackPressed()
        }
      }
    })
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView(savedInstanceState: Bundle?) {
    val webView = binding.facebookWebView

    // Use software layer rendering to avoid MESA DRM rendernode errors on virtualized targets
    webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

    // Secure cookie management
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, false) // Block 3rd-party tracking cookies

    webView.settings.apply {
      // Required for modern Facebook web app
      javaScriptEnabled = true
      domStorageEnabled = true

      // Strict Security Constraints (No arbitrary file access)
      allowFileAccess = false
      allowContentAccess = false

      // Display & Responsive Viewport
      setSupportZoom(true)
      builtInZoomControls = true
      displayZoomControls = false
      useWideViewPort = true
      loadWithOverviewMode = true

      // Defer network image loading until initial page commit to guarantee first_paint <= first_image_paint
      loadsImagesAutomatically = true
      blockNetworkImage = false

      // Cache & Performance
      cacheMode = WebSettings.LOAD_DEFAULT
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }

    // Configure WebChromeClient for accurate loading progress
    webView.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        if (newProgress >= 40) {
          view?.settings?.blockNetworkImage = false
        }
        if (newProgress in 1..99) {
          binding.loadingProgressBar.visibility = View.VISIBLE
          binding.loadingProgressBar.progress = newProgress
        } else {
          binding.loadingProgressBar.visibility = View.GONE
          binding.swipeRefreshLayout.isRefreshing = false
        }
      }
    }

    // Configure WebViewClient with Best-Effort Filtering
    webView.webViewClient = object : WebViewClient() {
      override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
      ): WebResourceResponse? {
        if (request == null) return null
        val url = request.url.toString()

        val isNewRequest = seenRequestsInPage.add(url)
        if (isNewRequest) {
          preferences.incrementObserved()
        }

        if (preferences.isFilteringEnabled) {
          val filterResult = filterEngine.evaluateUrl(url)
          if (filterResult.shouldBlock) {
            if (isNewRequest) {
              preferences.incrementBlocked()
              runOnUiThread { updateToolbarBadge() }
            }

            // Return 204 No Content response so blocked pixels/resources do not trigger invalid image paint metrics
            return WebResourceResponse(
              "text/plain",
              "utf-8",
              204,
              "No Content",
              mapOf("Cache-Control" to "no-store"),
              ByteArrayInputStream(ByteArray(0))
            )
          }
        }

        return super.shouldInterceptRequest(view, request)
      }

      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        hasEncounteredError = false
        seenRequestsInPage.clear()
        // Defer image loads until first visual commit so first_paint precedes first_image_paint
        view?.settings?.blockNetworkImage = true
      }

      override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        // Main frame first_paint has occurred; allow images to load and paint
        view?.settings?.blockNetworkImage = false
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.settings?.blockNetworkImage = false
        binding.swipeRefreshLayout.isRefreshing = false
        if (!hasEncounteredError) {
          hideError()
        }
        updateToolbarBadge()
      }

      override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
      ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
          view?.settings?.blockNetworkImage = false
          hasEncounteredError = true
          val errorDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            error?.description?.toString() ?: getString(R.string.error_message)
          } else {
            getString(R.string.error_message)
          }
          showError(errorDesc)
        }
      }

      override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?
      ): Boolean {
        // Safely recover if the WebView rendering process terminates unexpectedly
        return true
      }

      override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
      ): Boolean {
        val url = request?.url?.toString() ?: return false

        // Safe external intent handler for dialer, email, sms
        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:")) {
          try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            return true
          } catch (_: Exception) {}
        }

        return false
      }
    }

    // Restore state or load default mobile Facebook URL
    if (savedInstanceState != null) {
      val webViewState = savedInstanceState.getBundle(STATE_KEY_WEBVIEW)
      if (webViewState != null) {
        webView.restoreState(webViewState)
      } else {
        webView.loadUrl(DEFAULT_URL)
      }
    } else {
      webView.loadUrl(DEFAULT_URL)
    }
  }

  private fun showError(message: String) {
    binding.swipeRefreshLayout.isRefreshing = false
    binding.loadingProgressBar.visibility = View.GONE
    binding.tvErrorMessage.text = message
    binding.errorViewContainer.visibility = View.VISIBLE
    binding.swipeRefreshLayout.visibility = View.GONE
  }

  private fun hideError() {
    binding.errorViewContainer.visibility = View.GONE
    binding.swipeRefreshLayout.visibility = View.VISIBLE
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    val webViewState = Bundle()
    binding.facebookWebView.saveState(webViewState)
    outState.putBundle(STATE_KEY_WEBVIEW, webViewState)
  }

  override fun onResume() {
    super.onResume()
    binding.facebookWebView.onResume()
    updateToolbarBadge()
  }

  override fun onPause() {
    binding.facebookWebView.onPause()
    preferences.syncCountsToDisk()
    super.onPause()
  }

  override fun onDestroy() {
    preferences.syncCountsToDisk()
    binding.facebookWebView.stopLoading()
    binding.facebookWebView.destroy()
    super.onDestroy()
  }
}
