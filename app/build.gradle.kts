plugins {
    id("com.android.application") // version resolved from the root build.gradle.kts
}

android {
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    namespace = "com.android.launcher2"   // rename to your own package to avoid colliding
                                            // with any AOSP-signed launcher already on the device
    compileSdk = 36                        // Android 16

    defaultConfig {
        applicationId = "com.android.launcher2" // strongly recommend changing this, e.g.
                                                  // "com.yourname.launcher2port"
        minSdk = 26          // pick a realistic floor; going lower means handling a LOT
                              // more historical API branching for little benefit
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Launcher2 ships its own proguard.flags; wire those in
                                     // once the app builds and runs
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
