import org.gradle.api.Project
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import org.gradle.api.GradleException
import org.gradle.api.file.ProjectLayout
import org.gradle.api.invocation.Gradle
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.Objects.requireNonNull
import java.util.Properties

class XdkBuildLogic(val project: Project) {
    private val prefix = "[${project.name}]"
    private val logger = project.logger

    private val props = XdkPropertyManager()

    fun gitHubClient(): GitHubPackages {
        return GitHubPackages(this)
    }

    private fun findExecutableOnPath(executable: String): Path? {
        return System.getenv(ENV_PATH)?.split(File.pathSeparator)?.map { File(it, executable) }?.first { it.exists() && it.canExecute() }?.toPath()?.toRealPath()
    }

    fun resolveLocalXdkInstallation(): File {
        return findExecutableOnPath(XTC_LAUNCHER)?.toFile()?.parentFile?.parentFile ?: throw project.buildException("Could not find local installation of XVM.")
    }

    fun getPropertyBoolean(key: String, defaultValue: Boolean? = null): Boolean {
        return props.get(key, defaultValue.toString()).toBoolean()
    }

    fun getProperty(key: String, defaultValue: String? = null): String {
        return props.get(key, defaultValue)
    }

    companion object {
        const val XDK_TASK_GROUP_DEBUG = "debug"
        const val XDK_TASK_GROUP_VERSION = "version"

        const val XDK_VERSION_CATALOG_GROUP = "xdkgroup"
        const val XDK_VERSION_CATALOG_VERSION = "xdk"

        const val REDACTED = "[REDACTED]"

        const val ENV_PATH = "PATH"
        const val XTC_LAUNCHER = "xec"

        // companion objects are guaranteed to be singletons by Kotlin.
        private val cache: MutableMap<Project, XdkBuildLogic> = mutableMapOf()

        fun resolve(project: Project): XdkBuildLogic {
            return cache[project] ?: XdkBuildLogic(project).also { cache[project] = it }
        }
    }

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
    private inner class XdkPropertyManager {
        private val secrets = mutableSetOf<String>()
        private val props = resolveXtcProjectProperties()

        /**
         * Get a property value from the nested property resolution for this project, or
         * throw an exception if the property does not exist, and no default value is given.
         *
         * The method first checks for the property by its "xdk" name, i.e., on the format
         * "org.xvm.group.someThing". If that fails, it tries to check the System environment
         * for "ORG_XVM_GROUP_SOME_THING", as a handy shortcut, if you need to modify something
         * in a build run, that isn't declared in a property file. Examples could be testing out
         * a new GitHub token, or adding an environment variable that the plugin needs at start
         * time (for example, because the project logger outputs aren't inherited by default in
         * a plugin, even if it has access to the actual project.logger instance, for the
         * project to which the plugin is applied.
         *
         * If the method fails to find the value of a property, both in its resolved property
         * map, as well as in the system environment, it will return the supplied defaultValue
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

        /*
         * Check if a property is a secret, and should not be printed or logged.
         */
        private fun isSecret(key: String): Boolean {
            return secrets.contains(key)
        }

        private fun toSystemEnvKey(key: String): String {
            // Convert e.g. org.xvm.something.testWithCamelCase -> ORG_XVM_SOMETHING_TEST_WITH_CAMEL_CASE
            // TODO: Unit test keys and make sure no secrets are kept in memory or printed/logged.
            return buildString {
                for (ch: Char in key) {
                    when {
                        ch == '.' -> append('_')
                        ch.isLowerCase() -> append(ch.uppercaseChar())
                        ch.isUpperCase() -> append('_').append(ch)
                        else -> append(ch)
                    }
                }
            }
        }

        private fun purgeSecrets() {
            var count = 0
            secrets.filter { p -> project.hasProperty(p) }.forEach { secretKey ->
                project.setProperty(secretKey, REDACTED)
                logger.info("$prefix     Purged secret key/value pair (key: $secretKey') from memory.")
                assert(project.property(secretKey) == REDACTED)
                count++
            }
            logger.lifecycle("$prefix Finished purgeSecrets; $count secrets were removed.")
        }

        /*
         * The method looks for all properties files from the project directory and upwards to the
         * gradle.rootDirectory
         */
        private fun resolveXtcProjectProperties(): Map<String, String> {
            val all = Properties()

            fun merge(to: Properties, from: Properties): Properties {
                from.forEach { key, value ->
                    val prev = to.putIfAbsent(key, value) // Check if this property is set already, then it's declared on a deeper level, and should not be reset.
                    if (prev == null) {
                        logger.info("$prefix Added project property: $key")
                    }
                }
                return to
            }

            // Merge to all properties from another file if they aren't set already
            // TODO project.run
            fun mergeFromDir(to: Properties, dir: File, secret: Boolean = false): Properties = project.run {
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

            logger.info("$prefix Resolved all XDK Gradle properties from ${project.name} up to ancestral root (dir: $root)... ")
            val allMap = buildMap {
                all.forEach { (k, v) ->
                    assert(k != null)
                    if (v != null) {
                        put(k.toString(), v.toString())
                    }
                }
            }
            purgeSecrets()
            return allMap
        }

        override fun toString(): String {
            return buildString {
                append("$prefix XVM Project Properties:")
                if (props.isEmpty()) {
                    appendLine(" (empty)")
                }
                props.keys.sorted().forEach { key ->
                    val value = if (isSecret(key)) REDACTED else props[key]
                    appendLine("$prefix     $key = '$value'")
                }
            }
        }
    }
}

val Gradle.rootGradle: Gradle
    get() {
        var dir = this
        while (dir.parent != null) {
            dir = dir.parent!!
        }
        return dir
    }

val Gradle.rootLayout: ProjectLayout get() = rootGradle.rootProject.layout

val Project.compositeRootProjectDirectory get() = gradle.rootLayout.projectDirectory
val Project.prefix get() = "[$name]"
val Project.xdkBuildLogic: XdkBuildLogic get() = XdkBuildLogic.resolve(this)

// TODO: Hacky, use a config, but there is a mutual dependency between the lib_xtc and javatools.
//  Better to add the resource directory as a source set?
val Project.xdkImplicitsPath: String get() = "$compositeRootProjectDirectory/lib_ecstasy/src/main/resources/implicit.x"

fun Project.getXdkPropertyBoolean(key: String, defaultValue: Boolean? = false): Boolean {
    return xdkBuildLogic.getPropertyBoolean(key, defaultValue)
}

fun Project.getXdkProperty(key: String, defaultValue: String? = null): String {
    return xdkBuildLogic.getProperty(key, defaultValue)
}

fun Project.buildException(msg: String): Throwable {
    logger.error("$prefix $msg")
    return GradleException(msg)
}

fun Project.executeCommand(vararg args: String): String? {
    val output = ByteArrayOutputStream()
    val result = project.exec {
        commandLine(*args)
        standardOutput = output
        isIgnoreExitValue = false
    }
    result.assertNormalExitValue()
    return output.toString().trim().ifEmpty { null }
}
