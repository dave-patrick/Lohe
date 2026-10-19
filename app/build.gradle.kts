import java.util.Properties

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
        versionCode = 8
        versionName = "0.8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Instrumented tests: real Room, real SQLite, real foreign keys. The
    // cascade that wiped a listener's episode state only reproduces against a
    // genuine foreign-key-enforcing database, so it cannot be faked on the JVM.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)

    // Android Automotive OS: the in-car app. It browses the same library the
    // phone publishes over the media browser service.
    implementation(libs.androidx.car.app)
}

/**
 * Runs the instrumented tests on a connected device *without uninstalling the
 * app afterwards*.
 *
 * `connectedAndroidTest` uninstalls both the app and the test package when it
 * finishes, which throws away everything in the app's data directory — the
 * library, the playlists, the listening history. On a device that holds a real
 * library that is not a cost worth paying for a test run, so this task
 * installs over the top with `adb install -r` and drives the runner directly.
 */
tasks.register("deviceTest") {
    group = "verification"
    description = "Runs the instrumented tests on a connected device, keeping the app's data."

    dependsOn("assembleDebug", "assembleDebugAndroidTest")

    val localProps = Properties()
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { localProps.load(it) }
    val sdkDir = localProps.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("No Android SDK found: set sdk.dir in local.properties or ANDROID_HOME.")
    val adb = "$sdkDir${File.separator}platform-tools${File.separator}adb"
    val appApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    val testApk = layout.buildDirectory.file("outputs/apk/androidTest/debug/app-debug-androidTest.apk")

    fun run(vararg command: String) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (output.isNotBlank()) logger.lifecycle(output.trim())
        check(exitCode == 0) {
            "Command failed (exit $exitCode): ${command.joinToString(" ")}"
        }
    }

    doFirst {
        run(adb, "install", "-r", "-t", appApk.get().asFile.absolutePath)
        run(adb, "install", "-r", "-t", testApk.get().asFile.absolutePath)
    }

    doLast {
        run(
            adb, "shell", "am", "instrument", "-w",
            "com.lohe.app.test/androidx.test.runner.AndroidJUnitRunner",
        )
    }
}