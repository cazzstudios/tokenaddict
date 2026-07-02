# Token Addict

Monitors AI usage limits for Claude.ai and Kimi Code. Displays real-time utilization, reset countdowns, and sends notifications when limits are approaching or have been reached.

## Prerequisites

- JDK 21
- Android SDK

## Build

```bash
./gradlew assembleDebug
```

## Test

```bash
./gradlew test
```

## Architecture

Token Addict follows an MVVM architecture with manual dependency injection:

- **`data/` layer**: Network calls via OkHttp, session/cookie management, encrypted storage via `EncryptedSharedPreferences`, and OAuth flows for Kimi
- **`ui/` layer**: Activities + ViewModels with LiveData and sealed `UiState` classes
- **`worker/` layer**: WorkManager-based periodic usage checks for both providers
- **`security/` layer**: Root/emulator/debug detection at startup

The app monitors two AI providers:
- **Claude.ai**: Session-based authentication via WebView login + cookie persistence
- **Kimi Code**: OAuth 2.0 Device Code flow with token refresh

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | API calls to Claude.ai and Kimi Code |
| `SCHEDULE_EXACT_ALARM` | Exact reset countdown notifications |
| `POST_NOTIFICATIONS` | Usage limit warnings and re-login alerts (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule periodic checks after device restart |
| `FOREGROUND_SERVICE` | Background usage polling |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Reliable background execution |

## Release Process

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`
2. Ensure `local.properties` contains `kimi.client.id=<your-client-id>`
3. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```
4. The signed APK is at `app/build/outputs/apk/release/app-release.apk`
5. Distribute via sideloading or your preferred channel

> **Note**: Debug builds use a placeholder client ID. Release builds require a valid `kimi.client.id` in `local.properties`.
