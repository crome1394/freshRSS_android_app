# FreshRSS Android

Personal [FreshRSS](https://freshrss.org/) client for your phone. Point it at **your** server (LAN, Tailscale, or HTTPS). No third-party account.

Repo: [crome1394/freshRSS_android_app](https://github.com/crome1394/freshRSS_android_app)

## Download

| Version | APK |
|---------|-----|
| **0.7.3** | [FreshRSS_Personal_Client-0.7.3.apk](https://github.com/crome1394/freshRSS_android_app/raw/main/releases/FreshRSS_Personal_Client-0.7.3.apk) |

Sideload and allow “Install unknown apps”. Debug-signed for personal use. See [CHANGELOG](CHANGELOG.md).

## Features

- Unread / all / read / starred · video & audio filters · date filters · search  
- Star, mark read (incl. swipe), mark feed read, share  
- **Browser** · **Play / Listen** opens native apps when installed (YouTube, Spotify, …)  
- Offline snapshot · light/dark theme · home-screen widget (unread + last updated)

## Setup

1. Install the APK (or build below).  
2. Settings: **server URL** (`https://…`), **user**, **API password** (Profile → API password).  
3. **Test connection** → Save. Optional: allow HTTP only on trusted LAN/VPN.

**Widget:** long-press home → Widgets → FreshRSS. Updates when you use the app (no background poll).

## Build

JDK 17+, Android SDK 35.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:installDebug   # device with USB debugging
```

```bash
# refresh the downloadable APK after a version bump
cp app/build/outputs/apk/debug/app-debug.apk releases/FreshRSS_Personal_Client-0.7.3.apk
```

Release signing: see `local.properties.example`.

## License

[MIT](LICENSE) © 2026 Matthew Crome · assisted by Grok (xAI)  
Not an official FreshRSS product.
