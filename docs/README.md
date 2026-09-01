# Camera Demo (Android 相机旋转与镜像演示项目)

本工程是一个基于 `android.hardware.Camera` (Camera 1 API) 与 `TextureView` 实现的 Android 相机演示应用。
专门针对嵌入式设备、工控主板（如 RK3566、RK3568 等）及 800x480 横屏设备进行了全屏预览与抽屉式控制面板深度优化。

---

## 核心功能特性

1. **底层相机控制 (`android.hardware.Camera`)**：
   - **多摄像头枚举与切换**：支持遍历并动态切换设备上挂载的所有物理/USB 摄像头（Camera ID 0, 1, 2...）。
   - **分辨率智能自适应**：遍历设备 `getSupportedPreviewSizes`，优先匹配 800x480、720p (1280x720)、1080p (1920x1080) 或 480p (640x480) 分辨率。
   - **安全生命周期管理**：结合 Activity `onResume` / `onPause` 与 `SurfaceTextureListener`，保证相机资源安全释放与平滑重建。

2. **双变换模式对比与实现**：
   - **模式 1：Matrix 矩阵变换模式 (`TextureView.setTransform(Matrix)`)**
     - 基于 View 像素中心坐标 `(cx, cy)` 进行仿射变换。
     - **画面宽高比自适应补偿**：在旋转 90° 或 270° 时，自动按 `scaleX = surfaceHeight / surfaceWidth` 与 `scaleY = surfaceWidth / surfaceHeight` 补偿非正方形视窗的轴向交换，确保旋转后画面不拉伸变形。
     - 支持 `matrix.postRotate(...)` 实现 0° / 90° / 180° / 270° 自由旋转。
     - 支持 `matrix.postScale(...)` 实现水平镜像 (左右) 与垂直镜像 (上下)。
   - **模式 2：View Scale / setDisplayOrientation 原生模式**
     - 通过系统 API `camera.setDisplayOrientation(degrees)` 调整相机驱动层画面方向。
     - 通过视图层 API `textureView.setScaleX(-1f / 1f)` 与 `textureView.setScaleY(-1f / 1f)` 实现视图翻转。
     - 用于与 Matrix 矩阵方案进行直观的效果对比与兼容性排查。

3. **800x480 横屏沉浸式 UI 与右侧抽屉控制面板**：
   - **100% 全屏沉浸预览**：强制横屏（Landscape），启用沉浸式全屏（`Immersive Sticky`）隐藏系统状态栏与底部导航栏。
   - **右侧滑出抽屉（290dp 宽）**：点击右上角 `⚙ 控制面板` 平滑滑出。抽屉展开时左侧仍保留 510px 预览视野，调节参数时可实时观测画面变化。
   - **左上角轻量 HUD 浮层**：实时展示核心状态（相机 ID、朝向、变换模式、旋转角度与镜像状态），支持点击标题快速折叠与展开。

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
│   └── README.md                       # 本说明文档
├── gradle/                             # Gradle 包装器
├── build.gradle.kts                    # 根工程构建脚本
└── settings.gradle.kts                 # 根工程设置 (rootProject.name = "camera_demo")
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

### 1. 编译生成 APK

在项目根目录下通过 Gradle Wrapper 编译 Debug 包：

```powershell
.\gradlew.bat assembleDebug
```

编译成功后，APK 文件位于：
`app/build/outputs/apk/debug/app-debug.apk`

### 2. ADB 烧录安装

确保设备已通过 USB 连接并开启开发者调试模式：

```powershell
# 查看连接设备
adb devices

# 覆盖安装 APK
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 预先授权相机权限 (可选)
adb shell pm grant com.example.camera_demo android.permission.CAMERA
```

### 3. 启动应用

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
