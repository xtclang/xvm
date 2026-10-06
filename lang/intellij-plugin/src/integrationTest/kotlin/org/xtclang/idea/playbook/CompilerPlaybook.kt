package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.impl.DriverCallException
import com.intellij.driver.client.service
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.HIGHLIGHTING_PANEL_ID
import com.intellij.driver.sdk.ProblemsViewToolWindowUtils
import com.intellij.driver.sdk.findFile
import com.intellij.driver.sdk.getPlugin
import com.intellij.driver.sdk.getProblemsViewProblems
import com.intellij.driver.sdk.getToolWindow
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.isPluginLoaded
import com.intellij.driver.sdk.selectProblemsViewTab
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.driver.sdk.waitForProblemsViewFile
import com.intellij.openapi.progress.ProcessCanceledException
import org.xtclang.idea.playbook.SharedScenarios.Companion.offset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

enum class PlaybookMode {
    FEATURES,
    STARTUP,
    FOCUS_RECOVERY,
    LARGE_FILE,
    PROJECT_LIFECYCLE,
    SETTINGS_PERSISTENCE,
}

/** Drive editor actions and inspect the diagnostics/lookup actually delivered to IntelliJ. */
class CompilerPlaybook(
    private val fixtures: Map<String, String>,
    private val shared: SharedScenarios,
    private val lsp4ijVersion: String,
    private val selectedIds: Set<String> = shared.implementedIds,
    private val mode: PlaybookMode = PlaybookMode.FEATURES,
    private val onResult: (Result) -> Unit = {},
) {
    data class Result(
        val id: String,
        val description: String,
        val status: String,
        val milliseconds: Long,
        val error: String? = null,
        val limitations: List<String> = emptyList(),
        val manual: List<String> = emptyList(),
    )

    private val completed = mutableListOf<Result>()
    val results: List<Result>
        get() =
            completed.filter { it.id.startsWith("START") } +
                shared.scenarios.map { (id, scenario) ->
                    val executed = completed.singleOrNull { it.id == id }
                    val status =
                        when {
                            executed?.status == "failed" -> "failed"
                            executed != null && scenario.intellij.coverage == "partial" -> "partial"
                            executed != null -> "passed"
                            scenario.intellij.coverage == "not-implemented" -> "not-implemented"
                            else -> "not-run"
                        }
                    Result(
                        id,
                        scenario.title,
                        status,
                        executed?.milliseconds ?: 0,
                        executed?.error,
                        scenario.intellij.limitations,
                        scenario.manual,
                    )
                }

    private fun Driver.assertTokenRanges(editor: JEditorUiComponent) {
        val uri = Path.of(editor.editor.getVirtualFile().getPath()).toUri().toString()
        val response =
            ClientProtocol(this)
                .query(
                    "textDocument/semanticTokens/full",
                    mapOf("textDocument" to mapOf("uri" to uri)),
                )
        if (response.isJsonNull) return // A failed parse may produce no semantic report.
        val data = response.asJsonObject["data"].asJsonArray.map { it.asInt }
        data.chunked(5).runningFold(0 to 0) { (column, previousEnd), token ->
            val next = if (token[0] == 0) column + token[1] else token[1]
            check(token[0] > 0 || next >= previousEnd) {
                "Overlapping semantic tokens at column $next"
            }
            check(token[2] > 0)
            next to next + token[2]
        }
    }

    fun run(
        driver: Driver,
        persistencePhase: Int = 0,
    ) {
        with(driver) {
            case(if (persistencePhase == 0) "START" else "START_REOPEN", "Packaged XTC and pinned LSP4IJ load") {
                waitForIndicators(2.minutes)
                withContext(OnDispatcher.EDT) {
                    utility(PlaybookProgress::class).install(singleProject())
                }
                check(getPlugin("org.xtclang.idea")?.isEnabled() == true)
                check(getPlugin("com.intellij.modules.ultimate")?.isEnabled() != true) {
                    "The playbook must run with Ultimate features disabled"
                }
                val lsp = requireNotNull(getPlugin("com.redhat.devtools.lsp4ij"))
                check(lsp.isEnabled() && lsp.getVersion() == lsp4ijVersion)
            }
            if (mode == PlaybookMode.STARTUP) {
                case(
                    "STARTUP",
                    "Edits, bulk replacement and close/reopen before server initialization",
                ) {
                    startupEditing(fixtures, shared)
                }
                return@with
            }
            if (mode == PlaybookMode.FOCUS_RECOVERY) {
                case(
                    "START_FOCUS",
                    "Restore interrupted popups without replaying completed edits",
                ) {
                    focusRecovery(fixtures, shared)
                }
                return@with
            }
            if (mode == PlaybookMode.LARGE_FILE) {
                case("START_LARGE_FILE", "Measure large-document range-marker updates") {
                    largeFileEditing(fixtures, shared)
                }
                return@with
            }
            if (mode == PlaybookMode.PROJECT_LIFECYCLE) {
                case("START_PROJECTS", "Two project windows, pending close and reopen") {
                    projectLifecycle(shared)
                }
                return@with
            }
            if (mode == PlaybookMode.SETTINGS_PERSISTENCE) {
                case("START_SETTINGS_$persistencePhase", "Settings survive a complete IDE exit and reopen") {
                    settingsPersistence(shared, persistencePhase)
                }
                return@with
            }
            configure(shared.graph)
            withContext(OnDispatcher.EDT) {
                getToolWindow("Language Servers").show()
                getToolWindow("Language Servers").hide()
            }
            case(
                shared.dependencyNavigation.id,
                "Initial compiler configuration and cross-module definition",
            ) {
                val scenario = shared.dependencyNavigation
                configure(shared.graph)
                val consumer = open(scenario.file)
                check(
                    service<FileEditorManager>(singleProject()).getAllEditors().none {
                        it.getFile().getPath().endsWith("/${scenario.location.targetFile}")
                    },
                ) {
                    "The dependency must resolve before opening its source file"
                }
                navigate(consumer, scenario.location)
                open(scenario.file).awaitDiagnostics(emptyList())
            }
            case(shared.dependencyEdit.id, "Unsaved dependency edit recompiles its consumer") {
                val scenario = shared.dependencyEdit
                val consumer = open(scenario.consumer)
                val version = cast(consumer.document, DocumentVersion::class).getModificationStamp()
                val library = open(scenario.file)
                val libraryPath = Path.of(library.editor.getVirtualFile().getPath())
                library.text = scenario.edit.apply(fixtures.getValue(scenario.file))
                val changed = open(scenario.consumer)
                changed.awaitError()
                awaitUi("consumer owns its type mismatch", 45.seconds) {
                    receivedDiagnostics(changed).let { items ->
                        items.isNotEmpty() &&
                            items.none {
                                it.code ==
                                    shared.scenarios.getValue(scenario.id).text("diagnosticCode")
                            }
                    }
                }
                check(
                    cast(changed.document, DocumentVersion::class).getModificationStamp() == version,
                ) {
                    "Dependency recompilation changed the consumer document"
                }
                check(Files.readString(libraryPath) == fixtures.getValue(scenario.file)) {
                    "The unsaved dependency edit was saved to disk"
                }
                open(scenario.file).text = fixtures.getValue(scenario.file)
                open(scenario.consumer).awaitDiagnostics(emptyList())
            }
            case(
                shared.configuration.id,
                "Clear and restore source graph through configuration notifications",
            ) {
                val consumer = open(shared.configuration.consumer)
                configure("""{"xtc":{"compiler":{"sourceModules":[]}}}""")
                consumer.awaitError()
                configure(shared.graph)
                consumer.awaitDiagnostics(emptyList())
                navigate(consumer, shared.dependencyNavigation.location)
            }
            case(
                shared.diagnostics.id,
                "Compiler error severity, source span and clearing without save",
            ) {
                val scenario = shared.diagnostics
                val editor = open(scenario.file)
                scenario.errors.forEach { expected ->
                    val text = expected.edit.apply(fixtures.getValue(scenario.file))
                    editor.text = text
                    val range = offset(text, expected.rangeStart)..offset(text, expected.rangeEnd)
                    awaitUi("compiler diagnostic at shared source span", 45.seconds) {
                        editor.diagnostics().any {
                            it.severity == expected.severity &&
                                it.start in range &&
                                it.description.contains(expected.messageContains, ignoreCase = true)
                        }
                    }
                    awaitUi("compiler diagnostic codes, source, and nonempty ranges", 45.seconds) {
                        receivedDiagnostics(editor).let { items ->
                            items.isNotEmpty() &&
                                items.all {
                                    it.code?.startsWith(expected.codePrefix) == true &&
                                        it.source == "xtc" &&
                                        it.start.first > 0 &&
                                        it.start != it.end
                                }
                        }
                    }
                    problems(editor)
                    nextProblem(editor)
                    editor.text = fixtures.getValue(scenario.file)
                    editor.awaitDiagnostics(emptyList())
                    problems(editor)
                }
                editor.text = fixtures.getValue(scenario.file) + scenario.cleanAppend
                editor.awaitDiagnostics(emptyList())
            }
            case(
                shared.definitions.id,
                "Shadowed local and property navigate to separate declarations",
            ) {
                val editor = open(shared.definitions.file)
                shared.definitions.locations.forEach { navigate(editor, it) }
                val uses = shared.definitions.locations.map { offset(editor.text, it.cursor) }
                uses.forEach { at ->
                    val client = ClientProtocol(this)
                    val params =
                        mapOf(
                            "textDocument" to
                                mapOf(
                                    "uri" to
                                        Path
                                            .of(editor.editor.getVirtualFile().getPath())
                                            .toUri()
                                            .toString(),
                                ),
                            "position" to
                                mapOf(
                                    "line" to editor.text.take(at).count { it == '\n' },
                                    "character" to (at - editor.text.lastIndexOf('\n', at - 1) - 1),
                                ),
                        )
                    check(
                        client.query("textDocument/declaration", params) ==
                            client.query("textDocument/definition", params),
                    ) {
                        "Local/property declarations must match their resolved definition"
                    }
                }
                uses.forEach { at -> referencesAndHighlights(editor, at, uses.single { it != at }) }
            }
            case(
                shared.warning.id,
                "Exactly one compiler warning for the duplicate inherited annotation",
            ) {
                val scenario = shared.warning
                val editor = open(scenario.file)
                awaitUi("one duplicate-annotation warning", 45.seconds) {
                    editor.diagnostics().let { values ->
                        values.size == scenario.count &&
                            values.all {
                                it.severity == scenario.severity &&
                                    it.start == offset(editor.text, scenario.declaration) &&
                                    it.description.contains(
                                        scenario.messageContains,
                                        ignoreCase = true,
                                    )
                            }
                    }
                }
                problems(editor)
                val data = shared.scenarios.getValue(scenario.id)
                awaitUi("compiler warning metadata", 45.seconds) {
                    receivedDiagnostics(editor).let { items ->
                        items.size == scenario.count &&
                            items.all {
                                it.code == scenario.code &&
                                    it.source == "xtc" &&
                                    it.severity == "Warning" &&
                                    it.start.first == it.end.first &&
                                    it.end.second - it.start.second ==
                                    data.values["warningCount"].asInt
                            }
                    }
                }
                nextProblem(editor)
                val error = open(data.text("errorFile"))
                error.text =
                    fixtures
                        .getValue(data.text("errorFile"))
                        .replace(data.text("replaceFrom"), data.text("replaceWith"))
                error.awaitError()
                val warning = open(scenario.file)
                check(warning.diagnostics().size == scenario.count)
                warning.text = scenario.edit.apply(warning.text)
                warning.awaitDiagnostics(emptyList())
                problems(warning)
                open(data.text("errorFile")).awaitError()
                restore(data.text("errorFile"))
                val restored = open(scenario.file)
                restored.text = fixtures.getValue(scenario.file)
                awaitUi("restored compiler warning", 45.seconds) {
                    receivedDiagnostics(restored).let { items ->
                        items.size == scenario.count && items.single().code == scenario.code
                    }
                }
            }
            recoveryStructure()
            case(
                shared.completion.id,
                "Compiler member completion replaces the typed prefix exactly",
            ) {
                val scenario = shared.completion
                val editor = open(scenario.file)
                val incomplete = scenario.edit.apply(fixtures.getValue(scenario.file))
                editor.text = incomplete
                editor.awaitError()
                accept(editor, offset(incomplete, scenario.cursor), scenario.label)
                check(editor.text == scenario.accepted.apply(incomplete)) {
                    "Shared accepted completion text differs"
                }
                editor.awaitDiagnostics(emptyList())
            }
            scenario("X6") { data ->
                data.strings("bodies").forEach { body ->
                    val (editor, at) = editing(body)
                    lookup(editor, at) { items ->
                        hasType(
                            items,
                            data.text("label"),
                            data.values["type"].asJsonObject["source"].asString,
                        ) && items.none { it.getLookupString() == data.text("excludedLabel") }
                    }
                    invokeAction("EditorEscape", component = editor.component)
                    accept(editor, at, data.text("label"))
                    check(editor.text.contains(data.text("acceptedExpression")))
                    editor.text =
                        editor.text.replace(
                            data.text("acceptedExpression"),
                            data.text("validStatement"),
                        )
                    editor.awaitDiagnostics(emptyList())
                }
                restore(shared.common.editing.file)
            }
            scenario("X8") { data ->
                val (editor, at) = editing(data.text("body"))
                accept(editor, at, data.text("label"))
                check(editor.text.contains(data.text("acceptedSuffix")))
                editor.awaitDiagnostics(emptyList())
                restore(shared.common.editing.file)
            }
            for (id in listOf("X9", "X12")) {
                scenario(id) { data ->
                    val (editor, at) = editing(data.text("body"))
                    lookup(editor, at) { items ->
                        val names = items.map { it.getLookupString() }
                        names.containsAll(data.strings("include")) &&
                            data.strings("exclude").none { it in names }
                    }
                    invokeAction("EditorEscape", component = editor.component)
                    restore(shared.common.editing.file)
                }
            }
            scenario("X10") { data ->
                val (editor, at) = editing(data.text("body"))
                accept(editor, at, data.text("label"))
                restore(shared.common.editing.file)
            }
            scenario("X11") { data ->
                val (first, firstAt) = editing(data.text("body"), inspect = true)
                lookup(first, firstAt) { items ->
                    val names = items.map { it.getLookupString() }
                    names.containsAll(data.strings("include")) &&
                        hasType(
                            items,
                            data.text("label"),
                            data.values["genericType"].asJsonObject["source"].asString,
                        )
                }
                invokeAction("EditorEscape", component = first.component)
                val (second, secondAt) = editing(data.text("shadowedBody"), inspect = true)
                lookup(second, secondAt) { items ->
                    items.none { it.getLookupString() == data.text("excludedLabel") } &&
                        hasType(
                            items,
                            data.text("label"),
                            data.values["shadowedType"].asJsonObject["source"].asString,
                        )
                }
                invokeAction("EditorEscape", component = second.component)
                restore(shared.common.editing.file)
            }
            scenario("X13") { data ->
                data.rows("variants").forEach { variant ->
                    val body = SharedScenarios.text(data.text("body"), variant["prefix"].asString)
                    val (editor, at) =
                        editing(body) { text ->
                            text.replace(
                                data.text("moduleStart"),
                                SharedScenarios.text(
                                    data.text("moduleWithImport"),
                                    variant["addition"].asString,
                                ),
                            )
                        }
                    accept(editor, at, variant["name"].asString)
                }
                restore(shared.common.editing.file)
            }
            signatureScenarios()
            additionalScenarios()
            workspaceScenarios()
            for (id in listOf("X71", "X75")) {
                scenario(id) { data ->
                    val editor = open(data.text("file"))
                    data.strings("variants").forEach { expression ->
                        editor.text =
                            fixtures
                                .getValue(data.text("file"))
                                .replace(
                                    data.text("replaceFrom"),
                                    SharedScenarios.text(data.text("replaceWith"), expression),
                                )
                        if (id == "X75") editor.awaitError()
                        val at =
                            editor.text.indexOf(data.text("anchor")) + data.values["offset"].asInt
                        accept(editor, at, data.text("label"))
                        if (id == "X75") editor.awaitError()
                    }
                    restore(data.text("file"))
                }
            }
            scenario("X86") { data ->
                val editor = open(data.text("file"))
                data.strings("variants").forEach { expression ->
                    editor.text =
                        fixtures
                            .getValue(data.text("file"))
                            .replace(data.text("replaceFrom"), expression)
                    editor.awaitError()
                    accept(
                        editor,
                        editor.text.indexOf(expression) + expression.length,
                        data.text("label"),
                    )
                    editor.awaitError()
                    restore(data.text("file"))
                }
            }
            scenario("X87") { data ->
                val editor = open(data.text("file"))
                data.rows("variants").forEach { variant ->
                    val prefix = variant["prefix"].asString
                    val suffix = variant["suffix"].asString
                    val selected = variant["selected"].asString
                    val pattern = Regex(data.values["replaceFrom"].asJsonObject["source"].asString)
                    val text =
                        pattern.replace(fixtures.getValue(data.text("file"))) {
                            SharedScenarios.text(data.text("replaceWith"), prefix, suffix)
                        }
                    editor.text = text
                    editor.awaitError()
                    accept(editor, text.indexOf(prefix) + prefix.length, selected)
                    editor.text =
                        text.replace(
                            prefix,
                            prefix.dropLast(data.values["prefixLength"].asInt) +
                                selected +
                                variant["terminator"].asString,
                        )
                    editor.awaitDiagnostics(emptyList())
                }
                restore(data.text("file"))
            }
            ParityScenarios(driver, fixtures, shared) { id, action ->
                case(
                    id,
                    shared.scenarios.getValue(id).title,
                    continueAfterFailure = true,
                    action = action,
                )
            }.run()
            check(completed.filter { it.id != "START" }.map { it.id }.toSet() == selectedIds) {
                "Every selected implemented case must execute; omitted cases remain not-run"
            }
            check(completed.none { it.status == "failed" }) {
                "Failed IntelliJ cases: ${completed.filter { it.status == "failed" }.map { it.id }}; see results.json"
            }
        }
    }

    private fun Driver.signatureScenarios() {
        scenario("X15") { data ->
            data.rows("variants").forEach { variant ->
                val (editor, at) =
                    editing(SharedScenarios.text(data.text("body"), variant["argument"].asString))
                signature(editor, at) { items ->
                    items.size == data.values["expected"].asInt &&
                        Regex(variant["type"].asString).containsMatchIn(items.single().label) &&
                        items.single().activeParameter == data.values["expected"].asInt
                }
            }
            restore(shared.common.editing.file)
        }
        scenario("X16") { data ->
            val (editor, at) = editing(data.text("body"))
            signature(editor, at) { items ->
                items.size == data.values["expected"].asInt &&
                    Regex(data.values["type"].asJsonObject["source"].asString)
                        .containsMatchIn(items.single().label) &&
                    items.single().activeParameter == data.values["activeParameter"].asInt
            }
            editor.text = editor.text.replace(data.text("replaceFrom"), data.text("replaceWith"))
            editor.awaitDiagnostics(emptyList())
            signature(
                editor,
                editor.text.indexOf(data.text("completedArgument")) +
                    data.values["argumentOffset"].asInt,
            ) {
                it.isNotEmpty()
            }
            definition(
                editor,
                editor.text.indexOf(data.text("call")) + data.values["callOffset"].asInt,
            )
            restore(shared.common.editing.file)
        }
        scenario("X17") { data ->
            data.rows("variants").forEach { variant ->
                val (editor, at) = editing(variant["body"].asString)
                val type =
                    Regex(SharedScenarios.text(data.text("typePattern"), variant["type"].asString))
                signature(editor, at) { it.isNotEmpty() && type.containsMatchIn(it.first().label) }
            }
            restore(shared.common.editing.file)
        }
        scenario("X18") { data ->
            data.strings("bodies").forEach { body ->
                val valid = shared.scenarios.getValue("X15")
                val argument = valid.rows("variants").first()["argument"].asString
                val (previous, previousAt) =
                    editing(SharedScenarios.text(valid.text("body"), argument))
                signature(previous, previousAt, keepOpen = true) { it.isNotEmpty() }
                val (editor, at) = editing(body)
                signature(editor, at) { it.size == data.values["signatureCount"].asInt }
            }
            restore(shared.common.editing.file)
        }
        scenario("X19") { data ->
            data.values["receivers"].asJsonArray.forEach { receiver ->
                val instance = receiver.asBoolean
                val (editor, at) =
                    editing(
                        data.text(if (instance) "instanceBody" else "staticBody"),
                        inspect = instance,
                    ) {
                        it.replace(data.text("replaceFrom"), data.text("replaceWith"))
                    }
                signature(editor, at) {
                    it.isNotEmpty() &&
                        it.first().activeParameter == data.values["activeParameter"].asInt
                }
                editor.text =
                    editor.text.replaceRange(
                        at,
                        at,
                        data.text(if (instance) "instanceArgument" else "staticArgument"),
                    )
                editor.awaitDiagnostics(emptyList())
            }
            restore(shared.common.editing.file)
        }
        scenario("X20") { data ->
            val (editor, at) = editing(data.text("body"))
            signature(editor, at) { it.isNotEmpty() && it.first().parameters.isEmpty() }
            val targets =
                data.strings("bodies").map { body ->
                    val (selected, _) = editing(body)
                    selected.awaitDiagnostics(emptyList())
                    definition(
                        selected,
                        selected.text.indexOf(data.text("anchor")) + data.values["offset"].asInt,
                    )
                }
            check(targets.distinct().size == targets.size) {
                "Written argument types must select different overload declarations"
            }
            restore(shared.common.editing.file)
        }
        scenario("X74") { data ->
            val editor = open(data.text("file"))
            data.rows("variants").forEach { variant ->
                val call = variant["call"].asString
                editor.text =
                    fixtures
                        .getValue(data.text("file"))
                        .replace(
                            data.text("replaceFrom"),
                            SharedScenarios.text(data.text("replaceWith"), call),
                        )
                signature(editor, editor.text.indexOf(call) + call.length) {
                    it.isNotEmpty() &&
                        it.first().label == data.text("signature") &&
                        it.first().activeParameter == variant["active"].asInt
                }
            }
            restore(data.text("file"))
        }
        for (id in listOf("X83", "X84")) {
            scenario(id) { data ->
                val editor = open(data.text("file"))
                data.strings(if (id == "X84") "constructors" else "variants").forEach { original ->
                    val prefix = original.replace(data.text("replaceFrom"), data.text("prefix"))
                    editor.text =
                        fixtures
                            .getValue(data.text("file"))
                            .replace(
                                original,
                                SharedScenarios.text(data.text("replaceWith"), prefix),
                            )
                    editor.awaitError()
                    val at = editor.text.indexOf(prefix) + prefix.length
                    signature(editor, at) {
                        it.isNotEmpty() &&
                            Regex(data.values["signature"].asJsonObject["source"].asString)
                                .containsMatchIn(it.first().label) &&
                            (
                                id != "X83" ||
                                    it.first().activeParameter == data.values["activeParameter"].asInt
                            )
                    }
                    val labels =
                        data.strings(
                            if (id == "X83") {
                                "labels"
                            } else if (original.contains(data.text("requiredContext"))) {
                                "requiredLabels"
                            } else {
                                "provisionalLabels"
                            },
                        )
                    acceptCandidates(
                        editor,
                        at,
                        labels,
                        if (id == "X83") labels.single() else data.text("label"),
                    )
                    check(editor.text == fixtures.getValue(data.text("file")))
                    editor.awaitDiagnostics(emptyList())
                }
                restore(data.text("file"))
            }
        }
        scenario("X85") { data ->
            val editor = open(data.text("file"))
            data.strings("variants").forEach { prefix ->
                editor.text =
                    fixtures
                        .getValue(data.text("file"))
                        .replace(
                            data.text("replaceFrom"),
                            SharedScenarios.text(data.text("replaceWith"), prefix),
                        )
                editor.awaitError()
                val at = editor.text.indexOf(prefix) + prefix.length
                signature(editor, at) {
                    it.isNotEmpty() &&
                        it.first().activeParameter == data.values["activeParameter"].asInt &&
                        Regex(data.values["parameter"].asJsonObject["source"].asString)
                            .containsMatchIn(it.first().label)
                }
                acceptCandidates(
                    editor,
                    at,
                    data.strings("labels"),
                    data.strings("labels").single(),
                )
                editor.awaitDiagnostics(emptyList())
            }
            restore(data.text("file"))
        }
        for (id in listOf("X88", "X89")) {
            scenario(id) { data ->
                val editor = open(data.text("file"))
                val prefix = data.text("prefix")
                val replacements =
                    if (id == "X89") {
                        data.strings("closers").map { prefix + it }
                    } else {
                        listOf(SharedScenarios.text(data.text("replaceWith"), prefix))
                    }
                replacements.forEach { replacement ->
                    editor.text =
                        fixtures
                            .getValue(data.text("file"))
                            .replace(data.text("original"), replacement)
                    editor.awaitError()
                    val at = editor.text.indexOf(prefix) + prefix.length
                    signature(editor, at) {
                        it.isNotEmpty() &&
                            it.first().label == data.text("signature") &&
                            it.first().activeParameter == data.values["activeParameter"].asInt
                    }
                    acceptCandidates(
                        editor,
                        at,
                        data.strings("labels"),
                        data.strings("labels").single(),
                    )
                    if (id == "X89" && replacement == prefix) {
                        editor.awaitError()
                    } else {
                        check(editor.text == fixtures.getValue(data.text("file")))
                        editor.awaitDiagnostics(emptyList())
                    }
                }
                restore(data.text("file"))
            }
        }
        scenario("X90") { data ->
            val editor = open(data.text("file"))
            val prefix = data.text("prefix")
            val selected = data.strings("labels").single()
            data.rows("variants").forEach { variant ->
                editor.text =
                    fixtures
                        .getValue(data.text("file"))
                        .replace(data.text("original"), prefix + variant["suffix"].asString)
                val at = editor.text.indexOf(prefix) + prefix.length
                signature(editor, at) {
                    it.size == 1 &&
                        it.single().label == data.text("signature") &&
                        it.single().activeParameter == data.values["activeParameter"].asInt
                }
                acceptCandidates(editor, at, data.strings("labels"), selected)
                if (variant["validAfterAcceptance"].asBoolean) {
                    editor.awaitDiagnostics(emptyList())
                } else {
                    awaitUi("missing array bracket remains a diagnostic", 30.seconds) {
                        editor.diagnostics().isNotEmpty()
                    }
                }
                val accepted =
                    prefix.dropLast(-data.values["replacementStartDelta"].asInt) + selected
                editor.text =
                    fixtures
                        .getValue(data.text("file"))
                        .replace(
                            data.text("original"),
                            accepted + variant["repairedSuffix"].asString,
                        )
                editor.awaitDiagnostics(emptyList())
                restore(data.text("file"))
            }
        }
        scenario("X91") { data ->
            val editor = open(data.text("file"))
            data.strings("variants").forEach { variant ->
                val marked = SharedScenarios.text(data.text("source"), variant)
                val at = marked.indexOf('|')
                editor.text = marked.replace("|", "")
                editor.awaitError()
                signature(editor, at) { it.isEmpty() }
                lookup(editor, at) { items ->
                    val names = items.map { it.getLookupString() }
                    names.containsAll(data.strings("include")) &&
                        data.strings("exclude").none { it in names }
                }
                invokeAction("EditorEscape", component = editor.component)
                accept(editor, at, data.text("selected"))
                check(
                    editor.text ==
                        marked.take(at - data.values["prefixLength"].asInt) +
                        data.text("selected") +
                        marked.substring(at + 1),
                )
                editor.awaitDiagnostics(emptyList())
            }
            restore(data.text("file"))
        }
        scenario("X93") { data ->
            val editor = open(data.text("file"))
            data.rows("variants").forEach { variant ->
                val marked =
                    SharedScenarios.text(data.text("source"), variant["declaration"].asString)
                val at = marked.indexOf('|')
                editor.text = marked.replace("|", "")
                editor.awaitError()
                signature(editor, at) { it.isEmpty() }
                lookup(editor, at) { items ->
                    val names = items.map { it.getLookupString() }
                    names.containsAll(variant["include"].asJsonArray.map { it.asString }) &&
                        data.strings("exclude").none { it in names }
                }
                invokeAction("EditorEscape", component = editor.component)
                accept(editor, at, variant["selected"].asString)
                check(
                    editor.text ==
                        marked.take(at - data.values["prefixLength"].asInt) +
                        variant["selected"].asString +
                        marked.substring(at + 1),
                )
                editor.awaitDiagnostics(emptyList())
            }
            restore(data.text("file"))
        }
        listOf("X94", "X95", "X96", "X97", "X98", "X106", "X107", "X108").forEach { id ->
            scenario(id) { data ->
                val editor = open(data.text("file"))
                data.rows("variants").forEach { variant ->
                    val marked =
                        SharedScenarios.text(data.text("source"), variant["declaration"].asString)
                    val at = marked.indexOf(data.text("marker"))
                    val before = variant["prefixLength"]?.asInt ?: data.values["prefixLength"].asInt
                    val after = variant["suffixLength"]?.asInt ?: 0
                    editor.text = marked.replace(data.text("marker"), "")
                    if (variant["initiallyValid"]?.asBoolean == true) {
                        editor.awaitDiagnostics(emptyList())
                    } else {
                        editor.awaitError()
                    }
                    signature(
                        editor,
                        at,
                        inspectDocumentation = variant["signatureDocumentationContains"] != null,
                    ) { signatures ->
                        (
                            if (data.values["callContext"]?.asBoolean == true) {
                                signatures.isNotEmpty()
                            } else {
                                signatures.isEmpty()
                            }
                        ) &&
                            (
                                variant["activeParameter"] == null ||
                                    signatures.all {
                                        it.activeParameter == variant["activeParameter"].asInt
                                    }
                            ) &&
                            (
                                variant["signatureDocumentationContains"] == null ||
                                    signatures.any {
                                        it.documentation?.contains(
                                            variant["signatureDocumentationContains"].asString,
                                        ) == true
                                    }
                            )
                    }
                    lookup(editor, at) { items ->
                        val names = items.map { it.getLookupString() }
                        names.containsAll(variant["include"].asJsonArray.map { it.asString }) &&
                            (
                                variant["exclude"]?.asJsonArray?.map { it.asString }
                                    ?: data.strings("exclude")
                            ).none { it in names } &&
                            (
                                variant["metadata"] == null ||
                                    hasCompletionMetadata(
                                        items,
                                        variant["selected"].asString,
                                        variant["metadata"].asJsonObject,
                                    )
                            )
                    }
                    dismissPopups()
                    accept(
                        editor,
                        at,
                        variant["selected"].asString,
                        marked.take(at - before) +
                            variant["selected"].asString +
                            marked.substring(at + data.text("marker").length + after),
                    )
                    if (variant["validAfterAcceptance"].asBoolean) {
                        editor.awaitDiagnostics(emptyList())
                    } else {
                        editor.awaitError()
                    }
                    editor.text =
                        SharedScenarios.text(
                            data.text("source"),
                            variant["repairedDeclaration"].asString,
                        )
                    editor.awaitDiagnostics(emptyList())
                }
                restore(data.text("file"))
            }
        }
    }

    private fun Driver.recoveryStructure() {
        scenario("X92") { data ->
            val editor = open(data.text("file"))
            data.strings("parameters").forEach { parameters ->
                editor.text = SharedScenarios.text(data.text("source"), parameters)
                editor.awaitError()
                problems(editor)
                structure(editor, data.strings("symbols"), listOf(data.text("excludedSymbol")))
                val expected = data.values["foldStart"].asInt..data.values["foldEnd"].asInt
                awaitUi(
                    message = "unfinished declaration body fold $expected",
                    errorMessage = { "Expected native fold $expected; actual: ${folds(editor)}" },
                    timeout = 45.seconds,
                ) {
                    expected in folds(editor)
                }
                editor.text =
                    SharedScenarios.text(data.text("source"), data.text("repairedParameters"))
                editor.awaitDiagnostics(emptyList())
                problems(editor)
            }
            restore(data.text("file"))
        }
    }

    private fun Driver.additionalScenarios() {
        scenario("X34") { data ->
            val editor = open(data.text("file"))
            editor.awaitDiagnostics(emptyList())
            declaration(
                definition(
                    editor,
                    editor.text.lastIndexOf(data.text("narrowedUse")) +
                        data.values["narrowedOffset"].asInt,
                    "LSP.GotoTypeDefinition",
                ),
                data.strings("narrowedTargets").single(),
            )
            val reopened = open(data.text("file"))
            focusEditor(reopened)
            caret(
                reopened,
                reopened.text.indexOf(data.text("unionUse")) + data.values["unionOffset"].asInt,
            )
            invokeAction("LSP.GotoTypeDefinition", component = reopened.component)
            val expected = data.strings("unionTargets")
            chooseTargets(reopened, expected, expected.last()) {
                invokeAction("LSP.GotoTypeDefinition", component = reopened.component)
            }
            awaitUi("selected union declaration opens", 30.seconds) {
                withContext(OnDispatcher.EDT) {
                    service<FileEditorManager>(singleProject()).getSelectedTextEditor()?.let { selected ->
                        reopened.text
                            .substring(selected.getCaretModel().getOffset())
                            .startsWith(expected.last())
                    } == true
                }
            }
        }
        scenario("X33") { data ->
            data.rows("variants").forEach { variant ->
                val editor = open(data.text("file"))
                editor.awaitDiagnostics(emptyList())
                val target =
                    definition(
                        editor,
                        editor.text.indexOf(variant["anchor"].asString),
                        "LSP.GotoTypeDefinition",
                    )
                check(target.first.endsWith("/${data.text("file")}"))
                declaration(target, variant["expected"].asString)
            }
        }
        scenario("X35") { data ->
            val editor = open(data.text("file"))
            editor.awaitDiagnostics(emptyList())
            val formal =
                definition(
                    editor,
                    editor.text.indexOf(data.text("formalUse")) + data.values["offset"].asInt,
                    "LSP.GotoTypeDefinition",
                )
            check(formal.first.endsWith("/${data.text("file")}"))
            declaration(formal, data.strings("formalTargets").single())
            val reopened = open(data.text("file"))
            val library =
                definition(
                    reopened,
                    reopened.text.indexOf(data.text("binaryUse")),
                    "LSP.GotoTypeDefinition",
                )
            libraryDeclaration(library, data.strings("binaryTargets").single())
        }
        scenario("X101") { data ->
            data.strings("types").forEach { name ->
                val editor = open(data.text("file"))
                editor.awaitDiagnostics(emptyList())
                val target = definition(editor, editor.text.indexOf(name))
                libraryDeclaration(target, name)
                val library =
                    ideFrame().codeEditorForFile(Path.of(target.first).fileName.toString())
                val original = library.text
                rejectFormatting(library)
                check(
                    library.text == original && Files.readString(Path.of(target.first)) == original,
                )
                check(!Files.isWritable(Path.of(target.first)))
                val reopened = open(data.text("file"))
                rejectRename(reopened, reopened.text.indexOf(name), data.text("renameRejection"))
            }
            val editor = open(data.text("file"))
            libraryDeclaration(
                definition(
                    editor,
                    editor.text.indexOf(data.text("variable")),
                    "LSP.GotoTypeDefinition",
                ),
                data.text("variableType"),
            )
        }
        scenario("X1") { data ->
            val editor = open(data.text("file"))
            editor.awaitDiagnostics(emptyList())
            structure(editor, data.strings("symbolNames"))
            awaitUi("native fold regions", 45.seconds) {
                folds(editor).size >= data.values["minimumFolds"].asInt
            }
            selectionParents(
                editor,
                editor.text.indexOf(data.text("anchor")) + data.values["offset"].asInt,
            )
        }
        scenario("X70") { data ->
            val editor = open(data.text("file"))
            editor.awaitDiagnostics(emptyList())
            val at = editor.text.indexOf(data.text("anchor"))
            signature(editor, at + data.values["offset"].asInt) {
                it.firstOrNull()?.label == data.text("signature")
            }
            val (_, target) = definition(editor, at)
            check(editor.text.substring(target).startsWith(data.text("targetName")))
        }
        scenario("X73") { data ->
            val editor = open(data.text("file"))
            data.rows("variants").forEach { variant ->
                val call = variant["call"].asString
                editor.text =
                    fixtures
                        .getValue(data.text("file"))
                        .replace(
                            data.text("replaceFrom"),
                            SharedScenarios.text(data.text("incompleteExpression"), call),
                        )
                val anchor = SharedScenarios.text(data.text("callAnchor"), call)
                signature(editor, editor.text.indexOf(anchor) + anchor.length) {
                    it.firstOrNull()?.let { item ->
                        item.label == data.text("signature") &&
                            item.activeParameter == variant["active"].asInt
                    } == true
                }
            }
            editor.text =
                fixtures
                    .getValue(data.text("file"))
                    .replace(data.text("replaceFrom"), data.text("invalidExpression"))
            signature(
                editor,
                editor.text.indexOf(data.text("invalidCall")) + data.values["offset"].asInt,
            ) {
                it.size == data.values["rejectedSignatureCount"].asInt
            }
            restore(data.text("file"))
        }
        scenario("X76") { data ->
            val editor = open(data.text("file"))
            editor.awaitDiagnostics(emptyList())
            if (data.values["tokensNonOverlapping"].asBoolean) assertTokenRanges(editor)
            val expression = data.text("expression")
            editor.text =
                fixtures
                    .getValue(data.text("file"))
                    .replace(
                        data.text("replaceFrom"),
                        SharedScenarios.text(data.text("replaceWith"), expression),
                    )
            editor.awaitError()
            if (data.values["tokensNonOverlapping"].asBoolean) assertTokenRanges(editor)
            signature(editor, editor.text.indexOf(expression) + expression.length) {
                it.firstOrNull()?.let { item ->
                    item.label == data.text("signature") &&
                        item.activeParameter == data.values["activeParameter"].asInt
                } == true
            }
            restore(data.text("file"))
        }
        for (id in listOf("X77", "X79")) {
            scenario(id) { data ->
                val editor = open(data.text("file"))
                data.rows("variants").forEach { variant ->
                    val prefix = variant["prefix"].asString
                    editor.text =
                        fixtures
                            .getValue(data.text("file"))
                            .replace(
                                variant["original"].asString,
                                SharedScenarios.text(
                                    data.text(if (id == "X77") "replaceWith" else "replaceFrom"),
                                    prefix,
                                ),
                            )
                    editor.awaitError()
                    val at = editor.text.lastIndexOf(prefix) + prefix.length
                    acceptCandidates(
                        editor,
                        at,
                        data.strings("labels"),
                        if (id == "X77") {
                            data.text("label")
                        } else {
                            data.strings("labels").single()
                        },
                    )
                    editor.awaitDiagnostics(emptyList())
                }
                restore(data.text("file"))
            }
        }
        for (id in listOf("X78", "X80")) {
            scenario(id) { data ->
                val editor = open(data.text("file"))
                data.strings(if (id == "X78") "selections" else "labels").forEach { selected ->
                    editor.text =
                        fixtures
                            .getValue(data.text("file"))
                            .replace(data.text("replaceFrom"), data.text("anchor"))
                    editor.awaitError()
                    val at = editor.text.indexOf(data.text("anchor")) + data.values["offset"].asInt
                    signature(editor, at) { it.size == data.values["signatureCount"].asInt }
                    acceptCandidates(editor, at, data.strings("labels"), selected)
                    editor.awaitDiagnostics(emptyList())
                    val acceptedAt =
                        at +
                            selected.length +
                            if (id == "X80") data.values["replacementStartDelta"].asInt else 0
                    signature(editor, acceptedAt) { it.size == 1 }
                }
                restore(data.text("file"))
            }
        }
        scenario("X81") { data ->
            val editor = open(data.text("file"))
            data.strings("variants").forEach { prefix ->
                editor.text =
                    fixtures
                        .getValue(data.text("file"))
                        .replace(
                            data.text("replaceFrom"),
                            SharedScenarios.text(data.text("replaceWith"), prefix),
                        )
                editor.awaitError()
                val at = editor.text.indexOf(prefix) + prefix.length
                lookup(editor, at) { items ->
                    items.map { it.getLookupString() }.sorted() ==
                        data.strings("labels").sorted() &&
                        hasType(
                            items,
                            data.text("label"),
                            data.values["type"].asJsonObject["source"].asString,
                        ) &&
                        hasCompletionKinds(items, data.strings("labels"), 10)
                }
                invokeAction("EditorEscape", component = editor.component)
                accept(editor, at, data.text("label"))
                editor.awaitDiagnostics(emptyList())
            }
            restore(data.text("file"))
        }
        scenario("X82") { data ->
            val editor = open(data.text("file"))
            editor.text =
                fixtures
                    .getValue(data.text("file"))
                    .replace(data.text("replaceFrom"), data.text("anchor"))
            editor.awaitError()
            val at = editor.text.indexOf(data.text("anchor")) + data.values["offset"].asInt
            acceptCandidates(
                editor,
                at,
                data.strings("labels"),
                data.strings("labels").single(),
                kind = 10,
            )
            check(editor.text == fixtures.getValue(data.text("file")))
            editor.awaitDiagnostics(emptyList())
        }
        scenario("7a.9") { data ->
            val editor = open(data.text("file"))
            editor.text =
                data.text("moduleStart") +
                (0 until data.values["declarationCount"].asInt).joinToString("\n") {
                    SharedScenarios.text(data.text("declaration"), it.toString(), it.toString())
                } +
                data.text("moduleEnd")
            editor.awaitError()
            awaitUi("bounded source diagnostics without an internal compiler failure", 45.seconds) {
                receivedDiagnostics(editor).let { items ->
                    items.isNotEmpty() &&
                        items.count { it.severity == "Error" } <=
                        data.values["maximumErrors"].asInt &&
                        items.none { it.code == data.text("diagnosticCode") }
                }
            }
            problems(editor)
            editor.text = data.text("moduleStart") + data.text("moduleEnd")
            editor.awaitDiagnostics(emptyList())
        }
    }

    private fun Driver.nextProblem(editor: JEditorUiComponent) {
        val positions = editor.diagnostics().map { it.start }
        check(positions.isNotEmpty())
        caret(editor, 0)
        invokeAction("GotoNextError", component = editor.component)
        awaitUi("Next Problem moves to an actual compiler diagnostic", 15.seconds) {
            withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().getOffset() in positions }
        }
        // Next Problem also shows a diagnostic hint; dismiss it before another editor/tool-window
        // action.
        withContext(OnDispatcher.EDT) { service<EditorHints>().hideAllHints() }
    }

    private fun Driver.problems(editor: JEditorUiComponent) {
        val text = editor.text
        val expected =
            editor
                .diagnostics()
                .map { diagnostic ->
                    check(diagnostic.start in 0..text.length)
                    val preceding = text.take(diagnostic.start)
                    preceding.count { it == '\n' } to
                        (diagnostic.start - preceding.lastIndexOf('\n') - 1)
                }.sortedWith(compareBy({ it.first }, { it.second }))
        val file =
            withContext(OnDispatcher.EDT) {
                requireNotNull(service<FileEditorManager>(singleProject()).getSelectedTextEditor())
                    .getVirtualFile()
            }
        val visible =
            withContext(OnDispatcher.EDT) {
                utility(ProblemsViewToolWindowUtils::class)
                    .getToolWindow(singleProject())
                    ?.isVisible() == true
            }
        if (!visible) invokeAction("ActivateProblemsViewToolWindow", component = editor.component)
        selectProblemsViewTab(HIGHLIGHTING_PANEL_ID)
        awaitUi("Problems tool window is visible", 15.seconds) {
            withContext(OnDispatcher.EDT) {
                utility(ProblemsViewToolWindowUtils::class)
                    .getToolWindow(singleProject())
                    ?.isVisible() == true
            }
        }
        waitForProblemsViewFile(file)
        awaitUi("Problems rows match editor diagnostic locations, including clearing", 45.seconds) {
            getProblemsViewProblems(file)
                .map { it.getLine() to it.getColumn() }
                .sortedWith(compareBy({ it.first }, { it.second })) == expected
        }
    }

    private fun Driver.scenario(
        id: String,
        action: (SharedScenarios.Scenario) -> Unit,
    ) {
        val scenario = shared.scenarios.getValue(id)
        case(id, scenario.title) { action(scenario) }
    }

    private fun Driver.restore(file: String) {
        val editor = open(file)
        editor.text = fixtures.getValue(file)
        editor.awaitDiagnostics(emptyList())
    }

    private fun Driver.editing(
        body: String,
        inspect: Boolean = false,
        transform: (String) -> String = { it },
    ): Pair<JEditorUiComponent, Int> {
        val setup = shared.common.editing
        val anchor = if (inspect) setup.inspect else setup.run
        val text =
            transform(fixtures.getValue(setup.file))
                .replace(anchor, anchor.replace("{}", "{ $body }"))
        val at = text.indexOf('§')
        check(at >= 0 && text.indexOf('§', at + 1) < 0) { "Expected one cursor marker" }
        val editor = open(setup.file)
        editor.text = text.replace("§", "")
        return editor to at
    }

    private fun Driver.case(
        id: String,
        description: String,
        continueAfterFailure: Boolean = false,
        action: () -> Unit,
    ) {
        if (!id.startsWith("START") && id !in selectedIds) return
        check(completed.none { it.id == id }) { "Duplicate native case $id" }
        val start = TimeSource.Monotonic.markNow()
        println("IntelliJ playbook $id: $description")
        if (id != "START" && id != "START_REOPEN") progress(id, "running", description)
        try {
            action()
            check(!isPluginLoaded("com.intellij.modules.ultimate")) {
                "Ultimate became active during $id; this cannot establish Community support"
            }
            completed +=
                Result(id, description, "passed", start.elapsedNow().inWholeMilliseconds)
                    .also(onResult)
            progress(id, "passed", description)
        } catch (failure: Throwable) {
            runCatching {
                ClientTrace(this)
                    .capture(
                        Path
                            .of(singleProject().getBasePath())
                            .parent
                            .resolve("client-trace-$id.log"),
                    )
            }.onFailure(failure::addSuppressed)
            completed +=
                Result(
                    id,
                    description,
                    "failed",
                    start.elapsedNow().inWholeMilliseconds,
                    failure.stackTraceToString(),
                ).also(onResult)
            runCatching { progress(id, "failed", description) }.onFailure(failure::addSuppressed)
            if (
                !continueAfterFailure ||
                failure is InterruptedException ||
                (failure !is Exception && failure !is AssertionError)
            ) {
                throw failure
            }
        }
    }

    private fun Driver.progress(
        id: String,
        status: String,
        description: String,
    ) {
        val total =
            when (mode) {
                PlaybookMode.FEATURES -> selectedIds.size
                PlaybookMode.SETTINGS_PERSISTENCE -> 2
                else -> 1
            }
        val done = completed.count { it.id != "START" && it.id != "START_REOPEN" }
        val failed = completed.count { it.status == "failed" }
        val text =
            "Ecstasy playbook: $done/$total completed, ${total - done} left | $id $status" +
                if (failed > 0) " | $failed failed" else ""
        val shortDescription = if (description.length > 80) description.take(77).trimEnd() + "…" else description
        withContext(OnDispatcher.EDT) {
            utility(PlaybookProgress::class).update(singleProject(), "$text — $shortDescription", "$text — $description")
        }
    }

    private fun Driver.workspaceScenarios() {
        ((109..121).map { "X$it" } + listOf("X155", "X159", "X160", "X164", "X165", "X166", "X167", "X168", "X220")).forEach { id ->
            scenario(id) {
                discovered(id) { data ->
                    val projectSettings = data.values["projectSettingsRoundTrip"]?.asBoolean == true
                    try {
                        if (projectSettings) {
                            withContext(OnDispatcher.EDT) {
                                utility(CompilerSettingsPage::class)
                                    .installProjectGraph(singleProject())
                            }
                        }
                        renameFamily(id, data, { open(it) }, { it.awaitDiagnostics(emptyList()) })
                    } finally {
                        if (projectSettings) {
                            withContext(OnDispatcher.EDT) {
                                utility(CompilerSettingsPage::class)
                                    .clearProjectGraph(singleProject())
                            }
                        }
                    }
                }
            }
        }
        scenario("X99") {
            discovered("X99") { data ->
                val consumer = open(data.text("file"))
                consumer.text = data.text("changed")
                consumer.awaitDiagnostics(emptyList())
                declaration(
                    definition(consumer, consumer.text.indexOf(data.text("anchor"))),
                    data.text("target"),
                )
                val library = open(data.text("library"))
                library.text = data.text("brokenLibrary")
                open(data.text("file")).awaitError()
                val discarded = open(data.text("library"))
                check(
                    Files.readString(Path.of(discarded.editor.getVirtualFile().getPath())) ==
                        data.text("libraryText"),
                )
                discarded.text = data.text("libraryText")
                withContext(OnDispatcher.EDT) {
                    service<FileEditorManager>(singleProject())
                        .closeFile(discarded.editor.getVirtualFile())
                }
                val restored = open(data.text("file"))
                restored.awaitDiagnostics(emptyList())
                check(
                    Files.readString(Path.of(restored.editor.getVirtualFile().getPath())) ==
                        data.text("original"),
                )
                restored.text = data.text("original")
            }
        }
        scenario("X100") {
            discovered("X100") { data ->
                val neighbor = open(data.text("neighbor"))
                neighbor.text = data.text("brokenNeighbor")
                neighbor.awaitError()
                val healthy = open(data.text("file"))
                partialGraphHierarchy(
                    healthy,
                    healthy.text.indexOf(data.text("anchor")),
                    data.text("child"),
                    healthy.text.lastIndexOf(data.text("anchor")),
                )
                val restored = open(data.text("neighbor"))
                restored.text = data.text("neighborText")
                restored.awaitDiagnostics(emptyList())
            }
        }
        listOf("X102", "X104").forEach { id ->
            scenario(id) {
                discovered(id) { data ->
                    val editor = open(data.text("file"))
                    editor.awaitDiagnostics(emptyList())
                    rename(
                        editor,
                        editor.text.indexOf(data.text("anchor")),
                        data.text("replacement"),
                    )
                    awaitUi("all source rename edits applied", 45.seconds) {
                        editor.text.split(data.text("replacement")).size - 1 ==
                            data.values["edits"].asInt
                    }
                    editor.awaitDiagnostics(emptyList())
                    data.values["preserved"]?.let { check(it.asString in editor.text) }
                    editor.text = data.text("source")
                    editor.awaitDiagnostics(emptyList())
                }
            }
        }
        scenario("X103") {
            discovered("X103") { data ->
                val editor = open(data.text("file"))
                editor.awaitDiagnostics(emptyList())
                val root = Path.of(editor.editor.getVirtualFile().getPath()).parent
                rename(editor, editor.text.indexOf(data.text("anchor")), data.text("replacement"))
                awaitUi("type rename moves the member file", 45.seconds) {
                    Files.exists(root.resolve(data.text("destination"))) &&
                        !Files.exists(root.resolve(data.text("member"))) &&
                        editor.text.contains(data.text("replacement"))
                }
                val moved = open("X103/${data.text("destination")}")
                check(
                    moved.text ==
                        data
                            .text("memberSource")
                            .replace(data.text("anchor"), data.text("replacement")),
                )
                check(
                    Files.readString(root.resolve(data.text("companionDestination"))) ==
                        data.text("companionSource"),
                )
                check(!Files.exists(root.resolve(data.text("companion"))))
                val reopened = open(data.text("file"))
                reopened.awaitDiagnostics(emptyList())
                // Restore through the same native refactoring path, including the reverse move.
                rename(
                    reopened,
                    reopened.text.indexOf(data.text("replacement")),
                    data.text("anchor"),
                )
                awaitUi("reverse rename restores source and the member", 45.seconds) {
                    Files.exists(root.resolve(data.text("member"))) &&
                        !Files.exists(root.resolve(data.text("destination"))) &&
                        reopened.text == data.text("source")
                }
                reopened.awaitDiagnostics(emptyList())
                val restored = open("X103/${data.text("member")}")
                check(restored.text == data.text("memberSource")) {
                    "Reverse rename must restore the member declaration in the open editor"
                }
                // Refactoring updates an open document; persistence must not depend on autosave.
                withContext(OnDispatcher.EDT, semantics = LockSemantics.WRITE_ACTION) {
                    service<ParityDocuments>()
                        .saveDocument(cast(restored.document, ParityDocument::class))
                }
                check(
                    Files.readString(root.resolve(data.text("member"))) == data.text("memberSource"),
                ) { "Saving the reverse rename must persist the restored member declaration" }
                check(
                    Files.readString(root.resolve(data.text("companion"))) ==
                        data.text("companionSource"),
                )
                check(!Files.exists(root.resolve(data.text("companionDestination"))))
            }
        }
        scenario("X123") { data ->
            val editor = open(data.text("file"))
            val protocol = ClientProtocol(this)
            check(protocol.capabilities().asJsonObject.has("diagnosticProvider"))
            val uri = Path.of(editor.editor.getVirtualFile().getPath()).toUri().toString()
            val params = mapOf("textDocument" to mapOf("uri" to uri), "identifier" to "xtc")
            try {
                editor.text = data.text("broken")
                editor.awaitError()
                val first = protocol.query("textDocument/diagnostic", params).asJsonObject
                check(first["kind"].asString == "full" && first["items"].asJsonArray.size() > 0)
                val previous = params + ("previousResultId" to first["resultId"].asString)
                check(
                    protocol
                        .query("textDocument/diagnostic", previous)
                        .asJsonObject["kind"]
                        .asString == "unchanged",
                )
                editor.text = data.text("repaired")
                editor.awaitDiagnostics(emptyList())
                val repaired = protocol.query("textDocument/diagnostic", previous).asJsonObject
                check(repaired["kind"].asString == "full" && repaired["items"].asJsonArray.isEmpty)
                check(repaired["resultId"] != first["resultId"])
            } finally {
                restore(data.text("file"))
            }
        }
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
            scenario(id) {
                discovered(id) { data ->
                    val editor = open(data.text("file"))
                    val original = data.text("source")
                    val companion = data.values.has("destinationFile")
                    check(editor.text == original) { "Fresh scenario source differs from shared fixture: $id" }

                    fun callerDiagnostics(broken: Boolean) {
                        // The locator sees the selected tab. Dependency-file checks also switch
                        // tabs; selecting the caller never reapplies an edit or changes its text.
                        val caller = open(data.text("file"))
                        if (companion) check(caller.text == original)
                        if (broken) caller.awaitError() else caller.awaitDiagnostics(emptyList())
                    }

                    fun additionalFiles(applied: Boolean) {
                        if (!data.values.has("files")) return
                        data.rows("files").forEach { file ->
                            val target = open("$id/${file["file"].asString}")
                            val expected = file[if (applied && file.has("expected")) "expected" else "source"].asString
                            awaitUi("additional refactoring file ${file["file"].asString}", 45.seconds) { target.text == expected }
                        }
                    }
                    val initiallyBroken = data.values["initiallyValid"]?.asBoolean == false
                    callerDiagnostics(initiallyBroken)
                    val at = data.values["selectionOffset"]?.asInt ?: original.indexOf(data.text("selected"))
                    if (data.values["refused"]?.asBoolean == true) {
                        fun position(offset: Int): Map<String, Int> {
                            val prefix = original.take(offset)
                            return mapOf("line" to prefix.count { it == '\n' }, "character" to (offset - prefix.lastIndexOf('\n') - 1))
                        }
                        val actions =
                            ClientProtocol(this)
                                .query(
                                    "textDocument/codeAction",
                                    mapOf(
                                        "textDocument" to
                                            mapOf(
                                                "uri" to Path.of(editor.editor.getVirtualFile().getPath()).toUri().toString(),
                                            ),
                                        "range" to mapOf("start" to position(at), "end" to position(at + data.text("selected").length)),
                                        "context" to mapOf("diagnostics" to emptyList<Any>()),
                                    ),
                                ).asJsonArray
                        check(actions.none { it.asJsonObject["title"].asString == data.text("title") })
                        check(editor.text == original)
                        return@discovered
                    }
                    // Diagnostic quick fixes use a caret at the error; expression refactorings
                    // use the complete selection specified by the shared scenario.
                    val selectionEnd = if (data.values["initiallyValid"]?.asBoolean == false) at else at + data.text("selected").length
                    quickFix(editor, at, data.text("title"), selectionEnd)
                    val destination = if (companion) open(data.text("destinationFile")) else editor
                    val destinationOriginal = if (companion) data.text("destinationSource") else original
                    awaitUi("local refactoring matches shared source", 45.seconds) { destination.text == data.text("expected") }
                    additionalFiles(true)
                    callerDiagnostics(false)
                    val history =
                        listOf("\$Undo" to destinationOriginal, "\$Redo" to data.text("expected"), "\$Undo" to destinationOriginal)
                    history.forEach { (action, expected) ->
                        val target = if (companion) open(data.text("destinationFile")) else editor
                        focusEditor(target)
                        invokeAction(action, now = false, component = target.component)
                        awaitUi("$action local refactoring", 45.seconds) { target.text == expected }
                        additionalFiles(action == "\$Redo")
                        callerDiagnostics(expected == destinationOriginal && initiallyBroken)
                    }
                }
            }
        }
        listOf("X149", "X150", "X151", "X152").forEach { id ->
            scenario(id) {
                discovered(id) { data ->
                    val editor = open(data.text("file"))
                    syntaxCompletions(data, editor) { editor.awaitDiagnostics(emptyList()) }
                }
            }
        }
        scenario("X122") {
            discovered("X122") { data ->
                memberActions(data, open(data.text("file"))) { editor, broken ->
                    if (broken) editor.awaitError() else editor.awaitDiagnostics(emptyList())
                }
            }
        }
        scenario("X105") {
            discovered("X105") { data ->
                val editor = open(data.text("file"))
                data.rows("variants").forEach { variant ->
                    editor.text = variant["source"].asString
                    editor.awaitError()
                    quickFix(
                        editor,
                        editor.text.indexOf(variant["anchor"].asString),
                        variant["title"].asString,
                    )
                    awaitUi("native import quick fix applied", 45.seconds) {
                        variant["importText"].asString in editor.text
                    }
                    editor.awaitDiagnostics(emptyList())
                }
                data.rows("completions").forEach { variant ->
                    val marked = variant["source"].asString
                    editor.text = marked.replace("§", "")
                    editor.awaitError()
                    accept(editor, marked.indexOf('§'), variant["label"].asString, variant["expected"].asString)
                    awaitUi("completion imports the selected type", 45.seconds) {
                        variant["importText"].asString in editor.text
                    }
                    editor.awaitDiagnostics(emptyList())
                }
                editor.text = "module AutoImports {}"
                editor.awaitDiagnostics(emptyList())
            }
        }
    }

    private fun Driver.discovered(
        id: String,
        action: (SharedScenarios.Scenario) -> Unit,
    ) {
        val original = shared.scenarios.getValue(id)
        val data =
            original.copy(
                values =
                    original.values.deepCopy().apply {
                        listOf("file", "library", "neighbor", "destinationFile").filter(::has).forEach { key ->
                            addProperty(key, "$id/${get(key).asString}")
                        }
                    },
            )
        val discovery = !data.values.has("sourceModules")
        if (discovery && service<LanguageClients>(singleProject()).getStartedServers().isEmpty()) {
            // A selected discovery case can be the first to start the server. Bootstrap with
            // the common graph's valid consumer, never this case's not-yet-configured source.
            open(shared.dependencyNavigation.file)
        }
        // Independent cases replace the source graph. Close their predecessors' fixture tabs,
        // as the workspace scenarios already did, before opening this case's caller.
        // TODO LSP4IJ: UP07 — keeping unrelated broken fixtures open during replacement can
        // cancel the new caller's lazy intentions after a successful server action reply.
        // This isolates fixtures; it does not claim to repair that production delivery race.
        withContext(OnDispatcher.EDT) {
            val manager = service<FileEditorManager>(singleProject())
            manager
                .getAllEditors()
                .map { it.getFile() }
                .distinctBy { it.getPath() }
                .forEach(manager::closeFile)
        }
        // Match VS Code's per-case discovery workspace so unrelated teaching fixtures cannot
        // silently alter this scenario's graph or the scope of its refactoring proof.
        val root = Path.of(singleProject().getBasePath())
        // Explicit graphs already isolate the case. Keep their project-relative roots anchored
        // to the real project; moving the workspace folder would change their meaning.
        if (discovery) {
            // The server is already started. Opening the target before selecting its graph
            // lets LSP4IJ cache an empty lazy quick-fix result against the previous graph.
            changeWorkspaceFolders(root, root.resolve(id))
            configure("""{"xtc":{"compiler":{"sourceModules":null}}}""")
        }
        try {
            if (data.values.has("sourceModules")) {
                val modules = data.values["sourceModules"].deepCopy().asJsonArray
                modules.forEach { module ->
                    val entry = module.asJsonObject
                    entry.addProperty(
                        "uri",
                        Path
                            .of(singleProject().getBasePath())
                            .resolve(id)
                            .resolve(entry["uri"].asString)
                            .toUri()
                            .toString(),
                    )
                }
                configure("""{"xtc":{"compiler":{"sourceModules":$modules}}}""")
            }
            // Open only after selecting this case's graph, avoiding a cold pull against the
            // previous fixture graph followed immediately by diagnostic invalidation.
            open(data.text("file"))
            action(data)
        } finally {
            configure(shared.graph)
            if (discovery) changeWorkspaceFolders(root.resolve(id), root)
        }
    }

    private fun Driver.configure(content: String) =
        withContext(OnDispatcher.EDT) {
            val settings = new(LspServerSettings::class)
            settings.setConfigurationContent(content)
            service<LspSettings>().updateSettings("xtcLanguageServer", settings)
            val traceSettings =
                new(LspServerSettings::class)
                    .setServerTrace(utility(ClientTraceLevel::class).valueOf("verbose"))
            service<ProjectLspSettings>(singleProject())
                .updateSettings("xtcLanguageServer", traceSettings)
        }

    private fun Driver.open(file: String): JEditorUiComponent {
        val target =
            awaitUi(
                message = "File is available: $file",
                timeout = 10.seconds,
                getter = { findFile(relativePath = file) },
                checker = { it != null },
            )
        withContext(OnDispatcher.EDT) {
            // Driver.openFile always requests focus; only popup checks need foreground activation.
            service<FileEditorManager>(singleProject())
                .openFile(requireNotNull(target), false, false)
        }
        // Tool windows such as Find Usages also contain editors; bind actions to this file's tab.
        val editor = ideFrame().codeEditorForFile(Path.of(file).fileName.toString())
        // A focused run may edit the very first file. Wait for the client's initial snapshot to be
        // sent before changing its document; merely opening the tab does not mean LSP startup
        // ended.
        awaitUi("language client opened $file", 45.seconds) {
            service<LanguageClients>(singleProject())
                .getStartedServers()
                .flatMap { it.getOpenedDocuments() }
                .filter { it.getFile().getPath() == editor.editor.getVirtualFile().getPath() }
                .any {
                    // OpenedDocument is registered before its synchronizer is installed.
                    it.getSynchronizer()?.getDidOpenFuture()?.let { future ->
                        future.isDone() && !future.isCompletedExceptionally()
                    } == true
                }
        }
        return editor
    }

    private fun Driver.caret(
        editor: JEditorUiComponent,
        offset: Int,
    ) {
        withContext(OnDispatcher.EDT) {
            editor.editor.getCaretModel().moveToOffset(offset)
        }
    }

    private fun Driver.navigate(
        editor: JEditorUiComponent,
        location: SharedScenarios.Location,
    ) {
        val targetOffset = offset(fixtures.getValue(location.targetFile), location.target)
        caret(editor, offset(editor.text, location.cursor))
        invokeAction("GotoDeclaration", component = editor.component)
        awaitUi("definition ${location.targetFile}:$targetOffset", 45.seconds) {
            withContext(OnDispatcher.EDT) {
                service<FileEditorManager>(singleProject()).getSelectedTextEditor()?.let {
                    it.getVirtualFile().getPath().endsWith("/${location.targetFile}") &&
                        it.getCaretModel().getOffset() == targetOffset
                } == true
            }
        }
    }

    private fun Driver.definition(
        editor: JEditorUiComponent,
        at: Int,
        action: String = "GotoDeclaration",
    ): Pair<String, Int> {
        val origin = editor.editor.getVirtualFile().getPath() to at
        focusEditor(editor)
        caret(editor, at)
        invokeAction(action, component = editor.component)

        fun selected() =
            withContext(OnDispatcher.EDT) {
                service<FileEditorManager>(singleProject()).getSelectedTextEditor()?.let {
                    it.getVirtualFile().getPath() to it.getCaretModel().getOffset()
                }
            }
        awaitUi(
            message = "$action navigates to a single declaration",
            errorMessage = {
                "Origin: $origin; selected: ${selected()}; diagnostics: ${editor.diagnostics()}"
            },
            timeout = 45.seconds,
        ) {
            selected()?.let { it != origin } == true
        }
        return requireNotNull(selected())
    }

    private fun declaration(
        target: Pair<String, Int>,
        name: String,
    ) {
        val text = Files.readString(Path.of(target.first))
        check(Regex("${Regex.escape(name)}\\b").matchesAt(text, target.second)) {
            "Expected declaration '$name' at $target"
        }
    }

    private fun libraryDeclaration(
        target: Pair<String, Int>,
        name: String,
    ) {
        declaration(target, name)
        check(!Files.isWritable(Path.of(target.first))) {
            "Bundled declaration must open read-only: ${target.first}"
        }
    }

    private fun JEditorUiComponent.diagnostics(): List<Diagnostic> =
        awaitUiNotNull("read editor diagnostics", 45.seconds) { readDiagnostics() }

    private fun JEditorUiComponent.readDiagnostics(): List<Diagnostic>? =
        try {
            installedDiagnostics()
        } catch (e: DriverCallException) {
            // A concurrent edit can cancel an IDE read.
            // Retry that transient read; never turn cancellation into an empty problem list.
            if (generateSequence<Throwable>(e) { it.cause }.none { it is ProcessCanceledException }) {
                throw e
            }
            null
        }

    private fun JEditorUiComponent.awaitError() {
        awaitUi(
            message = "compiler error in editor",
            timeout = 45.seconds,
            errorMessage = { "Source: $text; editor diagnostics: ${readDiagnostics()}" },
        ) {
            readDiagnostics()?.any { it.severity == "ERROR" } == true
        }
    }

    private fun JEditorUiComponent.awaitDiagnostics(expected: List<Diagnostic>) {
        awaitUi(
            message = "diagnostics $expected",
            errorMessage = { "Expected $expected; editor diagnostics: ${readDiagnostics()}" },
            timeout = 45.seconds,
        ) {
            readDiagnostics() == expected
        }
    }
}
