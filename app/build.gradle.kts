import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Every build value that carries the app name comes from app.properties at the repo root (UTF-8).
val appProps = Properties().apply {
    rootProject.file("app.properties").reader(Charsets.UTF_8).use { load(it) }
}
fun appProp(key: String): String =
    appProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: throw GradleException("app.properties: $key is missing")

val appName = appProp("appName")
val appNameEn = appProp("appNameEn")
val appSlug = appProp("appSlug")
val appIdSuffix = appProp("appIdSuffix")
val teamName = appProp("teamName")
val releaseApkName = "${teamName}_${appNameEn}_release.apk"

// Release signing values, first found wins:
//   1. Gradle property kilobook.release.* (~/.gradle/gradle.properties, outside the repo; the prefix keeps other projects from reading them)
//   2. Gradle property release.* (-P on the command line)
//   3. release.* in local.properties (git-ignored)
// The key file itself stays outside the repo. storeFile may start with "~/".
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.reader(Charsets.UTF_8)?.use { load(it) }
}
fun signingProp(key: String): String? =
    providers.gradleProperty("kilobook.$key").orNull
        ?: providers.gradleProperty(key).orNull
        ?: localProps.getProperty(key)
val releaseStoreFile = signingProp("release.storeFile")?.trim()?.let {
    if (it.startsWith("~/")) System.getProperty("user.home") + it.substring(1) else it
}
val hasReleaseSigning = releaseStoreFile != null

android {
    // Kotlin package of the source code (not shown to users). Kept equal to applicationId; renaming it means moving the source folders.
    namespace = "com.kilobook.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.$appSlug.$appIdSuffix"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resValue("string", "app_name", appName)
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingProp("release.storePassword")
                keyAlias = signingProp("release.keyAlias")
                keyPassword = signingProp("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Off for now: the Pleos SDK ships obfuscated and no shrink rules have been verified for it.
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }

    // Android Automotive standard APIs (Car, CarPropertyManager, CarUxRestrictionsManager)
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.pleos.vehicle)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
}

// ./gradlew releaseApk -> signed release APK copied to apk/<teamName>_<appNameEn>_release.apk (the submission file).
tasks.register<Copy>("releaseApk") {
    group = "submission"
    description = "Builds the signed release APK and copies it to apk/$releaseApkName"
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { _: String -> releaseApkName }
}

// Without signing values, stop before building so an unsigned APK never reaches apk/.
gradle.taskGraph.whenReady {
    if (hasTask(":app:releaseApk") && !hasReleaseSigning) {
        throw GradleException(
            "Release signing is not set: add kilobook.release.storeFile, kilobook.release.storePassword, " +
                "kilobook.release.keyAlias, kilobook.release.keyPassword to ~/.gradle/gradle.properties (see apk/README.md).",
        )
    }
}
