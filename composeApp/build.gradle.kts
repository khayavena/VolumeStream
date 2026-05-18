import java.util.Properties
import java.net.NetworkInterface
import java.net.URI
import java.util.Collections
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
val apiHostValueRaw: String  = localProps.getProperty("API_HOST",  "localhost")
val authPortValue: String = localProps.getProperty("AUTH_PORT", "8080")
val apiPortValue: String  = localProps.getProperty("API_PORT",  "8081")
val useHttpsValue: String = localProps.getProperty("USE_HTTPS", "")
val authUseHttpsValue: String = localProps.getProperty("AUTH_USE_HTTPS", useHttpsValue)
val apiUseHttpsValue: String = localProps.getProperty("API_USE_HTTPS", useHttpsValue)

fun readProp(name: String): String? =
    localProps.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

data class HostParts(
    val host: String,
    val useHttps: Boolean,
)

fun parseHostParts(rawHost: String): HostParts {
    val trimmed = rawHost.trim()
    if (trimmed.isEmpty()) return HostParts(host = "localhost", useHttps = false)
    return try {
        val candidate = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val uri = URI(candidate)
        val parsedHost = uri.host?.takeIf { it.isNotBlank() }
            ?: trimmed.substringBefore('/').substringBefore(':').ifBlank { "localhost" }
        HostParts(
            host = parsedHost,
            useHttps = uri.scheme.equals("https", ignoreCase = true)
        )
    } catch (_: Exception) {
        HostParts(
            host = trimmed.substringBefore('/').substringBefore(':').ifBlank { "localhost" },
            useHttps = trimmed.startsWith("https://", ignoreCase = true)
        )
    }
}

fun parseBooleanProp(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
    "true", "1", "yes", "y", "on" -> true
    "false", "0", "no", "n", "off" -> false
    else -> null
}

val iosEnvValue: String = when (readProp("IOS_ENV")?.lowercase()) {
    "prod", "production" -> "prod"
    "qa", "staging", "dev", "development" -> "qa"
    null, "" -> "qa"
    else -> "qa"
}

val iosHostRawValue: String = if (iosEnvValue == "prod") {
    readProp("PROD_API_HOST") ?: apiHostValueRaw
} else {
    readProp("QA_API_HOST") ?: apiHostValueRaw
}

val iosAuthPortValue: String = if (iosEnvValue == "prod") {
    readProp("PROD_AUTH_PORT") ?: authPortValue
} else {
    readProp("QA_AUTH_PORT") ?: authPortValue
}

val iosApiPortValue: String = if (iosEnvValue == "prod") {
    readProp("PROD_API_PORT") ?: apiPortValue
} else {
    readProp("QA_API_PORT") ?: apiPortValue
}

val iosAuthUseHttpsRaw: String? = if (iosEnvValue == "prod") {
    readProp("PROD_AUTH_USE_HTTPS") ?: authUseHttpsValue
} else {
    readProp("QA_AUTH_USE_HTTPS") ?: authUseHttpsValue
}

val iosApiUseHttpsRaw: String? = if (iosEnvValue == "prod") {
    readProp("PROD_API_USE_HTTPS") ?: apiUseHttpsValue
} else {
    readProp("QA_API_USE_HTTPS") ?: apiUseHttpsValue
}

val iosHostParts = parseHostParts(iosHostRawValue)
val iosAuthUseHttpsValue: Boolean = parseBooleanProp(iosAuthUseHttpsRaw)
    ?: (iosHostParts.useHttps || iosAuthPortValue == "443" || iosAuthPortValue == "18443")
val iosApiUseHttpsValue: Boolean = parseBooleanProp(iosApiUseHttpsRaw)
    ?: (iosHostParts.useHttps || iosApiPortValue == "443")

val androidHostParts = parseHostParts(apiHostValueRaw)
val androidAuthUseHttpsValue: Boolean = parseBooleanProp(authUseHttpsValue)
    ?: (androidHostParts.useHttps || authPortValue == "443" || authPortValue == "18443")
val androidApiUseHttpsValue: Boolean = parseBooleanProp(apiUseHttpsValue)
    ?: (androidHostParts.useHttps || apiPortValue == "443")

