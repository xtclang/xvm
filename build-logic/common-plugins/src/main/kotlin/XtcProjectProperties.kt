/**
 * Property helper for all projects.
 *
 * This is specific to the XDK build, and we really don't want it to be part of the plugin.
 * The properties are read from any "*.properties" file, starting in a given project directory.
 * The directory tree is then walked upwards to (and including) the ancestral Gradle project
 * root directory. Any value assigned deeper in the tree, overwrites any value defined at a more
 * shallow level, so that you can override global default property values in a more specific
 * context, such as a subproject.
 *
 * Finally, the property resolver checks the GRADLE_USER_HOME directory, which is a recommended
 * place to keep property files with secrets, like GitHub credentials, etc.
 */

import org.gradle.api.Project
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*
import java.util.Objects.requireNonNull

internal val xtcPropertiesResolver = XtcProjectPropertiesResolver()

fun Project.xdkProperty(key: String, defaultValue: String? = null): String {
    return xtcPropertiesResolver.get(this, key, defaultValue)
}

fun Project.xdkPropertyOrgXvm(key: String, defaultValue: String? = null): String {
    return xdkProperty("org.xvm.$key", defaultValue)
}

class XtcProjectPropertiesResolver {
    private val cache = mutableMapOf<Project, Map<String, String>>()
    private val secrets = mutableSetOf<String>()

    fun get(project: Project, key: String, defaultValue: String?): String {
        val all = resolveXtcProjectProperties(project)
        val value = all.getOrDefault(key, defaultValue)
        val logger = project.logger
        val prefix = project.prefix
        if (value == null) {
            val envKey = propertyNameToEnvVariableName(key)
            println("key -> envKey: $key -> $envKey")
            logger.warn("$prefix Unknown property $key; (TODO: implement logic to check if it is overridden by an environment variable instead?)")
        }
        if (value == null && defaultValue == null) {
            throw project.buildException("$prefix ERROR; property $key has no value, and no default was given.")
        }
        return value.toString()
    }

    private fun propertyNameToEnvVariableName(key: String): String {
        return buildString {
            var upper = false
            for (i in 0..key.length) {
                val c = key[i]
                if (c == '.') {
                    upper = true
                } else {
                    append(if (upper) c.uppercaseChar() else c)
                    upper = false
                }
            }
        }
    }

    private fun isSecret(key: String): Boolean {
        return secrets.contains(key)
    }

    private fun resolveXtcProjectProperties(project: Project): Map<String, String> {
        val cached: Map<String, String>? = cache[project]
        val logger = project.logger
        val prefix = project.prefix

        if (cached != null) {
            logger.info("$prefix Retrieved cached ${project.name} properties from property cache (${cached.count()} properties).")
            return cached
        }

        val all = Properties()

        // Merge to all properties from another file if they aren't set already
        fun mergeFromDir(to: Properties, dir: File, secret: Boolean = false): Properties {
            fun merge(to: Properties, from: Properties): Properties {
                from.forEach { key, value ->
                    // Check if this property is set already, then it's declared on a deeper level, and should not be reset.s
                    val prev = to.putIfAbsent(key, value)
                    if (prev == null) {
                        logger.info("$prefix Added project property: $key")
                    }
                }
                return to
            }

            assert(dir.isDirectory)
            val local = Properties()
            val files = requireNonNull(dir.listFiles()).filter { it.isFile && it.extension == "properties" }.toSet()
            if (files.isEmpty()) {
                return local
            }

            logger.info("$prefix Ingesting properties from $files")
            files.forEach { f ->
                assert(f.exists())
                assert(f.isFile)
                InputStreamReader(FileInputStream(f), Charsets.UTF_8).use {
                    logger.info("$prefix Reading ${f.absolutePath}...")
                    local.load(it)
                }
            }

            if (secret) {
                local.keys.forEach {
                    logger.info("$prefix Treating property '$it' as a secret.")
                    secrets.add(it.toString())
                }
            }

            return merge(to, local)
        }

        val root = project.compositeRootProjectDirectory.asFile
        var dir = project.projectDir
        logger.info("$prefix Ingesting *.properties files from '${dir.absolutePath}'...")

        do {
            mergeFromDir(all, dir)
            dir = dir.parentFile
        } while (dir != root.parentFile) // include composite root as last directory to scan for properties.

        // The GRADLE_USER_HOME is a recommended place to keep e.g. secret settings such as GitHub tokens for publications and so on...
        logger.info("$prefix Resolving (secret) properties from GRADLE_USER_HOME (${project.gradle.gradleUserHomeDir.absolutePath}...")
        mergeFromDir(all, project.gradle.gradleUserHomeDir, true)

        logger.info("$prefix Resolved all XTC Gradle properties from ${project.name} up to ancestral root (dir: $root)... ")
        val allMap = buildMap {
            all.forEach { (k, v) ->
                assert(k != null)
                if (v != null) {
                    put(k.toString(), v.toString())
                }
            }
        }

        assert(cache[project] == null)
        cache[project] = allMap
        logger.info("$prefix Adding project '${project.name}' to property cache (${allMap.count()} properties).")

        return allMap
    }

    override fun toString(): String {
        return buildString {
            appendLine("XDK build properties cache state:")
            if (cache.isEmpty()) {
                appendLine("   (empty)")
            } else {
                val keyCount = cache.keys.size
                val valueCount = cache.values.sumOf { it.size }
                appendLine("    Keys (number of projects): $keyCount")
                appendLine("    Values: $valueCount")
                cache.forEach { (project, props) ->
                    appendLine("    Project: ${project.name} (${props.size} properties):")
                    props.keys.sorted().forEach { key ->
                        val value = if (isSecret(key)) "[REDACTED]" else props[key]
                        appendLine("        $key (value: '$value')")
                    }
                }
            }
        }.trim()
    }
}
