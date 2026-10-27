plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.prettyj.driveroid"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.prettyj.driveroid"
        // Android 11 (API 30). This is the practical floor for the assistant: the microphone
        // foreground-service type (used and declared by VoiceAssistantService) was introduced in
        // API 30, and the POST_NOTIFICATIONS runtime permission is only asked for on API 33+
        // (see AssistantPermissions).
        minSdk = 30
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
    buildFeatures {
        // Used by DriveroidLog to enable verbose logging in debug builds only.
        buildConfig = true
        // findViewById-free view access in the Activities (Views/XML only, no Compose).
        viewBinding = true
    }

    testOptions {
        // The unit tests exercise classes that touch android.util.Log; returning default values
        // keeps them runnable as plain JVM tests, without Robolectric.
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    // lifecycleScope + repeatOnLifecycle: lets MainActivity observe the assistant state
    // for exactly as long as it is started.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}