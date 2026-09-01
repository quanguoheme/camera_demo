package com.example.camera_demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String TAG = "CameraDemoActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;

    private TextView statusView;
    private Button actionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.tv_status);
        actionButton = findViewById(R.id.btn_action);

        actionButton.setOnClickListener(v -> {
            if (checkCameraPermission()) {
                startCamera();
            } else {
                requestCameraPermission();
            }
        });

        if (checkCameraPermission()) {
            updateUiForGranted();
        } else {
            updateUiForDenied();
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
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
                updateUiForGranted();
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                updateUiForDenied();
            }
        }
    }

    private void updateUiForGranted() {
        statusView.setText("Camera permission is granted. Ready for camera implementation.");
        actionButton.setText("Start Camera");
    }

    private void updateUiForDenied() {
        statusView.setText(R.string.msg_camera_permission_required);
        actionButton.setText(R.string.btn_request_permission);
    }

    private void startCamera() {
        // TODO: Initialize Camera2 / CameraX / UVC camera preview here
        statusView.setText("Camera started (placeholder). Add your camera logic here.");
    }
}
