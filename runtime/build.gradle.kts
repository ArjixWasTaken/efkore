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
    implementation(libs.slf4j.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(11) }
}
