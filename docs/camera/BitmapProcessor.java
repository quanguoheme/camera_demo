package com.example.huck.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BitmapProcessor {
    private static final String TAG = "BitmapProcessor";
    
    // 最大缓存大小（以KB为单位）
    private static final int MAX_CACHE_SIZE = 4 * 1024; // 4MB
    
    private final LruCache<String, Bitmap> mBitmapCache;
    private final Executor mExecutor;
    private final Handler mMainHandler;
    
    // 用于控制处理频率，避免过度处理
    private long mLastProcessTime = 0;
    private static final long MIN_PROCESS_INTERVAL = 100; // 毫秒
    
    public interface BitmapProcessorCallback {
        void onBitmapProcessed(Bitmap bitmap);
        void onProcessError(String error);
    }
    
    public BitmapProcessor() {
        // 创建Bitmap缓存
        mBitmapCache = new LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // 返回Bitmap的大小（以KB为单位）
                return bitmap.getByteCount() / 1024;
            }
            
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                // 当Bitmap从缓存中移除时，如果不再使用，则回收它
                if (evicted && oldValue != null && !oldValue.isRecycled()) {
                    oldValue.recycle();
                }
            }
        };
        
        // 创建线程池
        mExecutor = Executors.newSingleThreadExecutor();
        mMainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * 处理相机预览帧并生成Bitmap
     * @param data 预览帧数据
     * @param camera 相机实例
     * @param cameraId 相机ID
     * @param callback 回调接口
     */
    public void processCameraFrame(final byte[] data, final Camera camera, final int cameraId, 
                                  final BitmapProcessorCallback callback) {
        // 控制处理频率
        long currentTime = System.currentTimeMillis();
        if (currentTime - mLastProcessTime < MIN_PROCESS_INTERVAL) {
            return;
        }
        mLastProcessTime = currentTime;
        
        // 在工作线程中处理
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bitmap = convertYuvToBitmap(data, camera, cameraId);
                    
                    if (bitmap != null && callback != null) {
                        // 在主线程中回调
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onBitmapProcessed(bitmap);
                            }
                        });
                    }
                } catch (final Exception e) {
                    Log.e(TAG, "Error processing camera frame: " + e.getMessage());
                    if (callback != null) {
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onProcessError("Error processing camera frame: " + e.getMessage());
                            }
                        });
                    }
                }
            }
        });
    }
    
    /**
     * 将YUV格式的预览帧转换为Bitmap
     * @param data 预览帧数据
     * @param camera 相机实例
     * @param cameraId 相机ID
     * @return 转换后的Bitmap
     */
    private Bitmap convertYuvToBitmap(byte[] data, Camera camera, int cameraId) {
        try {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();
            
            // 生成缓存键
            String cacheKey = size.width + "x" + size.height + "_" + cameraId;
            
            // 检查缓存中是否有可重用的Bitmap
            Bitmap cachedBitmap = mBitmapCache.get(cacheKey);
            
            // 创建YuvImage
            YuvImage image = new YuvImage(data, parameters.getPreviewFormat(),
                    size.width, size.height, null);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, size.width, size.height), 90, out);
            
            byte[] imageBytes = out.toByteArray();
            
            // 解码Bitmap
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true; // 创建可变的Bitmap，以便重用
            
            if (cachedBitmap != null && !cachedBitmap.isRecycled() &&
                    cachedBitmap.getWidth() == size.width && cachedBitmap.getHeight() == size.height) {
                // 重用缓存的Bitmap
                options.inBitmap = cachedBitmap;
            }
            
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
            //如果要打开 图片旋转 ,打开这个 注释
           /*
            // 根据相机方向旋转图片
            Matrix matrix = new Matrix();
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);
            
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                matrix.setRotate(270);
                matrix.postScale(-1, 1); // 前置摄像头需要镜像
            } else {
                matrix.setRotate(90);
            }
            
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);

            // 如果旋转后的Bitmap与原始Bitmap不同，则回收原始Bitmap
            if (rotatedBitmap != bitmap) {
                bitmap.recycle();
            }*/
            Bitmap rotatedBitmap =bitmap;
            // 将旋转后的Bitmap放入缓存
            mBitmapCache.put(cacheKey, rotatedBitmap);
            
            return rotatedBitmap;
        } catch (Exception e) {
            Log.e(TAG, "Error converting YUV to bitmap: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        mBitmapCache.evictAll();
    }
    
    /**
     * 释放资源
     */
    public void release() {
        clearCache();
    }
}