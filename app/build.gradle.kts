plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.camera_demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.camera_demo"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

