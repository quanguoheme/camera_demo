package com.example.camera_demo;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.TextureView;

import java.util.List;

/**
 * 相机管理类：封装底层 Camera 硬件交互、分辨率自适应、看门狗断流监测与热插拔自动重连。
 */
@SuppressWarnings("deprecation")
public class CameraPreviewManager implements TextureView.SurfaceTextureListener {
    private static final String TAG = "CameraPreviewManager";

    public enum TransformMode {
        MATRIX,
        VIEW_SCALE_ORIENTATION
    }

    public interface OnCameraStatusListener {
        void onStatusChanged(String statusText);
        void onToastMessage(String message);
    }

    private static volatile CameraPreviewManager instance;

    // 单例模式
    public static CameraPreviewManager getInstance() {
        if (instance == null) {
            synchronized (CameraPreviewManager.class) {
                if (instance == null) {
                    instance = new CameraPreviewManager();
                }
            }
        }
        return instance;
    }

    private TextureView textureView;
    private SurfaceTexture currentSurfaceTexture;
    private int surfaceWidth = 0;
    private int surfaceHeight = 0;

    // 相机状态
    private Camera camera;
    private int currentCameraId = 0;
    private int numberOfCameras = 0;
    private Camera.Size previewSize;

    // 变换参数
    private TransformMode currentMode = TransformMode.MATRIX;
    private int rotationDegrees = 0;
    private boolean mirrorHorizontal = false;
    private boolean mirrorVertical = false;

    // 看门狗与热插拔重试
    private static final long FRAME_TIMEOUT_MS = 10_000L; // 10秒无画面输出判定超时
    private static final long RETRY_INTERVAL_MS = 1_000L;  // 重连重试间隔 1 秒
    private static final long WATCHDOG_INTERVAL_MS = 1_000L; // 看门狗轮询间隔 1 秒

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile long lastFrameTimestamp = 0L;
    private volatile boolean isCameraStarted = false;
    private volatile boolean isReconnecting = false;
    private int retryCount = 0;

    private OnCameraStatusListener statusListener;

    private CameraPreviewManager() {
    }

    public void setOnCameraStatusListener(OnCameraStatusListener listener) {
        this.statusListener = listener;
        updateStatus(isCameraStarted ? "预览中" : "未启动");
    }

    /**
     * 绑定 TextureView 并启动预览
     */
    public void startPreview(TextureView view) {
        this.textureView = view;
        cancelReconnect();

        this.textureView.setSurfaceTextureListener(this);
        if (textureView.isAvailable()) {
            currentSurfaceTexture = textureView.getSurfaceTexture();
            surfaceWidth = textureView.getWidth();
            surfaceHeight = textureView.getHeight();
            startCameraInternal();
        }
    }

    /**
     * 停止预览并释放资源
     */
    public void stopPreview() {
        cancelReconnect();
        stopCamera();
    }

    /**
     * 切换前后/外置摄像头
     */
    public void switchCamera() {
        numberOfCameras = Camera.getNumberOfCameras();
        if (numberOfCameras <= 1) {
            if (statusListener != null) {
                statusListener.onToastMessage("仅有 " + numberOfCameras + " 个可用摄像头");
            }
            return;
        }
        cancelReconnect();
        currentCameraId = (currentCameraId + 1) % numberOfCameras;
        stopCamera();
        startCameraInternal();
    }

    public void setTransformMode(TransformMode mode) {
        this.currentMode = mode;
        applyTransform();
    }

    public void setRotation(int degrees) {
        this.rotationDegrees = degrees;
        applyTransform();
    }

    public void setMirror(boolean horizontal, boolean vertical) {
        this.mirrorHorizontal = horizontal;
        this.mirrorVertical = vertical;
        applyTransform();
    }

    public void resetTransforms() {
        this.rotationDegrees = 0;
        this.mirrorHorizontal = false;
        this.mirrorVertical = false;
        applyTransform();
    }

    public int getCurrentCameraId() {
        return currentCameraId;
    }

    public int getNumberOfCameras() {
        return numberOfCameras;
    }

