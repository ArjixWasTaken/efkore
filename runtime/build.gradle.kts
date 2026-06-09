plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.reactive)
    api(libs.r2dbc.spi)
    api(libs.kotlin.reflect)
    api(libs.zeko.sql.builder)
    implementation(libs.slf4j.api)

    testImplementation(libs.r2dbc.h2)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.slf4j.simple)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(11) }
}

tasks.test {
    useJUnitPlatform()
}
