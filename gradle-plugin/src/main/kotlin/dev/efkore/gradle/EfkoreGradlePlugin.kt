package dev.efkore.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginConfig
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class EfkoreGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginConfig(): KotlinCompilerPluginConfig? =
        KotlinCompilerPluginConfig("dev.efkore.compiler.EfkoreCompilerRegistrar")

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<SubpluginOption> {
        val project: Project = kotlinCompilation.target.project
        return project.provider { SubpluginOption("enabled", "true") }
    }
}
