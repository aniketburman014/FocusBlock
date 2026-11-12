import groovy.json.JsonOutput
import java.security.MessageDigest

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

// Resolve actual transitive versions and record the bytes used by this build.
// Test/build dependencies are labelled separately from code shipped in the APK.
tasks.register("writeDependencyInventory") {
    doLast {
        val scopes = linkedMapOf(
            "runtime" to configurations.getByName("debugRuntimeClasspath"),
            "unit-test" to configurations.getByName("debugUnitTestRuntimeClasspath"),
            "device-test" to configurations.getByName("debugAndroidTestRuntimeClasspath"),
            "build-tools" to rootProject.buildscript.configurations.getByName("classpath")
        )
        val entries = scopes.flatMap { (scope, configuration) ->
            configuration.resolvedConfiguration.resolvedArtifacts.map { artifact ->
                val module = artifact.moduleVersion.id
                val digest = MessageDigest.getInstance("SHA-256")
                artifact.file.inputStream().use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                mapOf(
                    "scope" to scope, "name" to "${module.group}:${module.name}",
                    "version" to module.version, "file" to artifact.file.name,
                    "sha256" to digest.digest().joinToString("") { "%02x".format(it) }
                )
            }
        }.sortedBy { "${it["scope"]}:${it["name"]}:${it["version"]}:${it["file"]}" }
        check(entries.any { it["scope"] == "runtime" }) { "Empty runtime dependency inventory" }
        val report = layout.buildDirectory.file("reports/dependencies/resolved.json").get().asFile
        report.parentFile.mkdirs()
        report.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mapOf("artifacts" to entries))))
        logger.lifecycle("Recorded ${entries.size} resolved dependency artifacts in $report")
    }
}
