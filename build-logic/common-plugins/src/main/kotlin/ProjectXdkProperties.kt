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

    fun has(key: String): Boolean = resolve(key) != null

    fun hasProvider(key: String): Provider<Boolean> = providers.provider { resolve(key) != null }

    private fun toEnvKey(key: String) = key.replace('.', '_').uppercase()
}
