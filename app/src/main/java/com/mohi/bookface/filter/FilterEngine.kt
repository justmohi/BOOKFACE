package com.mohi.bookface.filter

import java.net.URI
import java.util.Locale

/**
 * FilterResult encapsulates the evaluation verdict for an intercepted web request.
 */
data class FilterResult(
  val shouldBlock: Boolean,
  val ruleName: String? = null,
  val description: String? = null
)

/**
 * Best-effort request filtering engine.
 *
 * Designed with conservative, maintainable rules that specifically target
 * telemetry, conversion pixels, and third-party tracking beacons while
 * strictly preserving core Facebook functionality, login flows, and video playback.
 */
class FilterEngine {

  companion object {
    /**
     * Essential authentication, security, and account checkpoint paths.
     * These are explicitly whitelisted and NEVER blocked under any circumstance.
     */
    private val ESSENTIAL_AUTH_PATHS = listOf(
      "/login",
      "/checkpoint",
      "/recover",
      "/security",
      "/auth",
      "/two_step_verification",
      "/two_factor",
      "/sessions"
    )

    /**
     * Essential video and media delivery subdomains.
     * Requests to these domains are preserved to ensure unbroken video and photo streaming.
     */
    private val MEDIA_CDN_MARKERS = listOf(
      "video",
      "scontent",
      "static"
    )

    /**
     * Specific Facebook telemetry, pixel, and conversion tracking endpoints.
     * Rule 1: /tr/ -> Facebook Pixel beacon for cross-site attribution.
     * Rule 2: /ajax/bz -> Telemetry and user event logging beacon.
     * Rule 3: /privacy_sandbox/ -> Attribution reporting API endpoints.
     * Rule 4: /logging_client_events -> Interaction logger.
     * Rule 5: /signals/ -> Conversion signal telemetry.
     */
    private val FB_TELEMETRY_PREFIXES = listOf(
      "/tr/" to "Facebook Pixel Beacon (/tr/)",
      "/ajax/bz" to "Facebook Telemetry Beacon (/ajax/bz)",
      "/privacy_sandbox/" to "Privacy Sandbox Telemetry",
      "/logging_client_events" to "Client Event Logging",
      "/signals/" to "Conversion Signals API",
      "/network_ads_common" to "Audience Network Common Broker"
    )

    /**
     * Known tracking and ad-attribution script filenames.
     * Rule: fbevents.js -> Pixel loader script.
     * Rule: beacon.js -> General telemetry beacon.
     */
    private val TRACKING_SCRIPTS = listOf(
      "fbevents.js" to "Facebook Pixel Script (fbevents.js)",
      "beacon.js" to "Telemetry Beacon Script"
    )

    /**
     * Known third-party ad and analytics networks.
     * Verified domains commonly embedded by third-party web content.
     */
    private val THIRD_PARTY_AD_HOSTS = hashSetOf(
      "doubleclick.net",
      "google-analytics.com",
      "googlesyndication.com",
      "pagead2.googlesyndication.com",
      "scorecardresearch.com",
      "taboola.com",
      "outbrain.com",
      "criteo.com",
      "appsflyer.com",
      "adjust.com",
      "hotjar.com",
      "quantserve.com",
      "adroll.com"
    )
  }

  /**
   * Evaluates an intercepted URL and decides whether it should be blocked.
   *
   * @param url The intercepted request URL
   * @return FilterResult containing whether to block and the matching rule description.
   */
  fun evaluateUrl(url: String): FilterResult {
    if (url.isBlank()) {
      return FilterResult(shouldBlock = false)
    }

    val (host, path, _) = extractUrlParts(url)
    if (host == null) {
      return FilterResult(shouldBlock = false)
    }

    // SAFETY CHECK 1: Never block authentication or login checkpoints
    for (authPath in ESSENTIAL_AUTH_PATHS) {
      if (path.startsWith(authPath)) {
        return FilterResult(shouldBlock = false, description = "Essential Authentication Flow")
      }
    }

    // SAFETY CHECK 2: Protect media CDN hosts (video streaming & photos)
    if (host.endsWith("fbcdn.net")) {
      for (marker in MEDIA_CDN_MARKERS) {
        if (host.contains(marker)) {
          return FilterResult(shouldBlock = false, description = "Media CDN Stream")
        }
      }
    }

    // RULE SET 1: Third-party ad and tracker domain filtering
    for (blockedHost in THIRD_PARTY_AD_HOSTS) {
      if (host == blockedHost || host.endsWith(".$blockedHost")) {
        return FilterResult(
          shouldBlock = true,
          ruleName = "Third-Party Ad/Tracker",
          description = "Matched $blockedHost"
        )
      }
    }

    // RULE SET 2: Facebook-specific telemetry and pixel endpoints
    if (host.contains("facebook.com") || host.contains("connect.facebook.net")) {
      for ((prefix, desc) in FB_TELEMETRY_PREFIXES) {
        if (path.startsWith(prefix) || path.contains(prefix)) {
          return FilterResult(
            shouldBlock = true,
            ruleName = "FB Telemetry",
            description = desc
          )
        }
      }

      for ((script, desc) in TRACKING_SCRIPTS) {
        if (path.contains(script)) {
          return FilterResult(
            shouldBlock = true,
            ruleName = "Tracking Script",
            description = desc
          )
        }
      }
    }

    return FilterResult(shouldBlock = false)
  }

  private fun extractUrlParts(url: String): Triple<String?, String, String> {
    return try {
      val parsed = URI(url)
      val host = parsed.host?.lowercase(Locale.ROOT)
      val path = parsed.path?.lowercase(Locale.ROOT) ?: ""
      val query = parsed.query?.lowercase(Locale.ROOT) ?: ""
      Triple(host, path, query)
    } catch (_: Exception) {
      // Robust string-based fallback parser for non-standard URI schemes
      val withoutProtocol = if (url.contains("://")) url.substringAfter("://") else url
      val hostPart = withoutProtocol.substringBefore("/").substringBefore("?").substringBefore(":").lowercase(Locale.ROOT)
      val afterHost = if (withoutProtocol.contains("/")) withoutProtocol.substringAfter("/") else ""
      val pathPart = if (afterHost.isNotEmpty()) "/" + afterHost.substringBefore("?").lowercase(Locale.ROOT) else ""
      val queryPart = if (url.contains("?")) url.substringAfter("?").lowercase(Locale.ROOT) else ""
      Triple(hostPart.ifBlank { null }, pathPart, queryPart)
    }
  }
}
