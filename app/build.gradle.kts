plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lohe.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lohe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Durable preferences (theme choice, etc.)
    implementation(libs.androidx.datastore.preferences)
    // datastore pulls in an older serialization; pin the cached one.
    implementation(libs.kotlinx.serialization.json)

    // Data layer
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Networking (for podcast search + RSS)
    implementation(libs.com.squareup.okhttp)
    implementation(libs.com.squareup.okhttp.logging)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)

    // SAF tree traversal for local folder playback
    implementation(libs.androidx.documentfile)

    // ViewModel Compose integration
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Declared so Gradle settles on 1.5.0: activity 1.13 asks for a savedstate
    // version that resolves fine online but is absent from this cache.
    implementation(libs.androidx.savedstate)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    // JVM XmlPullParser so FeedParser can be exercised off-device; Android's
    // android.util.Xml is unavailable in plain unit tests.
    testImplementation(libs.kxml2)
    // For the repository's suspend fan-out logic: runTest gives the test a
    // scope without needing Android's main looper.
    testImplementation(libs.coroutines.test)
}