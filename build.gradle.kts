// Security updates are scoped to the buildscript classpath, not app dependencies.
// Keep each family aligned and retain the OSV gate to validate the resolved graph.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Settings loads this early; declaring it here includes it in the audit.
        classpath("com.android.tools:r8:9.1.29")
    }
    configurations.named("classpath") {
        resolutionStrategy.eachDependency {
            val patchedVersion = when {
                requested.group == "io.netty" && requested.name.startsWith("netty-") &&
                    !requested.name.startsWith("netty-tcnative") -> "4.1.140.Final"
                requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") -> "1.86"
                requested.group == "com.google.protobuf" &&
                    requested.version?.startsWith("3.") == true && requested.name in setOf(
                    "protobuf-java", "protobuf-javalite", "protobuf-java-util",
                    "protobuf-kotlin", "protobuf-kotlin-lite"
                ) -> "3.25.8"
                requested.group == "com.google.guava" && requested.name == "guava" -> "33.7.2-jre"
                requested.group == "commons-io" && requested.name == "commons-io" -> "2.22.0"
                requested.group == "org.apache.commons" && requested.name == "commons-compress" -> "1.28.0"
                requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" -> "0.9.6"
                requested.group == "org.jdom" && requested.name == "jdom2" -> "2.0.6.1"
                else -> null
            }
            if (patchedVersion != null) {
                useVersion(patchedVersion)
                because("Address build-tool advisories reported by the resolved dependency audit")
            }
        }
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}

tasks.register("verifyD8Version") {
    doLast {
        val actual = com.android.tools.r8.Version.getVersionString()
        check(actual.startsWith("9.1.29")) { "Unexpected D8/R8 implementation: $actual" }
        logger.lifecycle("Active D8/R8 version: $actual")
    }
}
