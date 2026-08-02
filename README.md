# FreshRSS Android

**A personal Android client for [FreshRSS](https://freshrss.org/)** — your self-hosted RSS reader, on your phone.

Point it at **your** server (home LAN, Tailscale, or HTTPS). No account with us, no hard-coded host, no cloud middleman. You keep the feeds and the credentials.

Repository: [github.com/crome1394/freshRSS_android_app](https://github.com/crome1394/freshRSS_android_app)

---

## Download

Install the latest prebuilt APK (sideload; enable “Install unknown apps” for your browser/files app):

| Version | APK |
|---------|-----|
| **0.7.1** | [**FreshRSS-0.7.1.apk**](https://github.com/crome1394/freshRSS_android_app/raw/main/releases/FreshRSS-0.7.1.apk) |

Direct path in the repo: [`releases/FreshRSS-0.7.1.apk`](releases/FreshRSS-0.7.1.apk)

> Debug-signed build for personal use. Prefer building a release APK yourself if you need your own signing key.

---

## What it does

FreshRSS Android is a lightweight feed reader UI for a **self-hosted FreshRSS** instance. It talks the same APIs as many desktop clients (Fever + Google Reader), so you can:

- Browse **unread**, **all**, **read**, and **starred** items  
- Filter by **video** or **audio/podcast-style** articles  
- Narrow by date (**today**, **yesterday**, last **7 / 14 / 30** days)  
- Search titles and summaries  
- Mark read/unread (including swipe), star, mark a whole feed read  
- Open articles in the browser, or play/listen when media is detected  
- Keep working briefly **offline** from the last successful download  
- Tune layout (chips and filters top or bottom) and **light / dark / system** theme  

It is a port of a Quickshell desktop widget (`FreshRssPill.qml` + `freshrss-api.sh`), not an official FreshRSS product.

---

## Features (overview)

| Area | Details |
|------|---------|
| **Scopes** | Unread / All / Read / Starred (Fever + GReader backends) |
| **Media** | Videos and Sound filter chips |
| **Dates** | All · Today · -1 · -7 · -14 · -21 |
| **Reading** | Collapsible feeds & day groups, dual-pane on wide screens |
| **Actions** | Star, mark read, mark feed read, share, browser / play / listen |
| **Offline** | Snapshot of the last successful load |
| **Layout** | Title bar, filter chips, and filters panel can sit at the bottom |
| **Theme** | System, Light, or Dark (Settings → Appearance) |
| **Extras** | Optional Tailscale shortcut; first-run setup wizard |

---

## First-run setup (on the phone)

1. Install the [APK](https://github.com/crome1394/freshRSS_android_app/raw/main/releases/FreshRSS-0.7.1.apk) (or build from source below).
2. On first launch, the **setup** screen appears (or open **Settings**).
3. Enter:
   - **FRESHRSS_BASE_URL** — prefer `https://…` (a bare hostname becomes `https://` automatically)
   - **FRESHRSS_USER**
   - **FRESHRSS_API_PASSWORD** — FreshRSS **Profile → API password**, not the web form password
4. Optional: **Allow insecure HTTP** only on trusted LAN/VPN.
5. **Test connection**, then **Save** / **Save & continue**.

---

## Build from source

### Requirements

| Tool | Notes |
|------|--------|
| **JDK 17+** | 21 recommended (`jdk21-openjdk` or Temurin) |
| **Android SDK 35** | Command-line tools or Android Studio |
| **Device/emulator** | Must reach your FreshRSS host |

### Quick start

```bash
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH

echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:assembleDebug
./gradlew :app:installDebug   # USB debugging authorized
```

Or open the project in **Android Studio** and run the `app` configuration.

### Build variants

| Task | Output |
|------|--------|
| `./gradlew :app:assembleDebug` | Debuggable APK |
| `./gradlew :app:assembleRelease` | Minified APK (R8); optional signing via `local.properties` |
| `./gradlew :app:testDebugUnitTest` | Unit tests |

Optional release signing (see `local.properties.example`):

```properties
RELEASE_STORE_FILE=/absolute/path/to/upload.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=...
```

After a release build you can refresh the downloadable file:

```bash
cp app/build/outputs/apk/debug/app-debug.apk releases/FreshRSS-0.7.1.apk
# or from release outputs when signed
```

---

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
    settings/    # Config, layout, theme, security
  util/          # Fever auth, HTML strip, media + URL helpers
releases/        # Prebuilt APK for sideload download
```

---

## Security (summary)

- API password in **EncryptedSharedPreferences** (Android Keystore)
- GReader session token in an **EncryptedFile**
- Android **backup disabled** for app data
- Outbound article links limited to **http/https**
- **HTTPS by default** for the server URL; HTTP only with an explicit setting
- In-flight refresh is cancelled when a new one starts

Treat cleartext HTTP and untrusted networks carefully.

---

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

---

## Roadmap / not yet

- HTML WebView article body (list uses plain text today)
- Home-screen unread widget
- Background WorkManager polling
- Full Room offline database (snapshot cache only)

---

## Credits

- **Matthew Crome** ([@crome1394](https://github.com/crome1394)) — author and maintainer  
- **Grok (xAI)** — development assistance on the Android port and features  

---

## License

[MIT](LICENSE) © 2026 Matthew Crome

Not an official [FreshRSS](https://freshrss.org/) product.
