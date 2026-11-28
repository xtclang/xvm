package org.xtclang.idea.run

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jdom.Element
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import org.xtclang.idea.dap.XtcDebugAdapterDescriptor

/**
 * The time limits of the code a debug session runs, Evaluate Expression's and a lazy value's, as both Ecstasy
 * configurations have them: the language server's own until set, sent with the launch or the attach only once set,
 * saved with the configuration, carried by its settings editor, and checked against their range.
 */
class EvaluationLimitsTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `the limits are the language server's until set, and only set ones reach the launch and the attach`() {
        runInEdtAndWait {
            val run = application().apply { moduleName = "App" }
            val attach = attach()
            assertThat(listOf(run.evaluationTimeLimit, run.lazyValueTimeLimit, attach.evaluationTimeLimit, attach.lazyValueTimeLimit))
                .containsExactly(2_000, 1_000, 2_000, 1_000)
            assertThat(launch(run)).doesNotContainKeys(EvaluationLimits.EVALUATION, EvaluationLimits.LAZY_VALUE)
            assertThat(attach.dapParameters()).doesNotContainKeys(EvaluationLimits.EVALUATION, EvaluationLimits.LAZY_VALUE)

            run.evaluationTimeLimit = 5_000
            run.lazyValueTimeLimit = 2_500
            assertThat(launch(run))
                .containsEntry("evaluationTimeLimit", 5_000)
                .containsEntry("lazyValueTimeLimit", 2_500)
                .containsEntry("module", "App")
            attach.lazyValueTimeLimit = 30_000
            assertThat(attach.dapParameters())
                .containsEntry("lazyValueTimeLimit", 30_000)
                .doesNotContainKey("evaluationTimeLimit")
        }
    }

    @Test
    fun `the limits are saved with the configuration, and its settings editor carries them`() {
        runInEdtAndWait {
            val run = application().apply { evaluationTimeLimit = 7_000 }
            val attach = attach().apply { lazyValueTimeLimit = 400 }

            val savedRun = application().apply { readExternal(Element("configuration").also(run::writeExternal)) }
            val savedAttach = attach().apply { readExternal(Element("configuration").also(attach::writeExternal)) }
            assertThat(listOf(savedRun.evaluationTimeLimit, savedRun.lazyValueTimeLimit)).containsExactly(7_000, 1_000)
            assertThat(listOf(savedAttach.evaluationTimeLimit, savedAttach.lazyValueTimeLimit)).containsExactly(2_000, 400)
            // a configuration saved before the limits existed has the language server's
            val older = attach().apply { readExternal(Element("configuration").setAttribute("port", "6000")) }
            assertThat(listOf(older.port, older.evaluationTimeLimit, older.lazyValueTimeLimit)).containsExactly(6_000, 2_000, 1_000)

            val editedRun = application()
            edit(XtcRunSettingsEditor(), run, editedRun)
            assertThat(listOf(editedRun.evaluationTimeLimit, editedRun.lazyValueTimeLimit)).containsExactly(7_000, 1_000)
            val editedAttach = attach()
            edit(XtcAttachSettingsEditor(), attach, editedAttach)
            assertThat(listOf(editedAttach.evaluationTimeLimit, editedAttach.lazyValueTimeLimit)).containsExactly(2_000, 400)
        }
    }

    @Test
    fun `a limit outside its range fails the configuration's check`() {
        runInEdtAndWait {
            val run = application().apply { moduleName = "App" }
            run.checkConfiguration()
            run.lazyValueTimeLimit = 30_001
            assertThatThrownBy { run.checkConfiguration() }
                .isInstanceOf(RuntimeConfigurationError::class.java)
                .hasMessage("The lazy value time limit must be from 100 to 30000 ms")

            val attach = attach()
            attach.checkConfiguration()
            attach.evaluationTimeLimit = 99
            assertThatThrownBy { attach.checkConfiguration() }
                .isInstanceOf(RuntimeConfigurationError::class.java)
                .hasMessage("The evaluation time limit must be from 100 to 30000 ms")
        }
    }

    private fun application() = configuration(XtcRunConfigurationType()) as XtcRunConfiguration

    private fun attach() = configuration(XtcAttachConfigurationType()) as XtcAttachConfiguration

    private fun configuration(type: ConfigurationType) =
        type.configurationFactories.first().createTemplateConfiguration(editor.fixture.project)

    /** The arguments of the configuration's `launch` under Debug, as its debug adapter sends them. */
    private fun launch(configuration: XtcRunConfiguration): Map<String, Any> {
        val debug = DefaultDebugExecutor.getDebugExecutorInstance()
        val environment = ExecutionEnvironmentBuilder.create(editor.fixture.project, debug, configuration).build()
        return XtcDebugAdapterDescriptor(RunConfigurationOptions(), environment, null).dapParameters
    }

    /** Shows [from] in [settings], as the Run/Debug Configurations dialog does, and applies what it shows to [to]. */
    private fun <T : Any> edit(
        settings: SettingsEditor<T>,
        from: T,
        to: T,
    ) {
        try {
            settings.resetFrom(from)
            settings.applyTo(to)
        } finally {
            Disposer.dispose(settings)
        }
    }
}
