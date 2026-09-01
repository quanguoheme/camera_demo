package com.example.huck.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;
import android.view.TextureView;

import java.io.IOException;
import java.util.List;

import android_serialport_api.sample3.PreviewTexture;

/**
 * Time: 2019/1/24
 * Author: v_chaixiaogang
 * Description:
 */
public class CameraPreviewManager2   {

    private static final String TAG = "camera_preview";
   final int mCameraNum=1;
    private TextureView mTextureView;
    private PreviewTexture mPreview;
    private Camera mCamera;

    public static final int CAMERA_FACING_BACK = 0;
    public static final int CAMERA_FACING_FRONT = 1;

    /**
     * 当前相机的ID。
     */
    private int cameraFacing = CAMERA_FACING_BACK;



    private int mWidth;
    private int mHeight;




    private int displayOrientation = 0;
    private int cameraId = 0;
    private int mirror = 1; // 镜像处理

    public void setmCameraDataCallback(BitmapProcessor.BitmapProcessorCallback mCameraDataCallback) {
        this.mCameraDataCallback = mCameraDataCallback;
    }

    private BitmapProcessor.BitmapProcessorCallback mCameraDataCallback;
    private static volatile CameraPreviewManager2 instance = null;

    public static CameraPreviewManager2 getInstance() {
        synchronized (CameraPreviewManager2.class) {
            if (instance == null) {
                instance = new CameraPreviewManager2();
            }
        }
        return instance;
    }


    public void setCameraFacing(int cameraFacing) {
        this.cameraFacing = cameraFacing;
    }



    /**
     * 开启预览
     * @param textureView
     */
    public void startPreview (Context context, TextureView textureView, int width,
                              int height/*, CameraDataCallback cameraDataCallback*/) {
        Log.e(TAG, "开启预览模式");
        mWidth=width;
        this.previewWidth = width;
        this.previewHeight = height;
        mHeight=height;
        mTextureView=textureView;
      //  mCamera =  Camera.open(this.cameraFacing );
        mPreview = new PreviewTexture(context, textureView);
    }



    /**
     * 关闭预览
     */
    public void stopPreview() {
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mPreview.release();
                mCamera.release();
                mCamera = null;

            } catch (Exception e) {
                Log.e("qing", "camera destory error");
                e.printStackTrace();

            }
        }
    }
int previewWidth =720;
int previewHeight =1280;
BitmapProcessor mBitmapProcessor=new BitmapProcessor();
    /**
     * 开启摄像头
     */

    public void openCamera() {


        try {
            if (mCamera == null) {

                cameraId = cameraFacing;
                mCamera = Camera.open(cameraId);
                mPreview .setCamera(mCamera , mWidth, mHeight);
                Log.e(TAG, "initCamera---open camera");
            }


            Camera.Parameters params = mCamera.getParameters();
            List<Camera.Size> sizeList = params.getSupportedPreviewSizes(); // 获取所有支持的camera尺寸
            final Camera.Size optionSize = getOptimalPreviewSize(sizeList, previewWidth, previewHeight); // 获取一个最为适配的camera.size
            if (optionSize.width == previewWidth && optionSize.height == previewHeight) {

            } else {
                previewWidth = optionSize.width;
                previewHeight = optionSize.height;
            }
            params.setPreviewSize(previewWidth, previewHeight);

            mCamera.setParameters(params);

            mCamera.startPreview();

            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    mBitmapProcessor.processCameraFrame(data,camera,cameraId,mCameraDataCallback);
                }
            });
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
            }

    }
    private long time = 0;
    private int i = 0;


    private int getCameraDisplayOrientation(int degrees, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation + degrees) % 360;
            rotation = (360 - rotation) % 360; // compensate the mirror
        } else { // back-facing
            rotation = (info.orientation - degrees + 360) % 360;
        }
        return rotation;
    }


    /**
     * 解决预览变形问题
     *
     * @param sizes
     * @param w
     * @param h
     * @return
     */
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double aspectTolerance = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) {
            return null;
        }
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > aspectTolerance) {
                continue;
            }
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
    int a = 0;
    public void huan(){
        if (mCamera!=null) {
            mCamera.setDisplayOrientation(a);
            a += 90;
            if (a > 270) {
                a = 0;
            }
        }
    }


}