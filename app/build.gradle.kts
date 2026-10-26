plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.rotateall"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.example.rotateall"
        minSdk = 23
        targetSdk = 35
        versionCode = 10
        versionName = "1.9"
    }
}
