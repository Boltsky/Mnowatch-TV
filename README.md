# Munowatch TV 🎬📺

A high-performance, native **Android TV** application engineered for streaming Ugandan translated movies, series, and international cinema with zero buffering, crisp visual presentation, and remote control navigation.

[![Platform](https://img.shields.io/badge/Platform-Android%20TV-3DDC84.svg?style=flat&logo=android)](https://www.android.com/tv/)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20for%20TV-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/tv)
[![Player](https://img.shields.io/badge/Player-AndroidX%20Media3%20ExoPlayer-FF6F00.svg?style=flat)](https://developer.android.com/media/media3)

---

## 📥 Download APK

You can download the pre-compiled Android TV APK directly from this repository:

- 📦 **Direct Repository Download**: [**`apk/Munowatch-TV.apk`**](apk/Munowatch-TV.apk)
- 🚀 **GitHub Releases**: [**Releases Page**](https://github.com/Boltsky/Mnowatch-TV/releases)

To install directly to your Android TV or emulator via ADB:
```bash
adb install -r apk/Munowatch-TV.apk
```

---

## ✨ Key Features

### 🚀 High-Speed CDN & Zero-Buffer Playback
- **Direct Line-Rate Delivery**: Connects directly to high-capacity Cloudflare & Bunny CDN edge servers for fast startup (<500ms) and multi-megabyte throughput.
- **120-Second Memory Cache**: ExoPlayer `LoadControl` aggressively buffers up to 120 seconds ahead with a 30-second minimum floor, completely eliminating micro-stutters and network hiccups.
- **Mid-Stream Buffering Overlay**: Centered glowing loading indicator with real-time feedback during network pauses or long seeks.

### 🛡️ Smart HTTP 429 Bandwidth Limit Bypass
- **Direct-First Prioritization**: Automatically attempts direct line-rate streaming on every title.
- **Failover Proxy Pool**: When public IP limits or HTTP 429/403 rate limits occur, the app automatically fails over to verified HTTPS CONNECT proxies with partial content (HTTP 206) support, rotating client identities seamlessly.

### 🎯 Authentic Stream Quality Selector (No Hallucinations)
- **True Source Resolution**: Only displays real, streamable qualities detected for the title (`1080p Full HD`, `720p HD`, `480p SD`, or `Auto`).
- **CinemaScope Aspect Ratio Compensation**: Properly normalizes anamorphic and widescreen theatrical releases (e.g. 2:1 DAR, 480×270, 1280×534) to their true display resolution, preventing track-rejection black screens.

### 🎮 Tailored Android TV Leanback Experience
- **10-Foot D-Pad Navigation**: Optimized navigation for standard Android TV remotes and game controllers.
- **Synchronized Play/Pause**: Instant optimistic button updates (`Pause` when playing, `Play` when stopped) tied to ExoPlayer's `playWhenReady` pipeline.
- **On-Screen TV Keypad**: Built-in visual virtual keyboard for easy D-pad searching.
- **Continue Watching Shelf**: Automatically remembers watch positions across movies and multi-season series.

---

## 🎮 Remote Control Mapping

| Key / Button | Action |
| :--- | :--- |
| **D-Pad Center / OK** | Toggle HUD Controls / Select focused item |
| **Space / Play-Pause** | Instant Play / Pause toggle |
| **D-Pad Left / Right** | Seek backward (-10s) / forward (+10s) or navigate UI rows |
| **D-Pad Up / Down** | Jump between action buttons, timeline slider, and top badges |
| **Back / Escape** | Close overlays, hide HUD, or navigate to previous screen |

---

## 🏗️ Technical Architecture

The app is built following **Clean Architecture** and **MVI** patterns:

```
app/
├── src/main/java/com/example/
│   ├── data/
│   │   ├── api/             # Munowatch & Twolekede APIs, HTML scrapers & token extractors
│   │   ├── network/         # OkHttp client, dynamic proxy selector, cookie jar
│   │   ├── repository/      # MediaCache, PlaybackRepository implementation
│   │   └── session/         # Secure session manager & Android KeyStore
│   ├── domain/
│   │   ├── model/           # Movie, Series, Episode, PlaybackSource domain entities
│   │   └── repository/      # Repository contracts & business logic
│   ├── presentation/
│   │   ├── home/            # Featured banners, Continue Watching, Latest releases
│   │   ├── movies/          # Movies catalog with year/genre filtering
│   │   ├── series/          # TV series and episode navigation
│   │   ├── player/          # Media3 ExoPlayer, D-pad HUD, Quality dialog
│   │   └── search/          # TV search keyboard & multi-backend query resolution
│   └── ui/
│       ├── components/      # Reusable TV buttons, cards, focus borders
│       ├── navigation/      # Jetpack Compose Navigation host
│       └── theme/           # Munowatch dark green cinematic color scheme
```

---

## 🛠️ Build & Installation

### Prerequisites
- **Android Studio** Iguana / Jellyfish or newer
- **JDK 17+**
- **Android SDK** API 34+

### Build from Source
Clone the repository:
```bash
git clone https://github.com/Boltsky/Mnowatch-TV.git
cd Mnowatch-TV
```

Build the debug APK:
```bash
./gradlew assembleDebug
```

The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is licensed under the Apache License 2.0.
