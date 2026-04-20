import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails

abstract class TvosToIosKlibCompatibilityRule : AttributeCompatibilityRule<String> {
    override fun execute(details: CompatibilityCheckDetails<String>) {
        val consumer = details.consumerValue ?: return
        val producer = details.producerValue ?: return
        if (
            consumer.startsWith("tvos_") &&
            producer.startsWith("ios_") &&
            consumer.removePrefix("tvos_") == producer.removePrefix("ios_")
        ) {
            details.compatible()
        }
    }
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

subprojects {
    if (
        path.startsWith(":feature:") ||
        path == ":core:player" ||
        path == ":composeApp"
    ) {
        dependencies {
            attributesSchema {
                attribute(Attribute.of("org.jetbrains.kotlin.native.target", String::class.java)) {
                    compatibilityRules.add(TvosToIosKlibCompatibilityRule::class.java)
                }
            }
        }
    }
}
