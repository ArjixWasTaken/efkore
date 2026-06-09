package dev.efkore.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class EfkoreGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun getCompilerPluginId() = "dev.efkore.compiler"

    override fun getPluginArtifact() = SubpluginArtifact(
        groupId = "dev.efkore",
        artifactId = "compiler",
        version = "unspecified"
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>) = true

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }
}
