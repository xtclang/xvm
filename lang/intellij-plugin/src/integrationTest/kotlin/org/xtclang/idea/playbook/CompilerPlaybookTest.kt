package org.xtclang.idea.playbook

import com.google.gson.GsonBuilder
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
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
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/** Opt-in acceptance tests against the packaged plugin, a real IDE and the bundled compiler. */
class CompilerPlaybookTest {
    @Test
    fun compilerPlaybook() {
        require(System.getProperty("xtc.playbook.adapter") == "compiler") { "Run with -Plsp.adapter=compiler" }
        val reports = Files.createDirectories(Path.of(System.getProperty("xtc.playbook.reports")))
        val run = Files.createTempDirectory(reports, "run-")
        val workspace = Files.createDirectory(run.resolve("workspace"))
        val manual = Files.readString(Path.of(System.getProperty("xtc.playbook.manual")))
        val blocks = Regex("```xtc\\n([\\s\\S]*?)\\n```").findAll(manual).map { it.groupValues[1] }.toList()
        val scenarioPath = Path.of(System.getProperty("xtc.playbook.scenarios"))
        val shared = SharedScenarios.read(scenarioPath)
        val fixtures =
            shared.common.fixtures.associate { fixture ->
                val matches = blocks.filter { Regex(fixture.pattern, RegexOption.MULTILINE).containsMatchIn(it) }
                require(matches.size == 1) { "Expected one ${fixture.file} fixture in manual-test-plan.md" }
                val text = "${matches.single()}\n"
                val file = workspace.resolve(fixture.file)
                Files.createDirectories(file.parent)
                Files.writeString(file, text)
                fixture.file to text
            }
        val manualIds = Regex("^\\| (X\\d+) \\|", RegexOption.MULTILINE).findAll(manual).map { it.groupValues[1] }.toList()
        require(shared.ids.filter { it.startsWith("X") } == manualIds) { "Shared catalog and manual playbook rows differ" }
        shared.validate(fixtures)
        val ideVersion = System.getProperty("xtc.playbook.ideVersion")
        val lsp4ijVersion = System.getProperty("xtc.playbook.lsp4ijVersion")
        val ideFailures = CopyOnWriteArrayList<String>()
        val cases = CompilerPlaybook(fixtures, shared, lsp4ijVersion)
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
            val context =
                Starter.newContext(
                    "XtcCompilerPlaybook-${run.fileName}",
                    TestCase(IdeInfo.IdeaUltimate, LocalProjectInfo(workspace)).withVersion(ideVersion),
                )
            Files.writeString(run.resolve("ide-paths.txt"), context.paths.toString())
            PluginConfigurator(context).apply {
                installPluginFromPluginManager("com.redhat.devtools.lsp4ij", lsp4ijVersion)
                installPluginFromPath(Path.of(System.getProperty("path.to.build.plugin")))
                disablePlugins("com.intellij.kubernetes", "com.intellij.clouds.kubernetes")
            }
            // Keep even a sole candidate visible until the driver inspects and accepts it.
            // This affects only the disposable test IDE, not the packaged plugin's defaults.
            Files.writeString(
                Files.createDirectories(context.paths.configDir.resolve("options")).resolve("editor.xml"),
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
                context.paths.configDir.resolve("options/ide.general.xml"),
                """
                <application>
                  <component name="GeneralSettings">
                    <option name="autoSaveFiles" value="false" />
                    <option name="autoSaveIfInactive" value="false" />
                  </component>
                </application>
                """.trimIndent(),
            )
            context
                .disableUltimateModule()
                .applyVMOptionsPatch {
                    // Prevent trial/license startup from dynamically re-enabling the paid module.
                    addSystemProperty("request.trial", false)
                    addSystemProperty("idea.suppressed.plugins.id", "com.intellij.modules.ultimate")
                    addSystemProperty("xtc.lsp.semanticTokens", true)
                    addSystemProperty("idea.auto.reload.plugins", false)
                }.runIdeWithDriver()
                .useDriverAndCloseIde {
                    cases.run(this)
                }
            check(ideFailures.isEmpty()) { ideFailures.joinToString("\n\n") }
        } finally {
            // Restore global Starter state even if serializing or writing the report fails.
            di = previousDi
            val report =
                mapOf(
                    "ideVersion" to ideVersion,
                    "lsp4ijVersion" to lsp4ijVersion,
                    "adapter" to "compiler",
                    "sharedScenarios" to mapOf("file" to scenarioPath.toString(), "sha256" to shared.sourceHash, "ids" to shared.ids),
                    "cases" to cases.results,
                    "counts" to cases.results.groupingBy { it.status }.eachCount(),
                    "scope" to
                        "Native IntelliJ actions with Ultimate disabled. Partial and unimplemented cases are explicit; manual visual checks remain separate.",
                    "ideFailures" to ideFailures.toList(),
                )
            Files.writeString(run.resolve("results.json"), GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n")
            println("IntelliJ compiler playbook report: ${run.resolve("results.json")}")
        }
    }
}
