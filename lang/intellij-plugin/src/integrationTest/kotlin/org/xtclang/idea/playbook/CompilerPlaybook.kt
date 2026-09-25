package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.HIGHLIGHTING_PANEL_ID
import com.intellij.driver.sdk.ProblemsViewToolWindowUtils
import com.intellij.driver.sdk.getPlugin
import com.intellij.driver.sdk.getProblemsViewProblems
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.isPluginLoaded
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.openProblemsViewToolWindow
import com.intellij.driver.sdk.selectProblemsViewTab
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.LookupElementPresentation
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.driver.sdk.waitForProblemsViewFile
import org.xtclang.idea.playbook.SharedScenarios.Companion.offset
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
                navigate(consumer, scenario.location)
                open(scenario.file).awaitDiagnostics(emptyList())
            }
            case(shared.dependencyEdit.id, "Unsaved dependency edit recompiles its consumer") {
                val scenario = shared.dependencyEdit
                val library = open(scenario.file)
                library.text = scenario.edit.apply(fixtures.getValue(scenario.file))
                open(scenario.consumer).awaitError()
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
                    problems(editor)
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
                editor.text = scenario.edit.apply(editor.text)
                editor.awaitDiagnostics(emptyList())
                problems(editor)
            }
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
                    signature(editor, editor.text.indexOf(prefix) + prefix.length) {
                        it.isNotEmpty() &&
                            Regex(data.values["signature"].asJsonObject["source"].asString).containsMatchIn(it.first().label) &&
                            (id != "X83" || it.first().activeParameter == data.values["activeParameter"].asInt)
                    }
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
                signature(editor, editor.text.indexOf(prefix) + prefix.length) {
                    it.isNotEmpty() && it.first().activeParameter == data.values["activeParameter"].asInt &&
                        Regex(data.values["parameter"].asJsonObject["source"].asString).containsMatchIn(it.first().label)
                }
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
                    signature(editor, editor.text.indexOf(prefix) + prefix.length) {
                        it.isNotEmpty() && it.first().label == data.text("signature") &&
                            it.first().activeParameter == data.values["activeParameter"].asInt
                    }
                }
                restore(data.text("file"))
            }
        }
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
        openProblemsViewToolWindow()
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
        caret(editor, at)
        invokeAction("CodeCompletion", component = editor.component)
        val manager = utility(EditorLookupManager::class).getInstance(singleProject())
        waitFor("shared completion scope", 45.seconds) {
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

    private fun Driver.accept(
        editor: JEditorUiComponent,
        at: Int,
        label: String,
    ) {
        val before = editor.text
        check(at in 0..before.length)
        val prefix = before.take(at).takeLastWhile { it.isLetterOrDigit() || it == '_' }
        val expected = before.replaceRange(at - prefix.length, at, label)
        caret(editor, at)
        invokeAction("CodeCompletion", component = editor.component)
        val manager = utility(EditorLookupManager::class).getInstance(singleProject())
        waitFor("$label completion or single-item insertion", 45.seconds) {
            editor.text == expected ||
                manager.getActiveLookup()?.let { !it.isCalculating() && it.getItems().any { item -> item.getLookupString() == label } } ==
                true
        }
        if (editor.text != expected) {
            withContext(OnDispatcher.EDT) {
                val lookup = requireNotNull(manager.getActiveLookup())
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
        openFile(file, waitForCodeAnalysis = false)
        return ideFrame().editor()
    }

    private fun Driver.caret(
        editor: JEditorUiComponent,
        offset: Int,
    ) = withContext(OnDispatcher.EDT) {
        editor.editor.getCaretModel().moveToOffset(offset)
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
