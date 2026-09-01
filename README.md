# Android Camera Rotation & Mirror Demo

[English](README.md) | [中文文档](README-ZH.md)

[![GitHub Repo](https://img.shields.io/badge/GitHub-quanguoheme%2Fcamera__demo-181717.svg?logo=github)](https://github.com/quanguoheme/camera_demo)
[![Android](https://img.shields.io/badge/Platform-Android%2014%2B-green.svg)](https://developer.android.com)
[![Gradle](https://img.shields.io/badge/Gradle-9.2.1-blue.svg)](https://gradle.org)
[![API](https://img.shields.io/badge/API-34%2B-orange.svg)](https://developer.android.com/about/dashboards)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

- **GitHub Repository**: [https://github.com/quanguoheme/camera_demo](https://github.com/quanguoheme/camera_demo)
- **Clone URL**: `https://github.com/quanguoheme/camera_demo.git`

A robust Android camera preview demonstration application built on top of `android.hardware.Camera` (Camera 1 API) and `TextureView`.
Designed and optimized for embedded devices, industrial motherboards (e.g. Rockchip RK3566 / RK3568 / RK3588), and 800x480 landscape touchscreens.

---

## Key Features

1. **Dual Transformation Modes Comparison**:
   - **Mode 1: TextureView Matrix Transformation (`TextureView.setTransform(Matrix)`)**
     - Calculates affine matrix transformations around the View's center coordinate $(c_x, c_y)$.
     - **Aspect Ratio Distortion Compensation**: When rotating 90° or 270° in non-square views, axes swap automatically with scale factors:
       $$\text{scale}_x = \frac{\text{height}}{\text{width}}, \quad \text{scale}_y = \frac{\text{width}}{\text{height}}$$
       This guarantees no image stretching or aspect ratio distortion.
     - Supports seamless 0° / 90° / 180° / 270° rotation (`matrix.postRotate`).
     - Supports horizontal mirroring (left-right) and vertical mirroring (up-down) via `matrix.postScale`.
   - **Mode 2: Native View Scale & setDisplayOrientation**
     - Adjusts hardware preview orientation via `camera.setDisplayOrientation(degrees)`.
     - Flips view axes via `textureView.setScaleX(-1f / 1f)` and `textureView.setScaleY(-1f / 1f)`.
     - Perfect for side-by-side comparison and HAL compatibility verification.

2. **Multi-Camera Enumeration & Dynamic Switching**:
   - Automatically detects all available hardware/USB cameras (`Camera.getNumberOfCameras()`).
   - One-touch cyclic camera switching (ID: 0, 1, 2...).
   - Automatically queries `getSupportedPreviewSizes` and selects the best resolution (800x480, 720p, 1080p, or 480p).

3. **800x480 Landscape Optimized UI**:
   - **100% Immersive Fullscreen**: Locked in landscape orientation with `Immersive Sticky` mode (hides system status bar and navigation bar).
   - **Right Slide-out Drawer Panel (290dp)**: Tap the floating `⚙ Control Panel` button to open. The left 510px preview area remains visible so you can observe visual changes in real time while tuning parameters.
   - **Top-Left Lightweight HUD**: Displays camera ID, sensor orientation, current transformation mode, rotation angle, and mirror toggles. Click to fold/unfold.

---

## Project Structure

```text
camera_demo/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/camera_demo/
│   │   │   └── MainActivity.java       # Core camera lifecycle, Matrix math & drawer UI logic
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml   # Fullscreen TextureView, HUD & drawer layout
│   │   │   └── values/
│   │   │       ├── strings.xml         # String resources
│   │   │       └── themes.xml          # Theme definitions
│   │   └── AndroidManifest.xml         # Camera permissions & landscape configurations
│   └── build.gradle.kts                # App build configuration (com.example.camera_demo)
├── docs/                               # Documentation & Reference files
│   └── README.md                       # Chinese detailed documentation
├── gradle/                             # Gradle wrapper & version catalog
├── build.gradle.kts                    # Root build script
├── settings.gradle.kts                 # Project settings (rootProject.name = "camera_demo")
├── README.md                           # English documentation
└── README-ZH.md                        # Chinese documentation
```

---

## Core Transformation Implementation

### TextureView Matrix Transformation (Anti-distortion)

In `MainActivity.java`:

```java
Matrix matrix = new Matrix();
float cx = surfaceWidth / 2.0f;
float cy = surfaceHeight / 2.0f;

// 1. Compensate for aspect ratio swap when rotating 90 or 270 degrees in rectangular view
if (rotationDegrees == 90 || rotationDegrees == 270) {
    float scaleX = (float) surfaceHeight / surfaceWidth;
    float scaleY = (float) surfaceWidth / surfaceHeight;
    matrix.postScale(scaleX, scaleY, cx, cy);
}

// 2. Apply rotation
matrix.postRotate(rotationDegrees, cx, cy);

// 3. Apply horizontal / vertical mirroring
float mirrorScaleX = mirrorHorizontal ? -1.0f : 1.0f;
float mirrorScaleY = mirrorVertical ? -1.0f : 1.0f;
if (mirrorScaleX != 1.0f || mirrorScaleY != 1.0f) {
    matrix.postScale(mirrorScaleX, mirrorScaleY, cx, cy);
}

textureView.setTransform(matrix);
```

---

## Quick Start & Build Instructions

### 1. Clone Repository

```powershell
git clone https://github.com/quanguoheme/camera_demo.git
cd camera_demo
```

### 2. Build APK

Run the Gradle wrapper to build the debug APK:

```powershell
.\gradlew.bat assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Install via ADB

Ensure your Android device is connected with ADB debugging enabled:

```powershell
# Verify connected device
adb devices

# Install APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Grant camera permission (Optional, auto requested on first launch)
adb shell pm grant com.example.camera_demo android.permission.CAMERA
```

### 4. Launch Application

```powershell
adb shell am start -n com.example.camera_demo/.MainActivity
```

---

## FAQ

- **Q: How to fix an inverted/upside-down camera preview?**
  - Open the right `⚙ Control Panel`, toggle `Horizontal Mirror` or `Vertical Mirror`, or select the `90°` / `180°` / `270°` buttons.
- **Q: How to use external USB UVC cameras?**
  - Click `Switch Camera` to cycle through registered hardware camera IDs (`#0`, `#1`, etc.).
- **Q: How to bring back system navigation bars in full-screen mode?**
  - Swipe inward from the screen edge to temporarily show system navigation bars.

---

## License

This project is licensed under the Apache 2.0 License.