    // ==================== TextureView.SurfaceTextureListener ====================

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        currentSurfaceTexture = surface;
        surfaceWidth = width;
        surfaceHeight = height;
        startCameraInternal();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        applyTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        currentSurfaceTexture = null;
        stopCamera();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        lastFrameTimestamp = SystemClock.uptimeMillis();
        if (isReconnecting) {
            isReconnecting = false;
            retryCount = 0;
            mainHandler.removeCallbacks(retryConnectRunnable);
            Log.i(TAG, "Camera frame received! Reconnection successful.");
            updateStatus("预览中");
        }
    }

    // ==================== 看门狗与重连调度 ====================

    private void startWatchdog() {
        mainHandler.removeCallbacks(watchdogRunnable);
        mainHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS);
    }

    private void stopWatchdog() {
        mainHandler.removeCallbacks(watchdogRunnable);
    }

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (isCameraStarted && !isReconnecting) {
                long now = SystemClock.uptimeMillis();
                if (lastFrameTimestamp > 0 && (now - lastFrameTimestamp >= FRAME_TIMEOUT_MS)) {
                    Log.w(TAG, "No camera frame received for " + (now - lastFrameTimestamp) + "ms. Triggering reconnect.");
                    triggerReconnect("超过 10 秒无画面输出");
                    return;
                }
            }
            if (isCameraStarted) {
                mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS);
            }
        }
    };

    private void triggerReconnect(String reason) {
        if (isReconnecting) {
            return;
        }
        isReconnecting = true;
        retryCount = 0;
        Log.w(TAG, "triggerReconnect: " + reason);

        stopCamera();
        updateStatus("无画面输出，正在重试连接 (1s)...");

        mainHandler.removeCallbacks(retryConnectRunnable);
        mainHandler.postDelayed(retryConnectRunnable, RETRY_INTERVAL_MS);
    }

    private final Runnable retryConnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isReconnecting) {
                return;
            }
            retryCount++;
            numberOfCameras = Camera.getNumberOfCameras();
            Log.d(TAG, "Retrying camera #" + currentCameraId + " (attempt " + retryCount + "), total: " + numberOfCameras);

            if (currentSurfaceTexture != null && numberOfCameras > 0 && currentCameraId < numberOfCameras) {
                boolean success = startCameraInternal();
                if (success) {
                    Log.i(TAG, "Camera reopened successfully, waiting for frame output...");
                    updateStatus(String.format("相机已重新打开，等待画面输出 (第 %d 次)...", retryCount));
                    startWatchdog();
                    return;
                }
            }

            updateStatus(String.format("无画面输出，正在重试连接 (第 %d 次)...", retryCount));
            mainHandler.postDelayed(this, RETRY_INTERVAL_MS);
        }
    };

    private void cancelReconnect() {
        isReconnecting = false;
        retryCount = 0;
        mainHandler.removeCallbacks(retryConnectRunnable);
    }

    // ==================== 底层相机控制 ====================

    private boolean startCameraInternal() {
        if (camera != null || currentSurfaceTexture == null) {
            return false;
        }

        numberOfCameras = Camera.getNumberOfCameras();
        if (numberOfCameras == 0 || currentCameraId >= numberOfCameras) {
            updateStatus(String.format("相机未就绪 (ID: %d, 可用总数: %d)", currentCameraId, numberOfCameras));
            if (!isReconnecting) {
                triggerReconnect("相机设备未就绪");
            }
            return false;
        }

        try {
            camera = Camera.open(currentCameraId);
            if (camera == null) {
                updateStatus("打开摄像头失败 (ID: " + currentCameraId + ")");
                if (!isReconnecting) {
                    triggerReconnect("打开摄像头失败");
                }
                return false;
            }

            camera.setErrorCallback((error, cam) -> {
                Log.e(TAG, "Camera error callback received: " + error);
                mainHandler.post(() -> triggerReconnect("相机硬件错误 (" + error + ")"));
            });

            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> supportedSizes = parameters.getSupportedPreviewSizes();
            previewSize = chooseOptimalPreviewSize(supportedSizes, surfaceWidth, surfaceHeight);

            if (previewSize != null) {
                parameters.setPreviewSize(previewSize.width, previewSize.height);
            }

            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }

            camera.setParameters(parameters);
            camera.setPreviewTexture(currentSurfaceTexture);

            applyTransform();

            camera.startPreview();
            isCameraStarted = true;
            lastFrameTimestamp = SystemClock.uptimeMillis();
            startWatchdog();

            if (!isReconnecting) {
                updateStatus("预览中");
            }
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera", e);
            updateStatus("启动相机异常: " + e.getMessage());
            stopCamera();
            if (!isReconnecting) {
                triggerReconnect("启动异常: " + e.getMessage());
            }
            return false;
        }
    }

    private void stopCamera() {
        isCameraStarted = false;
        stopWatchdog();
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.setPreviewTexture(null);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping camera", e);
            }
            try {
                camera.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing camera", e);
            }
            camera = null;
        }
    }

    // ==================== 分辨率与矩阵变换 ====================

    private Camera.Size chooseOptimalPreviewSize(List<Camera.Size> sizes, int targetWidth, int targetHeight) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }

        // 优先匹配 800x480 或常见的 720p / 1080p / 480p 分辨率
        for (Camera.Size size : sizes) {
            if (size.width == 800 && size.height == 480) {
                return size;
            }
        }
        for (Camera.Size size : sizes) {
            if (size.width == 1280 && size.height == 720) {
                return size;
            }
        }
        for (Camera.Size size : sizes) {
            if (size.width == 1920 && size.height == 1080) {
                return size;
            }
        }
        for (Camera.Size size : sizes) {
            if (size.width == 640 && size.height == 480) {
                return size;
            }
        }

        return sizes.get(0);
    }

    private void applyTransform() {
        if (textureView == null || surfaceWidth == 0 || surfaceHeight == 0) {
            return;
        }

        if (currentMode == TransformMode.MATRIX) {
            // 模式 1：TextureView Matrix 矩阵变换
            textureView.setScaleX(1.0f);
            textureView.setScaleY(1.0f);

            if (camera != null) {
                try {
                    camera.setDisplayOrientation(0);
                } catch (Exception e) {
                    Log.e(TAG, "setDisplayOrientation error", e);
                }
            }

            Matrix matrix = new Matrix();
            float cx = surfaceWidth / 2.0f;
            float cy = surfaceHeight / 2.0f;

            // 旋转 90° 或 270° 时进行宽高比轴向对齐缩放补偿，防止画面变形拉伸
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                float scaleX = (float) surfaceHeight / surfaceWidth;
                float scaleY = (float) surfaceWidth / surfaceHeight;
                matrix.postScale(scaleX, scaleY, cx, cy);
            }

            // 矩阵旋转
            matrix.postRotate(rotationDegrees, cx, cy);

            // 矩阵镜像
            float mirrorScaleX = mirrorHorizontal ? -1.0f : 1.0f;
            float mirrorScaleY = mirrorVertical ? -1.0f : 1.0f;
            if (mirrorScaleX != 1.0f || mirrorScaleY != 1.0f) {
                matrix.postScale(mirrorScaleX, mirrorScaleY, cx, cy);
            }

            textureView.setTransform(matrix);

        } else {
            // 模式 2：View Scale & setDisplayOrientation 变换
            textureView.setTransform(null);

            if (camera != null) {
                try {
                    camera.setDisplayOrientation(rotationDegrees);
                } catch (Exception e) {
                    Log.e(TAG, "setDisplayOrientation error", e);
                }
            }

            textureView.setScaleX(mirrorHorizontal ? -1.0f : 1.0f);
            textureView.setScaleY(mirrorVertical ? -1.0f : 1.0f);
        }

        if (!isReconnecting) {
            updateStatus("预览中");
        }
    }

    private void updateStatus(String state) {
        if (statusListener == null) {
            return;
        }

        Camera.CameraInfo info = new Camera.CameraInfo();
        String facingStr = "未知";
        int sensorOrientation = 0;
        try {
            Camera.getCameraInfo(currentCameraId, info);
            sensorOrientation = info.orientation;
            facingStr = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) ? "前置" :
                    (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) ? "后置" : "外置/其他";
        } catch (Exception ignored) {
        }

        String resStr = (previewSize != null) ? (previewSize.width + "x" + previewSize.height) : "未知";
        String modeStr = (currentMode == TransformMode.MATRIX) ? "Matrix" : "View Scale/Orientation";

        String infoText = String.format(
                "状态: %s | 相机: #%d (%s, %d°)\n" +
                "模式: %s | 分辨率: %s | 视窗: %dx%d\n" +
                "旋转: %d° | 水平镜像: %s | 垂直镜像: %s",
                state, currentCameraId, facingStr, sensorOrientation,
                modeStr, resStr, surfaceWidth, surfaceHeight,
                rotationDegrees, mirrorHorizontal ? "开" : "关", mirrorVertical ? "开" : "关"
        );

        statusListener.onStatusChanged(infoText);
    }
}
