plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.pwhs.dunebox.native_engine"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared"
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        prefab = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    // Native hooking
    implementation(libs.dobby)
    implementation(libs.xdl)

    // Logging
    implementation(libs.timber)
}
