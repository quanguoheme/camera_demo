# Android 相机旋转与镜像演示项目 (Camera Demo)

[English](README.md) | [中文文档](README-ZH.md)

[![GitHub Repo](https://img.shields.io/badge/GitHub-quanguoheme%2Fcamera__demo-181717.svg?logo=github)](https://github.com/quanguoheme/camera_demo)
[![Android](https://img.shields.io/badge/Platform-Android%2014%2B-green.svg)](https://developer.android.com)
[![Gradle](https://img.shields.io/badge/Gradle-9.2.1-blue.svg)](https://gradle.org)
[![API](https://img.shields.io/badge/API-34%2B-orange.svg)](https://developer.android.com/about/dashboards)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

- **GitHub 仓库地址**：[https://github.com/quanguoheme/camera_demo](https://github.com/quanguoheme/camera_demo)
- **Git Clone 地址**：`https://github.com/quanguoheme/camera_demo.git`

本工程是一个基于 `android.hardware.Camera` (Camera 1 API) 与 `TextureView` 实现的高性能 Android 相机预览演示应用。
专门针对嵌入式开发板、工控主板（如瑞芯微 RK3566 / RK3568 / RK3588 等）及 800x480 横屏设备进行了全屏沉浸式预览与滑出抽屉式控制面板优化。

---

## 核心功能特性

1. **双变换模式对比与实现**：
   - **模式 1：TextureView Matrix 矩阵变换模式 (`TextureView.setTransform(Matrix)`)**
     - 基于 View 像素中心坐标 $(c_x, c_y)$ 进行仿射矩阵变换。
     - **画面宽高比防拉伸补偿**：在旋转 90° 或 270° 时，自动按以下缩放比例补偿非正方形视窗的轴向交换，确保旋转后画面不发生任何变形拉伸：
       $$\text{scale}_x = \frac{\text{height}}{\text{width}}, \quad \text{scale}_y = \frac{\text{width}}{\text{height}}$$
     - 支持 0° / 90° / 180° / 270° 无缝旋转 (`matrix.postRotate`)。
     - 支持水平镜像（左右）与垂直镜像（上下）矩阵翻转 (`matrix.postScale`)。
   - **模式 2：View Scale / setDisplayOrientation 原生模式**
     - 通过底层 API `camera.setDisplayOrientation(degrees)` 调整驱动层预览方向。
     - 通过视图层 API `textureView.setScaleX(-1f / 1f)` 与 `textureView.setScaleY(-1f / 1f)` 实现视图级翻转。
     - 便于与 Matrix 方案进行直观效果对比与兼容性排查。

2. **多摄像头枚举与动态切换**：
   - 支持自动检测所有可用物理与 USB 免驱摄像头 (`Camera.getNumberOfCameras()`)。
   - 一键循环切换摄像头（Camera ID: 0, 1, 2...）。
   - 自动遍历 `getSupportedPreviewSizes` 匹配最佳分辨率（800x480、720p、1080p 或 480p）。

3. **800x480 横屏沉浸式 UI 与右侧抽屉面板**：
   - **100% 全屏沉浸预览**：强制横屏（Landscape），启用沉浸式全屏（`Immersive Sticky`）隐藏系统状态栏与底部导航栏。
   - **右侧滑出抽屉（290dp 宽）**：点击右上角 `⚙ 控制面板` 平滑滑出。抽屉展开时左侧仍保留 510px 预览视野，调节参数时可实时观测画面变化。
   - **左上角轻量 HUD 浮层**：实时展示当前相机 ID、朝向、变换模式、旋转角度与镜像状态；支持点击标题快速折叠与展开。

---

## 项目工程结构

```text
camera_demo/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/camera_demo/
│   │   │   └── MainActivity.java       # 主活动：相机生命周期、Matrix 变换算法、抽屉动画与 UI 交互
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml   # 全屏 TextureView、HUD 浮层与右侧抽屉布局
│   │   │   └── values/
│   │   │       ├── strings.xml         # 中文文本资源
│   │   │       └── themes.xml          # 应用主题
│   │   └── AndroidManifest.xml         # 相机权限、硬件特性声明与横屏配置
│   └── build.gradle.kts                # 应用级构建配置 (Package: com.example.camera_demo)
├── docs/                               # 文档目录
│   └── README.md                       # 内部说明文档
├── gradle/                             # Gradle 包装器
├── build.gradle.kts                    # 根工程构建脚本
├── settings.gradle.kts                 # 根工程设置 (rootProject.name = "camera_demo")
├── README.md                           # 英文说明文档
└── README-ZH.md                        # 中文说明文档
```

---

## 核心实现代码说明

### 1. Matrix 矩阵旋转与防拉伸镜像实现

在 `MainActivity.java` 中，通过 `TextureView.setTransform(Matrix)` 统一完成旋转与镜像计算：

```java
Matrix matrix = new Matrix();
float cx = surfaceWidth / 2.0f;
float cy = surfaceHeight / 2.0f;

// 1. 旋转 90° 或 270° 时进行宽高比轴向对齐缩放补偿，防止画面变形拉伸
if (rotationDegrees == 90 || rotationDegrees == 270) {
    float scaleX = (float) surfaceHeight / surfaceWidth;
    float scaleY = (float) surfaceWidth / surfaceHeight;
    matrix.postScale(scaleX, scaleY, cx, cy);
}

// 2. 矩阵旋转
matrix.postRotate(rotationDegrees, cx, cy);

// 3. 矩阵镜像 (水平 / 垂直)
float mirrorScaleX = mirrorHorizontal ? -1.0f : 1.0f;
float mirrorScaleY = mirrorVertical ? -1.0f : 1.0f;
if (mirrorScaleX != 1.0f || mirrorScaleY != 1.0f) {
    matrix.postScale(mirrorScaleX, mirrorScaleY, cx, cy);
}

textureView.setTransform(matrix);
```

### 2. View Scale 与 setDisplayOrientation 模式实现

```java
// 重置 TextureView Matrix 为单位矩阵
textureView.setTransform(null);

// 调整相机底层输出方向
if (camera != null) {
    camera.setDisplayOrientation(rotationDegrees);
}

// View 层缩放镜像
textureView.setScaleX(mirrorHorizontal ? -1.0f : 1.0f);
textureView.setScaleY(mirrorVertical ? -1.0f : 1.0f);
```

---

## 编译与烧录运行指南

### 1. 克隆项目仓库

```powershell
git clone https://github.com/quanguoheme/camera_demo.git
cd camera_demo
```

### 2. 编译生成 APK

在项目根目录下通过 Gradle Wrapper 编译 Debug 包：

```powershell
.\gradlew.bat assembleDebug
```

编译成功后，APK 文件位于：
`app/build/outputs/apk/debug/app-debug.apk`

### 3. ADB 烧录安装

确保设备已通过 USB 连接并开启开发者调试模式：

```powershell
# 查看连接设备
adb devices

# 覆盖安装 APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 预先授权相机权限 (可选)
adb shell pm grant com.example.camera_demo android.permission.CAMERA
```

### 4. 启动应用

```powershell
adb shell am start -n com.example.camera_demo/.MainActivity
```

---

## 常见问题与排查 (FAQ)

1. **画面出现左右或上下颠倒：**
   - 打开右侧 `⚙ 控制面板`，勾选 `水平镜像 (左右)` 或 `垂直镜像 (上下)` 进行实时翻转。
   - 或者点击 `90°` / `180°` / `270°` 快速修正安装方向。
2. **外接免驱 USB 摄像头无法打开：**
   - 点击 `切换摄像头` 按钮依次遍历设备上挂载的 Camera ID（如 ID 0、ID 1 等）。
   - 查看左上角 HUD 浮层确认当前激活的 Camera ID 及传感器方向参数。
3. **沉浸式全屏退出：**
   - 本应用配置了 `Immersive Sticky`，从屏幕顶端或底端滑动可临时唤出系统导航栏，无操作后会自动恢复全屏。

---

## 开源协议

本项目采用 Apache 2.0 许可证。
