import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Objects.requireNonNull
import java.util.Properties

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
 *
 * The property helper tries to ensure that no standard or inherited task that derives from, ore
 * uses "./gradlew properties" will inadvertently dump secrets to the console.
 */
class XdkProperties(buildLogic: XdkBuildLogic) {
    private val project = buildLogic.project
    private val secrets = mutableSetOf<String>()
    private val props = resolveXtcProjectProperties()

    /**
     * Get a property value from the nested property resolution for this project, or
     * throw an exception if the property does not exist, and no default value is given.
     *
     * The method first checks for the property by its "xdk" name, i.e., on the format
     * "org.xtclang.group.someThing". If that fails, it tries to check the System environment
     * for "ORG_XVM_GROUP_SOME_THING", as a handy shortcut, if you need to modify something
     * in a build run, that isn't declared in a property file. Examples could be testing out
     * a new GitHub token, or adding an environment variable that the plugin needs at start
     * time (for example, because the project logger outputs aren't inherited by default in
     * a plugin, even if it has access to the actual project.logger instance), for the
     * project to which the plugin is applied.
     *
     * If the method fails to find the value of a property, both in its resolved property
     * map, and in the system environment, it will return the supplied defaultValue
     * parameter. If no default value was supplied, an exception will be thrown, and the
     * build breaks.
     */
    fun get(key: String, defaultValue: String? = null): String {
        val value = props[key] ?: System.getenv(toSystemEnvKey(key)) ?: defaultValue
        if (value == null && defaultValue == null) {
            throw project.buildException("ERROR; property $key has no value, and no default was given.")
        }
        return value.toString()
    }

    private fun isSecret(key: String): Boolean {
        return secrets.contains(key)
    }

    /**
     * Convert e.g. org.xtclang.something.testWithCamelCase -> ORG_XVM_SOMETHING_TEST_WITH_CAMEL_CASE
     */
    private fun toSystemEnvKey(key: String): String {
        // TODO: Unit test keys and make sure no secrets are kept in memory or printed/logged.
        return buildString {
            for (ch in key) {
                when {
                    ch == '.' -> append('_')
                    ch.isLowerCase() -> append(ch.uppercaseChar())
                    ch.isUpperCase() -> append('_').append(ch)
                    else -> append(ch)
                }
            }
        }
    }

    /*
     * The method looks for all properties files from the project directory and upwards to the
     * gradle.rootDirectory. It also checks GRADLE_USER_HOME and its init.d folder, if it exists.
     */
    private fun resolveXtcProjectProperties(): Properties = project.run {
        val root = project.compositeRootProjectDirectory.asFile
        var dir = project.projectDir
        val gradleUserHomeDir = project.gradle.gradleUserHomeDir
        val gradleInitDir = gradleUserHomeDir.resolve("init.d")

        logger.info("$prefix Ingesting *.properties files from '${dir.absolutePath}'...")
        val all = Properties()
        do {
            mergeFromDir(all, dir)
            dir = dir.parentFile
        } while (dir != root.parentFile) // include composite root as last directory to scan for properties.

        mergeFromDir(all, gradleInitDir).keys.forEach { secrets.add(it.toString()) }
        if (gradleInitDir.exists() && gradleInitDir.isDirectory) {
            mergeFromDir(all, gradleInitDir).keys.forEach { secrets.add(it.toString()) }
        }
        logger.info("$prefix Resolved all XDK Gradle properties (${secrets.size} secrets) from $name up to ancestral root (dir: $root)... ")
        return all
    }

    override fun toString(): String = project.run {
        return buildString {
            append("$prefix XVM Project Properties:")
            if (props.isEmpty) {
                appendLine(" [empty]")
            }
            props.keys.map { it.toString() }.sorted().forEach { key ->
                val value = if (isSecret(key)) XdkBuildLogic.REDACTED else props[key]
                appendLine("$prefix     $key = '$value'")
            }
        }
    }

    private fun merge(to: Properties, from: Properties): Properties = project.run {
        from.forEach { key, value ->
            // Check if this property is set already, then it's declared on a deeper level, and should not be reset.
            val prev = to.putIfAbsent(key, value)
            if (prev == null) {
                logger.info("$prefix Added project property: $key")
            }
        }
        return to
    }

    // Merge to all properties from another file if they aren't set already
    private fun mergeFromDir(to: Properties, dir: File): Properties = project.run {
        assert(dir.isDirectory)

        val files = requireNonNull(dir.listFiles())
            .filter { it.isFile && it.extension == "properties" }
            .toSet()
        val local = Properties()

        if (files.isEmpty()) {
            return local
        }

        logger.info("$prefix Ingesting properties from $files")
        files.forEach { f ->
            assert(f.exists())
            assert(f.isFile)
            logger.info("$prefix     Reading ${f.absolutePath}...")
            InputStreamReader(FileInputStream(f), Charsets.UTF_8).use {
                local.load(it)
            }
        }

        return merge(to, local)
    }
}
