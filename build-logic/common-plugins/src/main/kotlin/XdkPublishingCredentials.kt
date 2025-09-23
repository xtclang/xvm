import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Centralized credential management extension that provides unified access to
 * publishing credentials from properties and environment variables.
 */
abstract class XdkPublishingCredentials @Inject constructor(
    //private val project: Project,
    private val providers: ProviderFactory
) {
    val gitHubUsername: Provider<String> =
        providers.gradleProperty("GitHubUsername")
            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
            .orElse(providers.provider { "" })

    val gitHubPassword: Provider<String> =
        providers.gradleProperty("GitHubPassword")
            .orElse(providers.environmentVariable("GITHUB_TOKEN"))
            .orElse(providers.provider { "" })

    val gradlePublishKey: Provider<String> =
        providers.gradleProperty("gradle.publish.key")
            .orElse(providers.environmentVariable("GRADLE_PUBLISH_KEY"))
            .orElse(providers.provider { "" })

    val gradlePublishSecret: Provider<String> =
        providers.gradleProperty("gradle.publish.secret")
            .orElse(providers.environmentVariable("GRADLE_PUBLISH_SECRET"))
            .orElse(providers.provider { "" })

    val enableGitHub: Provider<Boolean> =
        getXdkPropertyBooleanProvider("org.xtclang.publish.GitHub", true)

    val enablePluginPortal: Provider<Boolean> =
        getXdkPropertyBooleanProvider("org.xtclang.publish.gradlePluginPortal", false)

    val enableMavenCentral: Provider<Boolean> =
        getXdkPropertyBooleanProvider("org.xtclang.publish.mavenCentral", false)

    private fun getXdkPropertyBooleanProvider(name: String, defaultValue: Boolean): Provider<Boolean> {
        val propertyProvider = providers.gradleProperty(name).map { it.toBoolean() }
        val envVarName = name.uppercase().replace('.', '_')
        val envProvider = providers.environmentVariable(envVarName).map { it.toBoolean() }
        return propertyProvider.orElse(envProvider).orElse(providers.provider { defaultValue })
    }
}