val updateApiHostFromNetwork by tasks.registering {
    group = "configuration"
    description = "Detects this machine's LAN IPv4 and writes it to local.properties as API_HOST"
    doLast {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .filterNot {
                it.name.startsWith("docker") ||
                it.name.startsWith("vbox") ||
                it.name.startsWith("bridge") ||
                it.name.startsWith("utun")
            }
            .sortedBy {
                when {
                    it.name.startsWith("en") -> 0
                    it.name.startsWith("wlan") -> 1
                    it.name.startsWith("eth") -> 2
                    else -> 3
                }
            }

        val lanIp = interfaces.firstNotNullOfOrNull { networkInterface ->
            Collections.list(networkInterface.inetAddresses)
                .mapNotNull { address ->
                    val host = address.hostAddress ?: return@mapNotNull null
                    if (
                        host.contains(":") ||
                        host == "127.0.0.1" ||
                        host.startsWith("169.254.")
                    ) null else host
                }
                .firstOrNull()
        } ?: throw org.gradle.api.GradleException(
            "Could not detect a LAN IPv4 address. Connect to Wi-Fi/Ethernet and try again."
        )

        val localPropertiesFile = rootProject.file("local.properties")
        if (!localPropertiesFile.exists()) {
            localPropertiesFile.createNewFile()
        }

        val props = Properties()
        if (localPropertiesFile.length() > 0L) {
            localPropertiesFile.inputStream().use { props.load(it) }
        }
        props.setProperty("API_HOST", lanIp)
        localPropertiesFile.outputStream().use {
            props.store(it, "Updated by :composeApp:updateApiHostFromNetwork")
        }

        logger.lifecycle("API_HOST set to $lanIp in ${localPropertiesFile.absolutePath}")
    }
}

// Generates AppConfig.kt directly into the iosMain source tree so the IDE
// can resolve the constants without a prior Gradle build.  The file is
// gitignored (it contains machine-specific values from local.properties).
val generateIosAppConfig by tasks.registering {
    // Write straight into the checked-in source directory so IntelliJ / AS
    // indexes it as a regular source file — no kotlin.srcDir plumbing needed.
    val outputFile = file(
        "src/iosMain/kotlin/com/vdigital/volumestream/config/AppConfig.kt"
    )
    // Declare local.properties as an input so the task is re-run whenever
    // the host/port values change (busts Gradle's up-to-date check).
    inputs.file(rootProject.file("local.properties"))
    outputs.file(outputFile)
    doFirst {
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            "// AUTO-GENERATED by composeApp/build.gradle.kts — do not edit by hand.\n" +
            "// Re-generated on every build from local.properties values.\n" +
            "package com.vdigital.volumestream.config\n\n" +
            "internal val API_HOST:   String = \"${iosHostParts.host}\"\n" +
            "internal val AUTH_PORT:  Int    = $iosAuthPortValue\n" +
            "internal val API_PORT:   Int    = $iosApiPortValue\n" +
            "internal val AUTH_USE_HTTPS: Boolean = $iosAuthUseHttpsValue\n" +
            "internal val API_USE_HTTPS:  Boolean = $iosApiUseHttpsValue\n" +
            "internal val USE_HTTPS:      Boolean = ${iosAuthUseHttpsValue || iosApiUseHttpsValue}\n"
        )
    }
}
// ─────────────────────────────────────────────────────────────────────────────

kotlin {
    applyDefaultHierarchyTemplate()
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
            implementation(libs.androidx.media3.exoplayer.dash)
            implementation(libs.androidx.tv.material)
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
            implementation(libs.image.loader)
        }
        iosMain {
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
        buildConfigField("String", "API_HOST",  "\"${androidHostParts.host}\"")
        buildConfigField("int",    "AUTH_PORT", authPortValue)
        buildConfigField("int",    "API_PORT",  apiPortValue)
        buildConfigField("boolean", "AUTH_USE_HTTPS", androidAuthUseHttpsValue.toString())
        buildConfigField("boolean", "API_USE_HTTPS", androidApiUseHttpsValue.toString())
        buildConfigField("boolean", "USE_HTTPS", (androidAuthUseHttpsValue || androidApiUseHttpsValue).toString())
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
    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
            applicationIdSuffix = ".phone"
            versionNameSuffix = "-phone"
        }
        create("tv") {
            dimension = "device"
            applicationIdSuffix = ".tv"
            versionNameSuffix = "-tv"
        }
    }
    dependencies {
        debugImplementation(compose.uiTooling)
    }
}
dependencies {
    implementation(libs.androidx.lifecycle.common.jvm)
}

