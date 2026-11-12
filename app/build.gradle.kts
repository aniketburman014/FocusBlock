plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val personalKeystorePath = providers.environmentVariable("FOCUSBLOCK_KEYSTORE_PATH").orNull

android {
    namespace = "com.focusblock.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.focusblock.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (personalKeystorePath != null) {
            create("personalRelease") {
                storeFile = file(personalKeystorePath)
                storePassword = providers.environmentVariable("FOCUSBLOCK_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("FOCUSBLOCK_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("FOCUSBLOCK_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without personal signing credentials, produce an unsigned release.
            // Never silently sign a release with the development key.
            signingConfig = signingConfigs.findByName("personalRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ""
        }
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

    applicationVariants.all {
        val variant = this
        outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = if (variant.buildType.name == "release") {
                    "FocusBlock.apk"
                } else {
                    "FocusBlock-debug.apk"
                }
            }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
