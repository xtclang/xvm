import org.gradle.api.provider.MapProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File

abstract class XdkPropertiesService : BuildService<XdkPropertiesService.Params>, AutoCloseable {
    interface Params : BuildServiceParameters {
        val entries: MapProperty<String, String>
    }

    fun get(key: String): String? = parameters.entries.get()[key]

    override fun close() { }  // No resources to clean up

    companion object {
        /**
         * Find the composite root directory by walking up from startDir until we find
         * a marker file (typically gradle.properties or version.properties).
         *
         * This utility is shared between settings plugins and build plugins to ensure consistent
         * composite root resolution across the entire composite build.
         */
        fun compositeRootDirectory(startDir: File, markerFile: String = "version.properties"): File {
            var dir = startDir
            while (dir.parentFile != null && !File(dir, markerFile).exists()) {
                dir = dir.parentFile
            }
            return dir
        }

        /**
         * Find a file relative to the composite root (not just rootProject, which may be an included build).
         * Walks up the directory tree until it finds the requested file.
         *
         * This utility is shared between settings plugins and build plugins to ensure consistent
         * property file resolution across the entire composite build.
         */
        fun compositeRootRelativeFile(startDir: File, path: String): File {
            var dir = startDir
            var file = File(dir, path)
            while (!file.exists() && dir.parentFile != null) {
                dir = dir.parentFile
                file = File(dir, path)
            }
            return file
        }
    }
}
