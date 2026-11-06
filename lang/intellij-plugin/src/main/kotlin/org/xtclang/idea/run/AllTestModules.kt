package org.xtclang.idea.run

import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.xtclang.idea.lsp.ModuleTests
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path

/**
 * Runs every test module's tests, each module in turn, into one test console, as Run All Ecstasy Tests does: embedded,
 * each module's run in the language server, or with `xtc test`, each module's command; Gradle's `testXtc` runs them all
 * itself. The modules are those the language server lists tests of (`xtc/tests`), by name.
 */
internal class AllTestModulesState(
    private val configuration: XtcRunConfiguration,
    private val environment: ExecutionEnvironment,
) : RunProfileState {
    override fun execute(
        executor: Executor,
        runner: ProgramRunner<*>,
    ): ExecutionResult {
        val project = environment.project
        val mode = configuration.effectiveRunMode()
        val runs =
            XtcTestCatalog.refresh(project).thenApply { modules ->
                modules.filter { it.tests.children.isNotEmpty() }.sortedBy { it.module }.map { module -> { run(project, mode, module) } }
            }
        val handler = SequentialProcessHandler(runs)
        ProcessTerminatedListener.attach(handler)
        return testResult(handler, XtcTestConsoleProperties(configuration, executor))
    }

    private fun run(
        project: Project,
        mode: XtcRunMode,
        module: ModuleTests,
    ): ProcessHandler =
        if (mode == XtcRunMode.COMMAND_LINE) {
            OSProcessHandler(
                XtcRunConfiguration.xtcTestCommandLine(module.module, emptyList()).apply {
                    workDirectory = project.basePath?.let { Path(it).toFile() }
                },
            )
        } else {
            EmbeddedExecution.runHandler(
                project,
                XtcRunConfiguration.embeddedRequest(module.module, module.uri, "", "", emptyList()),
                module.module,
            )
        }
}

/**
 * A process made of [runs], run one after another once it starts: each run's output is its own, xunit's events among
 * it, and it ends when the last run ends, with 1 if a run failed, else 0. Stop stops the run that runs and starts no
 * other; the runs come once they are known, as the test modules come from the language server.
 */
