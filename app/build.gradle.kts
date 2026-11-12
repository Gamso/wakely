plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.gamso.wakely"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gamso.wakely"
        minSdk = 26
        targetSdk = 36
        // Overridden by the CI from the release tag (e.g. -PversionName=1.2.3 -PversionCode=10203).
        // versionCode = major * 10000 + minor * 100 + patch.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(10200)
        versionName = providers.gradleProperty("versionName").getOrElse("1.2.0")
    }

    // The release signing config is only set up when the keystore is provided (CI or local machine).
    val keystorePath = providers.environmentVariable("SIGNING_KEYSTORE_FILE").orNull
    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("SIGNING_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        ignoreTestSources = true
    }

    // No dependency metadata block in the APK / AAB: it is encrypted with a Google key, nobody else can read it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
}
