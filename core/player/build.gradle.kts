import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    applyDefaultHierarchyTemplate()
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    tvosX64()
    tvosArm64()
    tvosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":data"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.dash)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.session)
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
            implementation(libs.koin.core)
        }
        // tvOS uses the same AVKit/Foundation/Security implementations as iOS.
        // Sharing iosMain/kotlin as srcDirs avoids duplicating every actual file.
        val tvosMain by getting {
            kotlin.srcDirs("src/iosMain/kotlin")
            dependencies { implementation(libs.koin.core) }
        }
    }
}

android {
    namespace = "com.vdigital.volumestream.core.player"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    buildFeatures {
        compose = true
    }
}
