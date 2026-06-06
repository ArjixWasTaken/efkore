plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}
repositories { mavenCentral() }
dependencies {
    implementation(project(":compiler"))
    compileOnly(libs.kotlin.gradle.plugin.api)
}
gradlePlugin {
    plugins {
        register("efkore") {
            id = "dev.efkore"
            implementationClass = "dev.efkore.gradle.EfkoreGradlePlugin"
        }
    }
}
