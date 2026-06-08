plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}
repositories { mavenCentral() }
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    implementation(project(":runtime"))

    testImplementation(libs.kctfork.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
}
java {
    toolchain { languageVersion = JavaLanguageVersion.of(11) }
}
tasks.test { useJUnitPlatform() }
