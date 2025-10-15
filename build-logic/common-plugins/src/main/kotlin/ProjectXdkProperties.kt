import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

abstract class ProjectXdkProperties @Inject constructor(
    private val providers: ProviderFactory,
    private val service: XdkPropertiesService
) {
    private fun resolve(key: String): String? =
        providers.environmentVariable(toEnvKey(key)).orNull
            ?: providers.gradleProperty(key).orNull
            ?: providers.systemProperty(key).orNull
            ?: service.get(key)

    fun string(key: String): Provider<String> =
        providers.provider { resolve(key) ?: error("Missing property '$key'") }

    fun string(key: String, default: String): Provider<String> =
        providers.provider { resolve(key) ?: default }

    fun int(key: String): Provider<Int> = string(key).map(String::toInt)

    fun int(key: String, default: Int): Provider<Int> =
        string(key, default.toString()).map(String::toInt)

    fun boolean(key: String): Provider<Boolean> = string(key).map(String::toBoolean)

    fun boolean(key: String, default: Boolean): Provider<Boolean> =
        string(key, default.toString()).map(String::toBoolean)

    fun stringValue(key: String): String = string(key).get()

    fun stringValue(key: String, default: String): String = string(key, default).get()

    fun intValue(key: String): Int = int(key).get()

    fun intValue(key: String, default: Int): Int = int(key, default).get()

    fun booleanValue(key: String): Boolean = boolean(key).get()

    fun booleanValue(key: String, default: Boolean): Boolean = boolean(key, default).get()

    fun has(key: String): Boolean = resolve(key) != null

    fun hasProvider(key: String): Provider<Boolean> = providers.provider { resolve(key) != null }

    /**
     * Convert a property key to environment variable format.
     * Examples:
     *   org.xtclang.publish.github -> ORG_XTCLANG_PUBLISH_GITHUB
     *   githubUsername -> GITHUB_USERNAME
     *   mavenCentralUsername -> MAVEN_CENTRAL_USERNAME
     */
    private fun toEnvKey(key: String): String {
        // First replace dots with underscores
        val withUnderscores = key.replace('.', '_')
        // Then insert underscores before uppercase letters in camelCase
        val withCamelCase = withUnderscores.replace(Regex("([a-z])([A-Z])")) { matchResult ->
            "${matchResult.groupValues[1]}_${matchResult.groupValues[2]}"
        }
        return withCamelCase.uppercase()
    }
}

/**
 * Typed extension accessor for ProjectXdkProperties.
 * Use this in build scripts to access properties with Provider API.
 * Example: val jdk = xdkProperties.int("org.xtclang.java.jdk")
 */
val Project.xdkProperties: ProjectXdkProperties
    get() = extensions.getByType(ProjectXdkProperties::class.java)

/**
 * Semantic version accessor (group:name:version).
 */
val Project.semanticVersion: String
    get() = "$group:$name:$version"
