package org.xtclang.idea.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.hours

/**
 * Run All Ecstasy Tests: the test modules' runs, one after another, as one process, which Stop ends without starting
 * another; and the Project window's folders it is offered on.
 */
class AllTestModulesTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    private val started = CopyOnWriteArrayList<String>()

    @Test
    fun `the runs go one after another, each one's output in turn, and the process ends with the last`() {
        val all = SequentialProcessHandler(CompletableFuture.completedFuture(listOf(run("one\n", 0), run("two\n", 0))))
        val output = listen(all)
        all.startNotify()

        assertThat(all.waitFor(BOUND)).isTrue()
        assertThat(started).containsExactly("one\n", "two\n")
        assertThat(output.joinToString("")).isEqualTo("one\ntwo\n")
        assertThat(all.exitCode).isEqualTo(0)
    }

    @Test
    fun `a run that fails fails the process, and the runs after it still run`() {
        val all = SequentialProcessHandler(CompletableFuture.completedFuture(listOf(run("one\n", 3), run("two\n", 0))))
        all.startNotify()

        assertThat(all.waitFor(BOUND)).isTrue()
        assertThat(started).containsExactly("one\n", "two\n")
        assertThat(all.exitCode).isEqualTo(1)
    }

    @Test
    fun `Stop stops the run that runs and starts no other`() {
        // the first run ends only when it is stopped
        val all = SequentialProcessHandler(CompletableFuture.completedFuture(listOf(run("one\n", null), run("two\n", 0))))
        all.startNotify()
        assertThat(started).containsExactly("one\n")

        all.destroyProcess()
        assertThat(all.waitFor(BOUND)).isTrue()
        assertThat(started).containsExactly("one\n")
        assertThat(all.isProcessTerminated).isTrue()
    }

    @Test
    fun `it is offered on the project's folder and on the folders down to its test sources, and not beside them`() {
        runInEdtAndWait {
            val dirs = editor.fixture.tempDirFixture
            val tests = dirs.findOrCreateDir("project/src/test/x")
            val main = dirs.findOrCreateDir("project/src/main/x")
            val root = checkNotNull(tests.parent.parent.parent)
            val holds = { dir: VirtualFile -> XtcAllTestsConfigurationProducer.holdsTests(dir, root) }

            assertThat(listOf(root, tests.parent.parent, tests.parent, tests).map(holds)).containsOnly(true)
            assertThat(holds(main)).isFalse()
            assertThat(holds(main.parent)).isFalse()
            // a project without test sources has no Run All
            assertThat(XtcAllTestsConfigurationProducer.holdsTests(main, main.parent.parent)).isFalse()
        }
    }

    @Test
    fun `in a Gradle project it ranks before Gradle's Tests in on the test sources that hold no JVM tests`() {
        RunConfigurationProducer.EP_NAME.point.registerExtension(GradleTestsIn(), editor.fixture.testRootDisposable)
        editor.fixture.addFileToProject("src/test/x/DeckTest.x", "module DeckTest {}")

        // without the test sources' own element as Run All's source, Gradle's replaces it, and the menu has only Gradle's
        assertThat(offered("src/test/x")).containsExactly(XtcAllTestsConfigurationProducer.NAME)
        assertThat(offered("src/test")).containsExactly(XtcAllTestsConfigurationProducer.NAME)
    }

    @Test
    fun `where the tests are JVM ones too, Gradle's Tests in stays offered`() {
        RunConfigurationProducer.EP_NAME.point.registerExtension(GradleTestsIn(), editor.fixture.testRootDisposable)
        editor.fixture.addFileToProject("src/test/x/DeckTest.x", "module DeckTest {}")
        editor.fixture.addFileToProject("src/test/java/DeckJavaTest.java", "class DeckJavaTest {}")

        assertThat(offered("src/test")).contains("Tests in 'test'")
        // the Ecstasy test sources themselves still hold no JVM test
        assertThat(offered("src/test/x")).containsExactly(XtcAllTestsConfigurationProducer.NAME)
    }

    @Test
    fun `run again from the test sources, it is the configuration of the first run, not a copy of it`() {
        editor.fixture.addFileToProject("src/test/x/DeckTest.x", "module DeckTest {}")
        val runs = RunManager.getInstance(editor.fixture.project)
        try {
            val first = runFrom("src/test/x")
            val second = runFrom("src/test/x")

            assertThat(second).isSameAs(first)
            assertThat(runs.allSettings.map { it.name }).containsExactly(XtcAllTestsConfigurationProducer.NAME)
        } finally {
            // the next test's light project is this one
            runInEdtAndWait { runs.allSettings.forEach(runs::removeConfiguration) }
        }
    }

    @Test
    fun `Run All Ecstasy Tests is its own choice on the folders whose tests are JVM ones too, and only there`() {
        RunConfigurationProducer.EP_NAME.point.registerExtension(GradleTestsIn(), editor.fixture.testRootDisposable)
        editor.fixture.addFileToProject("src/test/x/DeckTest.x", "module DeckTest {}")
        editor.fixture.addFileToProject("src/test/java/DeckJavaTest.java", "class DeckJavaTest {}")
        editor.fixture.addFileToProject("src/main/x/Deck.x", "module Deck {}")

        // where Gradle's Tests in replaces the producer's Run All, the only choice that the context's Run offers
        assertThat(offered("src/test")).containsExactly("Tests in 'test'")
        assertThat(actionShown("src/test")).isTrue()
        // where the context's Run offers Run All already, the menu does not list it twice
        assertThat(actionShown("src/test/x")).isFalse()
        // nor where no test module is
        assertThat(actionShown("src/main/x")).isFalse()
    }

    @Test
    fun `Run All Ecstasy Tests runs the configuration that the context's Run makes, or makes it, not a copy of it`() {
        editor.fixture.addFileToProject("src/test/x/DeckTest.x", "module DeckTest {}")
        val project = editor.fixture.project
        val runs = RunManager.getInstance(project)
        try {
            // the context's Run made it first
            val first = runFrom("src/test/x")
            assertThat(runInEdtAndGet { XtcRunAllTestsAction.configuration(project) }).isSameAs(first)
            runInEdtAndWait { runs.allSettings.forEach(runs::removeConfiguration) }

            // the action made it first
            val made = runInEdtAndGet { XtcRunAllTestsAction.configuration(project) }
            assertThat((made.configuration as XtcRunConfiguration).allTestModules).isTrue()
            assertThat(runInEdtAndGet { XtcRunAllTestsAction.configuration(project) }).isSameAs(made)
            assertThat(runFrom("src/test/x")).isSameAs(made)
            assertThat(runs.allSettings.map { it.name }).containsExactly(XtcAllTestsConfigurationProducer.NAME)
        } finally {
            // the next test's light project is this one
            runInEdtAndWait { runs.allSettings.forEach(runs::removeConfiguration) }
        }
    }

    /** Whether the Project window's context menu shows the Run All Ecstasy Tests action on the folder [path]. */
    private fun actionShown(path: String): Boolean =
        runInEdtAndGet {
            val action = XtcRunAllTestsAction()
            val event = TestActionEvent.createTestEvent(action, data(path))
            action.update(event)
            event.presentation.isEnabledAndVisible
        }

    /**
     * The configuration that Run in the Project window's context menu on the folder [path] runs: the context's existing
     * one, or else the context's own, which becomes the temporary configuration.
     */
    private fun runFrom(path: String): RunnerAndConfigurationSettings =
        runInEdtAndGet {
            val context = context(path)
            context.findExisting() ?: checkNotNull(context.configuration) { "Nothing to run on $path" }.also {
                RunManager.getInstance(editor.fixture.project).setTemporaryConfiguration(it)
            }
        }

    /** The configurations that the Project window's context menu offers on the light project's folder [path]. */
    private fun offered(path: String): List<String> =
        runInEdtAndGet {
            context(path)
                .configurationsFromContext
                .orEmpty()
                .map { it.configuration.name }
        }

    /** The Project window's context on the light project's folder [path]. */
    private fun context(path: String): ConfigurationContext =
        ConfigurationContext.getFromContext(data(path), ActionPlaces.PROJECT_VIEW_POPUP)

    /** The Project window's data on the light project's folder [path]. */
    private fun data(path: String): DataContext {
        val project = editor.fixture.project
        val folder = checkNotNull(editor.fixture.tempDirFixture.getFile(path)) { "No folder $path" }
        val directory = checkNotNull(PsiManager.getInstance(project).findDirectory(folder)) { "No directory $path" }
        return SimpleDataContext
            .builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, arrayOf<PsiElement>(directory))
            .add(PlatformCoreDataKeys.MODULE, editor.fixture.module)
            .build()
    }

    /**
     * Gradle's Tests in, as it ranks in a project whose tests Gradle runs, the default: preferred to, and replacing,
     * every other configuration of its folder, as GradleTestRunConfigurationProducer's answers are. Gradle's own
     * produces only for a linked Gradle module, which the light project has not.
     */
    private class GradleTestsIn : LazyRunConfigurationProducer<XtcRunConfiguration>() {
        override fun getConfigurationFactory(): ConfigurationFactory = XtcRunConfigurationType().configurationFactories.first()

        override fun setupConfigurationFromContext(
            configuration: XtcRunConfiguration,
            context: ConfigurationContext,
            sourceElement: Ref<PsiElement>,
        ): Boolean {
            val directory = context.psiLocation as? PsiDirectory ?: return false
            configuration.name = "Tests in '${directory.name}'"
            sourceElement.set(directory)
            return true
        }

        override fun isConfigurationFromContext(
            configuration: XtcRunConfiguration,
            context: ConfigurationContext,
        ): Boolean = false

        override fun isPreferredConfiguration(
            self: ConfigurationFromContext,
            other: ConfigurationFromContext,
        ): Boolean = true

        override fun shouldReplace(
            self: ConfigurationFromContext,
            other: ConfigurationFromContext,
        ): Boolean = true
    }

    /** A run that prints [text] as it starts, and ends with [exitCode] then, or, for none, only when stopped. */
    private fun run(
        text: String,
        exitCode: Int?,
    ): () -> ProcessHandler =
        {
            object : ProcessHandler() {
                init {
                    addProcessListener(
                        object : ProcessListener {
                            override fun startNotified(event: ProcessEvent) {
                                started += text
                                notifyTextAvailable(text, ProcessOutputTypes.STDOUT)
                                exitCode?.let(::notifyProcessTerminated)
                            }
                        },
                    )
                }

                override fun destroyProcessImpl() = notifyProcessTerminated(143)

                override fun detachProcessImpl() = notifyProcessDetached()

                override fun detachIsDefault() = false

                override fun getProcessInput(): OutputStream? = null
            }
        }

    private fun listen(handler: ProcessHandler): List<String> =
        CopyOnWriteArrayList<String>().also { output ->
            handler.addProcessListener(
                object : ProcessListener {
                    override fun onTextAvailable(
                        event: ProcessEvent,
                        outputType: Key<*>,
                    ) {
                        if (outputType == ProcessOutputTypes.STDOUT) output += event.text
                    }
                },
            )
        }

    private companion object {
        /** A bound against a hang, which a run that ends never reaches. */
        val BOUND = 1.hours.inWholeMilliseconds
    }
}
