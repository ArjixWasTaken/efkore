plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)

    testImplementation(libs.kctfork.core)
    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(project(":runtime"))
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
