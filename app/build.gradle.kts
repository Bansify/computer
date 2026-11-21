plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.bansify.computer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bansify.computer"
        minSdk = 21
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 7
        versionName = (project.findProperty("versionName") as? String) ?: "1.0.6"
    }

    // Release signing key, supplied by CI via environment variables (from GitHub
    // secrets). Absent locally and on F-Droid, so this config stays inert there.
    val ciKeystore = System.getenv("KEYSTORE_FILE")
    val rootKeystore = file("${rootDir}/debug.keystore")
    signingConfigs {
        create("debugConfig") {
            if (rootKeystore.exists()) {
                storeFile = rootKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            } else {
                val homeKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storeFile = if (homeKeystore.exists()) homeKeystore else rootKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (ciKeystore != null) {
            create("ci") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (rootKeystore.exists() || file("${System.getProperty("user.home")}/.android/debug.keystore").exists()) {
                signingConfig = signingConfigs.getByName("debugConfig")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing priority:
            //  • CI (KEYSTORE_FILE env set)  → your real release key, from secrets.
            //  • -PlocalSign                 → the auto-generated debug key (local test).
            //  • otherwise (incl. F-Droid)   → UNSIGNED; F-Droid signs with its own key.
            signingConfig = when {
                ciKeystore != null -> signingConfigs.getByName("ci")
                project.hasProperty("localSign") -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    // Reproducibility for F-Droid: don't embed the Google-signed dependency
    // metadata block in the artifact.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    // iOS-style continuous (squircle) corners via native Drawables — perceptually
    // smoother than the circular-arc RoundedCornerShape.
    implementation("androidx.graphics:graphics-shapes:1.0.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.annotation:annotation:1.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    // Applies the bundled baseline profile (src/main/baseline-prof.txt) so the
    // startup + scroll paths are AOT-compiled on first run instead of JIT'd —
    // the big cold-start "screen shows but not smooth yet" win.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
}
