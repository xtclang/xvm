import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.findByType
import java.io.File
import java.util.Enumeration
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Simple helper functions, mostly printing out dependencies, inputs and outputs and other
 * useful information for an XDK build, that is a bit too brief to deserve tasks of its own.
 *
 * They are wrapped in helper tasks.
 */

fun Project.printAllTaskInputs(level: LogLevel = LIFECYCLE) {
    tasks.forEach { printTaskInputs(level, it.name) }
}

fun Project.printAllTaskOutputs(level: LogLevel = LIFECYCLE) {
    tasks.forEach { printTaskInputs(level, it.name) }
}

fun Project.printAllTaskDependencies(level: LogLevel = LIFECYCLE) {
    tasks.forEach { printTaskDependencies(level, it.name) }
}

fun Task.printTaskInputs(level: LogLevel = LIFECYCLE) {
    return project.printTaskInputs(level, name)
}

fun Task.printTaskOutputs(level: LogLevel = LIFECYCLE) {
    return project.printTaskOutputs(level, name)
}

fun Task.printTaskDependencies(level: LogLevel = LIFECYCLE) {
    return project.printTaskDependencies(level, name)
}

@Suppress("unused")
fun Project.printRepos(level: LogLevel = LIFECYCLE) {
    repositories.map { it.name }.forEach {
        logger.log(level, "$prefix Repository: '$it'")
    }
}

fun Project.printResolvedConfigFiles(level: LogLevel = LIFECYCLE, configNames: Collection<String>) {
    configNames.forEach { printResolvedConfigFile(level, it) }
}

fun Project.printAllResolvedConfigFiles(level: LogLevel = LIFECYCLE) {
    return printResolvedConfigFiles(level, project.configurations.map { it.name })
}

fun Project.checkTask(taskName: String): Task {
    return project.tasks.findByName(taskName) ?: throw buildException("No task named '$taskName' found.")
}

fun Project.printResolvedConfigFiles(level: LogLevel = LIFECYCLE, configName: String) {
    // This only works on resolved configurations, and after the configuration phase.
    val config = configurations.getByName(configName)
    if (!config.isCanBeResolved) {
        logger.warn("$prefix Configuration '$configName' is not resolvable.")
        return
    }
    val files = config.resolvedConfiguration.resolvedArtifacts.map { it.file }
    logger.log(level, "$prefix Configuration '$configName' has ${files.size} files:")
    files.forEach {
        logger.log(level, "$prefix    file: '$it'")
    }
}

fun Project.printTaskInputs(level: LogLevel = LIFECYCLE, taskName: String) {
    val task = tasks.getByName(taskName)
    val outputs = task.inputs.files
    logger.log(level, "$prefix Task '$taskName' has ${outputs.files.size} inputs:")
    outputs.asFileTree.forEach { logger.log(level, "$prefix   input : '$it'") }
}

fun Project.printTaskOutputs(level: LogLevel = LIFECYCLE, taskName: String) {
    val task = tasks.getByName(taskName)
    val outputs = task.outputs.files
    logger.log(level, "$prefix Task '$taskName' has ${outputs.files.size} outputs:")
    outputs.asFileTree.forEach { logger.log(level, "$prefix   output: '$it'") }
}

fun Project.printResolvedConfigFile(level: LogLevel = LIFECYCLE, configName: String) {
    // This only works on resolved configurations, and after the configuration phase.
    val files = DebugBuild.resolvableConfig(project, configName)
    files?.resolvedConfiguration?.resolvedArtifacts?.map { it.file }?.also { f ->
        logger.log(level, "$prefix Configuration '$configName' has ${f.size} files:")
        f.forEach {
            logger.log(level, "$prefix    Path: '${it.absolutePath}'")
        }
    }
}

fun Project.printTaskDependencies(level: LogLevel = LIFECYCLE, taskName: String) {
    // NOTE: Calling this method from a task action is not supported when configuration caching is enabled.
    val task = checkTask(taskName)
    val projectName = project.name

    logger.log(level, "$prefix $projectName.printTaskDependencies('$taskName'):")

    val parents = task.taskDependencies.getDependencies(task).toSet()
    logger.log(level, "$prefix     Task '$projectName:$taskName' depends on ${parents.size} other tasks.")
    parents.forEach {
        logger.log(level, "$prefix         Task '$projectName:$taskName' <- dependsOn: '$projectName:${it.name}'")
    }

    val children = project.tasks.filter { it.dependsOn.contains(task) }.toSet()
    logger.log(level, "$prefix     Task '$projectName:$taskName' is a dependency of ${children.size} other tasks.")
    children.forEach {
        logger.log(level, "$prefix     Task '$projectName:taskName' -> isDependencyOf: '$projectName:${it.name}'")
    }
}

fun Project.printPublications(level: LogLevel = LIFECYCLE) {
    val publicationContainer: PublishingExtension? = project.extensions.findByType<PublishingExtension>()
    if (publicationContainer == null) {
        logger.warn("$prefix Does not declare any publications. Task has no effect.")
        return
    }

    val projectName = project.name
    val publications = publicationContainer.publications
    if (publications.isEmpty()) {
        logger.log(level, "$prefix Project has no declared publications.")
    }
    val count = publications.size
    logger.log(level, "$prefix Project '$projectName' has $count publications.")
    publications.forEachIndexed { i, it ->
        logger.log(level, "$prefix     (${i + 1} / $count) Publication: '$projectName:${it.name}' (type: ${it::class}")
        if (it is MavenPublication) {
            logger.log(level, "$prefix     Publication '${projectName}.${it.name}' has ${it.artifacts.size} artifacts.")
            it.artifacts.forEachIndexed { j, artifact ->
                logger.log(level, "$prefix         (${j + 1} / ${it.artifacts.size}) Artifact: '$artifact'")
            }
        }
    }
}

/**
 * Sanity checker for jar files. Triggers a build error if specified path elements are
 * not present in the jar file, and/or if the number of entries in the jar file was
 * not equal to an optional specified size.
 */
class DebugBuild {
    companion object {
        fun resolvableConfig(project: Project, configName: String): Configuration? = project.run {
            val config = configurations.getByName(configName)
            if (!config.isCanBeResolved) {
                logger.warn("Configuration '$configName' is not resolvable.")
                return null
            }
            return config
        }

        fun verifyJarFileContents(project: Project, required: List<String>, size: Int = -1): Boolean {
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

        private fun jarContents(jarFile: File): Set<String> {
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
    }
}


