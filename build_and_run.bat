@echo off
chcp 65001 >nul
title Camera Demo 编译运行
echo ====================================================
echo             Camera Demo 编译并运行脚本
echo ====================================================

echo.
echo [1/4] 检查连接的 Android 设备...
adb wait-for-device
if errorlevel 1 (
    echo [错误] 未检测到已连接的 Android 设备，请检查 USB 调试连接！
    goto error
)
echo 设备已连接。

echo.
echo [2/4] 开始编译 Debug APK...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo [错误] 编译失败，请检查编译日志！
    goto error
)

echo.
echo [3/4] 安装 APK 到设备...
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK_PATH%" (
    echo [错误] 未找到生成的 APK 文件: %APK_PATH%
    goto error
)

adb install -r "%APK_PATH%"
if errorlevel 1 (
    echo.
    echo [错误] 安装失败，请检查设备连接或权限！
    goto error
)

echo.
echo [4/4] 授予权限并启动应用...
adb shell pm grant com.example.camera_demo android.permission.CAMERA >nul 2>&1
adb shell am start -n com.example.camera_demo/.MainActivity
if errorlevel 1 (
    echo.
    echo [错误] 启动应用失败！
    goto error
)

echo.
echo ====================================================
echo [成功] Camera Demo 编译并启动成功！
echo ====================================================
goto end

:error
echo.
echo [失败] 执行过程中出现错误。

:end
echo.
pause
