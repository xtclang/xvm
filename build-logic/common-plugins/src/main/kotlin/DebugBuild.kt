import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.findByType
import java.io.File
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Simple helper functions, mostly printing out dependencies, inputs and outputs and other
 * useful information for an XDK build, that is a bit too brief to deserve tasks of its own.
 *
 * They are wrapped in helper tasks.
 */

fun Project.printAllTaskInputs() {
    tasks.forEach { printTaskInputs(it.name) }
}

fun Project.printAllTaskOutputs() {
    tasks.forEach { printTaskInputs(it.name) }
}

fun Project.printAllTaskDependencies() {
    tasks.forEach { printTaskDependencies(it.name) }
}

fun Task.printTaskInputs() {
    return project.printTaskInputs(name)
}

fun Task.printTaskOutputs() {
    return project.printTaskOutputs(name)
}

fun Task.printTaskDependencies() {
    return project.printTaskDependencies(name)
}

fun Project.printRepos() {
    repositories.forEach {
        logger.lifecycle("$prefix Repository: '$it'")
    }
}

fun Project.printResolvedConfigFiles(configNames: Collection<String>) {
    configNames.forEach { printResolvedConfigFile(it) }
}

fun Project.printAllResolvedConfigFiles() {
    return printResolvedConfigFiles(project.configurations.map { it.name })
}

fun Project.checkTask(taskName: String): Task {
    return project.tasks.findByName(taskName) ?: throw buildException("No task named '$taskName' found.")
}

fun Project.printResolvedConfigFiles(configName: String) {
    // This only works on resolved configurations, and after the configuration phase.
    val config = configurations.getByName(configName)
    if (!config.isCanBeResolved) {
        logger.warn("$prefix Configuration '$configName' is not resolvable.")
        return
    }
    val files = config.resolvedConfiguration.resolvedArtifacts.map { it.file }
    logger.lifecycle("$prefix Configuration '$configName' has ${files.size} files:")
    files.forEach {
        logger.lifecycle("$prefix    file: '$it'")
    }
}

fun Project.printTaskInputs(taskName: String) {
    val task = tasks.getByName(taskName)
    val outputs = task.inputs.files
    logger.lifecycle("$prefix Task '$taskName' has ${outputs.files.size} inputs:")
    outputs.asFileTree.forEach { println("$prefix   input : '$it'") }
}

fun Project.printTaskOutputs(taskName: String) {
    val task = tasks.getByName(taskName)
    val outputs = task.outputs.files
    logger.lifecycle("$prefix Task '$taskName' has ${outputs.files.size} outputs:")
    outputs.asFileTree.forEach { println("$prefix   output: '$it'") }
}

fun Project.printResolvedConfigFile(configName: String) {
    // This only works on resolved configurations, and after the configuration phase.
    val config = configurations.getByName(configName)
    if (!config.isCanBeResolved) {
        logger.warn("$prefix Configuration '$configName' is not resolvable.")
        return
    }
    val files = config.resolvedConfiguration.resolvedArtifacts.map { it.file }
    logger.lifecycle("$prefix Configuration '$configName' has ${files.size} files:")
    files.forEach {
        logger.lifecycle("$prefix    Path: '${it.absolutePath}'")
    }
}

fun Project.printTaskDependencies(taskName: String) {
    // NOTE: Calling this method from a task action is not supported when configuration caching is enabled.
    val task = checkTask(taskName)
    val projectName = project.name

    logger.lifecycle("$prefix $projectName.printTaskDependencies('$taskName'):")

    val parents = task.taskDependencies.getDependencies(task).toSet()
    logger.lifecycle("$prefix     Task '$projectName:$taskName' depends on ${parents.size} other tasks.")
    parents.forEach {
        logger.lifecycle("$prefix         Task '$projectName:$taskName' <- dependsOn: '$projectName:${it.name}'")
    }

    val children = project.tasks.filter { it.dependsOn.contains(task) }.toSet()
    logger.lifecycle("$prefix     Task '$projectName:$taskName' is a dependency of ${children.size} other tasks.")
    children.forEach {
        logger.lifecycle("$prefix     Task '$projectName:taskName' -> isDependencyOf: '$projectName:${it.name}'")
    }
}

fun Project.printPublications() {
    val publicationContainer : PublishingExtension? = project.extensions.findByType<PublishingExtension>()
    if (publicationContainer == null) {
        logger.warn("$prefix Does not declare any publications. Task has no effect.")
        return
    }

    val projectName = project.name
    val publications = publicationContainer.publications
    if (publications.isEmpty()) {
        logger.lifecycle("$prefix Project has no declared publications.")
    }
    val count = publications.size
    logger.lifecycle("$prefix Project '$projectName' has $count publications.")
    publications.forEachIndexed{ i, it ->
        logger.lifecycle("$prefix     (${i + 1} / $count) Publication: '$projectName:${it.name}' (type: ${it::class}")
        if (it is MavenPublication) {
            logger.lifecycle("$prefix     Publication '${projectName}.${it.name}' has ${it.artifacts.size} artifacts.")
            it.artifacts.forEachIndexed { j, artifact ->
                logger.lifecycle("$prefix         (${j + 1} / ${it.artifacts.size}) Artifact: '$artifact'")
            }
        }
    }
}

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
