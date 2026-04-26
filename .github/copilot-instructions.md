# Copilot Instructions

## Project Overview

This repo contains two JioTV streaming apps that share the same backend API. When working on **ClassicsKotlin** (Android TV), use **jiotv-flutter** (`../jiotv-flutter/`) as the reference implementation — it has a cleaner, more modern API layer.

---

## ClassicsKotlin — Android TV App

**Location:** `ClassicsKotlin/`  
**Package:** `com.android.tv.classics`  
**Min SDK:** 26 | **Target SDK:** 29 | **Kotlin:** 1.4.32

### Build Commands

```bash
# From ClassicsKotlin/
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Build + install to connected device
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumented tests
./gradlew lint                   # Lint (abortOnError = false)

# Run a single test class
./gradlew test --tests "com.android.tv.classics.FooTest"
```

### Architecture

- **Leanback UI:** `BrowseSupportFragment` (channel grid) → `VideoSupportFragment` (playback via `NowPlayingFragment`)
- **Navigation:** Jetpack Navigation component with Safe Args; nav graph connects `MediaBrowserFragment` ↔ `NowPlayingFragment`
- **ViewModel/LiveData** via `lifecycle-extensions`; coroutines (`lifecycleScope`) for async work in fragments
- **Room DB:** `TvMediaDatabase` — entities are `TvMediaMetadata` (channels), `TvMediaCollection` (genre groups), `TvMediaEPG` (EPG shows). DAOs are nested interfaces in each entity file.
- **WorkManager:** `TvMediaSynchronizer` syncs channels from JioAPI to Room on startup. `TvTokenRefresher` refreshes auth token in the background.
- **Firebase:** Realtime Database for remote config, `FirebaseAuth` for Google Sign-In (`GAuthFragment`), Crashlytics, Analytics.
- **TV Launcher:** `TvLauncherUtils` creates/updates `PreviewChannel` and `PreviewProgram` entries so channels appear on the Android TV home screen. `TvLauncherReceiver` handles launcher broadcast intents.
- **Playback:** ExoPlayer 2.13.3 with Leanback adapter (`LeanbackPlayerAdapter`) and `MediaSessionConnector`. HLS only — cookie-authenticated via `getHlsCookie`.

### Key Files

| File | Role |
|---|---|
| `jio/JioAPI.kt` | All JioTV HTTP calls (singleton `object`) |
| `jio/Constants.kt` | All header names, URL constants, and hardcoded device values |
| `jio/store/PrefStore.kt` | SharedPreferences wrapper (Jackson JSON for Map serialisation) |
| `jio/store/HttpStore.kt` | OkHttpClient factory + ExoPlayer DataSource factories |
| `workers/TvMediaSynchronizer.kt` | WorkManager worker: fetches channels → parses → writes to Room |
| `workers/TvTokenRefresher.kt` | WorkManager worker: periodic token refresh |
| `fragments/NowPlayingFragment.kt` | ExoPlayer playback, EPG fetch, channel switching |
| `fragments/MediaBrowserFragment.kt` | Leanback browse UI, channel rows by genre |
| `utils/TvLauncherUtils.kt` | Android TV home-screen channel management |

---

## JioTV API — Shared by Both Apps

Both apps call the **same endpoints** with **identical headers**. When the Android TV app behaves differently from the Flutter app, the Flutter implementation is the source of truth.

### Auth Flow

1. `POST /userservice/apis/v1/loginotp/send` — body: `{ "number": base64("+91XXXXXXXXXX") }`
2. `POST /userservice/apis/v2/loginotp/verify` — body from `login-request.json` asset + number + otp
3. Response envelope: `{ data: { authToken, refreshToken, ssoToken, sessionAttributes: { user: { uid, unique, subscriberId } } } }`
4. Session is valid when both `authToken` and `uniqueId` (= `user.unique`) are non-empty.
5. `POST /tokenservice/apis/v2/refreshtoken` — refresh every ~25 min; sends `authToken` + `uniqueId` headers.

