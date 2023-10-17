import org.gradle.api.Project
import java.io.File
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Sanity checker for jar files. Triggers a build error if specified path elements are
 * not present in the jar file, and/or if the number of entries in the jar file was
 * not equal to an optional specified size.
 */
fun verifyJarFileContents(project: Project, required: List<String>, size: Int = -1): Boolean {
    fun jarContents(jarFile: File): Set<String> {
        val contents = mutableMapOf<String, Long>()
        JarFile(jarFile).use { jar ->
            val enumEntries: Enumeration<JarEntry> = jar.entries()
            while (enumEntries.hasMoreElements()) {
                val entry: JarEntry = enumEntries.nextElement() as JarEntry
                contents[entry.name] = entry.size
            }
        }
        return contents.keys
    }
    val jar = project.tasks.getByName("jar").outputs.files.singleFile
    val contents = jarContents(jar)

    // TODO: Very hacky sanity check verification. Need to keep this updated or remove it when we are confident artifact creation is race free
    if (size >= 0 && contents.size != size) {
        throw project.buildException("ERROR: Expected '$jar' to contain $size entries (was: ${contents.size})")
    }

    required.forEach {
        fun matches(contents: Collection<String>, match: String): Boolean {
            // Get all keys with match in them, but without '$' in them (inner classes do not count)
            return contents.filter { name -> name.contains(match) && !name.contains("$") }.size == 1
        }
        if (!matches(contents, it)) {
            throw project.buildException("ERROR: Corrupted jar file; needs to contain entry matching '$it'")
        }
    }

    return true
}
