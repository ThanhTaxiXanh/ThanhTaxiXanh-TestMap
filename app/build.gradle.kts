plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.thanhtaxixanh.wazebridge"
    compileSdk = 35
    defaultConfig {
        applicationId = "vn.thanhtaxixanh.wazebridge"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.4.0-auto-osm-without-waze"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
