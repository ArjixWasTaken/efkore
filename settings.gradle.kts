plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "efkore"
include("runtime", "compiler", "gradle-plugin", "sample")
