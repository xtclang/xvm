import org.gradle.api.Project
import java.io.File
import java.io.FileInputStream
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
class XdkProperties(project: Project): XdkProjectBuildLogic(project) {
    private val secrets = mutableSetOf<String>()
    private val properties: Properties = resolve()

    init {
        toString().lines().forEach { logger.info("$prefix $it") }
    }

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
        val value = properties[key] ?: System.getenv(toSystemEnvKey(key)) ?: defaultValue
        if (value == null && defaultValue == null) {
            throw project.buildException("ERROR: Property '$key' has no value, and no default was given.")
        }
        return value.toString()
    }

    override fun toString(): String {
        return buildString {
            append("XdkProperties('${project.name}'):")
            if (properties.isEmpty) {
                append(" [empty]")
            }
            appendLine()
            properties.keys.map { it.toString() }.sorted().forEach { key ->
                append("    $key = ")
                val value = if (isSecret(key)) REDACTED else "'${properties[key]}'"
                appendLine(value)
            }
        }
    }

    private fun resolve(includeExternal: Boolean = true): Properties {
        val all = Properties()
        val ext = Properties()
        resolveProjectDirs().forEach { mergeFromDir(all, it) }
        if (includeExternal) {
            resolveExternalDirs().forEach { mergeFromDir(ext, it) }
            ext.keys.map { it.toString() }.forEach(secrets::add)
        }
        logger.info("$prefix Internals; loaded properties (${all.size} internal, ${ext.size} external).")
        return merge(all, ext)
    }

    private fun resolveProjectDirs(): List<File> {
        val compositeRoot = project.compositeRootProjectDirectory.asFile
        var dir = project.projectDir
        return buildList {
            do {
                add(dir)
                dir = dir.parentFile
            } while (dir != compositeRoot.parentFile)
        }
    }

    private fun resolveExternalDirs(): List<File> {
        return buildList {
            add(project.gradle.gradleUserHomeDir)
            val gradleInitDir = project.userInitScriptDirectory
            if (gradleInitDir.isDirectory) {
                add(gradleInitDir)
            }
        }
    }

    /**
     * Accessor to check if a property key resolved by this project has a secret value.
     * This means that we should never log it, print it, or in any way leak it from a run.
     * Secret values <=> properties defined outside the project hiearchy, e.g. in
     * GRADLE_USER_HOME or GRADLE_USER_HOME/init.d, or similar well-defined places.
     */
    private fun isSecret(key: String): Boolean {
        return secrets.contains(key)
    }

    /**
     * For each property, check if this property is set already, then it's declared on a deeper
     * level, and should not be reset.
     */
    private fun merge(to: Properties, from: Properties): Properties {
        from.forEach { key, value ->
            val old = to.putIfAbsent(key, value)
            if (old == null) {
                logger.info("$prefix Defined new property: '$key'")
            } else {
                logger.info("$prefix Property '$key' already defined, not overwriting.")
                if (old != value) {
                    logger.info("$prefix     WARNING: Property '$key' has different values at different levels.")
                }
            }
        }
        return to
    }

    private fun mergeFromDir(to: Properties, dir: File): Properties = project.run {
        assert(dir.isDirectory)
        val files = requireNonNull(dir.listFiles()).filter { it.isFile && it.extension == PROPERTIES_EXT }.toSet()
        val local = Properties()
        if (files.isEmpty()) {
            return local
        }
        for (f in files) {
            assert(f.exists() && f.isFile)
            FileInputStream(f).use { local.load(it) }
            logger.info("$prefix Loaded ${local.size} properties from ${f.absolutePath}")
        }
        return merge(to, local)
    }

    companion object {
        const val REDACTED = "[REDACTED]"

        private const val PROPERTIES_EXT = "properties"

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
    }
}
