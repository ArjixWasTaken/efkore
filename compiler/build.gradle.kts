plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}
repositories { mavenCentral() }
dependencies {
    compileOnly(libs.kotlin.compiler.embeddable)
    implementation(project(":runtime"))
}
java {
    toolchain { languageVersion = JavaLanguageVersion.of(11) }
}
