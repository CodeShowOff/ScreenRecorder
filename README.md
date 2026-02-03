# Screen Recorder

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Screen Recorder Logo" width="100"/>
</p>

<p align="center">
  <a href="AppStore/" download>
    <img src="https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android" alt="Download APK" height="50"/>
  </a>
</p>

<p align="center">
  <b>A powerful, open-source screen recording application for Android</b>
</p>

<p align="center">
  <b>Originally developed by <a href="https://gitlab.com/vijai">Vijai</a></b><br/>
  <a href="https://gitlab.com/vijai/screenrecorder">Original GitLab Repository</a>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#requirements">Requirements</a> •
  <a href="#installation">Installation</a> •
  <a href="#usage">Usage</a> •
  <a href="#settings">Settings</a> •
  <a href="#building">Building</a> •
  <a href="#license">License</a>
</p>

---

## ⚠️ Credits & Disclaimer

> **This app was originally developed by [Vijai](https://gitlab.com/vijai).** All credits for the original concept, design, and implementation go to him.
>
> **Original Repository:** [https://gitlab.com/vijai/screenrecorder](https://gitlab.com/vijai/screenrecorder)

---

## 📖 Story Behind This Fork

This is a **revived and modernized version** of Vijai's original Screen Recorder app.

### Why This Fork Exists

The original Screen Recorder by Vijai was an excellent, lightweight screen recording app. However, **development and support stopped in 2019** (over 6 years ago), making the app incompatible with modern Android devices.

**My situation:** I own a Samsung phone that doesn't have a built-in screen recording feature. After searching for alternatives, I couldn't find an app that was:
- ✅ Lightweight
- ✅ Secure
- ✅ Privacy-oriented
- ✅ Free and open-source

After extensive research, I discovered Vijai's project on GitLab. It was exactly what I needed, but there were some issues:

1. **Outdated** - The app couldn't run on modern Android versions (Android 12+)
2. **Tracking** - The original version contained analytics and tracking
3. **Unused Features** - Some features were incomplete or caused issues

### What I Changed

With the help of AI tools, I've:

- 🔄 **Upgraded** the entire codebase to support **Android 12-15** (API 31-35)
- 🛡️ **Removed all tracking and analytics** - The app is now completely private
- 🧹 **Removed broken/incomplete features** - Cleaned up unused code (camera overlay, GIF conversion)
- 🎵 **Simplified audio settings** - Optimized defaults, microphone-only recording
- 📱 **Updated all dependencies** - Using latest AndroidX libraries
- 🔧 **Fixed compatibility issues** - Works on modern Samsung and other devices
- 🚀 **Optimized logging** - Debug logs only in debug builds, not release

### The Result

A **clean, safe, and fully functional** screen recorder that:
- Contains **zero tracking or analytics**
- Is **100% open source**
- Works on **today's Android phones**
- Respects your **privacy**

---

## Overview

Screen Recorder is a free, open-source Android application that allows you to record your device's screen with high-quality video and multiple audio recording options. The app supports modern Android features including internal audio capture (Android 10+) without requiring root access, Quick Settings tile integration, and a built-in video trimmer.

**Application ID:** `com.orpheusdroid.screenrecorder`  
**Minimum SDK:** Android 12 (API 31)  
**Target SDK:** Android 15 (API 35)  
**Current Version:** 5.0.0  
**Original Developer:** [Vijai](https://gitlab.com/vijai/screenrecorder)

---

## Features

### 🎥 Screen Recording
- **High-Quality Video Recording** - Record your screen in resolutions from 360p up to 4K (2160p) and native resolution
- **Customizable Frame Rate** - Choose from 25, 30, 35, 40, 50, or 60 FPS
- **Variable Bitrate** - Select from 1 Mbps to 100 Mbps for optimal quality-to-size ratio
- **Pause/Resume Recording** - Pause your recording and resume anytime without creating multiple files
- **Auto-optimized Encoding** - Automatic bitrate calculation based on resolution and FPS for best quality
- **H.264 High Profile** - Uses hardware-accelerated H.264 encoder for efficient recording

### 🎵 Audio Recording
- **No Root Required** - All audio features work without root access
- **Microphone Recording** - Record external audio via device microphone
- **Optimized Settings** - Audio defaults hardcoded to optimal values:
  - Bitrate: 192 kbps
  - Channels: Mono
  - Sampling Rate: Best available (typically 48 KHz)
  - AAC Audio Encoder

### 🎛️ Quick Access
- **Quick Settings Tile** - Start/stop recording directly from the notification shade
- **App Shortcuts** - Launch recording quickly from the home screen
- **Notification Controls** - Pause, resume, and stop recording from the persistent notification
- **Chronometer** - See elapsed recording time in the notification

### 📂 Video Management
- **Built-in Video Gallery** - Browse all your recorded videos in-app
- **Video Thumbnails** - Quick preview of recorded videos
- **Pull-to-Refresh** - Easily refresh your video list
- **Batch Operations:**
  - Select multiple videos
  - Delete videos individually or in batch
  - Share videos directly from the app
- **Grid Layout** - Videos displayed in an organized grid view

### ✂️ Video Editing
- **Built-in Video Trimmer** - Trim your recordings without external apps
- **Easy Timeline Navigation** - Visual timeline for precise trimming
- **Save Edited Videos** - Trimmed videos are saved separately

### 🎨 Themes & Customization
- **Multiple Themes:**
  - White Theme
  - Light Theme
  - Dark Theme
  - Black Theme (AMOLED-friendly)
- **Custom Save Location** - Choose where to save your recordings
- **Custom Filename Format** - Multiple date/time formats available
- **Custom Filename Prefix** - Set a prefix for your recording filenames
- **Orientation Lock** - Auto, Portrait, or Landscape orientation

---

## Requirements

- **Android Version:** 12.0 (API 31) or higher
- **Permissions Required:**
  - `RECORD_AUDIO` - For microphone recording
  - `WRITE_EXTERNAL_STORAGE` (Android 12 and below)
  - `READ_MEDIA_VIDEO` (Android 13+)
  - `POST_NOTIFICATIONS` (Android 13+) - For recording notifications
  - `SYSTEM_ALERT_WINDOW` - For overlay permissions
  - `MANAGE_EXTERNAL_STORAGE` (Android 11+) - For custom save locations

---

## Installation

### Download APK
Download the latest release APK: [ScreenRecorder.apk](AppStore/)

### From Source
1. Clone the repository:
   ```bash
   git clone https://github.com/codeshowoff/ScreenRecorder.git
   ```

2. Open the project in Android Studio

3. Build and run on your device:
   ```bash
   ./gradlew assembleDebug
   ```

### From GitHub Releases
Download the latest APK from the [Releases](../../releases) page.

---

## Usage

### Starting a Recording

1. **From the App:**
   - Open Screen Recorder
   - Tap the **Record** button
   - Grant screen capture permission when prompted
   - Recording starts automatically

2. **From Quick Settings:**
   - Swipe down to open Quick Settings
   - Add the Screen Recorder tile (if not added)
   - Tap the tile to start recording

3. **From App Shortcut:**
   - Long-press the app icon
   - Select "Quick Record"

### Controlling Recording

- **Pause/Resume:** Use the notification controls or return to the app
- **Stop:** Tap the Stop button in the notification or app

### After Recording

- Videos are saved to your configured save location
- A notification appears with options to:
  - Share the video
  - Edit (trim) the video
  - View in the Videos tab

---

## Settings

### Video Settings
| Setting | Options | Default |
|---------|---------|---------|
| Resolution | 360p, 720p, 1080p, 1440p (2K), 2160p (4K), 2880p, Native | 1080p |
| Frame Rate | 25, 30, 35, 40, 50, 60 FPS | 30 FPS |
| Bitrate | 1-100 Mbps | 6.8 Mbps |
| Orientation | Auto, Portrait, Landscape | Auto |

### Audio Settings
| Setting | Value | Note |
|---------|-------|------|
| Audio Source | None or Microphone | Hardcoded optimal defaults |
| Bitrate | 192 kbps | Optimized for quality |
| Channels | Mono | Best for voice |
| Sampling Rate | Device optimal | Usually 48 KHz |

**Note:** Audio settings are hardcoded to optimal values for best quality with reasonable file sizes.

### Save Options
| Setting | Description |
|---------|-------------|
| Save Location | Custom directory selection with SAF support |
| Filename Format | Multiple date/time patterns (e.g., yyyyMMdd_HHmmss) |
| Filename Prefix | Custom prefix (default: "recording") |

---

## Building

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 17
- Android SDK 35
- Gradle 8.x

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Run tests
./gradlew test
```

### Signing Release Builds

Create a `gradle.properties` file in the project root with:

```properties
RELEASE_STORE_FILE=/path/to/keystore.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

---

## Architecture

```
com.orpheusdroid.screenrecorder/
├── adapter/           # RecyclerView adapters
├── folderpicker/      # Custom folder picker component
├── interfaces/        # Interface definitions
├── services/
│   ├── RecorderService.java     # Core recording service
│   └── QuickRecordTile.java     # Quick Settings tile
├── ui/
│   ├── MainActivity.java              # Main activity with tabs
│   ├── SettingsPreferenceFragment.java # Settings screen
│   ├── VideosListFragment.java        # Video gallery
│   ├── EditVideoActivity.java         # Video trimmer
│   └── ShortcutActionActivity.java    # App shortcut handler
├── Const.java         # Constants and enums
├── ScreenCamApp.java  # Application class
└── ScreenCamBaseApp.java
```

---

## Libraries Used

| Library | Purpose | License |
|---------|---------|---------|
| [AndroidX](https://developer.android.com/jetpack/androidx) | Core Android components | Apache 2.0 |
| [Material Components](https://material.io/develop/android) | Material Design UI | Apache 2.0 |
| [k4l-video-trimmer](https://github.com/nicbytes/k4l-video-trimmer) | Video trimming functionality | MIT |
| [Changelog](https://github.com/MFlisar/changelog) | Changelog display | Apache 2.0 |
| [DocumentFile](https://developer.android.com/reference/androidx/documentfile/provider/DocumentFile) | SAF support | Apache 2.0 |

---

## Technical Notes

### Audio Recording

This version of the app features simplified audio recording:
- **Microphone Only**: Record your voice along with screen video
- **Optimized Settings**: All audio parameters are hardcoded to optimal values
  - Bitrate: 192 kbps (excellent quality)
  - Channels: Mono (best for voice/commentary)
  - Sample Rate: Automatically uses the best rate supported by your device
  - Encoder: AAC for wide compatibility
- **No Configuration Needed**: Settings are pre-optimized for you

### MediaProjection

- Android 14+ requires `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` before obtaining MediaProjection
- Recording service runs as a foreground service with persistent notification
- Virtual display mirrors the physical display for recording

### Storage

- Uses Scoped Storage on Android 11+
- Supports Storage Access Framework (SAF) for custom directories
- Default save location: App-specific external directory

---

## FAQ

**Q: How do I record with audio?**  
A: Go to Settings → Audio Source and select "Microphone" to record your voice along with the screen.

**Q: Can I customize audio quality?**  
A: Audio quality is hardcoded to optimal values (192 kbps, mono, best sample rate) for the best balance of quality and file size.

**Q: Can I record in 4K?**  
A: Yes, if your device supports 4K display. Select "2160P (4K)" or "Native" in resolution settings.

**Q: Where are my recordings saved?**  
A: By default, recordings are saved in your device's Movies directory. You can change the save location in Settings.

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## Acknowledgments

- **Original Developer:** [Vijai](https://gitlab.com/vijai) - All credits for the original app concept and implementation
- **Original Repository:** [https://gitlab.com/vijai/screenrecorder](https://gitlab.com/vijai/screenrecorder)
- App icons design by the original team
- All contributors and translators from the original project
- Open source libraries used in this project

---

## License

This project is licensed under the **GNU General Public License v3.0** as established by the original developer, Vijai.

All license and copyright notices in the source code were written by Vijai. As per the GPL v3 license terms, this fork maintains the same license.

**All modifications, edits, and removals made to this fork have been done in full compliance with the license and copyright of the original creator.**

```
Copyright (c) 2016-2018 Vijai Chandra Prasad R.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
```

---

<p align="center">
  Originally created with ❤️ by <a href="https://gitlab.com/vijai">Vijai</a><br/>
  Revived and modernized for today's Android devices
</p>
