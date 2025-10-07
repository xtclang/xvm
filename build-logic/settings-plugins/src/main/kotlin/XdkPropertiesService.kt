import org.gradle.api.provider.MapProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class XdkPropertiesService : BuildService<XdkPropertiesService.Params>, AutoCloseable {
    interface Params : BuildServiceParameters {
        val entries: MapProperty<String, String>
    }

    fun get(key: String): String? = parameters.entries.get()[key]

    override fun close() { }  // No resources to clean up
}
