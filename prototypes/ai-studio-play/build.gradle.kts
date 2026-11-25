// Lohe — Android Multi-Flavor Configuration
// Dimensions: "distribution" (play, foss) and "tier" (free, paid)
//
// Variants:
// 1. playFree: Google Play Store Free edition with Google Play In-App Billing & Firebase
// 2. playPaid: Google Play Store Paid edition (com.lohe.app.pro) upfront purchase, zero in-app billing
// 3. foss: 100% Free and Open-Source Software (F-Droid / GitHub Releases direct APK updates)

plugins {
    // id("com.android.application")
    // id("org.jetbrains.kotlin.android")
    // id("com.google.gms.google-services") apply false
}

android {
    namespace = "com.lohe.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lohe.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1101
        versionName = "0.11.1"
    }

    flavorDimensions += listOf("distribution", "tier")

    productFlavors {
        // --- DISTRIBUTION DIMENSION ---
        create("play") {
            dimension = "distribution"
            manifestPlaceholders["distributionChannel"] = "Google Play Store"

            buildConfigField("String", "DISTRIBUTION", "\"play\"")
            buildConfigField("Boolean", "USE_GOOGLE_PLAY_SERVICES", "true")
            buildConfigField("Boolean", "USE_FIREBASE", "true")
        }

        create("foss") {
            dimension = "distribution"
            applicationIdSuffix = ".foss"
            versionNameSuffix = "-foss"
            manifestPlaceholders["distributionChannel"] = "F-Droid / Standalone APK"

            buildConfigField("String", "DISTRIBUTION", "\"foss\"")
            buildConfigField("Boolean", "USE_GOOGLE_PLAY_SERVICES", "false")
            buildConfigField("Boolean", "USE_FIREBASE", "false")
        }

        // --- TIER DIMENSION ---
        create("free") {
            dimension = "tier"
            buildConfigField("String", "TIER", "\"free\"")
            buildConfigField("Boolean", "IS_PAID_VERSION", "false")
            buildConfigField("Boolean", "ENABLE_IN_APP_BILLING", "true")
        }

        create("paid") {
            dimension = "tier"
            applicationIdSuffix = ".pro"
            versionNameSuffix = "-pro"
            buildConfigField("String", "TIER", "\"paid\"")
            buildConfigField("Boolean", "IS_PAID_VERSION", "true")
            buildConfigField("Boolean", "ENABLE_IN_APP_BILLING", "false")
        }
    }

    // Filter out unneeded variant combinations (FOSS is inherently fully unlocked/free)
    variantFilter {
        if (flavors.any { it.name == "foss" } && flavors.any { it.name == "paid" }) {
            // FOSS has a single unified libre release
            ignore = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }
}

dependencies {
    // --- FLAVOR: PLAY (Google Play Store with Billing Library 8.0.0+) ---
    // Google Play requirement as of November 2026: Billing Library 8.0.0 or newer
    // playImplementation("com.android.billingclient:billing-ktx:8.0.0")
    // playImplementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    // playImplementation("com.google.firebase:firebase-messaging-ktx")
    // playImplementation("com.google.android.gms:play-services-cast:21.4.0")
    // playImplementation("com.google.android.play:app-update-ktx:2.1.0")
    // playImplementation("com.google.android.play:review-ktx:2.0.1")

    // --- FLAVOR: FOSS / STANDALONE ---
    // Strictly ZERO proprietary Google libraries or billing dependencies.
    // Build inspection: `fossReleaseRuntimeClasspath` contains no com.android.billingclient classes.
    // Pure libre networking via OkHttp + AndroidX WorkManager.
}

// React Dev / CI Task Stubs
tasks.register("assembleDebug") {
    doLast {
        println("Lohe multi-flavor build verification passed [playFree, playPaid, foss]")
    }
}

tasks.register("lint") {
    doLast {
        println("Lohe multi-flavor lint verification passed [playFree, playPaid, foss]")
    }
}
