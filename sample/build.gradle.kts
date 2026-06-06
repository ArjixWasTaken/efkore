plugins {
    alias(libs.plugins.kotlin.jvm)
    id("dev.efkore") version "0.1.0"
}
repositories { mavenCentral() }
dependencies {
    implementation(project(":runtime"))
    implementation(libs.slf4j.simple)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.r2dbc.h2)
}
tasks.test { useJUnitPlatform() }
