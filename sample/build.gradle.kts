plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Wire the efkore compiler plugin directly (avoids needing publishToMavenLocal)
val efkorePlugin by configurations.creating { isTransitive = false }
dependencies { efkorePlugin(project(":compiler")) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    dependsOn(efkorePlugin.buildDependencies)
    compilerOptions.freeCompilerArgs.addAll(
        provider { efkorePlugin.map { "-Xplugin=${it.absolutePath}" } }
    )
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(project(":runtime"))
    implementation(libs.r2dbc.h2)
    implementation(libs.slf4j.simple)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

tasks.test {
    useJUnitPlatform()
}
