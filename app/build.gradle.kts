import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release version changes only when requested. The final component of the
// custom build ID increases for each distinct build, even within one release.
val loheReleaseVersion = "0.11.1"
// Format: YYYY.DD.MM.revision (for example, 2025.25.10.4).
val loheBuildId = "2026.10.11.90"
val loheVersionParts = loheReleaseVersion.split(".").map { it.toInt() }
check(loheVersionParts.size == 3 && loheVersionParts.all { it in 0..99 }) {
    "Lohe release version must be major.minor.patch with each component from 0 to 99"
}
// Android's update-ordering code identifies the release version only. The
// custom build number is separate and is shown in About.
val loheAndroidVersionCode =
    loheVersionParts[0] * 10_000 + loheVersionParts[1] * 100 + loheVersionParts[2]

android {
    namespace = "com.lohe.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lohe.app"
        minSdk = 26
        targetSdk = 37
        versionCode = loheAndroidVersionCode
        versionName = loheReleaseVersion
        buildConfigField("String", "BUILD_ID", "\"$loheBuildId\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("play") {
            dimension = "distribution"
            // Phone/tablet Play Store build; merges src/play/AndroidManifest.xml to omit
            // unverified car declarations until separately validated.
            buildConfigField("String", "DISTRIBUTION", "\"play\"")
            buildConfigField("Boolean", "IS_FOSS", "false")
        }
        create("foss") {
            dimension = "distribution"
            // Libre standalone APK (GitHub Releases / F-Droid), installable side-by-side
            // with Google Play builds and free of proprietary Play dependencies.
            applicationIdSuffix = ".foss"
            versionNameSuffix = "-foss"
            buildConfigField("String", "DISTRIBUTION", "\"foss\"")
            buildConfigField("Boolean", "IS_FOSS", "true")
        }
    }

    buildTypes {
        debug {
            // Optional isolated installation for device tests; never replaces the user's app.
            applicationIdSuffix = providers.gradleProperty("loheDebugApplicationIdSuffix").orNull
        }
        release {
            isMinifyEnabled = true
            // Drop images, strings and layouts nothing references (R8 already
            // removes unused code). Nothing looks resources up by name.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val props = Properties()
            rootProject.file("local.properties")
                .takeIf { it.exists() }
                ?.inputStream()
                ?.use { props.load(it) }
            val keystorePath = providers.environmentVariable("LOHE_KEYSTORE_PATH").orNull
                ?.takeIf { it.isNotEmpty() }
                ?: props.getProperty("keystore.path", "")
            val keystorePassword = providers.environmentVariable("LOHE_KEYSTORE_PASSWORD").orNull
                ?.takeIf { it.isNotEmpty() }
                ?: props.getProperty("keystore.password", "")
            val keyAliasValue = providers.environmentVariable("LOHE_KEY_ALIAS").orNull
                ?.takeIf { it.isNotEmpty() }
                ?: props.getProperty("keystore.alias", "")
            val keyPasswordValue = providers.environmentVariable("LOHE_KEY_PASSWORD").orNull
                ?.takeIf { it.isNotEmpty() }
                ?: props.getProperty("keystore.key.password", "")
            // CI can validate an unsigned Play bundle or sign FOSS builds when secrets are supplied.
            // Never silently sign release builds with the Debug key.
            if (keystorePath.isNotEmpty()) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(keystorePath)
                    storePassword = keystorePassword
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                }
            }
        }
        create("smokeRelease") {
            initWith(getByName("release"))
            applicationIdSuffix = ".releaseaudit"
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // The debug control surface checks BuildConfig.DEBUG to decide
        // whether it may run at all.
        buildConfig = true
    }
}

// Room writes each database version's layout here (see AppDatabase.exportSchema).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Durable preferences (theme choice, etc.)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
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

    // Android Automotive OS embedded app surface (CarAppService).
    implementation(libs.androidx.car.app)
    // The in-vehicle CarAppActivity the head unit launches for templated apps.
    implementation(libs.androidx.car.app.automotive)
    implementation(libs.androidx.media)

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
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

abstract class VerifyFossDependenciesTask : DefaultTask() {
    @get:Input
    abstract val resolvedModules: ListProperty<String>

    @TaskAction
    fun verify() {
        val forbiddenPrefixes = listOf(
            "com.android.billingclient:",
            "com.google.android.gms:",
            "com.google.firebase:",
            "com.google.android.play:",
        )
        val resolved = resolvedModules.get()
        val violations = resolved.filter { coord ->
            forbiddenPrefixes.any { prefix -> coord.startsWith(prefix) }
        }
        check(violations.isEmpty()) {
            "FOSS release runtime classpath must not include proprietary Google/Play dependencies:\n" +
                violations.joinToString("\n") { " - $it" }
        }
        logger.lifecycle("FOSS dependency check passed (${resolved.size} resolved modules, 0 proprietary Play/Firebase modules).")
    }
}

tasks.register<VerifyFossDependenciesTask>("verifyFossDependencies") {
    group = "verification"
    description = "Verifies that the FOSS release runtime classpath contains no proprietary Google Play or Firebase dependencies."

    resolvedModules.set(
        configurations.named("fossReleaseRuntimeClasspath").flatMap { config ->
            config.incoming.resolutionResult.rootComponent.map { root ->
                val seen = LinkedHashSet<String>()
                fun visit(node: org.gradle.api.artifacts.result.ResolvedComponentResult) {
                    val mv = node.moduleVersion
                    val key = mv?.let { "${it.group}:${it.name}:${it.version}" } ?: node.id.displayName
                    if (!seen.add(key)) return
                    node.dependencies.forEach { dep ->
                        if (dep is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                            visit(dep.selected)
                        }
                    }
                }
                visit(root)
                seen.sorted()
            }
        },
    )
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
 *
 * Defaults to the `play` flavor (`com.lohe.app`). Pass `-PloheDeviceTestFlavor=foss`
 * to target the `foss` flavor (`com.lohe.app.foss`).
 */
tasks.register("deviceTest") {
    group = "verification"
    description = "Runs the instrumented tests on a connected device, keeping the app's data."

    val flavor = providers.gradleProperty("loheDeviceTestFlavor").orNull ?: "play"
    check(flavor == "play" || flavor == "foss") {
        "loheDeviceTestFlavor must be 'play' or 'foss' (got '$flavor')"
    }
    val flavorCap = flavor.replaceFirstChar { it.uppercase() }
    val debugSuffix = providers.gradleProperty("loheDebugApplicationIdSuffix").orNull.orEmpty()
    val testPackage = buildString {
        append("com.lohe.app")
        if (flavor == "foss") append(".foss")
        append(debugSuffix)
        append(".test")
    }

    dependsOn("assemble${flavorCap}Debug", "assemble${flavorCap}DebugAndroidTest")

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
    val appApk = layout.buildDirectory.file("outputs/apk/$flavor/debug/app-$flavor-debug.apk")
    val testApk = layout.buildDirectory.file("outputs/apk/androidTest/$flavor/debug/app-$flavor-debug-androidTest.apk")

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
            "$testPackage/androidx.test.runner.AndroidJUnitRunner",
        )
    }
}
