import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

// ── App config ───────────────────────────────────────────────────────────────
// All runtime config lives in local.properties (gitignored).
// Add entries there; they are injected into Android via BuildConfig and into
// iOS via a generated Kotlin source file at build time.
val localProps = Properties().also { props: Properties ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}
val apiHostValue: String = localProps.getProperty("API_HOST", "localhost")

// Generates AppConfig.kt into composeApp's iosMain so the iOS Koin module
// can read values that come from local.properties without hardcoding them.
val generateIosAppConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir(
        "generated/appConfig/kotlin/com/vdigital/volumestream/config"
    )
    outputs.dir(outputDir)
    doFirst {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        File(dir, "AppConfig.kt").writeText(
            "package com.vdigital.volumestream.config\n\ninternal val API_HOST: String = \"$apiHostValue\"\n"
        )
    }
}
// ─────────────────────────────────────────────────────────────────────────────

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export(project(":core:player"))
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.android.splashscreen)
        }
        commonMain.dependencies {
            implementation(project(":data"))
            implementation(project(":core:navigation"))
            api(project(":core:player"))
            implementation(project(":feature:home"))
            implementation(project(":feature:playback"))
            implementation(project(":feature:profile"))
            implementation(project(":feature:settings"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.napier)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeVM)
            implementation(libs.koin.core)
            implementation(libs.navigation.compose)
        }
        iosMain {
            kotlin.srcDir(
                generateIosAppConfig.map {
                    layout.buildDirectory.dir("generated/appConfig/kotlin")
                }
            )
            dependencies {
                implementation(libs.koin.core)
            }
        }
    }
}

android {
    namespace = "com.vdigital.volumestream"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "com.vdigital.volumestream"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "API_HOST", "\"$apiHostValue\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    dependencies {
        debugImplementation(compose.uiTooling)
    }
}
dependencies {
    implementation(libs.androidx.lifecycle.common.jvm)
}
