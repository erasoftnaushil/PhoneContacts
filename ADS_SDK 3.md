# JkAds SDK v1.0.8 — Developer Integration Guide

> **SDK:** `com.github.jksolapps:ad-sdk:1.0.8`  
> **Full Docs:** [AD_SDK_Doc_1.0.8.html](https://cdn.jkcdns.com/static/ad-sdk/AD_SDK_Doc_1.0.8.html)  
> **Quick-Integration Ref:** `AD_SDK_Doc_1.0.8_min.html`

---

## ⚠️ MANDATORY AGENT WORKFLOW (Two Stages — Do Not Skip)

When an AI agent or developer receives this file, it **MUST** follow exactly two stages:

### 🛑 STAGE 1 — Audit, Inventory & Plan (NO code changes yet)
1. Read the full official SDK doc before touching anything.
2. Inspect the **entire project**: Application class, Manifest, all Gradle files, all `strings.xml` (including flavor variants), every Activity, Fragment, Composable screen, XML layout, and any ad manager / wrapper classes.
3. **Trace every ad call site (The Load-and-Show Rule):**
   - For every ad, trace its lifecycle: `load trigger` → `display/show location` → `dismiss/fail handler`.
   - **Only count ads that are confirmed BOTH loaded AND shown to the user.**
   - Separate into two distinct groups:
     - **Active Placements:** Ads that are verified loaded and shown. (Migrate to `JkAds.*`)
     - **Unused Ad Requests & Dead Code:** Ads that are loaded/preloaded but never shown, unreachable ad calls, ghost layout containers, commented-out ad blocks, or obsolete legacy ad wrappers. (Flag for removal)
4. Produce the **Active Placement Inventory**, the **Unused Ad Removal List**, and the **Copy-Ready Live Ads IDs List** (simple `key = value` format starting with `app live id = ...` so the developer can copy all IDs directly without table formatting; see templates at the bottom).
5. Formulate the complete step-by-step migration plan per file.
6. **STOP. Present the plan to the developer and wait for approval.**  
   → **Do NOT change any code until the developer replies "Approved".**

### 🚀 STAGE 2 — Single Implementation Pass (only after approval)
1. Implement all changes across every active placement in one coordinated pass.
2. **Purge Unused Ad Code:** Safely delete all confirmed unused ad requests, ghost containers, and obsolete ad wrappers identified in Stage 1 without breaking app navigation or business logic.
3. Run `./gradlew compileDebugSources` and fix any errors.
4. Deliver the final verification report (detailing migrated placements AND pruned unused ad code).

---

## 📋 Table of Contents
1. [Core Rules & Guardrails](#1-core-rules--guardrails)
   - [1.1 Agent Implementation Guardrails & Edge Cases](#11-agent-implementation-guardrails--edge-cases)
2. [Placement Naming Convention](#2-placement-naming-convention)
3. [Audit Phase: Active Placement Extraction & Unused Ad Pruning](#3-audit-phase-active-placement-extraction--unused-ad-pruning)
   - [3.1 The Load-and-Show Rule (Active vs Unused)](#31-the-load-and-show-rule-active-vs-unused)
   - [3.2 Unused Ad Pruning Action Plan](#32-unused-ad-pruning-action-plan)
   - [3.3 Extraction Rules](#33-extraction-rules)
   - [3.4 Output — Copy-Ready Live Ads IDs List (`key = value`)](#34-output--copy-ready-live-ads-ids-list-key--value)
4. [Step 0: Gradle Setup](#4-step-0-gradle-setup)
5. [Step 1: Initialize (Application Class)](#5-step-1-initialize-application-class)
6. [Step 2: GDPR Consent](#6-step-2-gdpr-consent)
7. [Step 3: Screen Analytics (Compose only)](#7-step-3-screen-analytics-compose-only)
8. [Step 4: Banner Ads](#8-step-4-banner-ads)
9. [Step 5: Interstitial Ads](#9-step-5-interstitial-ads)
10. [Step 6: Rewarded Ads](#10-step-6-rewarded-ads)
11. [Step 7: App Open Ads](#11-step-7-app-open-ads)
12. [Step 8: Rewarded Interstitial Ads](#12-step-8-rewarded-interstitial-ads)
13. [Step 9: Native Ads](#13-step-9-native-ads)
14. [Step 10: Subscription / No-Ads Mode](#14-step-10-subscription--no-ads-mode)
   - [Centralized Preference / PreferenceHelper Pattern](#-centralized-preference--preferencehelper-pattern-wire-once-zero-per-screen-checks)
15. [API Cheat Sheet](#15-api-cheat-sheet)
16. [Stage 1 Output Template](#16-stage-1-output-template)
17. [Stage 2 Final Report Template](#17-stage-2-final-report-template)

---

## 1. Core Rules & Guardrails

| # | Rule |
| :---: | :--- |
| 1 | **Only count & migrate verified active ads.** An ad is only considered "active" if it is confirmed **both loaded AND shown** to the user. Every active ad must be migrated, never dropped. |
| 2 | **Never fabricate IDs or keys.** Extract real `ca-app-pub-.../.....` values from project code only. |
| 3 | **Never block navigation.** Both `onDismissed` and `onFailedToShow` must always continue the app flow. |
| 4 | **Grant reward only in `onRewarded`.** Do not grant on ad close alone. |
| 5 | **Exclude legacy GMA.** `play-services-ads` and `play-services-ads-lite` must be excluded to prevent DEX merge crashes. |
| 6 | **AdChoices is mandatory.** Every custom native layout must have `@id/jk_native_ad_choices` (XML) or `helpers.AdChoicesView()` (Compose). |
| 7 | **Ad badge is required.** Every custom native layout must include an "AD" badge: `@id/jk_native_ad_label` or `helpers.AdLabel()`. |
| 8 | **Preload size must match display size.** Mismatching sizes discards the cached banner and requests a fresh one. |
| 9 | **App Open Resume must NOT fire** during Splash, onboarding, permission, purchase, or payment screens. |
| 10 | **`JkAds.initialize()` must be called exactly once** in `Application.onCreate()`. |
| 11 | **Purge all unused ad requests & dead code.** Any ad request that is loaded/preloaded but never shown, any ghost ad container, or obsolete ad wrapper must be removed during migration. |
| 12 | **Centralize `JkAds.setSubscribed(...)` in Preference/Billing:** Wire once at the central preference helper or billing manager where premium status is stored/read. Never call `setSubscribed()` or check premium status manually across individual screens. |

### 1.1 Agent Implementation Guardrails & Edge Cases

When an AI agent or developer migrates an unfamiliar project, follow these **9 strict rules** for edge cases:

#### 1. Missing Custom `Application` Class
If the project does **not** have an `Application` subclass (`<application>` in `AndroidManifest.xml` lacks `android:name`):
- **Action:** Create `MainApplication.kt` (or `.java`), register it in `AndroidManifest.xml` (`android:name=".MainApplication"`), and place `JkAds.initialize()` inside its `onCreate()`. Never initialize inside an Activity.

#### 2. Gradle Repository Location (`settings.gradle` vs root `build.gradle`)
- **If the project uses `settings.gradle` / `settings.gradle.kts`** with `dependencyResolutionManagement`: Add JitPack, Pangle, and Mintegral repos inside `repositories { ... }` in `settings.gradle`.
- **If the project declares repos in root `build.gradle`** (`allprojects { repositories { ... } }`): Add the repos there. **Never add `dependencyResolutionManagement` to `settings.gradle` if root `build.gradle` already configures repos**, or Gradle will fail with a repository mode conflict error.

#### 3. Existing Custom `AdManager` or `AdsHelper` Wrappers
If the project already routes ads through a centralized singleton (e.g. `AdmobHelper`, `AdsManager`, `AdUtils`):
- **Action:** **Refactor the internal implementation of that helper class** to use `JkAds.*` instead of rewriting every Activity/Fragment call site. This keeps the migration diff minimal, avoids breaking call sites, and guarantees safety. For XML banner/native views, replace the XML tags with `JkAdsBannerView` / `JkAdsNativeView`.

#### 4. The 64-Character Hex Analytics Key (`setSecretKey`)
- Existing AdMob projects do **not** have this key.
- **Action:** Do **not** fail or halt migration searching for this key. Put `.setSecretKey("YOUR_64_CHARACTER_HEX_KEY")` in `AdConfig.Builder()` with a comment, and flag it in the Stage 1 Plan under **Remaining Actions for Developer**: *"Developer should supply the 64-character hex key from their team lead if CDO analytics are enabled, or omit this line if analytics are not used."*

#### 5. Splash Screen Ad Format (Never Alter Formats)
- If the app currently shows an **Interstitial** on Splash → migrate to `interstitial_splash` using `JkAds.loadAndShowInterstitial()`.
- If the app currently shows an **App Open ad** on Splash → migrate to `app_open_splash` using `JkAds.loadSplashAppOpenAd()`.
- **Rule:** Never replace an Interstitial with an App Open ad (or vice versa) unless the developer explicitly asks.

#### 6. App Open Resume Ad: Mandatory Exclusions
In `AppOpenResumeConfig.Builder()`, the agent **must inspect all project screens** and automatically add to `addExcludedActivities(...)` and `addExcludedScreens(...)`:
1. Splash, Welcome, Onboarding, and Tutorial screens.
2. Login, Signup, OTP, Forgot Password, and Auth screens.
3. Google Play Billing, Checkout, Paywall, Subscription, and VIP screens.
4. Runtime Permission activities / dialogs.

#### 7. Custom Native Layout: Optional vs Mandatory Views
- `@id/jk_native_ad_choices` is **MANDATORY** (SDK requires it).
- `@id/jk_native_ad_label` is **REQUIRED** by ad networks ("AD" badge).
- All other views (`@id/jk_native_icon`, `@id/jk_native_media_container`, `@id/jk_native_headline`, `@id/jk_native_body`, `@id/jk_native_cta`, `@id/jk_native_star_rating`) are **OPTIONAL**. Map only what exists in the app's existing design. **Never force UI elements that were not part of the designer's original layout.**

#### 8. Unused Ad Requests vs Active Ads (The Load-and-Show Rule)
- **Active Ad Rule:** Only count an ad placement as active if there is a verified call path that both loads and shows/displays the ad to the user.
- **Unused Ad Requests:** If an ad request exists (e.g., `InterstitialAd.load()`, `AdView.loadAd()`, `AdLoader.loadAd()`) but `show()` is never called, or the ad view is never attached to a visible layout, do NOT migrate it to JkAds. Mark it as **Unused Ad Request** in Stage 1 and delete the unused load call in Stage 2.
- **Dead Ad Code & Obsolete SDKs:** Remove abandoned ad helper classes, commented-out ad views, direct third-party ad network dependencies (e.g. legacy Unity/MoPub/Facebook direct SDK dependencies that are no longer used), and empty ad containers whose visibility was permanently GONE.

#### 9. Premium / Subscription Preference Wiring (`JkAds.setSubscribed`)
- **Locate Central Preference / Billing:** During the audit, identify how the project tracks premium status (e.g. `SharedPreferences`, `DataStore`, `AppPreferences`, `PrefUtils`, `BillingRepository`, or an `isPremium` variable).
- **Wire at the Central Preference:**
  1. **App Startup / Preference Load:** In `Application.onCreate()` or when the preference helper loads saved state, call `JkAds.setSubscribed(prefHelper.isPremiumUser)`.
  2. **Preference Setter / Purchase:** In the central method/setter where the premium preference is updated (e.g. `setPremium(true)` or in the billing purchase listener), immediately call `JkAds.setSubscribed(true)`. When subscription expires/revokes, call `JkAds.setSubscribed(false)`.
- **Zero Per-Screen Checks:** Individual Activities, Fragments, and Composables **must NOT** call `JkAds.setSubscribed()` and **must NOT** wrap ad calls with `if (!isPremium)`. `JkAds` maintains subscription state internally and automatically suppresses, cancels, and hides all ads app-wide once subscribed.

---

## 2. Placement Naming Convention

Every ad placement **must** use this naming pattern:
```
adformat_screen_action
```

| Prefix | Format |
| :--- | :--- |
| `banner_` | Adaptive, Standard, Large, MREC, Inline Adaptive, Fluid banners |
| `native_` | Native feed cards, dialogs, tool screens |
| `interstitial_` | Full-screen between screens / actions |
| `rewarded_` | User-initiated rewarded video |
| `rewarded_interstitial_` | Automatic rewarded interstitial (no opt-in) |
| `app_open_splash` | Manual cold-start splash App Open ad |
| `app_open_resume` | **Reserved.** Automatic foreground resume App Open ad |

**Examples:**
```
banner_home            banner_chat            banner_settings
native_home            native_detail          native_language
interstitial_splash    interstitial_feature   interstitial_back
rewarded_remove_ads    rewarded_coins
rewarded_interstitial_level_bonus
app_open_splash        app_open_resume
```

---

## 3. Audit Phase: Active Placement Extraction & Unused Ad Pruning

### Goal
1. Audit every ad call site to distinguish **Verified Active Ads** (loaded and shown) from **Unused / Dead Ad Requests** (loaded but never shown, ghost layouts, or obsolete wrappers).
2. Produce a **single clean table** of every confirmed active ad placement and its live AdMob Ad Unit ID for the **backend developer** to configure the server CDN JSON.
3. Formulate an **Unused Ad Pruning Plan** to cleanly remove all wasteful or dead ad code during Stage 2.

### 3.1 The Load-and-Show Rule (Active vs Unused)

To eliminate wasteful background network calls, battery drain, and ad network policy penalties, every ad call site in the project must be verified using the **Load-and-Show Rule**:

| Category | Criteria | Action Required |
| :--- | :--- | :--- |
| **Verified Active Ad** | Has an explicit trigger that calls `load()` AND a confirmed UI path that attaches/shows the ad to the user (`show()`, visible `AdView`, or RecyclerView item). | **Counted & Migrated** to `JkAds.*`. Included in the placement table for server CDN JSON. |
| **Unused Ad Request** | An ad is loaded or preloaded into memory (e.g., `InterstitialAd.load()` in `onCreate`/`onResume`), but `show()` is never called, or the view is never attached to a visible layout. | **Do NOT migrate.** Flag in Stage 1 and **DELETE the unused load request** in Stage 2. |
| **Ghost Ad Container** | An XML layout has an `<AdView>` or `<FrameLayout>` container that is permanently set to `android:visibility="gone"` or never populated by code. | **Do NOT migrate.** Remove the unused layout container. |
| **Dead Ad Helper / Wrapper** | Obsolete helper classes (e.g. `AdMobUtil`, `OldAdsManager`) or direct SDK calls to legacy networks (e.g. direct MoPub, Unity Ads, Facebook Audience Network direct) no longer in use. | **Do NOT migrate.** Delete the obsolete wrapper class and remove unused direct ad SDK dependencies. |
| **Commented / Dead Code** | Commented-out ad invocations, unreachable `if (false)` ad branches, or debug-only sandbox screens. | **Do NOT migrate.** Safely delete commented or dead ad blocks. |

### 3.2 Unused Ad Pruning Action Plan
During Stage 2 implementation, the agent/developer must:
1. **Preserve business logic & navigation:** When deleting an unused ad request, ensure flow callbacks, click listeners, and navigation continue seamlessly without waiting for ad callbacks.
2. **Remove dead imports:** Clean up unused imports of `com.google.android.gms.ads.*` or legacy third-party ad networks from files where unused ad calls were deleted.
3. **Purge obsolete dependencies:** If direct legacy ad network dependencies (e.g. direct Facebook Audience Network, Unity Ads, MoPub) are found in `app/build.gradle` and are no longer referenced, remove them.

### 3.3 Extraction Rules
- Extract only **real, active, production** IDs (`ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY`).
- Only include ads that are **confirmed both loaded and shown**. Skip all unused or dead placements.
- If an ID is from Firebase Remote Config or a backend API, note the **exact config key** and try to resolve its default value.
- If unresolvable, mark as `[UNRESOLVED — <reason>]`. **Never invent IDs.**
- Include the resource name (e.g. `@string/admob_banner_home_id`) alongside the resolved value.

### 3.4 Output — Copy-Ready Live Ads IDs List (`key = value`)
The agent fills this in during Stage 1 from verified active project code and manifest.

> ⚠️ **AGENT MANDATE — NO TABLES OR ASCII GRIDS FOR IDS:**  
> **DO NOT** output the ad IDs inside a table or ASCII box border. Tables make it difficult and tedious for developers to copy values cleanly.  
> Output them as a **clean, simple, line-by-line `key = value` text block inside a single code block**.  
> The first line **must** always be `app live id = ...` (from `AndroidManifest.xml` / `strings.xml`), followed by each verified active placement key.

```text
app live id = ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
banner_home = ca-app-pub-XXXXXXXXXXXXXXXX/1111111111
banner_language = ca-app-pub-XXXXXXXXXXXXXXXX/7554669611
interstitial_splash = ca-app-pub-XXXXXXXXXXXXXXXX/3333333333
interstitial_record_save = ca-app-pub-XXXXXXXXXXXXXXXX/9989261266
rewarded_coins = ca-app-pub-XXXXXXXXXXXXXXXX/4444444444
native_feed = ca-app-pub-XXXXXXXXXXXXXXXX/2222222222
app_open_resume = [UNRESOLVED — Firebase key: ad_app_open_id]
```

> **Developer Action:** Click the copy button on the code block above and send it directly to your backend developer to configure the server CDN JSON, or paste it directly into your remote config. Placement names match the SDK placement keys exactly.

---

## 4. Step 0: Gradle Setup

### `settings.gradle.kts` (Kotlin DSL)
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()

        // JitPack (private auth required)
        maven {
            url = uri("https://jitpack.io")
            credentials.username = providers.gradleProperty("adsAuthToken").orNull ?: ""
        }
        // Pangle network
        maven { url = uri("https://artifact.bytedance.com/repository/pangle/") }
        // Mintegral network
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
    }
}
```

### `settings.gradle` (Groovy DSL)
```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()

        // JitPack (private auth required)
        maven {
            url 'https://jitpack.io'
            credentials {
                username = settings.ext.has("adsAuthToken") ? settings.ext.get("adsAuthToken") : (providers.gradleProperty("adsAuthToken").orNull ?: "")
            }
        }
        // Pangle network
        maven { url 'https://artifact.bytedance.com/repository/pangle/' }
        // Mintegral network
        maven { url 'https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea' }
    }
}
```

### `gradle.properties`
```properties
# JitPack auth token for JkAds SDK
adsAuthToken=jp_5nsliunsllbj0inusdve3pe54q
```

### `app/build.gradle.kts` (Kotlin DSL)
> ⚠️ This exclusion is **mandatory**. Without it, the build fails with duplicate class errors (Next-Gen GMA vs legacy GMA).

```kotlin
// Exclude legacy GMA to prevent duplicate class merge errors
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}

dependencies {
    implementation("com.github.jksolapps:ad-sdk:1.0.8")
}
```

### `app/build.gradle` (Groovy DSL)
```groovy
// Exclude legacy GMA to prevent duplicate class merge errors
configurations.configureEach {
    exclude group: "com.google.android.gms", module: "play-services-ads"
    exclude group: "com.google.android.gms", module: "play-services-ads-lite"
}

dependencies {
    implementation 'com.github.jksolapps:ad-sdk:1.0.8'
}
```

### `AndroidManifest.xml`
> ⚠️ **Mandatory.** A missing or wrong AdMob App ID crashes the app on startup. Preserve the existing value.

```xml
<application>
    <!-- Preserve your existing live AdMob App ID — do NOT use the test ID below in production -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />
</application>
```

### ProGuard / R8 Rules (`proguard-rules.pro`)
> JkAds SDK embeds its own consumer ProGuard rules inside the AAR, so basic rules are applied automatically. If you use custom native ad view bindings or data classes, keep them from obfuscation:
```proguard
# Optional: keep custom native layouts and models if using reflection
-keep class com.jksol.ads.** { *; }
```

---

## 5. Step 1: Initialize (Application Class)

Call `JkAds.initialize()` **exactly once** in `Application.onCreate()`. Ad-unit credentials come from the **server JSON config**, not from code.

### Kotlin
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val resumeConfig = AppOpenResumeConfig.Builder()
            .setPlacement("app_open_resume")
            .addExcludedActivities(listOf(
                SplashActivity::class.java,
                PremiumActivity::class.java
                // Add any payment, purchase, permission, or onboarding activities
            ))
            .addExcludedScreens(listOf("checkout", "payment", "onboarding")) // Compose routes
            .build()

        val config = AdConfig.Builder()
            .setDebugMode(BuildConfig.DEBUG)             // SDK logs
            .setTestAdsEnabled(BuildConfig.DEBUG)        // Test ad units in debug builds
            .setTestConsentGeography(BuildConfig.DEBUG)  // Force GDPR form everywhere (debug)
            .setSecretKey("YOUR_64_CHARACTER_HEX_KEY")   // Analytics encryption key (from team lead)
            .setAppOpenResumeConfig(resumeConfig)
            .build()

        JkAds.initialize(this, config) { success, msg ->
            Log.d("JkAds", "Init: $success -> $msg")
            // Set subscription state immediately after init
            if (success) JkAds.setSubscribed(checkUserSubscription())
        }
    }
}
```

### Java
```java
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Configure automatic App Open Resume ads
        AppOpenResumeConfig resumeConfig = new AppOpenResumeConfig.Builder()
            .setPlacement("app_open_resume")
            .addExcludedActivities(Arrays.<Class<?>>asList(
                SplashActivity.class,
                PremiumActivity.class
                // Add any payment, purchase, permission, or onboarding activities
            ))
            .build();

        AdConfig config = new AdConfig.Builder()
            .setDebugMode(BuildConfig.DEBUG)
            .setTestAdsEnabled(BuildConfig.DEBUG)
            .setTestConsentGeography(BuildConfig.DEBUG)
            .setSecretKey("YOUR_64_CHARACTER_HEX_KEY")
            .setAppOpenResumeConfig(resumeConfig)
            .build();

        JkAds.INSTANCE.initialize(this, config, (success, msg) -> {
            Log.d("JkAds", "Init: " + success + " -> " + msg);
            if (success) {
                JkAds.INSTANCE.setSubscribed(checkUserSubscription());
            }
            return kotlin.Unit.INSTANCE;
        });
    }
}
```

> **Note for Compose apps:** `JkAdsTrackScreens(navController)` is also required — see Step 3.

---

## 6. Step 2: GDPR Consent

Call **once** in `onCreate()` of your **first/main Activity** (Splash or Launcher). The SDK shows UMP forms automatically.

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request consent — optional callback fires when consent form completes or is dismissed
        JkAds.requestConsent(this) { error ->
            if (error != null) Log.w("JkAds", "Consent error: $error")
            if (JkAds.canRequestAds()) {
                // Safe to preload initial ads if your app waits for consent
            }
        }
    }
}
```

```java
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        JkAds.INSTANCE.requestConsent(this, error -> {
            if (JkAds.INSTANCE.canRequestAds()) {
                // Safe to preload initial ads
            }
            return kotlin.Unit.INSTANCE;
        });
    }
}
```

### Privacy Options Button (Settings Screen)
Show a "Manage Privacy" button if required by the user's region:

**Kotlin:**
```kotlin
privacyBtn.visibility = if (JkAds.isPrivacyOptionsRequired()) View.VISIBLE else View.GONE
privacyBtn.setOnClickListener {
    JkAds.showPrivacyOptions(this) { status -> }
}
```

**Java:**
```java
privacyBtn.setVisibility(JkAds.INSTANCE.isPrivacyOptionsRequired() ? View.VISIBLE : View.GONE);
privacyBtn.setOnClickListener(v ->
    JkAds.INSTANCE.showPrivacyOptions(this, status -> kotlin.Unit.INSTANCE));
```

### Debug / Testing Consent (Debug Builds Only)
```kotlin
JkAds.resetConsent()                              // Clear cached consent choice
JkAds.debugResetAndShowConsent(activity) { err -> } // Clear + immediately show form
JkAds.getConsentDebugInfo()                       // Get diagnostic string
```

---

## 7. Step 3: Screen Analytics (Compose Only)

- **Activities / Fragments / XML:** Automatic — nothing to add.
- **Jetpack Compose:** Add `JkAdsTrackScreens` inside your `NavHost` composable. This is also **required** for App Open Resume route exclusions to work.

```kotlin
val navController = rememberNavController()
JkAdsTrackScreens(navController)   // ← add this line

NavHost(navController = navController, startDestination = "home") {
    composable("home") { HomeScreen() }
    composable("checkout") { CheckoutScreen() }  // excluded from resume ads
}
```

### Custom Screen Name (Dialogs, Bottom Sheets)
```kotlin
// XML / Kotlin programmatic
bannerAd.setScreenName("ExitDialog")   // call before loadBanner()
JkAds.loadInterstitial("exit_interstitial", customScreenName = "GameOver_Dialog")
JkAds.preloadNative("native_feed", customScreenName = "Nested_BottomSheet")

// XML attribute
app:jk_screen_name="ExitDialog"
```

---

## 8. Step 4: Banner Ads

### 4.1 Auto-Loading (Simplest — Use This Unless You Have a Reason Not To)

**XML + Kotlin:**
```xml
<!-- layout.xml — loads automatically when view attaches -->
<com.jksol.ads.ui.banner.JkAdsBannerView
    android:id="@+id/bannerAd"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:jk_placement="banner_home"
    app:jk_ad_size="adaptive" />
```
```kotlin
// Optional: add callbacks
val bannerAd = findViewById<JkAdsBannerView>(R.id.bannerAd)
bannerAd.setAdLifecycleListener(object : AdLifecycleListener {
    override fun onAdLoaded(event: AdEvent) { }
    override fun onAdFailedToLoad(event: AdEvent) { Log.e("JkAds", event.errorMessage) }
})
```

**Compose:**
```kotlin
JkAdsBanner(
    placement = "banner_home",
    adSize = AdSizeType.Adaptive,
    modifier = Modifier.fillMaxWidth()
)
```

**Cleanup (always add):**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    binding.bannerAd.destroy()
}
```

### 4.2 Manual / Conditional Load
Use when you need to delay loading (e.g., wait for data first):

```kotlin
// XML: set app:jk_auto_load="false", then configure and load in code
val bannerAd = findViewById<JkAdsBannerView>(R.id.bannerAd)
bannerAd.setPlacement("banner_home")       // required
bannerAd.setAdSize(AdSizeType.Adaptive)    // required
bannerAd.setAutoHideOnError(true)          // optional

fun onDataLoaded(items: List<Item>) {
    if (items.isNotEmpty()) bannerAd.loadBanner()
}
```

### 4.3 Preload for Instant Display
> ⚠️ The size in the view **must match** the preloaded size — otherwise the cache is discarded.

```kotlin
// Preload before navigating to the screen
JkAds.preloadBanner(context, "banner_detail", AdSizeType.MediumRectangle)

// Check readiness
val ready = JkAds.isBannerReady("banner_detail")

// The JkAdsBannerView with matching placement + size picks up the cache automatically
```

### Banner XML Attributes Reference

| Attribute | Default | Meaning |
| :--- | :--- | :--- |
| `app:jk_placement` | `""` | Placement key — **required** |
| `app:jk_ad_size` | `adaptive` | `adaptive`, `standard_banner`, `large_banner`, `medium_rectangle`, `inline_adaptive`, `fluid` |
| `app:jk_auto_load` | `true` | `false` = call `loadBanner()` manually |
| `app:jk_auto_hide` | `true` | Sets `GONE` on load failure |
| `app:jk_screen_name` | `""` | Analytics screen override (for dialogs) |
| `app:jk_show_shimmer` | `true` | Shimmer placeholder while loading |
| `app:jk_shimmer_color` | auto | Shimmer base color |
| `app:jk_inline_max_height` | `0` | Max dp for `inline_adaptive` |

### Tab Fragments — Use `show()/hide()`, Not `replace()`
```kotlin
// Correct: banner survives tab switch
supportFragmentManager.beginTransaction().hide(activeFragment).show(target).commit()
// Wrong: replace() destroys the banner view
```

---

## 9. Step 5: Interstitial Ads

> ⚠️ **Both `onDismissed` and `onFailedToShow` MUST continue the navigation flow.** Never leave the user stuck.

### Preload + Show (Recommended Pattern)

**Kotlin:**
```kotlin
// 1. Preload in background (e.g., in onResume or onViewCreated)
JkAds.loadInterstitial("interstitial_feature")

// 2. Show on transition trigger
if (JkAds.isInterstitialReady("interstitial_feature")) {
    JkAds.showInterstitial(
        activity = this,
        placement = "interstitial_feature",
        onDismissed = { navigateToNextScreen() },      // ← always continue here
        onFailedToShow = { navigateToNextScreen() }    // ← always continue here too
    )
} else {
    navigateToNextScreen()  // ad not ready — still navigate
}
```

**Java:**
```java
// 1. Preload
JkAds.INSTANCE.loadInterstitial("interstitial_feature");

// 2. Show
if (JkAds.INSTANCE.isInterstitialReady("interstitial_feature")) {
    JkAds.INSTANCE.showInterstitial(
        this, "interstitial_feature",
        null, null, null,                   // onShown, onImpression, onClicked
        () -> { navigateToNextScreen(); return kotlin.Unit.INSTANCE; },      // onDismissed
        (error) -> { navigateToNextScreen(); return kotlin.Unit.INSTANCE; }, // onFailedToShow
        null                                // onRevenue
    );
} else {
    navigateToNextScreen();
}
```

### Load + Show in One Call (Simpler)
```kotlin
JkAds.loadAndShowInterstitial(
    activity = this,
    placement = "interstitial_splash",
    timeoutMs = 8_000L,
    onDismissed = { proceedToNextScreen() },
    onFailed = { proceedToNextScreen() }
)
```

---

## 10. Step 6: Rewarded Ads

> ⚠️ **Grant the reward ONLY inside `onRewarded`.** Do not grant on dismiss alone.  
> Rewarded ads are **single-use**: load a new one after each show.

### Preload + Show

**Kotlin:**
```kotlin
// 1. Preload
JkAds.loadRewarded("rewarded_coins", onFailed = { error -> hideWatchAdButton() })

// 2. Show on button tap
if (JkAds.isRewardedReady("rewarded_coins")) {
    JkAds.showRewarded(
        activity = this,
        placement = "rewarded_coins",
        onRewarded = { type, amount -> grantCoins(type, amount) },  // ← grant here only
        onDismissed = { resumeGame() },
        onFailedToShow = { error -> resumeGame() }
    )
} else {
    showLoadingOrError()
}
```

**Java:**
```java
// 1. Preload
JkAds.INSTANCE.loadRewarded("rewarded_coins", null, null, null);

// 2. Show on button tap
if (JkAds.INSTANCE.isRewardedReady("rewarded_coins")) {
    JkAds.INSTANCE.showRewarded(
        this, "rewarded_coins",
        (type, amount) -> { grantCoins(type, amount); return kotlin.Unit.INSTANCE; }, // onRewarded
        null, null, null,                                                            // onShown, onImpression, onClicked
        () -> { resumeGame(); return kotlin.Unit.INSTANCE; },                        // onDismissed
        (error) -> { resumeGame(); return kotlin.Unit.INSTANCE; },                   // onFailedToShow
        null                                                                         // onRevenue
    );
} else {
    showLoadingOrError();
}
```

### Load + Show in One Call
```kotlin
JkAds.loadAndShowRewarded(
    activity = this,
    placement = "rewarded_coins",
    timeoutMs = 15_000L,
    onRewarded = { type, amount -> grantCoins(type, amount) },
    onDismissed = { resumeGame() },
    onFailed = { error -> resumeGame() }
)
```

---

## 11. Step 7: App Open Ads

### 7.1 Pause / Resume (Sensitive Screens)
Disable resume ads before Google Play Billing, active gameplay levels, or media playback:
```kotlin
JkAds.disableAppOpenResume()   // before sensitive flow
// ... sensitive screen ...
JkAds.enableAppOpenResume()    // after sensitive flow exits
```

### 7.2 Splash Screen — Manual App Open Ad
Use for custom cold-start splash screens:

> ⚠️ **Critical Rule:** If your app shows an App Open ad on Splash, `SplashActivity` **must** be listed in `addExcludedActivities(...)` in `AppOpenResumeConfig` (in `Application.onCreate()`) to prevent the automatic resume ad from firing at the same time.

**Kotlin:**
```kotlin
JkAds.loadSplashAppOpenAd(
    activity = this,
    placement = "app_open_splash",
    appOpenType = AppOpenType.APP_OPEN,  // or APP_OPEN_NATIVE
    timeoutMs = 10_000L,
    onLoaded = {
        JkAds.showSplashAppOpenAd(
            activity = this,
            onDismissed = { proceedToMainActivity() },
            onFailedToShow = { proceedToMainActivity() }
        )
    },
    onFailed = { error -> proceedToMainActivity() }
)
```

**Java:**
```java
JkAds.INSTANCE.loadSplashAppOpenAd(
    this, "app_open_splash",
    10000L,
    AppOpenType.APP_OPEN,
    null,
    () -> {
        JkAds.INSTANCE.showSplashAppOpenAd(
            this,
            null, null, null,
            () -> { proceedToMainActivity(); return kotlin.Unit.INSTANCE; },
            (error) -> { proceedToMainActivity(); return kotlin.Unit.INSTANCE; }
        );
        return kotlin.Unit.INSTANCE;
    },
    (error) -> { proceedToMainActivity(); return kotlin.Unit.INSTANCE; }
);
```

### 7.3 Native App Open Colors (Optional — `APP_OPEN_NATIVE` only)
```kotlin
val loadingConfig = AppOpenLoadingConfig.Builder()
    .setBackgroundColor(Color.parseColor("#121212"))
    .setCtaButtonBgColor(Color.parseColor("#FF5722"))
    .setCtaButtonTextColor(Color.WHITE)
    .setAdLabelBgColor(Color.parseColor("#F5B041"))
    .setAdLabelTextColor(Color.BLACK)
    .build()

// Pass to AdConfig.Builder():
.setAppOpenLoadingConfig(loadingConfig)
```

---

## 12. Step 8: Rewarded Interstitial Ads

Shown automatically at natural breaks (e.g., level complete). No user opt-in.  
Same callbacks as Rewarded: `onRewarded` fires first, then `onDismissed`. Early close fires only `onDismissed`.

### Preload + Show
```kotlin
// 1. Preload
JkAds.loadRewardedInterstitial("rewarded_interstitial_level_bonus",
    onFailed = { error -> }
)

// 2. Show at natural break
if (JkAds.isRewardedInterstitialReady("rewarded_interstitial_level_bonus")) {
    JkAds.showRewardedInterstitial(
        activity = activity,
        placement = "rewarded_interstitial_level_bonus",
        onRewarded = { type, amount -> grantCoins(type, amount) },
        onDismissed = { resumeGame() },
        onFailedToShow = { error -> resumeGame() }
    )
} else {
    resumeGame()
}
```

### Load + Show in One Call
```kotlin
JkAds.loadAndShowRewardedInterstitial(
    activity = activity,
    placement = "rewarded_interstitial_level_bonus",
    timeoutMs = 15_000L,
    onRewarded = { type, amount -> grantCoins(type, amount) },
    onDismissed = { resumeGame() },
    onFailed = { error -> resumeGame() }
)
```

### Compose
```kotlin
var showAd by remember { mutableStateOf(false) }

Button(onClick = { showAd = true }) { Text("Next Level") }

JkAdsRewardedInterstitial(
    show = showAd,
    placement = "rewarded_interstitial_level_bonus",
    timeoutMs = 10_000L,
    onRewarded = { type, amount -> grantCoins(type, amount) },
    onDismissed = { showAd = false; resumeGame() },
    onFailed = { error -> showAd = false; resumeGame() }
)
```

---

## 13. Step 9: Native Ads

### 9.1 Built-in Templates (Simplest)

**XML:**
```xml
<!-- Auto-loads when the view attaches. No load() call needed. -->
<com.jksol.ads.ui.nativead.JkAdsNativeView
    android:id="@+id/nativeAd"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:jk_placement="native_home"
    app:jk_native_template="medium" />
    <!-- Options: small | medium | large | custom -->
```

**Compose:**
```kotlin
JkAdsNative(placement = "native_home", template = NativeAdTemplate.Small)
JkAdsNative(placement = "native_home")                                         // Medium (default)
JkAdsNative(placement = "native_home", template = NativeAdTemplate.Large, modifier = Modifier.fillMaxWidth())
```

---

### 9.2 Custom Layout — Preserve Your App's Existing UI Design

When migrating an existing custom native design, keep your XML layout and assign these **SDK view IDs** to the matching views:

| Component | XML View ID | View Type | Required? |
| :--- | :--- | :--- | :--- |
| AdChoices privacy icon | `@id/jk_native_ad_choices` | `FrameLayout` | ✅ **Mandatory** |
| "AD" badge | `@id/jk_native_ad_label` | `TextView` or `FrameLayout` | ✅ **Required by networks** |
| Headline | `@id/jk_native_headline` | `TextView` | Recommended |
| Body / Description | `@id/jk_native_body` | `TextView` | Recommended |
| CTA button | `@id/jk_native_cta` | `Button` | Recommended |
| App/Advertiser icon | `@id/jk_native_icon` | `ImageView` | Recommended |
| Media / Video container | `@id/jk_native_media_container` | `FrameLayout` | When using media |
| Star rating | `@id/jk_native_star_rating` | `LinearLayout` | Optional |
| Advertiser name | `@id/jk_native_advertiser` | `TextView` | Optional |
| Store | `@id/jk_native_store` | `TextView` | Optional |
| Price | `@id/jk_native_price` | `TextView` | Optional |

**Minimal custom layout example** (`res/layout/my_native_ad.xml`):
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <ImageView
            android:id="@id/jk_native_icon"
            android:layout_width="48dp"
            android:layout_height="48dp" />

        <TextView
            android:id="@id/jk_native_headline"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textSize="18sp"
            android:textStyle="bold" />
    </LinearLayout>

    <!-- Media/video container — requires app:jk_show_media_view="true" -->
    <FrameLayout
        android:id="@id/jk_native_media_container"
        android:layout_width="match_parent"
        android:layout_height="200dp"
        android:layout_marginTop="12dp" />

    <TextView
        android:id="@id/jk_native_body"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp" />

    <Button
        android:id="@id/jk_native_cta"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="12dp" />

    <!-- MANDATORY: AdChoices privacy icon -->
    <FrameLayout
        android:id="@id/jk_native_ad_choices"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />

</LinearLayout>
```

**Mount in your screen layout:**
```xml
<com.jksol.ads.ui.nativead.JkAdsNativeView
    android:id="@+id/nativeAd"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:jk_placement="native_home"
    app:jk_native_template="custom"
    app:jk_custom_layout="@layout/my_native_ad"
    app:jk_custom_shimmer_layout="@layout/my_native_shimmer"
    app:jk_show_media_view="true" />
    <!-- app:jk_show_media_view="true" is REQUIRED when using jk_native_media_container -->
```

---

### 9.2.1 Custom Shimmer Layout (XML)

> ⚠️ **The shimmer layout must structurally mirror your native ad layout.**  
> Match every component's **position, size, margin, and padding** exactly.  
> If they don't match, the layout jumps when the ad loads — the shimmer collapses and the ad snaps to a different height.

**Rule: one shimmer placeholder per real component in your native ad layout.**

Create `res/layout/my_native_shimmer.xml` — it is the visual skeleton of `my_native_ad.xml`:

```xml
<!-- res/layout/my_native_shimmer.xml -->
<!-- Mirror the structure of your native ad layout exactly -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">   <!-- same padding as my_native_ad.xml -->

    <!-- Row 1: mirrors icon (48dp) + headline — same as jk_native_icon + jk_native_headline -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <!-- Icon placeholder: same 48×48dp as ImageView in your native layout -->
        <View
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="@color/shimmer_color" />

        <View
            android:layout_width="0dp"
            android:layout_height="18dp"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:layout_gravity="center_vertical"
            android:background="@color/shimmer_color" />
    </LinearLayout>

    <!-- Row 2: mirrors media container — same 200dp height as jk_native_media_container -->
    <View
        android:layout_width="match_parent"
        android:layout_height="200dp"
        android:layout_marginTop="12dp"
        android:background="@color/shimmer_color" />

    <!-- Row 3: mirrors body text — same marginTop as jk_native_body -->
    <View
        android:layout_width="match_parent"
        android:layout_height="14dp"
        android:layout_marginTop="8dp"
        android:background="@color/shimmer_color" />

    <View
        android:layout_width="180dp"
        android:layout_height="14dp"
        android:layout_marginTop="6dp"
        android:background="@color/shimmer_color" />

    <!-- Row 4: mirrors CTA button — same 48dp height and marginTop as jk_native_cta -->
    <View
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="12dp"
        android:background="@color/shimmer_color" />

</LinearLayout>
```

> **Tips:**
> - Use a `shimmer_color` in your `colors.xml` — e.g. `#E2E8F0` (light) / `#1E293B` (dark) — or animate it with a `ShimmerFrameLayout` wrapper if your app uses the Shimmer library.
> - If your native layout has no `jk_native_media_container`, remove the 200dp row from the shimmer.
> - If your native layout has no body text, remove those rows.
> - The shimmer file's **total rendered height must equal** your native ad layout's rendered height to avoid a jump.

**Reference your shimmer in the `JkAdsNativeView`:**
```xml
<com.jksol.ads.ui.nativead.JkAdsNativeView
    ...
    app:jk_custom_layout="@layout/my_native_ad"
    app:jk_custom_shimmer_layout="@layout/my_native_shimmer"
    app:jk_show_shimmer="true" />
```

---

### 9.3 Custom Layout — Compose

```kotlin
JkAdsNative(
    placement = "native_home",
    template = NativeAdTemplate.Custom,
    showMediaView = true,              // REQUIRED when using helpers.MediaView()
    modifier = Modifier.fillMaxWidth(),

    // Custom shimmer: must match the height and structure of your customContent below
    customShimmerContent = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Row 1: mirrors icon + headline
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.height(12.dp))
            // Row 2: mirrors MediaView — same height as in customContent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(12.dp))
            // Row 3: mirrors body text lines
            Box(Modifier.fillMaxWidth().height(14.dp).background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.width(180.dp).height(14.dp).background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(8.dp))
            // Row 4: mirrors CTA button — same height as CtaButton
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
            )
        }
    },

    customContent = { content, helpers ->
        Card(Modifier.fillMaxWidth().padding(8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    helpers.IconImage(Modifier.size(48.dp))
                    Spacer(Modifier.width(8.dp))
                    helpers.HeadlineText(Modifier.weight(1f))
                    helpers.AdLabel()                          // ← required
                }
                Spacer(Modifier.height(12.dp))
                helpers.MediaView(Modifier.fillMaxWidth().height(200.dp))
                Spacer(Modifier.height(12.dp))
                helpers.BodyText()
                Spacer(Modifier.height(8.dp))
                helpers.CtaButton(Modifier.fillMaxWidth())
                helpers.AdChoicesView()                        // ← mandatory
            }
        }
    }
)
```

> **Compose Shimmer Rule:**  
> Every `Box`/`Spacer` in `customShimmerContent` must mirror the exact `Modifier` heights and spacings in `customContent`. The shimmer renders until the ad loads — if heights differ, the layout jumps.



### 9.4 Native Preload, Callbacks & Cleanup

```kotlin
// Preload before navigating to a screen
JkAds.preloadNative("native_home", showMediaView = true)

// Callbacks on the view
nativeAd.setAdLifecycleListener(object : AdLifecycleListener {
    override fun onAdLoaded(event: AdEvent) { }
    override fun onAdFailedToLoad(event: AdEvent) { }
    override fun onAdClicked(event: AdEvent) { }
})
nativeAd.setAdRevenueListener(object : AdRevenueListener {
    override fun onPaidRevenueEvent(event: AdEvent) {
        // event.revenueValue, event.revenueCurrency
    }
})

// Status & cleanup
JkAds.isNativeReady("native_home")
JkAds.destroyNative("native_home")
```

### Native XML Attributes Reference

| Attribute | Default | Meaning |
| :--- | :--- | :--- |
| `app:jk_placement` | `""` | Placement key — **required** |
| `app:jk_native_template` | `medium` | `small` / `medium` / `large` / `custom` |
| `app:jk_custom_layout` | — | Custom layout XML resource (for `custom` template) |
| `app:jk_custom_shimmer_layout` | — | Custom shimmer/skeleton layout |
| `app:jk_show_media_view` | auto | Force media on/off. **Required** when using `jk_native_media_container` |
| `app:jk_auto_load` | `true` | Auto-load on attach |
| `app:jk_auto_hide` | `true` | Set `GONE` on failure |
| `app:jk_show_shimmer` | `true` | Shimmer placeholder |
| `app:jk_card_background` | auto | Card background color |
| `app:jk_headline_color` | auto | Headline text color |
| `app:jk_cta_background` | auto | CTA button background |
| `app:jk_cta_text_color` | auto | CTA text color |
| `app:jk_ad_label_background` | auto | "AD" badge background |
| `app:jk_ad_label_text_color` | auto | "AD" badge text color |

---

## 14. Step 10: Subscription / No-Ads Mode

One call instantly hides all ads app-wide, blocks loading, and frees memory. No per-screen checks needed.

### 💡 Centralized Preference / PreferenceHelper Pattern (Wire Once, Zero Per-Screen Checks)

Most Android projects track premium state using a preference helper class (e.g., `AppPreferences`, `SharedPrefs`, `DataStoreManager`, `UserPrefs`, or `isPremium` flag). **You only need to hook `JkAds.setSubscribed` in that one central place!**

```kotlin
// Example: Inside your project's AppPreferences / PreferenceHelper
class AppPreferences(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var isPremium: Boolean
        get() = prefs.getBoolean("is_premium", false)
        set(value) {
            prefs.edit().putBoolean("is_premium", value).apply()
            // 🔥 Hook here directly: Syncs with JkAds automatically!
            JkAds.setSubscribed(value)
        }
}
```

**On App Launch (in Application.onCreate or after JkAds.initialize):**
```kotlin
JkAds.initialize(this, config) { success, _ ->
    if (success) {
        // Sync saved preference state once on startup
        JkAds.setSubscribed(appPreferences.isPremium)
    }
}
```

> **Why this matters:** Once `JkAds.setSubscribed(true)` is called, the SDK suppresses all banner, native, interstitial, rewarded, and app-open ads app-wide. **No other screen, Activity, or Fragment needs to call `JkAds.setSubscribed(true)` or check `if (!isPremium)` before requesting ads.**

### Direct Billing Integration

**Kotlin:**
```kotlin
// After SDK initializes — check existing entitlement
JkAds.initialize(this, config) { success, _ ->
    if (success) JkAds.setSubscribed(checkUserSubscription())
}

// On purchase complete
fun onPurchaseComplete() { JkAds.setSubscribed(true) }

// On subscription expired / revoked
fun onSubscriptionExpired() { JkAds.setSubscribed(false) }
```

**Java:**
```java
// After SDK initializes
JkAds.INSTANCE.initialize(this, config, (success, msg) -> {
    if (success) JkAds.INSTANCE.setSubscribed(checkUserSubscription());
    return kotlin.Unit.INSTANCE;
});

public void onPurchaseComplete()    { JkAds.INSTANCE.setSubscribed(true); }
public void onSubscriptionExpired() { JkAds.INSTANCE.setSubscribed(false); }
```

### Observe Subscription State (Reactive)
```kotlin
// One-time check
val isNowSubscribed = JkAds.isSubscribed()

// Collect as a Flow (in ViewModel / coroutine scope)
lifecycleScope.launch {
    JkAds.isSubscribed.collect { subscribed -> updateUi(subscribed) }
}

// Compose
val isSubscribed by JkAds.isSubscribed.collectAsState()
```

---

## 15. API Cheat Sheet

```
// Init & consent
JkAds.initialize(app, config) { ok, msg -> }
JkAds.requestConsent(activity)
JkAds.isPrivacyOptionsRequired()  /  showPrivacyOptions(activity) { }
JkAds.canRequestAds()

// Banner
JkAds.preloadBanner(ctx, placement, AdSizeType)
JkAds.isBannerReady(placement)
JkAds.refreshBanner(ctx, placement)
JkAds.destroyBanner(placement)

// Interstitial
JkAds.loadInterstitial(placement)
JkAds.isInterstitialReady(placement)
JkAds.showInterstitial(activity, placement, onDismissed, onFailedToShow)
JkAds.loadAndShowInterstitial(activity, placement, timeoutMs, onDismissed, onFailed)

// Rewarded
JkAds.loadRewarded(placement)
JkAds.isRewardedReady(placement)
JkAds.showRewarded(activity, placement, onRewarded, onDismissed, onFailedToShow)
JkAds.loadAndShowRewarded(activity, placement, timeoutMs, onRewarded, onDismissed, onFailed)

// Rewarded Interstitial
JkAds.loadRewardedInterstitial(placement)
JkAds.isRewardedInterstitialReady(placement)
JkAds.showRewardedInterstitial(activity, placement, onRewarded, onDismissed, onFailedToShow)
JkAds.loadAndShowRewardedInterstitial(activity, placement, timeoutMs, onRewarded, onDismissed, onFailed)
JkAds.destroyRewardedInterstitial(placement)

// App Open
JkAds.loadSplashAppOpenAd(activity, placement, appOpenType, timeoutMs, onLoaded, onFailed)
JkAds.showSplashAppOpenAd(activity, onDismissed, onFailedToShow)
JkAds.enableAppOpenResume()  /  disableAppOpenResume()

// Native
JkAds.preloadNative(placement, showMediaView)
JkAds.isNativeReady(placement)
JkAds.destroyNative(placement)

// Subscription
JkAds.setSubscribed(bool)
JkAds.isSubscribed()           // one-time Boolean
JkAds.isSubscribed             // StateFlow<Boolean>

// Java Interop Rule:
// In Java, call via JkAds.INSTANCE.*, pass null for unused optional callbacks,
// and lambda callbacks return kotlin.Unit.INSTANCE (e.g. () -> { ...; return kotlin.Unit.INSTANCE; })
```

---

## 16. Stage 1 Output Template

When presenting the migration plan, use this exact structure and **stop for approval**:

---

### 🔍 JkAds SDK v1.0.8 Migration Plan

#### 1. Project Audit Summary
- **App Module:** `<module_path>`
- **AdMob App ID (AndroidManifest):** `ca-app-pub-...~...` — preserved as-is
- **Active Formats Found:** Banner (X), Native (X), Interstitial (X), Rewarded (X), App Open (X)
- **Unused / Dead Ad Requests Found:** `<count + files where ads were requested but never shown>`
- **Legacy GMA Conflicts Found:** `<yes/no + details>`
- **Existing UMP/Consent Code Location:** `<class name and whether to remove it>`
- **Subscription / Premium Preference Integration:** `<class name (e.g. AppPreferences, DataStore, BillingManager) and where isPremium is stored/read>`

---

#### 2. Active Placement Inventory (Verified Loaded & Shown)

| Placement Name | Format & Size | Screen / Activity / Fragment | Layout / Container | Load Trigger | Show Trigger | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `banner_home` | Adaptive Banner | `MainActivity` | `R.id.banner_container` | `onCreate` | Auto | ✅ Active |
| `native_feed` | Custom Native | `HomeFragment` | `layout/item_native_ad.xml` | `onViewCreated` | Auto | ✅ Active |
| `interstitial_splash` | Interstitial | `SplashActivity` | — | `onResume` | On load complete | ✅ Active |
| `rewarded_coins` | Rewarded Video | `ShopActivity` | — | Button tap | Button tap | ✅ Active |
| `app_open_resume` | App Open (Auto) | App-wide | — | SDK foreground | SDK auto | ✅ Active |

---

#### 3. 🗑️ Unused Ad Requests & Dead Code Identified for Removal
> Any ad request that is loaded but never shown, ghost layout containers, or abandoned legacy ad wrappers are pruned to eliminate wasteful network calls and prevent policy violations.

| File / Location | Item Found | Why Unused? | Planned Action |
| :--- | :--- | :--- | :--- |
| `SettingsActivity.kt` | `InterstitialAd.load(...)` | `show()` is never called anywhere in the app | ❌ Delete unused load request |
| `fragment_profile.xml` | `<com.google.android.gms.ads.AdView>` | Container permanently `gone`, no code binding | ❌ Remove ghost ad view |
| `LegacyAdsManager.kt` | Obsolete ad helper class | Replaced by unified `JkAds` | ❌ Delete file & remove unused direct SDK deps |

---

#### 4. 📋 Copy-Ready Live Ads IDs List
> ⚠️ **MANDATORY FOR AGENT:** **DO NOT** output as an ASCII box or Markdown table. Output as a clean, plain `key = value` text block inside a code block so the developer can copy all IDs in one click for backend CDN JSON / remote config.
> Always start with `app live id = ...` followed by all active placement keys.

```text
app live id = ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
banner_home = ca-app-pub-XXXXXXXXXXXXXXXX/1111111111
banner_language = ca-app-pub-XXXXXXXXXXXXXXXX/7554669611
interstitial_splash = ca-app-pub-XXXXXXXXXXXXXXXX/3333333333
interstitial_record_save = ca-app-pub-XXXXXXXXXXXXXXXX/9989261266
rewarded_coins = ca-app-pub-XXXXXXXXXXXXXXXX/4444444444
native_feed = ca-app-pub-XXXXXXXXXXXXXXXX/2222222222
app_open_resume = [UNRESOLVED — Firebase key: ad_app_open_id]
```

---

#### 5. Planned Code Changes

**Gradle / Build**
- `settings.gradle.kts` — Add JitPack auth, Pangle, Mintegral repos
- `gradle.properties` — Add `adsAuthToken`
- `app/build.gradle.kts` — Add `configurations.configureEach` exclusion + SDK dependency
- `app/build.gradle.kts` — Remove obsolete third-party direct ad network dependencies (if any)

**Application Class**
- `MyApplication.kt` — Add `JkAds.initialize()` with `AdConfig` + `AppOpenResumeConfig` (excluded Activities/routes listed above)

**Consent**
- `MainActivity.kt` — Add `JkAds.requestConsent(this)`
- Remove duplicate manual UMP calls from: `<list files>`
- `AppNavigation.kt` — Add `JkAdsTrackScreens(navController)` (if Compose)

**Per-Screen Ad Migrations (Active Placements)**
- `MainActivity.kt` — Replace `AdView` banner → `JkAdsBannerView`
- `HomeFragment.kt` — Replace `AdLoader` native → `JkAdsNativeView` (custom layout, IDs mapped)
- `SplashActivity.kt` — Replace `InterstitialAd.load()` → `JkAds.loadAndShowInterstitial()`
- `ShopActivity.kt` — Replace `RewardedAd.load()` → `JkAds.loadRewarded()` + `showRewarded()`
- `MyApplication.kt` — Add `AppOpenResumeConfig` (already covered above)

**Unused Ad Requests & Dead Code Purging**
- `<file>` — Delete unused ad request / remove ghost ad container
- `<file>` — Clean up obsolete imports and unused ad callbacks

**Subscription / Premium Preferences**
- `<PreferenceHelper/BillingManager>` — Wire `JkAds.setSubscribed(isPremium)` inside preference load and setter so individual screens do not need to check or call it

---

#### 6. Risks & Safety Checklist
- ✅ Navigation never blocked: both `onDismissed` and `onFailedToShow` always call next navigation action
- ✅ Custom native layouts preserved: existing XML kept, SDK IDs assigned to matching views
- ✅ Only verified active ads migrated: every currently-loading and showing ad is migrated, no active placement dropped
- ✅ Unused ad requests safely purged: business logic and navigation continue smoothly without dead ad callbacks
- ✅ Premium state: ads suppressed immediately on subscription confirmation, no restart needed

---

### ⏸️ AWAITING DEVELOPER APPROVAL
> Review the inventory, planned changes, and copy-ready live ad ID list above.  
> Reply **"Approved"** (or provide corrections) to begin the implementation pass.

---

## 17. Stage 2 Final Report Template

After implementation completes:

---

### ✅ JkAds SDK v1.0.8 Migration Completed

#### 1. Migration Summary (Active Placements)

| Placement | Format | Screen | Old API | New API |
| :--- | :--- | :--- | :--- | :--- |
| `banner_home` | Adaptive | `MainActivity` | `AdView` | `JkAdsBannerView` |
| `native_feed` | Custom Native | `HomeFragment` | `AdLoader` | `JkAdsNativeView` (custom layout) |
| `interstitial_splash` | Interstitial | `SplashActivity` | `InterstitialAd.load()` | `JkAds.loadAndShowInterstitial()` |
| `rewarded_coins` | Rewarded | `ShopActivity` | `RewardedAd.load()` | `JkAds.loadRewarded()` + `showRewarded()` |
| `app_open_resume` | App Open Auto | App-wide | Manual `AppOpenAd` | `AppOpenResumeConfig` in `AdConfig` |

#### 2. 🗑️ Unused Ad Requests & Dead Code Purged

| File / Location | Item Purged | Result |
| :--- | :--- | :--- |
| `SettingsActivity.kt` | Unused `InterstitialAd.load()` request | Deleted without affecting settings flow |
| `fragment_profile.xml` | Empty `<AdView>` layout container | Removed |
| `LegacyAdsManager.kt` | Obsolete ad helper class | Deleted |

#### 3. Files Modified
- `settings.gradle.kts` — Added JitPack, Pangle, Mintegral repos
- `gradle.properties` — Added `adsAuthToken`
- `app/build.gradle.kts` — Added exclusion + SDK dependency
- `MyApplication.kt` — `JkAds.initialize()` + `AppOpenResumeConfig`
- `MainActivity.kt` — `requestConsent()` + banner migration
- `SplashActivity.kt` — Interstitial migration
- `HomeFragment.kt` — Native ad migration (custom layout preserved)
- `ShopActivity.kt` — Rewarded ad migration
- `BillingManager.kt` — `setSubscribed()` wired

#### 4. Build & Verification
- **Command:** `./gradlew compileDebugSources`
- **Result:** `BUILD SUCCESSFUL`
- **Consent:** `JkAds.requestConsent()` called in `MainActivity`
- **App Open Resume Exclusions:** `[SplashActivity, PremiumActivity, checkout, payment]`
- **Subscription:** Wired to `BillingManager` on `PURCHASED`, `RESTORED`, `EXPIRED`

#### 5. Remaining Actions for Developer
1. **Send the Copy-Ready Live Ads IDs List** (from Stage 1) to your backend developer to configure the server CDN JSON.
2. **CDO Secret Key:** If CDO analytics are enabled, add the approved 64-character hex key via `.setSecretKey()`. Do not commit it to source control — use a secure config source.
3. **Test on a real device:** Confirm test ads appear in debug builds, then verify live ads appear after the backend configures the server JSON.
