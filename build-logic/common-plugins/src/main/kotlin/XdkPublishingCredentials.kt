import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

/**
 * Centralized credential management extension that provides unified access to
 * publishing credentials from properties and environment variables.
 */
abstract class XdkPublishingCredentials @Inject constructor(
    private val providers: ProviderFactory
) {
    val githubUsername: Provider<String> =
        providers.gradleProperty("githubUsername")
            .orElse(providers.environmentVariable("GITHUB_ACTOR"))
            .orElse(providers.provider { "" })

    val githubPassword: Provider<String> =
        providers.gradleProperty("githubPassword")
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

    // Maven Central / Sonatype credentials
    val mavenCentralUsername: Provider<String> =
        providers.gradleProperty("mavenCentralUsername")
            .orElse(providers.environmentVariable("MAVEN_CENTRAL_USERNAME"))
            .orElse(providers.provider { "" })

    val mavenCentralPassword: Provider<String> =
        providers.gradleProperty("mavenCentralPassword")
            .orElse(providers.environmentVariable("MAVEN_CENTRAL_PASSWORD"))
            .orElse(providers.provider { "" })

    // Signing credentials
    val signingKeyId: Provider<String> =
        providers.gradleProperty("signing.keyId")
            .orElse(providers.environmentVariable("SIGNING_KEY_ID"))
            .orElse(providers.provider { "" })

    val signingPassword: Provider<String> =
        providers.gradleProperty("signing.password")
            .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
            .orElse(providers.provider { "" })

    val signingSecretKeyRingFile: Provider<String> =
        providers.gradleProperty("signing.secretKeyRingFile")
            .orElse(providers.environmentVariable("SIGNING_SECRET_KEY_RING_FILE"))
            .orElse(providers.provider { "" })

    // In-memory signing key with escaped newlines (Gradle standard property)
    // Format: signing.key=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
    val signingKey: Provider<String> =
        providers.gradleProperty("signing.key")
            .orElse(providers.environmentVariable("SIGNING_KEY"))
            .map { it.replace("\\n", "\n") } // Unescape \n to actual newlines
            .orElse(providers.provider { "" })

    // Legacy in-memory signing key (Vanniktech-specific, kept for compatibility)
    val signingInMemoryKey: Provider<String> =
        providers.gradleProperty("signingInMemoryKey")
            .orElse(providers.environmentVariable("SIGNING_IN_MEMORY_KEY"))
            .orElse(providers.provider { "" })

    // Publishing toggles
    val enableGithub: Provider<Boolean> =
        getXdkPropertyBooleanProvider("org.xtclang.publish.github", true)

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
