plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.pwhs.dunebox.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
}

dependencies {
    // Native engine
    implementation(project(":dunebox-native"))

    // Hooking
    implementation(libs.lsplant)
    implementation(libs.hidden.api.bypass)

    // Serialization
    implementation(libs.gson)

    // Logging
    implementation(libs.timber)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Android core
    implementation(libs.androidx.core.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
