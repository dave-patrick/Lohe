import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lohe.sync"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lohe.sync"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
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
            if (keystorePath.isNotEmpty()) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(keystorePath)
                    storePassword = keystorePassword
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                }
            }
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
