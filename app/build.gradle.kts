plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    alias(libs.plugins.navigation.safe.args)
}

android {
    namespace = "com.example.ginnasticaritmica"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.ginnasticaritmica"
        minSdk = 24
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
        viewBinding = true
        dataBinding = true
    }


    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.all {
    resolutionStrategy {
        // Forza la compatibilità delle librerie
        force("androidx.core:core-ktx:1.12.0")
    }
}

dependencies {
    implementation("com.github.bumptech.glide:glide:5.0.9")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    // Importa Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("androidx.work:work-runtime-ktx:2.11.2")
// Aggiunge Auth e Firestore
    implementation("com.firebaseui:firebase-ui-auth:9.1.1")
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-firestore")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- LIBRERIE PER LA TESI: COMPUTER VISION ---

    // 1. CameraX (La fotocamera nativa di Jetpack)
    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    // 2. Google MediaPipe (Il motore di Pose Estimation)
    implementation("com.google.mediapipe:tasks-vision:1.0.0")

}