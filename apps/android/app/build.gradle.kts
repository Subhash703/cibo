plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "in.foodlens.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "in.foodlens.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Dev backend URL. Current value is an ngrok tunnel — works through
        // corporate Wi-Fi AP isolation, cellular, and any firewall. Regenerate
        // when the ngrok session restarts (free tier rotates the subdomain).
        // Other options for development:
        //   • USB: `adb reverse tcp:8000 tcp:8000` → "http://localhost:8000"
        //   • Same-LAN no isolation: "http://<mac-lan-ip>:8000"
        //   • Emulator host loopback: "http://10.0.2.2:8000"
        // For prod, replace with the deployed HTTPS API.
        buildConfigField(
            "String",
            "BACKEND_BASE_URL",
            "\"https://de69-103-159-11-202.ngrok-free.app\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}
