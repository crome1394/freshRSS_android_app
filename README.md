# FreshRSS Android

Personal [FreshRSS](https://freshrss.org/) client for Android. Connects to **your** self-hosted instance (LAN, Tailscale, or HTTPS). No hard-coded server; nothing is published for you.

Inspired by a Quickshell desktop widget (`FreshRssPill.qml` + `freshrss-api.sh`).

## Features

- **Unread / All / Read / Starred** scopes (Fever + Google Reader APIs)
- **Videos / Sound** media filters
- Date filters: All, Today, -1, -7, -14, -30
- Collapsible feeds and date groups
- Search, pull-to-refresh, swipe mark read/unread
- Star, mark feed read, open in browser / play / listen
- Offline snapshot of last successful load
- Layout options: title bar, filter chips, and filters panel top or bottom
- Optional Tailscale shortcut in the title bar

## Requirements

| Tool | Notes |
|------|--------|
| **JDK 17+** | 21 recommended (`jdk21-openjdk` or Temurin) |
| **Android SDK 35** | Command-line tools or Android Studio |
| **Device/emulator** | Must reach your FreshRSS host |

## Quick start

```bash
# Environment (adjust paths as needed)
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

# Point Gradle at the SDK (once)
echo "sdk.dir=$ANDROID_HOME" > local.properties

cd /path/to/freshRSS_android_app
./gradlew :app:assembleDebug
./gradlew :app:installDebug   # USB debugging authorized
```

Or open the project in **Android Studio** and run the `app` configuration.

## First-run setup (on device)

1. On first launch, the **setup** screen asks for your server (or open **Settings** later).
2. Set:
   - **FRESHRSS_BASE_URL** — prefer `https://…` (bare hostnames get `https://` automatically)
   - **FRESHRSS_USER**
   - **FRESHRSS_API_PASSWORD** — Profile → API password (**not** the web login password)
3. Optional: enable **Allow insecure HTTP** only for trusted LAN/VPN cleartext.
4. **Test connection**, then **Save** (or **Save & continue** on first run).

## Project layout

```
app/src/main/java/com/crome/freshrss/
  data/
    model/       # Article, ReadScope, config
    remote/      # FreshRssClient (Fever + GReader + RSS)
    prefs/       # DataStore settings
    secure/      # Encrypted password + GReader token store
    offline/     # Disk snapshot for offline open
  ui/
    home/        # Feed list, filters, scope chips
    article/     # Detail, share, browser / play
    settings/    # Config, layout, security toggles
  util/          # Fever auth, HTML strip, media + URL helpers
```

## Build variants

| Task | Output |
|------|--------|
| `./gradlew :app:assembleDebug` | Debuggable APK (`versionName` e.g. `0.7.0`) |
| `./gradlew :app:assembleRelease` | Minified APK (R8); optional signing via `local.properties` |
| `./gradlew :app:testDebugUnitTest` | Unit tests |

Optional release signing keys (see `local.properties.example`):

```properties
RELEASE_STORE_FILE=/absolute/path/to/upload.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=...
```

## Security (summary)

- API password in **EncryptedSharedPreferences** (Android Keystore)
- GReader session token in an **EncryptedFile**
- Android **backup disabled** for app data
- Outbound article links limited to **http/https**
- **HTTPS by default** for the server URL; HTTP only with an explicit setting
- In-flight refresh is cancelled when a new one starts

This is a **private / personal** client. Treat cleartext HTTP and untrusted networks carefully.

## API surface

Mirrors the desktop shell helpers:

| Concept | Method |
|---------|--------|
| Status / unread maps | `FreshRssClient.status()` |
| Items | `items(limit, scope, perFeed, historyDays)` |
| Mark read/unread | `markItem(id, read)` |
| Star / unstar | `starItem(id, saved)` |
| Mark feed | `markFeed(feedId, read)` |

| Scope | Backend |
|-------|---------|
| All / Read | Google Reader API, parallel per-feed |
| Unread / Starred | Fever API id lists |
| No API password | Public RSS `/i/?a=rss` (read-only) |

## Roadmap / not yet

- HTML WebView article body (list uses plain text today)
- Home-screen unread widget
- Background WorkManager polling
- Full Room offline database (snapshot cache only)

## License

Personal project. Not an official FreshRSS product. No store package is published from this tree by default.
