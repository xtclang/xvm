import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Centralized credential management extension that provides unified access to
 * publishing credentials from properties and environment variables.
 */
abstract class XdkPublishingCredentials @Inject constructor(
    private val project: Project,
    private val providers: ProviderFactory
) {
    val gitHubUsername: Provider<String> = providers.provider {
        project.findProperty("GitHubUsername")?.toString()
            ?: providers.environmentVariable("GITHUB_ACTOR").getOrNull()
            ?: ""
    }

    val gitHubPassword: Provider<String> = providers.provider {
        project.findProperty("GitHubPassword")?.toString()
            ?: providers.environmentVariable("GITHUB_TOKEN").getOrNull()
            ?: ""
    }

    val gradlePublishKey: Provider<String> = providers.provider {
        project.findProperty("gradle.publish.key")?.toString()
            ?: providers.environmentVariable("GRADLE_PUBLISH_KEY").getOrNull()
            ?: ""
    }

    val gradlePublishSecret: Provider<String> = providers.provider {
        project.findProperty("gradle.publish.secret")?.toString()
            ?: providers.environmentVariable("GRADLE_PUBLISH_SECRET").getOrNull()
            ?: ""
    }

    val enableGitHub: Provider<Boolean> = providers.provider {
        getXdkPropertyBoolean("org.xtclang.publish.GitHub", true)
    }

    val enablePluginPortal: Provider<Boolean> = providers.provider {
        getXdkPropertyBoolean("org.xtclang.publish.gradlePluginPortal", false)
    }

    val enableMavenCentral: Provider<Boolean> = providers.provider {
        getXdkPropertyBoolean("org.xtclang.publish.mavenCentral", false)
    }

    private fun getXdkPropertyBoolean(name: String, defaultValue: Boolean): Boolean =
        project.findProperty(name)?.toString()?.toBoolean() ?:
        providers.environmentVariable(name.uppercase().replace('.', '_')).getOrNull()?.toBoolean() ?:
        defaultValue
}