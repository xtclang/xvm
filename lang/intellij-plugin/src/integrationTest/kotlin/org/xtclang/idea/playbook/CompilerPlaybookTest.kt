package org.xtclang.idea.playbook

import com.google.gson.Gson
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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.kodein.di.DI
import org.kodein.di.bindSingleton
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes

/** Opt-in acceptance tests against the packaged plugin, a real IDE and the bundled compiler. */
class CompilerPlaybookTest {
    @Test fun compilerPlaybook() = runPlaybook(PlaybookMode.FEATURES)

    @Test fun startupEditing() = runPlaybook(PlaybookMode.STARTUP)

    @Test fun focusRecovery() = runPlaybook(PlaybookMode.FOCUS_RECOVERY)

    @Test fun settingsPersistence() = runPlaybook(PlaybookMode.SETTINGS_PERSISTENCE)

    @Test fun projectLifecycle() = runPlaybook(PlaybookMode.PROJECT_LIFECYCLE)

    @Test
    @EnabledIfSystemProperty(named = "xtc.playbook.largeFileProbe", matches = "true")
    fun largeFileEditing() = runPlaybook(PlaybookMode.LARGE_FILE)

    private fun runPlaybook(mode: PlaybookMode) {
        val adapter = System.getProperty("xtc.playbook.adapter")
        require(adapter == "compiler" || (adapter == "treesitter" && mode == PlaybookMode.SETTINGS_PERSISTENCE)) {
            "Feature playbooks require -Plsp.adapter=compiler; settings persistence also supports treesitter"
        }
        val reports = Files.createDirectories(Path.of(System.getProperty("xtc.playbook.reports")))
        val run = Files.createTempDirectory(reports, "run-")
        val workspace = Files.createDirectory(run.resolve("workspace"))
        val manual = Files.readString(Path.of(System.getProperty("xtc.playbook.manual")))
        val blocks =
            Regex("```xtc\\n([\\s\\S]*?)\\n```").findAll(manual).map { it.groupValues[1] }.toList()
        val scenarioPath = Path.of(System.getProperty("xtc.playbook.scenarios"))
        val shared = SharedScenarios.read(scenarioPath)
        val fixtures =
            shared.common.fixtures.associate { fixture ->
                val matches =
                    blocks.filter {
                        Regex(fixture.pattern, RegexOption.MULTILINE).containsMatchIn(it)
                    }
                require(matches.size == 1) {
                    "Expected one ${fixture.file} fixture in manual-test-plan.md"
                }
                val text = "${matches.single()}\n"
                val file = workspace.resolve(fixture.file)
                Files.createDirectories(file.parent)
                Files.writeString(file, text)
                fixture.file to text
            }
        val manualIds =
            Regex("^\\| (X\\d+) \\|", RegexOption.MULTILINE)
                .findAll(manual)
                .map { it.groupValues[1] }
                .toList()

        // These cases supply complete sources in the shared catalog instead of duplicate code
        // blocks.
        fun fixture(
            file: String,
            text: String,
        ) {
            val target = workspace.resolve(file)
            Files.createDirectories(target.parent)
            Files.writeString(target, text)
        }
        listOf("X101", "X102", "X103", "X104").forEach { id ->
            val data = shared.scenarios.getValue(id)
            fixture(
                if (id == "X101") data.text("file") else "$id/${data.text("file")}",
                data.text("source"),
            )
        }
        shared.scenarios.getValue("X99").let {
            fixture("X99/${it.text("file")}", it.text("original"))
            fixture("X99/${it.text("library")}", it.text("libraryText"))
        }
        shared.scenarios.getValue("X100").let {
            fixture("X100/${it.text("file")}", it.text("source"))
            fixture("X100/${it.text("neighbor")}", it.text("neighborText"))
        }
        shared.scenarios.getValue("X103").let {
            fixture("X103/${it.text("member")}", it.text("memberSource"))
        }
        shared.scenarios.getValue("X103").let {
            fixture("X103/${it.text("companion")}", it.text("companionSource"))
        }
        shared.scenarios.getValue("X105").let {
            fixture("X105/${it.text("file")}", "module AutoImports {}")
            fixture("X105/${it.text("library")}", it.text("libraryText"))
        }
        fixture("X122/Actions.x", "module Actions {}")
        listOf(
            "X148",
            "X156",
            "X157",
            "X177",
            "X178",
            "X179",
            "X180",
            "X181",
            "X182",
            "X183",
            "X184",
            "X185",
            "X186",
            "X187",
            "X188",
            "X189",
            "X190",
            "X191",
            "X192",
            "X193",
            "X194",
            "X195",
            "X196",
            "X197",
            "X198",
            "X199",
            "X200",
            "X201",
            "X202",
            "X203",
            "X204",
            "X205",
            "X206",
            "X207",
            "X208",
            "X209",
            "X210",
            "X211",
            "X212",
            "X213",
            "X214",
            "X215",
            "X221",
            "X222",
            "X223",
            "X224",
            "X225",
            "X226",
            "X227",
            "X228",
            "X229",
            "X230",
            "X231",
            "X232",
            "X233",
            "X234",
            "X235",
            "X236",
            "X237",
            "X238",
            "X239",
            "X240",
            "X241",
            "X242",
        ).forEach { id ->
            val data = shared.scenarios.getValue(id)
            fixture("$id/Extract.x", data.text("source"))
            if (data.values.has("destinationFile")) fixture("$id/${data.text("destinationFile")}", data.text("destinationSource"))
            if (data.values.has("files")) {
                data.rows("files").forEach { file ->
                    fixture("$id/${file["file"].asString}", file["source"].asString)
                }
            }
        }
        listOf("X149", "X150", "X151", "X152").forEach { id ->
            val data = shared.scenarios.getValue(id)
            fixture("$id/${data.text("file")}", data.rows("variants").first()["source"].asString)
        }
        ((109..121).map { "X$it" } + listOf("X155", "X159", "X160", "X164", "X165", "X166", "X167", "X168", "X220")).forEach { id ->
            shared.scenarios.getValue(id).rows("files").forEach { file ->
                fixture("$id/${file["file"].asString}", file["source"].asString)
            }
        }
        shared.scenarios.getValue("7a.9").let {
            fixture(it.text("file"), it.text("moduleStart") + it.text("moduleEnd"))
        }
        require(shared.ids.filter { it.startsWith("X") } == manualIds) {
            "Shared catalog and manual playbook rows differ"
        }
        shared.validate(fixtures)
        val ideVersion = System.getProperty("xtc.playbook.ideVersion")
        val localLsp4ij = System.getProperty("xtc.playbook.lsp4ijPlugin")?.let { LocalLsp4ijPlugin.read(Path.of(it)) }
        val lsp4ijVersion = localLsp4ij?.version ?: System.getProperty("xtc.playbook.lsp4ijVersion")
        val ideFailures = CopyOnWriteArrayList<String>()
        val selection =
            System
                .getProperty("xtc.playbook.cases", "")
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        require(selection.all { it in shared.implementedIds }) {
            "Unknown or unimplemented native case: $selection"
        }
        val cases =
            CompilerPlaybook(
                fixtures,
                shared,
                lsp4ijVersion,
                selection.ifEmpty { shared.implementedIds },
                mode,
            ) { result ->
                Files.writeString(
                    run.resolve("progress.jsonl"),
                    Gson().toJson(result) + "\n",
                    CREATE,
                    APPEND,
                )
            }
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
                    "XtcCompilerPlaybook-${run.fileName}",
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
                    installPluginFromPluginManager("com.redhat.devtools.lsp4ij", lsp4ijVersion)
                } else {
                    installPluginFromDir(Path.of(localLsp4ij.path))
                }
                installPluginFromPath(Path.of(System.getProperty("path.to.build.plugin")))
                installPluginFromDir(DiagnosticProbePlugin.create(run))
                disablePlugins("com.intellij.kubernetes", "com.intellij.clouds.kubernetes")
            }
            // Keep even a sole candidate visible until the driver inspects and accepts it.
            // This affects only the disposable test IDE, not the packaged plugin's defaults.
            Files.writeString(
                Files
                    .createDirectories(context.paths.configDir.resolve("options"))
                    .resolve("editor.xml"),
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
                        addSystemProperty(
                            "xtc.lsp.colorPrototype",
                            shared.common.colorPrototype["cases"].asJsonArray.any {
                                selection.isEmpty() || it.asString in selection
                            },
                        )
                        addSystemProperty("xtc.trace.directory", run.resolve("server-trace").toString())
                        addSystemProperty("xtc.logs.directory", run.resolve("server-logs").toString())
                        addSystemProperty("idea.auto.reload.plugins", false)
                    }
            repeat(if (mode == PlaybookMode.SETTINGS_PERSISTENCE) 2 else 1) { phase ->
                configured.runIdeWithDriver(runTimeout = 30.minutes).useDriverAndCloseIde {
                    cases.run(this, phase)
                }
            }
            Files.writeString(run.resolve(".completed"), "IDE closed\n")
            check(ideFailures.isEmpty()) { ideFailures.joinToString("\n\n") }
        } finally {
            // Restore global Starter state even if serializing or writing the report fails.
            di = previousDi
            val report =
                mapOf(
                    "ideVersion" to ideVersion,
                    "lsp4ijVersion" to lsp4ijVersion,
                    "localLsp4ij" to localLsp4ij,
                    "adapter" to adapter,
                    "startupOnly" to (mode == PlaybookMode.STARTUP),
                    "mode" to mode.name,
                    "selectedCases" to
                        when (mode) {
                            PlaybookMode.FEATURES -> selection.ifEmpty { shared.implementedIds }
                            PlaybookMode.STARTUP -> setOf("STARTUP")
                            PlaybookMode.FOCUS_RECOVERY -> setOf("START_FOCUS")
                            PlaybookMode.LARGE_FILE -> setOf("START_LARGE_FILE")
                            PlaybookMode.PROJECT_LIFECYCLE -> setOf("START_PROJECTS")
                            PlaybookMode.SETTINGS_PERSISTENCE -> setOf("START_SETTINGS_0", "START_SETTINGS_1")
                        },
                    "sharedScenarios" to
                        mapOf(
                            "file" to scenarioPath.toString(),
                            "sha256" to shared.sourceHash,
                            "ids" to shared.ids,
                        ),
                    "cases" to cases.results,
                    "counts" to cases.results.groupingBy { it.status }.eachCount(),
                    "scope" to
                        "Native IntelliJ actions with Ultimate disabled. Partial and unimplemented cases are explicit; manual visual checks remain separate.",
                    "ideFailures" to ideFailures.toList(),
                )
            Files.writeString(
                run.resolve("results.json"),
                GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n",
            )
            println("IntelliJ compiler playbook report: ${run.resolve("results.json")}")
        }
    }
}
