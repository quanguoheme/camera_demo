package com.example.camera_demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 界面主 Activity：仅负责 UI 交互、控件事件与 HUD 状态展示，不直接操作底层 Camera 类。
 */
public final class MainActivity extends Activity implements CameraPreviewManager.OnCameraStatusListener {
    private static final int REQUEST_CAMERA_PERMISSION = 1001;

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

    private CameraPreviewManager cameraManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemUI();

        cameraManager = CameraPreviewManager.getInstance();
        cameraManager.setOnCameraStatusListener(this);

        initViews();
        setupListeners();

        if (checkCameraPermission()) {
            layoutPermissionPrompt.setVisibility(View.GONE);
            cameraManager.startPreview(textureView);
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
                cameraManager.setTransformMode(CameraPreviewManager.TransformMode.MATRIX);
            } else {
                cameraManager.setTransformMode(CameraPreviewManager.TransformMode.VIEW_SCALE_ORIENTATION);
            }
        });

        btnRot0.setOnClickListener(v -> cameraManager.setRotation(0));
        btnRot90.setOnClickListener(v -> cameraManager.setRotation(90));
        btnRot180.setOnClickListener(v -> cameraManager.setRotation(180));
        btnRot270.setOnClickListener(v -> cameraManager.setRotation(270));

        cbMirrorH.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cameraManager.setMirror(isChecked, cbMirrorV.isChecked());
        });

        cbMirrorV.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cameraManager.setMirror(cbMirrorH.isChecked(), isChecked);
        });

        btnSwitchCamera.setOnClickListener(v -> cameraManager.switchCamera());

        btnReset.setOnClickListener(v -> {
            cbMirrorH.setChecked(false);
            cbMirrorV.setChecked(false);
            cameraManager.resetTransforms();
        });
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
                cameraManager.startPreview(textureView);
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
        if (checkCameraPermission()) {
            cameraManager.startPreview(textureView);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraManager.stopPreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraManager.stopPreview();
    }

    // ==================== CameraPreviewManager.OnCameraStatusListener ====================

    @Override
    public void onStatusChanged(String statusText) {
        if (tvStatusInfo != null) {
            tvStatusInfo.setText(statusText);
        }
    }

    @Override
    public void onToastMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
