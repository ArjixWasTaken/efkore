plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin.api)
    implementation(project(":compiler"))
}

gradlePlugin {
    plugins {
        create("efkore") {
            id = "dev.efkore"
            implementationClass = "dev.efkore.gradle.EfkoreGradlePlugin"
        }
    }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}
