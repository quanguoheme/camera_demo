package com.example.camera_demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

@SuppressWarnings("deprecation")
public final class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "CameraDemoActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;

    // 变换模式
    private enum TransformMode {
        MATRIX,
        VIEW_SCALE_ORIENTATION
    }

    private TextureView textureView;
    private LinearLayout layoutPermissionPrompt;
    private Button btnGrantPermission;

    // HUD 状态
    private LinearLayout layoutHud;
    private TextView tvHudTitle;
    private TextView tvStatusInfo;
    private boolean isHudCollapsed = false;

    // 抽屉控件
    private Button btnOpenDrawer;
    private View viewDrawerMask;
    private LinearLayout layoutDrawer;
    private Button btnCloseDrawer;
    private boolean isDrawerOpen = false;

    // 抽屉内部控制项
    private RadioGroup rgMode;
    private RadioButton rbModeMatrix;
    private RadioButton rbModeViewScale;

    private Button btnRot0;
    private Button btnRot90;
    private Button btnRot180;
    private Button btnRot270;

    private CheckBox cbMirrorH;
    private CheckBox cbMirrorV;

    private Button btnSwitchCamera;
    private Button btnReset;

    // 相机与预览状态
    private Camera camera;
    private int currentCameraId = 0;
    private int numberOfCameras = 0;
    private Camera.Size previewSize;
    private SurfaceTexture currentSurfaceTexture;
    private int surfaceWidth = 0;
    private int surfaceHeight = 0;

    // 变换参数
    private TransformMode currentMode = TransformMode.MATRIX;
    private int rotationDegrees = 0; // 0, 90, 180, 270
    private boolean mirrorHorizontal = false;
    private boolean mirrorVertical = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemUI();

        initViews();
        setupListeners();

        numberOfCameras = Camera.getNumberOfCameras();
        if (numberOfCameras == 0) {
            tvStatusInfo.setText("未检测到可用摄像头设备");
            return;
        }

        if (checkCameraPermission()) {
            layoutPermissionPrompt.setVisibility(View.GONE);
        } else {
            layoutPermissionPrompt.setVisibility(View.VISIBLE);
            requestCameraPermission();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void initViews() {
        textureView = findViewById(R.id.camera_texture_view);
        layoutPermissionPrompt = findViewById(R.id.layout_permission_prompt);
        btnGrantPermission = findViewById(R.id.btn_grant_permission);

        layoutHud = findViewById(R.id.layout_hud);
        tvHudTitle = findViewById(R.id.tv_hud_title);
        tvStatusInfo = findViewById(R.id.tv_status_info);

        btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        viewDrawerMask = findViewById(R.id.view_drawer_mask);
        layoutDrawer = findViewById(R.id.layout_drawer);
        btnCloseDrawer = findViewById(R.id.btn_close_drawer);

        rgMode = findViewById(R.id.rg_mode);
        rbModeMatrix = findViewById(R.id.rb_mode_matrix);
        rbModeViewScale = findViewById(R.id.rb_mode_view_scale);

        btnRot0 = findViewById(R.id.btn_rot_0);
        btnRot90 = findViewById(R.id.btn_rot_90);
        btnRot180 = findViewById(R.id.btn_rot_180);
        btnRot270 = findViewById(R.id.btn_rot_270);

        cbMirrorH = findViewById(R.id.cb_mirror_h);
        cbMirrorV = findViewById(R.id.cb_mirror_v);

        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnReset = findViewById(R.id.btn_reset);

        textureView.setSurfaceTextureListener(this);
    }

    private void setupListeners() {
        btnGrantPermission.setOnClickListener(v -> requestCameraPermission());

        // HUD 折叠/展开
        layoutHud.setOnClickListener(v -> toggleHud());

        // 抽屉展开/收起
        btnOpenDrawer.setOnClickListener(v -> openDrawer());
        btnCloseDrawer.setOnClickListener(v -> closeDrawer());
        viewDrawerMask.setOnClickListener(v -> closeDrawer());

        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_mode_matrix) {
                currentMode = TransformMode.MATRIX;
            } else {
                currentMode = TransformMode.VIEW_SCALE_ORIENTATION;
            }
            applyTransform();
        });

        btnRot0.setOnClickListener(v -> setRotation(0));
        btnRot90.setOnClickListener(v -> setRotation(90));
        btnRot180.setOnClickListener(v -> setRotation(180));
        btnRot270.setOnClickListener(v -> setRotation(270));

        cbMirrorH.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mirrorHorizontal = isChecked;
            applyTransform();
        });

        cbMirrorV.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mirrorVertical = isChecked;
            applyTransform();
        });

        btnSwitchCamera.setOnClickListener(v -> switchCamera());

        btnReset.setOnClickListener(v -> resetTransforms());
    }

    private void toggleHud() {
        isHudCollapsed = !isHudCollapsed;
        tvStatusInfo.setVisibility(isHudCollapsed ? View.GONE : View.VISIBLE);
        tvHudTitle.setText(isHudCollapsed ? "📊 相机状态 (点击展开)" : getString(R.string.hud_title));
    }

    private void openDrawer() {
        if (isDrawerOpen) return;
        isDrawerOpen = true;

        viewDrawerMask.setVisibility(View.VISIBLE);
        viewDrawerMask.setAlpha(0f);
        viewDrawerMask.animate().alpha(1f).setDuration(220).start();

        layoutDrawer.setVisibility(View.VISIBLE);
        float drawerWidth = layoutDrawer.getWidth() > 0 ? layoutDrawer.getWidth() : (290 * getResources().getDisplayMetrics().density);
        layoutDrawer.setTranslationX(drawerWidth);
        layoutDrawer.animate()
                .translationX(0f)
                .setDuration(220)
                .setListener(null)
                .start();
    }

    private void closeDrawer() {
        if (!isDrawerOpen) return;
        isDrawerOpen = false;

        viewDrawerMask.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            viewDrawerMask.setVisibility(View.GONE);
        }).start();

        float drawerWidth = layoutDrawer.getWidth() > 0 ? layoutDrawer.getWidth() : (290 * getResources().getDisplayMetrics().density);
        layoutDrawer.animate()
                .translationX(drawerWidth)
                .setDuration(180)
                .withEndAction(() -> {
                    layoutDrawer.setVisibility(View.GONE);
                })
                .start();
    }

    @Override
    public void onBackPressed() {
        if (isDrawerOpen) {
            closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    private void setRotation(int degrees) {
        rotationDegrees = degrees;
        applyTransform();
    }

    private void switchCamera() {
        if (numberOfCameras <= 1) {
            Toast.makeText(this, "仅有 1 个可用摄像头", Toast.LENGTH_SHORT).show();
            return;
        }
        currentCameraId = (currentCameraId + 1) % numberOfCameras;
        stopCamera();
        startCamera();
    }

    private void resetTransforms() {
        rotationDegrees = 0;
        mirrorHorizontal = false;
        mirrorVertical = false;
        cbMirrorH.setChecked(false);
        cbMirrorV.setChecked(false);
        applyTransform();
    }

    private boolean checkCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                layoutPermissionPrompt.setVisibility(View.GONE);
                startCamera();
            } else {
                layoutPermissionPrompt.setVisibility(View.VISIBLE);
                Toast.makeText(this, "需要相机权限才能演示", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (checkCameraPermission() && textureView.isAvailable()) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCamera();
    }

    // TextureView.SurfaceTextureListener 实现
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        currentSurfaceTexture = surface;
        surfaceWidth = width;
        surfaceHeight = height;
        if (checkCameraPermission()) {
            startCamera();
        }
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
    }

    private void startCamera() {
        if (camera != null || currentSurfaceTexture == null) {
            return;
        }

        try {
            camera = Camera.open(currentCameraId);
            if (camera == null) {
                updateStatus("打开摄像头失败 (ID: " + currentCameraId + ")");
                return;
            }

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
            updateStatus("预览中");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera", e);
            updateStatus("启动相机异常: " + e.getMessage());
            stopCamera();
        }
    }

    private void stopCamera() {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.setPreviewTexture(null);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping camera", e);
            }
            camera.release();
            camera = null;
        }
    }

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
        if (surfaceWidth == 0 || surfaceHeight == 0) {
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

        updateStatus("运行中");
    }

    private void updateStatus(String state) {
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

        String modeStr = (currentMode == TransformMode.MATRIX) ? "Matrix" : "View Scale/Orientation";

        String infoText = String.format(
                "状态: %s | 相机: #%d (%s, %d°)\n" +
                "模式: %s | 旋转: %d°\n" +
                "水平镜像: %s | 垂直镜像: %s",
                state, currentCameraId, facingStr, sensorOrientation,
                modeStr, rotationDegrees,
                mirrorHorizontal ? "开" : "关", mirrorVertical ? "开" : "关"
        );

        tvStatusInfo.setText(infoText);
    }
}

