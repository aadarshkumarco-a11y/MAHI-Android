plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.mahi.assistant"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.mahi.assistant"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Room schema export directory
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        warning += "MissingTranslation"
    }

    kapt {
        correctErrorTypes = true
    }
}

dependencies {
    // ──────────────────────────────────────────────
    // Jetpack Compose (BOM-managed versions)
    // ──────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2023.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ──────────────────────────────────────────────
    // AndroidX Core & Lifecycle
    // ──────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.activity:activity-ktx:1.7.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")

    // ──────────────────────────────────────────────
    // Navigation
    // ──────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.6.0")
    implementation("androidx.navigation:navigation-runtime-ktx:2.6.0")

    // ──────────────────────────────────────────────
    // Hilt (Dependency Injection)
    // ──────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.0.0")
    kapt("androidx.hilt:hilt-compiler:1.0.0")

    // ──────────────────────────────────────────────
    // Room (Local Database)
    // ──────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.5.2")
    implementation("androidx.room:room-ktx:2.5.2")
    kapt("androidx.room:room-compiler:2.5.2")

    // ──────────────────────────────────────────────
    // Retrofit + OkHttp (Networking)
    // ──────────────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ──────────────────────────────────────────────
    // Porcupine (Wake Word Detection)
    // ──────────────────────────────────────────────
    implementation("ai.picovoice:porcupine-android:2.2.0")

    // ──────────────────────────────────────────────
    // Coil (Image Loading)
    // ──────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.4.0")

    // ──────────────────────────────────────────────
    // Lottie Compose (Animations)
    // ──────────────────────────────────────────────
    implementation("com.airbnb.android:lottie-compose:6.1.0")

    // ──────────────────────────────────────────────
    // Accompanist (Permissions)
    // ──────────────────────────────────────────────
    implementation("com.google.accompanist:accompanist-permissions:0.30.1")

    // ──────────────────────────────────────────────
    // DataStore Preferences
    // ──────────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ──────────────────────────────────────────────
    // WorkManager
    // ──────────────────────────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("androidx.hilt:hilt-work:1.0.0")

    // ──────────────────────────────────────────────
    // Google ML Kit (Text Recognition)
    // ──────────────────────────────────────────────
    implementation("com.google.mlkit:text-recognition:16.0.0-beta6")

    // ──────────────────────────────────────────────
    // Coroutines
    // ──────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ──────────────────────────────────────────────
    // Gson (JSON parsing — used alongside Retrofit converter)
    // ──────────────────────────────────────────────
    implementation("com.google.code.gson:gson:2.10.1")

    // ──────────────────────────────────────────────
    // Material Components (for XML-based screens if needed)
    // ──────────────────────────────────────────────
    implementation("com.google.android.material:material:1.11.0")

    // ──────────────────────────────────────────────
    // Testing
    // ──────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.google.dagger:hilt-android-testing:2.48")
    kaptTest("com.google.dagger:hilt-android-compiler:2.48")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.48")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.48")
}
