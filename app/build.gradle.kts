plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.example.scoutface"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.scoutface"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
    androidResources {
        // MobileFaceNet.tflite must stay uncompressed in the APK so it can
        // be mapped directly into memory at runtime.
        noCompress += "tflite"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    packaging {
        jniLibs {
            // NDK 28.2 llvm-strip crashes on Windows (STATUS_ILLEGAL_INSTRUCTION) when
            // processing x86_64 ELF binaries from ML Kit AARs. Scout is arm64-only so
            // these files are never loaded. Skipping the strip step avoids the crash.
            // The x86_64 .so files remain in debug APKs but are harmless on arm64 devices.
            keepDebugSymbols += "*/x86_64/*.so"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // ML Kit — versions confirmed 16KB page-aligned on arm64-v8a (Scout's only ABI).
    // face-detection 16.1.7: arm64 fixed Dec 2025 (issue #986); 32-bit still 4KB but Scout
    // doesn't ship armeabi-v7a. image-labeling 17.0.9: used by official ML Kit sample;
    // pulls in fixed vision-common that resolves libimage_processing_util_jni.so alignment.
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // TensorFlow Lite — ⚠️ not 16KB page-aligned (shows debug popup on Fold 7 / Android 15).
    // Migrate to com.google.ai.edge.litert:litert before Play Store submission.
    // Verify the latest available version at maven.google.com before switching.
    implementation("org.tensorflow:tensorflow-lite:2.17.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room (only if you’re using it)
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