internal class SequentialProcessHandler(
    private val runs: CompletableFuture<List<() -> ProcessHandler>>,
) : ProcessHandler() {
    private val stopped = AtomicBoolean()
    private val failed = AtomicBoolean()
    private val ended = AtomicBoolean()
    private val current = AtomicReference<ProcessHandler?>()

    init {
        addProcessListener(
            object : ProcessListener {
                override fun startNotified(event: ProcessEvent) {
                    runs.whenComplete { list, failure ->
                        when {
                            stopped.get() -> {
                                end(1)
                            }

                            failure == null -> {
                                next(list, 0)
                            }

                            else -> {
                                notifyTextAvailable("No test modules: ${failure.message}\n", ProcessOutputTypes.STDERR)
                                end(1)
                            }
                        }
                    }
                }
            },
        )
    }

    private fun next(
        list: List<() -> ProcessHandler>,
        index: Int,
    ) {
        if (stopped.get() || index == list.size) {
            end(if (failed.get()) 1 else 0)
            return
        }
        val run = list[index]()
        run.addProcessListener(
            object : ProcessListener {
                override fun onTextAvailable(
                    event: ProcessEvent,
                    outputType: Key<*>,
                ) = notifyTextAvailable(event.text, outputType)

                override fun processTerminated(event: ProcessEvent) {
                    if (event.exitCode != 0) failed.set(true)
                    current.set(null)
                    next(list, index + 1)
                }
            },
        )
        current.set(run)
        // stopped while it was made: it never starts
        if (stopped.get()) {
            current.set(null)
            end(if (failed.get()) 1 else 0)
            return
        }
        run.startNotify()
    }

    private fun end(exitCode: Int) {
        if (ended.compareAndSet(false, true)) notifyProcessTerminated(exitCode)
    }

    override fun destroyProcessImpl() {
        stopped.set(true)
        val running = current.get()
        when {
            running != null -> running.destroyProcess()

            // between two runs, or before the modules are known: the next run never starts
            runs.isDone -> end(1)

            else -> runs.cancel(false)
        }
    }

    override fun detachProcessImpl() {
        stopped.set(true)
        current.get()?.detachProcess()
        if (ended.compareAndSet(false, true)) notifyProcessDetached()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null
}

/**
 * Produces Run All Ecstasy Tests from the Project window's context: on the project's own folder, its `src`, its
 * `src/test` or its test sources' `src/test/x`, an Ecstasy Application configuration that runs every test module's
 * tests, each in turn, with the project's build mode.
 *
 * The context menu offers only the configurations that rank first. In a Gradle project, Gradle's "Tests in" replaces
 * every other configuration of the same folder, so Run All gives the test sources as its source element where the
 * folder holds no Java or Kotlin tests: a source element inside another's ranks before it, ahead of what replaces
 * what, and Gradle's, which would run only the JVM tests, then is not offered. Where the folder holds JVM tests, Run
 * All gives the folder, and Gradle's "Tests in" stays.
 */
class XtcAllTestsConfigurationProducer : LazyRunConfigurationProducer<XtcRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory = XtcRunConfigurationType.instance.configurationFactories.first()

    override fun setupConfigurationFromContext(
        configuration: XtcRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val directory = context.psiLocation as? PsiDirectory ?: return false
        if (!holdsTests(directory.virtualFile, directory.project.guessProjectDir())) return false
        configuration.allTestModules = true
        configuration.moduleName = ""
        configuration.moduleUri = ""
        configuration.tests = emptyList()
        configuration.runWith = XtcRunMode.PROJECT
        configuration.name = NAME
        sourceElement.set(rankingElement(directory) ?: directory)
        return true
    }

    /**
     * The element inside [directory] that Run All gives as its source, so that it ranks before Gradle's configuration
     * of [directory], or null where [directory] holds Java or Kotlin tests, which Gradle's runs: the test sources'
     * folder below [directory], or in the test sources themselves, their first module by name.
     */
    private fun rankingElement(directory: PsiDirectory): PsiElement? {
        val root = directory.project.guessProjectDir() ?: return null
        if (holdsJvmTests(directory.virtualFile, root)) return null
        val tests = root.findFileByRelativePath(TESTS) ?: return null
        val manager = directory.manager
        return if (tests == directory.virtualFile) {
            directory.files.filter { it.virtualFile.extension == "x" }.minByOrNull { it.name }
                ?: directory.subdirectories.minByOrNull { it.name }
        } else {
            manager.findDirectory(tests)
        }
    }

    override fun isConfigurationFromContext(
        configuration: XtcRunConfiguration,
        context: ConfigurationContext,
    ): Boolean =
        configuration.allTestModules &&
            (context.psiLocation as? PsiDirectory)?.let { holdsTests(it.virtualFile, it.project.guessProjectDir()) } == true

    companion object {
        /** The configuration's name, as the context menu shows it: Run 'All Ecstasy Tests'. */
        const val NAME = "All Ecstasy Tests"

        /** Where `xtc init` puts a project's test modules, under its folder. */
        private const val TESTS = "src/test/x"

        /** Where a Gradle project's tests are, the Ecstasy ones in its `x`, and a JVM language's beside them. */
        private const val TEST_SOURCES = "src/test"

        /** The extensions of the Java and Kotlin sources whose tests Gradle's configurations run. */
        private val JVM_SOURCES = setOf("java", "kt")

        /** Whether [directory] holds the project's test sources: [root]'s `src/test/x`, or a folder of [root]'s on its way. */
        fun holdsTests(
            directory: VirtualFile,
            root: VirtualFile?,
        ): Boolean {
            val tests = root?.findFileByRelativePath(TESTS)?.takeIf { it.isDirectory } ?: return false
            return VfsUtilCore.isAncestor(root, directory, false) && VfsUtilCore.isAncestor(directory, tests, false)
        }

        /**
         * Whether the project's tests that [directory] holds, those of [root]'s `src/test` below it, include Java or
         * Kotlin sources beside the Ecstasy ones.
         */
        fun holdsJvmTests(
            directory: VirtualFile,
            root: VirtualFile,
        ): Boolean {
            val tests = root.findFileByRelativePath(TEST_SOURCES) ?: return false
            val scope =
                when {
                    VfsUtilCore.isAncestor(directory, tests, false) -> tests
                    VfsUtilCore.isAncestor(tests, directory, false) -> directory
                    else -> return false
                }
            var found = false
            VfsUtilCore.iterateChildrenRecursively(scope, null) { file ->
                found = !file.isDirectory && file.extension in JVM_SOURCES
                !found
            }
            return found
        }
    }
}
