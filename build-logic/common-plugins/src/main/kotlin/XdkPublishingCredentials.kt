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
        getXdkPropertyBooleanProvider("org.xtclang.publish.gitHub", false)

    val enablePluginPortal: Provider<Boolean> =
        getXdkPropertyBooleanProvider("org.xtclang.publish.gradlePluginPortal", false)

    val enableMavenCentral: Provider<Boolean> =
        getXdkPropertyBooleanProvider("org.xtclang.publish.mavenCentral", false)

    // Maven Central credentials (Vanniktech plugin expects these property names)
    val mavenCentralUsername: Provider<String> =
        providers.gradleProperty("mavenCentralUsername")
            .orElse(providers.environmentVariable("MAVEN_CENTRAL_USERNAME"))
            .orElse(providers.environmentVariable("SONATYPE_USERNAME"))
            .orElse(providers.environmentVariable("OSSRH_USERNAME"))
            .orElse(providers.provider { "" })

    val mavenCentralPassword: Provider<String> =
        providers.gradleProperty("mavenCentralPassword")
            .orElse(providers.environmentVariable("MAVEN_CENTRAL_PASSWORD"))
            .orElse(providers.environmentVariable("SONATYPE_PASSWORD"))
            .orElse(providers.environmentVariable("OSSRH_PASSWORD"))
            .orElse(providers.provider { "" })

    // Maven Central signing credentials
    val signingKeyId: Provider<String> =
        providers.gradleProperty("signing.keyId")
            .orElse(providers.environmentVariable("SIGNING_KEY_ID"))
            .orElse(providers.provider { "" })

    val signingPassword: Provider<String> =
        providers.gradleProperty("signing.password")
            .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
            .orElse(providers.provider { "" })

    val signingSecretKey: Provider<String> =
        providers.gradleProperty("signing.secretKeyRingFile")
            .orElse(providers.environmentVariable("SIGNING_SECRET_KEY_RING_FILE"))
            .orElse(providers.provider { "" })

    // Vanniktech plugin uses signingInMemoryKey for base64-encoded GPG key
    val signingInMemoryKey: Provider<String> =
        providers.gradleProperty("signingInMemoryKey")
            .orElse(providers.environmentVariable("SIGNING_IN_MEMORY_KEY"))
            .orElse(providers.provider { "" })

    private fun getXdkPropertyBooleanProvider(name: String, defaultValue: Boolean): Provider<Boolean> {
        val propertyProvider = providers.gradleProperty(name).map { it.toBoolean() }
        val envVarName = name.uppercase().replace('.', '_')
        val envProvider = providers.environmentVariable(envVarName).map { it.toBoolean() }
        return propertyProvider.orElse(envProvider).orElse(providers.provider { defaultValue })
    }
}
