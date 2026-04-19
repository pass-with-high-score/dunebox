plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "app.pwhs.dunebox.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.pwhs.dunebox.sample"
        minSdk = 26
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

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
}