tasks.named("preBuild") {
    dependsOn(updateApiHostFromNetwork)
}

// Keep AppConfig.kt up to date whenever iOS Kotlin sources are compiled.
tasks.matching {
    it.name.startsWith("compileKotlinIos") ||
    it.name == "compileIosMainKotlinMetadata"
}.configureEach {
    dependsOn(generateIosAppConfig)
}

fun projectEnvOrDefault(name: String, defaultValue: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: defaultValue

tasks.register("launchPhoneDebugFromIde") {
    group = "ide"
    description = "Assemble, install, and launch the Android phone debug app (uses ANDROID_SERIAL when set)."
    dependsOn("assemblePhoneDebug")
    doLast {
        val launchScript = rootProject.file("composeApp/scripts/launch_android_variant.sh")
        if (!launchScript.exists()) {
            throw GradleException("Android launch script not found: ${launchScript.absolutePath}")
        }
        val apk = layout.buildDirectory.file("outputs/apk/phone/debug/composeApp-phone-debug.apk").get().asFile
        if (!apk.exists()) {
            throw GradleException("Phone APK not found: ${apk.absolutePath}")
        }
        exec {
            environment(
                mapOf(
                    "APK_PATH" to apk.absolutePath,
                    "APP_COMPONENT" to "com.vdigital.volumestream.phone/com.vdigital.volumestream.platform.activity.MainActivity",
                    "PREFER_EMULATOR" to "false",
                    "ANDROID_SDK_DIR_HINT" to (localProps.getProperty("sdk.dir") ?: "")
                )
            )
            commandLine("/bin/zsh", launchScript.absolutePath)
        }
    }
}

tasks.register("launchTvDebugFromIde") {
    group = "ide"
    description = "Assemble, install, and launch the Android TV debug app (uses ANDROID_SERIAL when set)."
    dependsOn("assembleTvDebug")
    doLast {
        val launchScript = rootProject.file("composeApp/scripts/launch_android_tv.sh")
        if (!launchScript.exists()) {
            throw GradleException("Android launch script not found: ${launchScript.absolutePath}")
        }
        val apk = layout.buildDirectory.file("outputs/apk/tv/debug/composeApp-tv-debug.apk").get().asFile
        if (!apk.exists()) {
            throw GradleException("TV APK not found: ${apk.absolutePath}")
        }
        exec {
            environment(
                mapOf(
                    "APK_PATH" to apk.absolutePath,
                    "ANDROID_SDK_DIR_HINT" to (localProps.getProperty("sdk.dir") ?: "")
                )
            )
            commandLine("/bin/zsh", launchScript.absolutePath)
        }
    }
}

tasks.register("launchIosSimulatorFromIde") {
    group = "ide"
    description = "Build, install, and launch iosApp on the selected iOS simulator."
    doLast {
        val simulatorName = projectEnvOrDefault("IOS_SIMULATOR", "iPhone 16e")
        val scheme = projectEnvOrDefault("IOS_SCHEME", "iosApp")
        val bundleId = projectEnvOrDefault("IOS_BUNDLE_ID", "com.vdigital.volumestream.VolumeStream")
        val projectPath = rootProject.file("iosApp/iosApp.xcodeproj").absolutePath
        val derivedDataPath = rootProject.file("build/ios_derived/ide").absolutePath
        val appPath = "$derivedDataPath/Build/Products/Debug-iphonesimulator/$scheme.app"
        val launchScript = rootProject.file("composeApp/scripts/launch_ios_simulator.sh")
        if (!launchScript.exists()) {
            throw GradleException("iOS launch script not found: ${launchScript.absolutePath}")
        }

        exec {
            environment(
                mapOf(
                    "SIMULATOR" to simulatorName,
                    "SCHEME" to scheme,
                    "BUNDLE_ID" to bundleId,
                    "PROJECT_PATH" to projectPath,
                    "DERIVED_DATA" to derivedDataPath,
                    "APP_PATH" to appPath,
                )
            )
            commandLine("/bin/zsh", launchScript.absolutePath)
        }
    }
}

