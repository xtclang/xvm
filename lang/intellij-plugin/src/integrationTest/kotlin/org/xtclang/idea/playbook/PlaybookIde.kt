package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.monitoringDumpsIntervalSeconds
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.Starter
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes

/**
 * The isolated IDE that the playbook and the tour drive: IntelliJ IDEA with Ultimate disabled, the LSP4IJ that the
 * build chose, the packaged plugin and the probe plugin, the editor settings that driven editing needs, and the XDK
 * built from this tree on the PATH. The IDE opens [workspace]; [run] receives the server's traces and logs and the
 * probe plugin, and Starter's paths are under [reports]. [ideFailures] are the IDE errors that Starter reported.
 */
internal class PlaybookIde(
    private val name: String,
    private val workspace: Path,
    private val run: Path,
    private val reports: Path,
) {
    val ideVersion: String = System.getProperty("xtc.playbook.ideVersion")
    val localLsp4ij: LocalLsp4ijPlugin? = System.getProperty("xtc.playbook.lsp4ijPlugin")?.let { LocalLsp4ijPlugin.read(Path.of(it)) }

    /** The Marketplace channel of [lsp4ijVersion], such as nightly; empty for the default channel. */
    val lsp4ijChannel: String = System.getProperty("xtc.playbook.lsp4ijChannel", "")
    val lsp4ijVersion: String = localLsp4ij?.version ?: System.getProperty("xtc.playbook.lsp4ijVersion")
    val ideFailures = CopyOnWriteArrayList<String>()

    /**
     * Starts the IDE [phases] times, each time running [body] with its driver and then closing it. [options] are
     * further files of the IDE's `options` directory, by name, such as a tour's editor settings.
     */
    fun run(
        phases: Int = 1,
        options: Map<String, String> = emptyMap(),
        body: Driver.(phase: Int) -> Unit,
    ) {
        // macOS 27 kills a JVM whose thread dump reads the mach port of a thread that has just exited (EXC_GUARD,
        // GUARD_TYPE_MACH_PORT, in VM_PrintThreads): the test IDE crashed in Starter's thread dump of every minute, so
        // it takes none there
        if (System.getProperty("os.name").startsWith("Mac")) ConfigurationStorage.monitoringDumpsIntervalSeconds(Int.MAX_VALUE)
        val previousDi = di
        di =
            DI {
                extend(previousDi)
                bindSingleton<GlobalPaths>(overrides = true) { object : GlobalPaths(reports) {} }
                bindSingleton<CIServer>(overrides = true) {
                    object : CIServer by NoCIServer {
                        override fun reportTestFailure(
                            testName: String,
                            message: String,
                            details: String,
                            linkToLogs: String?,
                            kind: SyntheticTestKind,
                            generifyTestName: Boolean,
                        ) {
                            ideFailures += "$testName: $message\n$details\n$linkToLogs"
                        }
                    }
                }
            }
        try {
            val testCase = TestCase(IdeInfo.IdeaUltimate, LocalProjectInfo(workspace)).withVersion(ideVersion)
            val context =
                Starter.newContext(
                    name,
                    testCase.copy(
                        ideInfo =
                            testCase.ideInfo.copy(
                                getInstaller = { CachedIdeInstaller(testCase.ideInfo.getInstaller(it)) },
                            ),
                    ),
                )
            Files.writeString(run.resolve("ide-paths.txt"), context.paths.toString())
            PluginConfigurator(context).apply {
                if (localLsp4ij == null) {
                    installPluginFromPluginManager("com.redhat.devtools.lsp4ij", lsp4ijVersion, lsp4ijChannel.ifEmpty { null })
                } else {
                    installPluginFromDir(Path.of(localLsp4ij.path))
                }
                installPluginFromPath(Path.of(System.getProperty("path.to.build.plugin")))
                installPluginFromDir(DiagnosticProbePlugin.create(run))
                disablePlugins("com.intellij.kubernetes", "com.intellij.clouds.kubernetes")
            }
            val optionsDirectory = Files.createDirectories(context.paths.configDir.resolve("options"))
            // Keep even a sole candidate visible until the driver inspects and accepts it.
            // This affects only the disposable test IDE, not the packaged plugin's defaults.
            Files.writeString(
                optionsDirectory.resolve("editor.xml"),
                """
                <application>
                  <component name="CodeInsightSettings">
                    <option name="AUTOCOMPLETE_ON_CODE_COMPLETION" value="false" />
                    <option name="AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION" value="false" />
                    <option name="AUTO_POPUP_COMPLETION_LOOKUP" value="false" />
                  </component>
                </application>
                """.trimIndent(),
            )
            // Unsaved-overlay scenarios must not be silently saved by an application focus change.
            Files.writeString(
                optionsDirectory.resolve("ide.general.xml"),
                """
                <application>
                  <component name="GeneralSettings">
                    <option name="autoSaveFiles" value="false" />
                    <option name="autoSaveIfInactive" value="false" />
                  </component>
                </application>
                """.trimIndent(),
            )
            options.forEach { (file, text) -> Files.writeString(optionsDirectory.resolve(file), text) }
            // the bundled plugins that neither the playbook nor the tour uses, which the IDE then does not load
            Files.writeString(context.paths.configDir.resolve("disabled_plugins.txt"), disabledPlugins().joinToString("\n", postfix = "\n"))
            // The full playbook exceeds Starter's ten-minute default; individual waits
            // remain bounded so an unresponsive editor still fails promptly.
            val configured =
                context
                    .disableUltimateModule()
                    .applyVMOptionsPatch {
                        // Prevent trial/license startup from dynamically re-enabling the paid module.
                        addSystemProperty("request.trial", false)
                        addSystemProperty("idea.suppressed.plugins.id", "com.intellij.modules.ultimate")
                        addSystemProperty("xtc.lsp.semanticTokens", true)
                        addSystemProperty("xtc.trace.directory", run.resolve("server-trace").toString())
                        addSystemProperty("xtc.logs.directory", run.resolve("server-logs").toString())
                        addSystemProperty("idea.auto.reload.plugins", false)
                        // where the IDE's memory goes, which MemoryProbe records; glibc's malloc keeps an arena per
                        // core for a process of many threads, which the container's memory pays for
                        addLine("-XX:NativeMemoryTracking=summary")
                        withEnv("MALLOC_ARENA_MAX", "2")
                        // command-line runs, X338's, run xtc from the PATH: the XDK built from this tree, whose xec
                        // X336 runs
                        withEnv("PATH", listOfNotNull(XDK_BIN.toString(), System.getenv("PATH")).joinToString(File.pathSeparator))
                    }
            repeat(phases) { phase ->
                configured.runIdeWithDriver(runTimeout = 30.minutes).useDriverAndCloseIde {
                    try {
                        body(phase)
                    } finally {
                        runCatching { Files.writeString(run.resolve("memory-$phase.txt"), utility(MemoryProbe::class).report()) }
                    }
                }
            }
        } finally {
            // Restore global Starter state even if the run fails.
            di = previousDi
        }
    }

    /** The ids of the bundled plugins the driven IDE leaves out, from playbook/disabled-plugins.txt. */
    private fun disabledPlugins(): List<String> =
        checkNotNull(javaClass.getResource("/playbook/disabled-plugins.txt")) { "No playbook/disabled-plugins.txt" }
            .readText()
            .lines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    companion object {
        /**
         * The root of this tree, whose lang/test-fixtures/compiler-playbook has the scenarios: the playbook's
         * scenario file is in it.
         */
        val TREE: Path =
            Path
                .of(System.getProperty("xtc.playbook.scenarios"))
                .toAbsolutePath()
                .parent.parent.parent.parent

        /** The bin directory of the XDK built from this tree, with xtc and xec. */
        val XDK_BIN: Path = TREE.resolve("xdk/build/install/xdk/bin")
    }
}

/** The IDE-side MemoryUi, which reports where the IDE's and its language server's memory goes. */
@Remote("org.xtclang.idea.playbook.probe.MemoryUi", plugin = "org.xtclang.playbook.probe")
internal interface MemoryProbe {
    fun report(): String
}
