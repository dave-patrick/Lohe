import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

// Release version changes only when requested. The final component of the
// custom build ID increases for each distinct build, even within one release.
val loheReleaseVersion = "0.12.0"
// Format: YYYY.DD.MM.revision (for example, 2026.19.11.120).
val loheBuildId = "2026.19.11.121"
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
  compileSdk = 36

  defaultConfig {
    applicationId = "com.lohe.app"
    minSdk = 26
    targetSdk = 36
    versionCode = loheAndroidVersionCode
    versionName = loheReleaseVersion
    buildConfigField("String", "BUILD_ID", "\"$loheBuildId\"")
    buildConfigField("String", "EDITION", "\"foss\"")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    debug {
      // Optional isolated installation for device tests; never replaces the user's app.
      applicationIdSuffix = providers.gradleProperty("loheDebugApplicationIdSuffix").orNull
    }
    release {
      isMinifyEnabled = true
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
      val keystorePath = props.getProperty("keystore.path", "").ifBlank {
        System.getenv("KEYSTORE_PATH").orEmpty()
      }
      // CI can validate an unsigned bundle. Never silently sign Play builds with Debug.
      if (keystorePath.isNotEmpty()) {
        signingConfig = signingConfigs.create("release") {
          storeFile = file(keystorePath)
          storePassword = props.getProperty("keystore.password", "").ifBlank {
            System.getenv("STORE_PASSWORD").orEmpty()
          }
          keyAlias = props.getProperty("keystore.alias", "").ifBlank {
            System.getenv("KEY_ALIAS").orEmpty()
          }
          keyPassword = props.getProperty("keystore.key.password", "").ifBlank {
            System.getenv("KEY_PASSWORD").orEmpty()
          }
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
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  lint {
    textReport = true
    textOutput = file("stdout")
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.gif)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.concurrent.futures.ktx)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)


  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
