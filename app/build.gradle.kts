plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.pwhs.dunebox"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.pwhs.dunebox"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            pickFirsts += listOf(
                "lib/arm64-v8a/libxdl.so",
                "lib/armeabi-v7a/libxdl.so",
                "lib/arm64-v8a/libdobby.so",
                "lib/armeabi-v7a/libdobby.so",
            )
        }
    }
}

dependencies {
    // DuneBox SDK + Stubs
    implementation(project(":dunebox-sdk"))
    implementation(project(":dunebox-stub"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.navigation)

    // DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Image loading
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.coroutines.android)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}