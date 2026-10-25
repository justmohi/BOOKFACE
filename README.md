# Bookface

**Bookface** is a native Android application built with Kotlin, Android WebView, and XML layouts. It opens Facebook's mobile website (`https://m.facebook.com/`) inside an interface styled in a modern dark theme with vibrant orange accents, protected by a best-effort ad and tracker filtering system.

---

## 1. Project Objective & Architecture

- **Platform**: Native Android SDK (Min SDK: 24 / Android 7.0, Target SDK: 36 / Android 15)
- **Language**: Kotlin 2.2.10
- **UI Architecture**: Traditional Android XML Layouts with ViewBinding
- **Theme**: Dark theme with orange accent colors (`#FF6D00`)
- **Package**: `com.mohi.bookface`
- **Application ID**: `com.mohi.bookface`

### Key Highlights
- **Zero Third-Party Black-Box Dependencies**: Ad and tracker filtering is implemented locally in `FilterEngine.kt` using standard `WebViewClient.shouldInterceptRequest`.
- **Zero Injected Unsafe Web Bridges**: No arbitrary JavaScript bridges are exposed to untrusted web content.
- **Strict Least-Privilege Permissions**: Only `INTERNET` and `ACCESS_NETWORK_STATE` are declared. File access (`allowFileAccess = false`) and content access are disabled.
- **SSL Security**: Full SSL certificate verification is preserved (no bypasses).
- **Privacy By Design**: Passwords, credentials, and cookies are never inspected or logged. Third-party tracking cookies are disabled via `CookieManager`.

---

## 2. Project Directory Structure

```
.
├── .env.example
├── .gitignore
├── README.md
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── metadata.json
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/
        │   │   └── com/
        │   │       └── mohi/
        │   │           └── bookface/
        │   │               ├── MainActivity.kt
        │   │               ├── SettingsActivity.kt
        │   │               ├── data/
        │   │               │   └── FilterPreferences.kt
        │   │               └── filter/
        │   │                   └── FilterEngine.kt
        │   └── res/
        │       ├── drawable/
        │       │   ├── bg_badge.xml
        │       │   ├── bg_orange_button.xml
        │       │   ├── bg_rounded_card.xml
        │       │   ├── ic_arrow_back.xml
        │       │   ├── ic_cloud_off.xml
        │       │   ├── ic_delete.xml
        │       │   ├── ic_launcher_background.xml
        │       │   ├── ic_launcher_foreground.xml
        │       │   ├── ic_refresh.xml
        │       │   ├── ic_settings.xml
        │       │   └── ic_shield.xml
        │       ├── layout/
        │       │   ├── activity_main.xml
        │       │   └── activity_settings.xml
        │       ├── values/
        │       │   ├── colors.xml
        │       │   ├── strings.xml
        │       │   └── themes.xml
        │       └── xml/
        │           ├── backup_rules.xml
        │           └── data_extraction_rules.xml
        ├── test/
        │   └── java/
        │       └── com/
        │           └── mohi/
        │               └── bookface/
        │                   ├── ExampleRobolectricTest.kt
        │                   └── ExampleUnitTest.kt
        └── androidTest/
            └── java/
                └── com/
                    └── mohi/
                        └── bookface/
                            └── ExampleInstrumentedTest.kt
```

---

## 3. Best-Effort Filtering Engine & Rules

The request filtering component (`FilterEngine.kt`) intercepts network requests via `WebViewClient.shouldInterceptRequest`:

### Rules Applied When Filtering is Enabled
1. **Facebook Pixel Beacon (`/tr/`)**: Neutralizes pixel trackers used for cross-site attribution.
2. **Facebook Telemetry Beacon (`/ajax/bz`)**: Neutralizes background interaction and scroll telemetry pings.
3. **Attribution Reporting (`/privacy_sandbox/`)**: Neutralizes ad conversion tracking endpoints.
4. **Client Event Logging (`/logging_client_events`)**: Intercepts background client logging.
5. **Tracking Scripts (`fbevents.js`, `beacon.js`)**: Blocks execution of tracking loader scripts.
6. **Third-Party Ad Networks**: Blocks known ad exchanges (`doubleclick.net`, `google-analytics.com`, `googlesyndication.com`, `taboola.com`, `outbrain.com`, `criteo.com`, `appsflyer.com`, `adjust.com`).

### Essential Protected Routes (Never Blocked)
- **Authentication**: `/login`, `/checkpoint`, `/recover`, `/security`, `/auth`, `/two_factor`, `/sessions`.
- **Media CDN Delivery**: `*.fbcdn.net` containing `video`, `scontent`, or `static` markers to guarantee uninterrupted video playback and photo feeds.

### Filtering Statistics & Counters
- **Blocked Requests**: Count of requests matching blocked rules.
- **Total Observed Requests**: Count of total requests inspected by `shouldInterceptRequest`.
- **Interception Rate**: Percentage ratio of blocked to total requests.
- **Reset**: Accessible via the Settings screen with a single tap.

---

## 4. Known Technical Limitations & Honest Disclaimers

1. **First-Party Video Ads**: Facebook serves video advertisements from the exact same Content Delivery Network (`fbcdn.net`) servers and streaming segments as ordinary user videos. These cannot be distinguished at the network level without breaking genuine video streams.
2. **Blocked Request Counter Definition**: The blocked counter represents requests matched by the defined filter rules, not verified individual advertisement creatives.
3. **Service Workers**: Some modern mobile web apps register service workers that handle fetch requests internally without passing through standard `shouldInterceptRequest` hooks.
4. **WebSocket & Encrypted Payloads**: Telemetry batched inside encrypted WebSocket streams or bundled JSON payloads cannot be intercepted by URL-based filtering.

---

## 5. Build, Installation, and Test Instructions

### Android Studio Build Instructions
1. Open **Android Studio** (Giraffe, Hedgehog, Iguana, Ladybug, or newer).
2. Select **File > Open...** and navigate to this project's root directory.
3. Allow Gradle to synchronize dependencies.
4. Select build variant `debug` in the Build Variants tool window.
5. Click **Run > Run 'app'** (or press Shift+F10) targeting a connected device or emulator (Android 7.0+ / API 24+).

### Generating a Debug APK via Command Line
Run the standard Gradle assemble task from the project root:
```bash
gradle :app:assembleDebug
```
The resulting APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Running Local Unit and Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

---

## 6. Functional Test Checklist

| # | Test Scenario | Steps | Expected Result |
|---|---------------|-------|-----------------|
| 1 | **App Startup** | Launch Bookface from launcher | Dark theme toolbar loads with orange "Bookface" title, shield icon, and mobile Facebook login page renders. |
| 2 | **Facebook Login** | Enter test credentials or browse public pages | Login inputs and security checkpoint flows operate normally without being blocked. |
| 3 | **Video Playback** | Open a Facebook video or Reel | Video streams from `fbcdn.net` load and play smoothly without interruption. |
| 4 | **Back Navigation** | Navigate forward 3 pages, press device Back button | WebView navigates backward through browser history; exits app only from the root page. |
| 5 | **Network Error & Fallback** | Toggle Airplane Mode, tap Refresh | Clean error card displays with "Retry" and "Open Facebook Basic" buttons. |
| 6 | **Filtering Toggle** | Open Settings, switch filter OFF | Toolbar status changes to "Shield Paused", requests pass through without interception. |
| 7 | **Statistics Reset** | In Settings, tap the trash can icon | Counters reset to 0 and toast notification confirms reset. |
