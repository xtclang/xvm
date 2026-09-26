package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.HIGHLIGHTING_PANEL_ID
import com.intellij.driver.sdk.ProblemsViewToolWindowUtils
import com.intellij.driver.sdk.findFile
import com.intellij.driver.sdk.getPlugin
import com.intellij.driver.sdk.getProblemsViewProblems
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.isPluginLoaded
import com.intellij.driver.sdk.selectProblemsViewTab
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.LookupElementPresentation
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.driver.sdk.waitForProblemsViewFile
import org.xtclang.idea.playbook.SharedScenarios.Companion.offset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Drive editor actions and inspect the diagnostics/lookup actually delivered to IntelliJ. */
class CompilerPlaybook(
    private val fixtures: Map<String, String>,
    private val shared: SharedScenarios,
    private val lsp4ijVersion: String,
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
            completed.filter { it.id == "START" } +
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

    fun run(driver: Driver) =
        with(driver) {
            case("START", "Packaged XTC and pinned LSP4IJ load") {
                waitForIndicators(2.minutes)
                check(getPlugin("org.xtclang.idea")?.isEnabled() == true)
                check(getPlugin("com.intellij.modules.ultimate")?.isEnabled() != true) {
                    "The playbook must run with Ultimate features disabled"
                }
                val lsp = requireNotNull(getPlugin("com.redhat.devtools.lsp4ij"))
                check(lsp.isEnabled() && lsp.getVersion() == lsp4ijVersion)
            }
            case(shared.dependencyNavigation.id, "Initial compiler configuration and cross-module definition") {
                val scenario = shared.dependencyNavigation
                configure(shared.graph)
                val consumer = open(scenario.file)
                check(
                    service<FileEditorManager>(singleProject()).getAllEditors().none {
                        it.getFile().getPath().endsWith("/${scenario.location.targetFile}")
                    },
                ) { "The dependency must resolve before opening its source file" }
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
                waitFor("consumer owns its type mismatch", 45.seconds) {
                    receivedDiagnostics(changed).let { items ->
                        items.isNotEmpty() && items.none { it.code == shared.scenarios.getValue(scenario.id).text("diagnosticCode") }
                    }
                }
                check(cast(changed.document, DocumentVersion::class).getModificationStamp() == version) {
                    "Dependency recompilation changed the consumer document"
                }
                check(Files.readString(libraryPath) == fixtures.getValue(scenario.file)) { "The unsaved dependency edit was saved to disk" }
                open(scenario.file).text = fixtures.getValue(scenario.file)
                open(scenario.consumer).awaitDiagnostics(emptyList())
            }
            case(shared.configuration.id, "Clear and restore source graph through configuration notifications") {
                val consumer = open(shared.configuration.consumer)
                configure("""{"xtc":{"compiler":{"sourceModules":[]}}}""")
                consumer.awaitError()
                configure(shared.graph)
                consumer.awaitDiagnostics(emptyList())
                navigate(consumer, shared.dependencyNavigation.location)
            }
            case(shared.diagnostics.id, "Compiler error severity, source span and clearing without save") {
                val scenario = shared.diagnostics
                val editor = open(scenario.file)
                scenario.errors.forEach { expected ->
                    val text = expected.edit.apply(fixtures.getValue(scenario.file))
                    editor.text = text
                    val range = offset(text, expected.rangeStart)..offset(text, expected.rangeEnd)
                    waitFor("compiler diagnostic at shared source span", 45.seconds) {
                        editor.diagnostics().any {
                            it.severity == expected.severity && it.start in range &&
                                it.description.contains(expected.messageContains, ignoreCase = true)
                        }
                    }
                    waitFor("compiler diagnostic codes, source, and nonempty ranges", 45.seconds) {
                        receivedDiagnostics(editor).let { items ->
                            items.isNotEmpty() &&
                                items.all {
                                    it.code?.startsWith(expected.codePrefix) == true && it.source == "xtc" &&
                                        it.start.first > 0 && it.start != it.end
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
            case(shared.definitions.id, "Shadowed local and property navigate to separate declarations") {
                val editor = open(shared.definitions.file)
                shared.definitions.locations.forEach { navigate(editor, it) }
                val uses = shared.definitions.locations.map { offset(editor.text, it.cursor) }
                uses.forEach { at -> referencesAndHighlights(editor, at, uses.single { it != at }) }
            }
            case(shared.warning.id, "Exactly one compiler warning for the duplicate inherited annotation") {
                val scenario = shared.warning
                val editor = open(scenario.file)
                waitFor("one duplicate-annotation warning", 45.seconds) {
                    editor.diagnostics().let { values ->
                        values.size == scenario.count &&
                            values.all {
                                it.severity == scenario.severity && it.start == offset(editor.text, scenario.declaration) &&
                                    it.description.contains(scenario.messageContains, ignoreCase = true)
                            }
                    }
                }
                problems(editor)
                val data = shared.scenarios.getValue(scenario.id)
                waitFor("compiler warning metadata", 45.seconds) {
                    receivedDiagnostics(editor).let { items ->
                        items.size == scenario.count &&
                            items.all {
                                it.code == scenario.code && it.source == "xtc" && it.severity == "Warning" &&
                                    it.start.first == it.end.first && it.end.second - it.start.second == data.values["warningCount"].asInt
                            }
                    }
                }
                nextProblem(editor)
                val error = open(data.text("errorFile"))
                error.text = fixtures.getValue(data.text("errorFile")).replace(data.text("replaceFrom"), data.text("replaceWith"))
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
                waitFor("restored compiler warning", 45.seconds) {
                    receivedDiagnostics(restored).let { items -> items.size == scenario.count && items.single().code == scenario.code }
                }
            }
            recoveryStructure()
            case(shared.completion.id, "Compiler member completion replaces the typed prefix exactly") {
                val scenario = shared.completion
                val editor = open(scenario.file)
                val incomplete = scenario.edit.apply(fixtures.getValue(scenario.file))
                editor.text = incomplete
                editor.awaitError()
                accept(editor, offset(incomplete, scenario.cursor), scenario.label)
                check(editor.text == scenario.accepted.apply(incomplete)) { "Shared accepted completion text differs" }
                editor.awaitDiagnostics(emptyList())
            }
            scenario("X6") { data ->
                data.strings("bodies").forEach { body ->
                    val (editor, at) = editing(body)
                    lookup(editor, at) { items ->
                        hasType(items, data.text("label"), data.values["type"].asJsonObject["source"].asString) &&
                            items.none { it.getLookupString() == data.text("excludedLabel") }
                    }
                    invokeAction("EditorEscape", component = editor.component)
                    accept(editor, at, data.text("label"))
                    check(editor.text.contains(data.text("acceptedExpression")))
                    editor.text = editor.text.replace(data.text("acceptedExpression"), data.text("validStatement"))
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
                        names.containsAll(data.strings("include")) && data.strings("exclude").none { it in names }
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
                        hasType(items, data.text("label"), data.values["genericType"].asJsonObject["source"].asString)
                }
                invokeAction("EditorEscape", component = first.component)
                val (second, secondAt) = editing(data.text("shadowedBody"), inspect = true)
                lookup(second, secondAt) { items ->
                    items.none { it.getLookupString() == data.text("excludedLabel") } &&
                        hasType(items, data.text("label"), data.values["shadowedType"].asJsonObject["source"].asString)
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
                                SharedScenarios.text(data.text("moduleWithImport"), variant["addition"].asString),
                            )
                        }
                    accept(editor, at, variant["name"].asString)
                }
                restore(shared.common.editing.file)
            }
            signatureScenarios()
            additionalScenarios()
            for (id in listOf("X71", "X75")) {
                scenario(id) { data ->
                    val editor = open(data.text("file"))
                    data.strings("variants").forEach { expression ->
                        editor.text =
                            fixtures.getValue(data.text("file")).replace(
                                data.text("replaceFrom"),
                                SharedScenarios.text(data.text("replaceWith"), expression),
                            )
                        if (id == "X75") editor.awaitError()
                        val at = editor.text.indexOf(data.text("anchor")) + data.values["offset"].asInt
                        accept(editor, at, data.text("label"))
                        if (id == "X75") editor.awaitError()
                    }
                    restore(data.text("file"))
                }
            }
            scenario("X86") { data ->
                val editor = open(data.text("file"))
                data.strings("variants").forEach { expression ->
                    editor.text = fixtures.getValue(data.text("file")).replace(data.text("replaceFrom"), expression)
                    editor.awaitError()
                    accept(editor, editor.text.indexOf(expression) + expression.length, data.text("label"))
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
                        text.replace(prefix, prefix.dropLast(data.values["prefixLength"].asInt) + selected + variant["terminator"].asString)
                    editor.awaitDiagnostics(emptyList())
                }
                restore(data.text("file"))
            }
            check(completed.filter { it.id != "START" }.map { it.id }.toSet() == shared.implementedIds) {
                "Every case declared implemented or partial must execute; unimplemented cases must stay explicit"
            }
        }

    private fun Driver.signatureScenarios() {
        scenario("X15") { data ->
            data.rows("variants").forEach { variant ->
                val (editor, at) = editing(SharedScenarios.text(data.text("body"), variant["argument"].asString))
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
                    Regex(data.values["type"].asJsonObject["source"].asString).containsMatchIn(items.single().label) &&
                    items.single().activeParameter == data.values["activeParameter"].asInt
            }
            editor.text = editor.text.replace(data.text("replaceFrom"), data.text("replaceWith"))
            editor.awaitDiagnostics(emptyList())
            signature(editor, editor.text.indexOf(data.text("completedArgument")) + data.values["argumentOffset"].asInt) {
                it.isNotEmpty()
            }
            definition(editor, editor.text.indexOf(data.text("call")) + data.values["callOffset"].asInt)
            restore(shared.common.editing.file)
        }
        scenario("X17") { data ->
            data.rows("variants").forEach { variant ->
                val (editor, at) = editing(variant["body"].asString)
                val type = Regex(SharedScenarios.text(data.text("typePattern"), variant["type"].asString))
                signature(editor, at) { it.isNotEmpty() && type.containsMatchIn(it.first().label) }
            }
            restore(shared.common.editing.file)
        }
        scenario("X18") { data ->
            data.strings("bodies").forEach { body ->
                val valid = shared.scenarios.getValue("X15")
                val argument = valid.rows("variants").first()["argument"].asString
                val (previous, previousAt) = editing(SharedScenarios.text(valid.text("body"), argument))
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
                    editing(data.text(if (instance) "instanceBody" else "staticBody"), inspect = instance) {
                        it.replace(data.text("replaceFrom"), data.text("replaceWith"))
                    }
                signature(editor, at) { it.isNotEmpty() && it.first().activeParameter == data.values["activeParameter"].asInt }
                editor.text = editor.text.replaceRange(at, at, data.text(if (instance) "instanceArgument" else "staticArgument"))
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
                    definition(selected, selected.text.indexOf(data.text("anchor")) + data.values["offset"].asInt)
                }
            check(targets.distinct().size == targets.size) { "Written argument types must select different overload declarations" }
            restore(shared.common.editing.file)
        }
        scenario("X74") { data ->
            val editor = open(data.text("file"))
            data.rows("variants").forEach { variant ->
                val call = variant["call"].asString
                editor.text =
                    fixtures.getValue(data.text("file")).replace(
                        data.text("replaceFrom"),
                        SharedScenarios.text(data.text("replaceWith"), call),
                    )
                signature(editor, editor.text.indexOf(call) + call.length) {
                    it.isNotEmpty() && it.first().label == data.text("signature") &&
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
                        fixtures.getValue(data.text("file")).replace(
                            original,
                            SharedScenarios.text(data.text("replaceWith"), prefix),
                        )
                    editor.awaitError()
                    val at = editor.text.indexOf(prefix) + prefix.length
                    signature(editor, at) {
                        it.isNotEmpty() &&
                            Regex(data.values["signature"].asJsonObject["source"].asString).containsMatchIn(it.first().label) &&
                            (id != "X83" || it.first().activeParameter == data.values["activeParameter"].asInt)
                    }
                    val labels =
                        data.strings(
                            if (id ==
                                "X83"
                            ) {
                                "labels"
                            } else if (original.contains(data.text("requiredContext"))) {
                                "requiredLabels"
                            } else {
                                "provisionalLabels"
                            },
                        )
                    acceptCandidates(editor, at, labels, if (id == "X83") labels.single() else data.text("label"))
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
                    fixtures.getValue(data.text("file")).replace(
                        data.text("replaceFrom"),
                        SharedScenarios.text(data.text("replaceWith"), prefix),
                    )
                editor.awaitError()
                val at = editor.text.indexOf(prefix) + prefix.length
                signature(editor, at) {
                    it.isNotEmpty() && it.first().activeParameter == data.values["activeParameter"].asInt &&
                        Regex(data.values["parameter"].asJsonObject["source"].asString).containsMatchIn(it.first().label)
                }
                acceptCandidates(editor, at, data.strings("labels"), data.strings("labels").single())
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
                    editor.text = fixtures.getValue(data.text("file")).replace(data.text("original"), replacement)
                    editor.awaitError()
                    val at = editor.text.indexOf(prefix) + prefix.length
                    signature(editor, at) {
                        it.isNotEmpty() && it.first().label == data.text("signature") &&
                            it.first().activeParameter == data.values["activeParameter"].asInt
                    }
                    acceptCandidates(editor, at, data.strings("labels"), data.strings("labels").single())
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
                editor.text = fixtures.getValue(data.text("file")).replace(data.text("original"), prefix + variant["suffix"].asString)
                val at = editor.text.indexOf(prefix) + prefix.length
                signature(editor, at) {
                    it.size == 1 && it.single().label == data.text("signature") &&
                        it.single().activeParameter == data.values["activeParameter"].asInt
                }
                acceptCandidates(editor, at, data.strings("labels"), selected)
                if (variant["validAfterAcceptance"].asBoolean) {
                    editor.awaitDiagnostics(emptyList())
                } else {
                    waitFor("missing array bracket remains a diagnostic", 30.seconds) { editor.diagnostics().isNotEmpty() }
                }
                val accepted = prefix.dropLast(-data.values["replacementStartDelta"].asInt) + selected
                editor.text =
                    fixtures.getValue(data.text("file")).replace(data.text("original"), accepted + variant["repairedSuffix"].asString)
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
                    names.containsAll(data.strings("include")) && data.strings("exclude").none { it in names }
                }
                invokeAction("EditorEscape", component = editor.component)
                accept(editor, at, data.text("selected"))
                check(
                    editor.text == marked.take(at - data.values["prefixLength"].asInt) +
                        data.text("selected") + marked.substring(at + 1),
                )
                editor.awaitDiagnostics(emptyList())
            }
            restore(data.text("file"))
        }
        scenario("X93") { data ->
            val editor = open(data.text("file"))
            data.rows("variants").forEach { variant ->
                val marked = SharedScenarios.text(data.text("source"), variant["declaration"].asString)
                val at = marked.indexOf('|')
                editor.text = marked.replace("|", "")
                editor.awaitError()
                signature(editor, at) { it.isEmpty() }
                lookup(editor, at) { items ->
                    val names = items.map { it.getLookupString() }
                    names.containsAll(variant["include"].asJsonArray.map { it.asString }) && data.strings("exclude").none { it in names }
                }
                invokeAction("EditorEscape", component = editor.component)
                accept(editor, at, variant["selected"].asString)
                check(
                    editor.text == marked.take(at - data.values["prefixLength"].asInt) +
                        variant["selected"].asString + marked.substring(at + 1),
                )
                editor.awaitDiagnostics(emptyList())
            }
            restore(data.text("file"))
        }
        scenario("X94") { data ->
            val editor = open(data.text("file"))
            data.rows("variants").forEach { variant ->
                val marked = SharedScenarios.text(data.text("source"), variant["declaration"].asString)
                val at = marked.indexOf(data.text("marker"))
                editor.text = marked.replace(data.text("marker"), "")
                editor.awaitError()
                signature(editor, at) { it.isEmpty() }
                lookup(editor, at) { items ->
                    val names = items.map { it.getLookupString() }
                    names.containsAll(variant["include"].asJsonArray.map { it.asString }) && data.strings("exclude").none { it in names }
                }
                invokeAction("EditorEscape", component = editor.component)
                accept(editor, at, variant["selected"].asString)
                check(
                    editor.text == marked.take(at - data.values["prefixLength"].asInt) +
                        variant["selected"].asString + marked.substring(at + data.text("marker").length),
                )
                if (variant["validAfterAcceptance"].asBoolean) {
                    editor.awaitDiagnostics(emptyList())
                } else {
                    editor.awaitError()
                }
                editor.text = SharedScenarios.text(data.text("source"), variant["repairedDeclaration"].asString)
                editor.awaitDiagnostics(emptyList())
            }
            restore(data.text("file"))
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
                waitFor(
                    message = "unfinished declaration body fold $expected",
                    errorMessage = { "Expected native fold $expected; actual: ${folds(editor)}" },
                    timeout = 45.seconds,
                ) { expected in folds(editor) }
                editor.text = SharedScenarios.text(data.text("source"), data.text("repairedParameters"))
                editor.awaitDiagnostics(emptyList())
                problems(editor)
            }
            restore(data.text("file"))
        }
    }

    private fun Driver.additionalScenarios() {
        scenario("X1") { data ->
            val editor = open(data.text("file"))
            editor.awaitDiagnostics(emptyList())
            structure(editor, data.strings("symbolNames"))
            waitFor("native fold regions", 45.seconds) { folds(editor).size >= data.values["minimumFolds"].asInt }
            selectionParents(editor, editor.text.indexOf(data.text("anchor")) + data.values["offset"].asInt)
        }
        scenario("X70") { data ->
            val editor = open(data.text("file"))
            editor.awaitDiagnostics(emptyList())
            val at = editor.text.indexOf(data.text("anchor"))
            signature(editor, at + data.values["offset"].asInt) { it.firstOrNull()?.label == data.text("signature") }
            val (_, target) = definition(editor, at)
            check(editor.text.substring(target).startsWith(data.text("targetName")))
        }
        scenario("X73") { data ->
            val editor = open(data.text("file"))
            data.rows("variants").forEach { variant ->
                val call = variant["call"].asString
                editor.text =
                    fixtures
                        .getValue(
                            data.text("file"),
                        ).replace(data.text("replaceFrom"), SharedScenarios.text(data.text("incompleteExpression"), call))
                val anchor = SharedScenarios.text(data.text("callAnchor"), call)
                signature(editor, editor.text.indexOf(anchor) + anchor.length) {
                    it.firstOrNull()?.let { item ->
                        item.label == data.text("signature") && item.activeParameter == variant["active"].asInt
                    } ==
                        true
                }
            }
            editor.text = fixtures.getValue(data.text("file")).replace(data.text("replaceFrom"), data.text("invalidExpression"))
            signature(editor, editor.text.indexOf(data.text("invalidCall")) + data.values["offset"].asInt) {
                it.size == data.values["rejectedSignatureCount"].asInt
            }
            restore(data.text("file"))
        }
        scenario("X76") { data ->
            val editor = open(data.text("file"))
            val expression = data.text("expression")
            editor.text =
                fixtures
                    .getValue(
                        data.text("file"),
                    ).replace(data.text("replaceFrom"), SharedScenarios.text(data.text("replaceWith"), expression))
            editor.awaitError()
            signature(editor, editor.text.indexOf(expression) + expression.length) {
                it.firstOrNull()?.let { item ->
                    item.label == data.text("signature") &&
                        item.activeParameter == data.values["activeParameter"].asInt
                } ==
                    true
            }
            restore(data.text("file"))
        }
        for (id in listOf("X77", "X79")) {
            scenario(id) { data ->
                val editor = open(data.text("file"))
                data.rows("variants").forEach { variant ->
                    val prefix = variant["prefix"].asString
                    editor.text =
                        fixtures.getValue(data.text("file")).replace(
                            variant["original"].asString,
                            SharedScenarios.text(data.text(if (id == "X77") "replaceWith" else "replaceFrom"), prefix),
                        )
                    editor.awaitError()
                    val at = editor.text.lastIndexOf(prefix) + prefix.length
                    acceptCandidates(
                        editor,
                        at,
                        data.strings("labels"),
                        if (id ==
                            "X77"
                        ) {
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
                    editor.text = fixtures.getValue(data.text("file")).replace(data.text("replaceFrom"), data.text("anchor"))
                    editor.awaitError()
                    val at = editor.text.indexOf(data.text("anchor")) + data.values["offset"].asInt
                    signature(editor, at) { it.size == data.values["signatureCount"].asInt }
                    acceptCandidates(editor, at, data.strings("labels"), selected)
                    editor.awaitDiagnostics(emptyList())
                    val acceptedAt = at + selected.length + if (id == "X80") data.values["replacementStartDelta"].asInt else 0
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
                        .getValue(
                            data.text("file"),
                        ).replace(data.text("replaceFrom"), SharedScenarios.text(data.text("replaceWith"), prefix))
                editor.awaitError()
                val at = editor.text.indexOf(prefix) + prefix.length
                lookup(editor, at) { items ->
                    items.map { it.getLookupString() }.sorted() == data.strings("labels").sorted() &&
                        hasType(items, data.text("label"), data.values["type"].asJsonObject["source"].asString)
                }
                invokeAction("EditorEscape", component = editor.component)
                accept(editor, at, data.text("label"))
                editor.awaitDiagnostics(emptyList())
            }
            restore(data.text("file"))
        }
        scenario("X82") { data ->
            val editor = open(data.text("file"))
            editor.text = fixtures.getValue(data.text("file")).replace(data.text("replaceFrom"), data.text("anchor"))
            editor.awaitError()
            val at = editor.text.indexOf(data.text("anchor")) + data.values["offset"].asInt
            acceptCandidates(editor, at, data.strings("labels"), data.strings("labels").single())
            check(editor.text == fixtures.getValue(data.text("file")))
            editor.awaitDiagnostics(emptyList())
        }
        scenario("7a.9") { data ->
            val editor = open(data.text("file"))
            editor.text = data.text("moduleStart") +
                (0 until data.values["declarationCount"].asInt).joinToString("\n") {
                    SharedScenarios.text(data.text("declaration"), it.toString(), it.toString())
                } + data.text("moduleEnd")
            editor.awaitError()
            waitFor("bounded source diagnostics without an internal compiler failure", 45.seconds) {
                receivedDiagnostics(editor).let { items ->
                    items.isNotEmpty() && items.count { it.severity == "Error" } <= data.values["maximumErrors"].asInt &&
                        items.none { it.code == data.text("diagnosticCode") }
                }
            }
            problems(editor)
            restore(data.text("file"))
        }
    }

    private fun Driver.nextProblem(editor: JEditorUiComponent) {
        val positions = editor.diagnostics().map { it.start }
        check(positions.isNotEmpty())
        caret(editor, 0)
        invokeAction("GotoNextError", component = editor.component)
        waitFor("Next Problem moves to an actual compiler diagnostic", 15.seconds) {
            withContext(OnDispatcher.EDT) { editor.editor.getCaretModel().getOffset() in positions }
        }
        // Next Problem also shows a diagnostic hint; dismiss it before another editor/tool-window action.
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
                    preceding.count { it == '\n' } to (diagnostic.start - preceding.lastIndexOf('\n') - 1)
                }.sortedWith(compareBy({ it.first }, { it.second }))
        val file =
            withContext(OnDispatcher.EDT) {
                requireNotNull(service<FileEditorManager>(singleProject()).getSelectedTextEditor()).getVirtualFile()
            }
        val visible =
            withContext(OnDispatcher.EDT) {
                utility(ProblemsViewToolWindowUtils::class).getToolWindow(singleProject())?.isVisible() == true
            }
        if (!visible) invokeAction("ActivateProblemsViewToolWindow", component = editor.component)
        selectProblemsViewTab(HIGHLIGHTING_PANEL_ID)
        waitFor("Problems tool window is visible", 15.seconds) {
            withContext(OnDispatcher.EDT) {
                utility(ProblemsViewToolWindowUtils::class).getToolWindow(singleProject())?.isVisible() == true
            }
        }
        waitForProblemsViewFile(file)
        waitFor("Problems rows match editor diagnostic locations, including clearing", 45.seconds) {
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
        val text = transform(fixtures.getValue(setup.file)).replace(anchor, anchor.replace("{}", "{ $body }"))
        val at = text.indexOf('§')
        check(at >= 0 && text.indexOf('§', at + 1) < 0) { "Expected one cursor marker" }
        val editor = open(setup.file)
        editor.text = text.replace("§", "")
        return editor to at
    }

    private fun Driver.lookup(
        editor: JEditorUiComponent,
        at: Int,
        matches: (List<CompletionItem>) -> Boolean,
    ) {
        focusEditor(editor)
        caret(editor, at)
        invokeAction("CodeCompletion", component = editor.component)
        val manager = utility(EditorLookupManager::class).getInstance(singleProject())
        waitFor("shared completion scope", 45.seconds) {
            requirePopupFocus()
            manager.getActiveLookup()?.let { !it.isCalculating() && matches(it.getItems()) } == true
        }
    }

    private fun Driver.hasType(
        items: List<CompletionItem>,
        label: String,
        pattern: String,
    ): Boolean =
        items.firstOrNull { it.getLookupString() == label }?.let { item ->
            val presentation = new(LookupElementPresentation::class)
            item.renderElement(presentation)
            Regex(pattern).containsMatchIn(presentation.getTypeText().orEmpty() + " " + presentation.getTailText().orEmpty())
        } == true

    private fun Driver.acceptCandidates(
        editor: JEditorUiComponent,
        at: Int,
        expected: List<String>,
        selected: String,
    ) {
        lookup(editor, at) { items -> items.map { it.getLookupString() }.sorted() == expected.sorted() }
        invokeAction("EditorEscape", component = editor.component)
        accept(editor, at, selected)
    }

    private fun Driver.accept(
        editor: JEditorUiComponent,
        at: Int,
        label: String,
    ) {
        val before = editor.text
        check(at in 0..before.length)
        val prefix = before.take(at).takeLastWhile { it.isLetterOrDigit() || it == '_' }
        val expected = before.replaceRange(at - prefix.length, at, label)
        focusEditor(editor)
        caret(editor, at)
        invokeAction("CodeCompletion", component = editor.component)
        val manager = utility(EditorLookupManager::class).getInstance(singleProject())
        waitFor("$label completion or single-item insertion", 45.seconds) {
            requirePopupFocus()
            editor.text == expected ||
                manager.getActiveLookup()?.let { !it.isCalculating() && it.getItems().any { item -> item.getLookupString() == label } } ==
                true
        }
        if (editor.text != expected) {
            withContext(OnDispatcher.EDT) {
                val lookup =
                    requireNotNull(manager.getActiveLookup()) {
                        "Native completion closed before acceptance; switching applications or editors cancels the popup"
                    }
                lookup.setCurrentItem(lookup.getItems().first { it.getLookupString() == label })
            }
            invokeAction("EditorChooseLookupItem", component = editor.component)
        }
        waitFor("exact replacement of the typed prefix", 15.seconds) { editor.text == expected }
    }

    private fun Driver.case(
        id: String,
        description: String,
        action: () -> Unit,
    ) {
        check(completed.none { it.id == id }) { "Duplicate native case $id" }
        val start = TimeSource.Monotonic.markNow()
        println("IntelliJ playbook $id: $description")
        try {
            action()
            check(!isPluginLoaded("com.intellij.modules.ultimate")) {
                "Ultimate became active during $id; this cannot establish Community support"
            }
            completed += Result(id, description, "passed", start.elapsedNow().inWholeMilliseconds)
        } catch (failure: Throwable) {
            completed += Result(id, description, "failed", start.elapsedNow().inWholeMilliseconds, failure.stackTraceToString())
            throw failure
        }
    }

    private fun Driver.configure(content: String) =
        withContext(OnDispatcher.EDT) {
            val settings = new(LspServerSettings::class)
            settings.setConfigurationContent(content)
            service<LspSettings>().updateSettings("xtcLanguageServer", settings)
        }

    private fun Driver.open(file: String): JEditorUiComponent {
        val target =
            waitFor(
                message = "File is available: $file",
                timeout = 10.seconds,
                getter = { findFile(relativePath = file) },
                checker = { it != null },
            )
        withContext(OnDispatcher.EDT) {
            // Driver.openFile always requests focus; only popup checks need foreground activation.
            service<FileEditorManager>(singleProject()).openFile(requireNotNull(target), false, false)
        }
        // Tool windows such as Find Usages also contain editors; bind actions to this file's tab.
        return ideFrame().codeEditorForFile(Path.of(file).fileName.toString())
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
        waitFor("definition ${location.targetFile}:$targetOffset", 45.seconds) {
            withContext(OnDispatcher.EDT) {
                service<FileEditorManager>(singleProject()).getSelectedTextEditor()?.let {
                    it.getVirtualFile().getPath().endsWith("/${location.targetFile}") && it.getCaretModel().getOffset() == targetOffset
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
        caret(editor, at)
        invokeAction(action, component = editor.component)

        fun selected() =
            withContext(OnDispatcher.EDT) {
                service<FileEditorManager>(singleProject()).getSelectedTextEditor()?.let {
                    it.getVirtualFile().getPath() to it.getCaretModel().getOffset()
                }
            }
        waitFor("$action navigates to a single declaration", 45.seconds) { selected()?.let { it != origin } == true }
        return requireNotNull(selected())
    }

    private data class Diagnostic(
        val severity: String,
        val description: String,
        val start: Int,
    )

    private fun JEditorUiComponent.diagnostics(): List<Diagnostic> =
        getAllHighlights().mapNotNull { highlight ->
            val severity = highlight.getSeverity().getName()
            if (severity in setOf("ERROR", "WARNING")) {
                Diagnostic(severity, highlight.getDescription().orEmpty(), highlight.getHighlighter()?.getStartOffset() ?: -1)
            } else {
                null
            }
        }

    private fun JEditorUiComponent.awaitError() {
        waitFor("compiler error in editor", 45.seconds) { diagnostics().any { it.severity == "ERROR" } }
    }

    private fun JEditorUiComponent.awaitDiagnostics(expected: List<Diagnostic>) {
        waitFor("diagnostics $expected", 45.seconds) { diagnostics() == expected }
    }
}