### Playback Flow

1. Fetch EPG: `GET /apis/v1.3/getepg/get?offset=0&channel_id={id}&langId=6` → find current show by epoch overlap
2. Get playback URL: `POST /playback/apis/v1.1/geturl?langId=6` — **form-urlencoded** body with `channel_id`, `stream_type=Seek`, `srno` (first 6 digits of EPG srno), `programId`, `begin`, `end` (UTC format `yyyyMMddTHHmmss`)
3. Get CDN cookie: `GET {playbackUrl}` with auth headers → extract `set-cookie` response header (take only `name=value` before the first `;`)
4. Pass cookie as `Cookie` header to ExoPlayer/media_kit for segment requests.

### Hardcoded Device Constants (must match exactly)

```
versionCode = "396"
appKey      = "NzNiMDhlYzQyNjJm"
deviceId    = "94f739da0e91b3a6"
userGroup   = "tvYR7NSNn7rymo3F"
deviceType  = "phone"
dm          = "OnePlus HD1911"
osVersion   = "12"
os          = "android"
lbcookie    = "1"
languageId  = "6"
appName     = "RJIL_JioTV"
User-Agent  = "okhttp/4.0.1"
```

### Channel Filtering

Both apps filter channels to language IDs `["1", "6", "3"]` (Hindi, English, Tamil). This is defined as `kAllowedLanguageIds` in flutter and applied in `TvMediaSynchronizer` in the Android app.

### Auth Headers for Playback

All playback and CDN requests require these headers (in addition to base headers):
`accesstoken`, `appkey`, `channelid`, `crmid`, `deviceId`, `sid` (=uniqueId), `subscriberId` (=crmId), `uniqueId`, `usergroup`, `userId`, `versionCode`, `dm`, `isott=false`, `languageId`, `lbcookie`, `osVersion`

---

## Flutter Reference App (jiotv-flutter)

**Location:** `../jiotv-flutter/` — Flutter/Dart, Android-only.

Use this when implementing or fixing anything in ClassicsKotlin related to:
- **API correctness** → `lib/services/jio_api_service.dart` (Dio-based, fully documented)
- **Auth session model** → `lib/models/auth_session.dart` (`isValid` = authToken + uniqueId non-empty)
- **Playback result model** → `lib/models/playback_result.dart` (DASH preferred over HLS; DRM detection logic)
- **EPG model** → `lib/models/epg_show.dart` (epoch fields, `srno` truncation to 6 digits)
- **Constants** → `lib/core/constants.dart` (all hardcoded values)
- **State management pattern** → `lib/providers/` (Provider + ChangeNotifier; AuthProvider, ChannelsProvider, PlayerProvider)

### Flutter Commands (for reference only)

```bash
flutter pub get
flutter run
flutter analyze
flutter test
flutter test test/widget_test.dart   # single test file
```

---

## Conventions

- **ClassicsKotlin** stores auth as a `Map<String, Any>` in `PrefStore` under the key `"authHeaders"`. The Flutter app stores it as a typed `AuthSession` class. When reading auth in the Android app, always use the helper `getStringValue(map, key)` pattern (handles null/non-String values gracefully).
- **JioAPI** in the Android app is a Kotlin `object` (singleton); do not instantiate it. Flutter's `JioApiService` is a class — instantiated once per Provider.
- **Leanback presenters** live in `presenters/`; each presenter renders a `TvMediaMetadata` card. Do not use `RecyclerView` for TV browse rows.
- **`@RequiresApi(26)`** is applied broadly in `TvLauncherUtils` — all launcher operations require API 26+.
- The `login-request.json` asset (in `app/src/main/assets/`) provides the device-info body template for OTP verification. The Flutter app constructs this inline in `jio_api_service.dart`; both must match.
- **Timber** is used for logging in ClassicsKotlin (`Timber.d/e/w`). Do not use `Log.d` in new code.
- **ViewBinding** is enabled (`buildFeatures { viewBinding true }`); use it instead of `findViewById`.
