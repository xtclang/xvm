package org.xtclang.idea.playbook

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE

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
            "X295",
            "X296",
            "X297",
            "X298",
            "X299",
            "X332",
            "X333",
            "X334",
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
        ((109..121).map { "X$it" } + listOf("X155", "X159", "X160", "X164", "X165", "X166", "X167", "X168", "X220", "X300")).forEach { id ->
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
        val ide = PlaybookIde("XtcCompilerPlaybook-${run.fileName}", workspace, run, reports)
        val ideVersion = ide.ideVersion
        val localLsp4ij = ide.localLsp4ij
        val lsp4ijChannel = ide.lsp4ijChannel
        val replacementGates = System.getProperty("xtc.playbook.replacementGates", "").split(',').filter(String::isNotEmpty)
        require(replacementGates.all { it in GATE_CASES }) { "Unknown replacement gate: $replacementGates" }
        require(replacementGates.isEmpty() || localLsp4ij != null || lsp4ijChannel.isNotEmpty()) {
            "Replacement gates require an LSP4IJ that isn't the release: a local build or a channel's"
        }
        val lsp4ijVersion = ide.lsp4ijVersion
        val ideFailures = ide.ideFailures
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
        try {
            ide.run(if (mode == PlaybookMode.SETTINGS_PERSISTENCE) 2 else 1) { phase -> cases.run(this, phase) }
            Files.writeString(run.resolve(".completed"), "IDE closed\n")
            check(ideFailures.isEmpty()) { ideFailures.joinToString("\n\n") }
        } finally {
            val report =
                mapOf(
                    "ideVersion" to ideVersion,
                    "lsp4ijVersion" to lsp4ijVersion,
                    "lsp4ijChannel" to lsp4ijChannel,
                    "localLsp4ij" to localLsp4ij,
                    "replacementGates" to replacementGates,
                    // The shared catalog retains shipping-version limitations. Report the
                    // additional opt-in assertions separately from those default statuses.
                    "replacementGateResults" to
                        replacementGates.associateWith { gate ->
                            when (val status = cases.results.single { it.id == GATE_CASES.getValue(gate) }.status) {
                                "passed", "partial" -> if (ideFailures.isEmpty()) "passed" else "ide-failure"
                                else -> status
                            }
                        },
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

/** The case that runs each replacement gate's opt-in assertions in place of its shipping ones. */
private val GATE_CASES = mapOf("UP25" to "X254", "UP26" to "X257", "UP52" to "X131")
