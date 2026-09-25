package org.xtclang.idea.playbook

import com.intellij.driver.client.Driver
import com.intellij.driver.client.service
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.FileEditorManager
import com.intellij.driver.sdk.getPlugin
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.isPluginLoaded
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.editor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Drive editor actions and inspect the diagnostics/lookup actually delivered to IntelliJ. */
class CompilerPlaybook(
    private val fixtures: Map<String, String>,
    private val lsp4ijVersion: String,
) {
    data class Result(
        val id: String,
        val description: String,
        val status: String,
        val milliseconds: Long,
        val error: String? = null,
    )

    private val completed = mutableListOf<Result>()
    val results: List<Result> get() = completed.toList()

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
            case("X45", "Initial compiler configuration and cross-module definition") {
                configure(GRAPH)
                val consumer = open("Consumer")
                navigate(consumer, "lib.value", "lib.".length, "Library", fixtures.getValue("Library").indexOf("value"))
                open("Consumer").awaitDiagnostics(emptyList())
            }
            case("X46", "Unsaved dependency edit recompiles its consumer") {
                val library = open("Library")
                library.text = fixtures.getValue("Library").replace("Int value()=1", "String value()=\"text\"")
                open("Consumer").awaitError()
                open("Library").text = fixtures.getValue("Library")
                open("Consumer").awaitDiagnostics(emptyList())
            }
            case("CFG1", "Clear and restore source graph through configuration notifications") {
                val consumer = open("Consumer")
                configure("""{"xtc":{"compiler":{"sourceModules":[]}}}""")
                consumer.awaitError()
                configure(GRAPH)
                consumer.awaitDiagnostics(emptyList())
                navigate(consumer, "lib.value", "lib.".length, "Library", fixtures.getValue("Library").indexOf("value"))
            }
            case("X2", "Compiler error severity, source span and clearing without save") {
                val editor = open("Navigation")
                editor.text = fixtures.getValue("Navigation").replace("Int value = 2", "String value = 2")
                editor.awaitError()
                val declaration = editor.text.indexOf("String value = 2")..editor.text.indexOf("return value +")
                check(editor.diagnostics().any { it.start in declaration })
                editor.text = fixtures.getValue("Navigation")
                editor.awaitDiagnostics(emptyList())
                editor.text = editor.text.replace("Object value = input", "Object value = missing")
                editor.awaitError()
                check(editor.diagnostics().any { it.description.contains("missing") })
                editor.text = fixtures.getValue("Navigation") + "// ERROR: test\n"
                editor.awaitDiagnostics(emptyList())
            }
            case("X4-definition", "Shadowed local and property navigate to separate declarations") {
                val editor = open("Navigation")
                navigate(editor, "return value +", "return ".length, "Navigation", editor.text.indexOf("value = 2"))
                navigate(editor, "this.value", "this.".length, "Navigation", editor.text.indexOf("value = 1"))
            }
            case("7a.8", "Exactly one compiler warning for the duplicate inherited annotation") {
                val editor = open("DupAnno")
                waitFor("one duplicate-annotation warning", 45.seconds) {
                    editor.diagnostics().let { it.size == 1 && it.single().severity == "WARNING" }
                }
                check(
                    editor
                        .diagnostics()
                        .single()
                        .description
                        .contains("annotation", ignoreCase = true),
                )
                editor.text = editor.text.replace("@Atomic @Override", "@Override")
                editor.awaitDiagnostics(emptyList())
            }
            case("X7", "Compiler member completion replaces the typed prefix exactly") {
                val editor = open("Editing")
                val incomplete = fixtures.getValue("Editing").replace("Object value) {}", "Object value) { Int size = getValue().si; }")
                editor.text = incomplete
                editor.awaitError()
                caret(editor, incomplete.indexOf(".si;") + 3)
                invokeAction("CodeCompletion", component = editor.component)
                val manager = utility(EditorLookupManager::class).getInstance(singleProject())
                val accepted = incomplete.replace(".si;", ".size;")
                waitFor("size completion or single-item insertion", 45.seconds) {
                    editor.text == accepted ||
                        manager.getActiveLookup()?.getItems()?.any { it.getLookupString() == "size" } == true
                }
                // IntelliJ may accept a sole match immediately. Otherwise exercise lookup acceptance.
                if (editor.text != accepted) {
                    withContext(OnDispatcher.EDT) {
                        val lookup = requireNotNull(manager.getActiveLookup())
                        lookup.setCurrentItem(lookup.getItems().first { it.getLookupString() == "size" })
                    }
                    invokeAction("EditorChooseLookupItem", component = editor.component)
                }
                waitFor("exact completion edit", 15.seconds) { editor.text == accepted }
                editor.awaitDiagnostics(emptyList())
            }
        }

    private fun Driver.case(
        id: String,
        description: String,
        action: () -> Unit,
    ) {
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

    private fun Driver.open(name: String): JEditorUiComponent {
        openFile("$name.x", waitForCodeAnalysis = false)
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
        text: String,
        within: Int,
        target: String,
        offset: Int,
    ) {
        caret(editor, editor.text.indexOf(text) + within)
        invokeAction("GotoDeclaration", component = editor.component)
        waitFor("definition $target.x:$offset", 45.seconds) {
            withContext(OnDispatcher.EDT) {
                service<FileEditorManager>(singleProject()).getSelectedTextEditor()?.let {
                    it.getVirtualFile().getPath().endsWith("/$target.x") && it.getCaretModel().getOffset() == offset
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

    companion object {
        private val GRAPH =
            """
            {"xtc":{"compiler":{"sourceModules":[
              {"name":"Library","uri":"Library.x"},
              {"name":"Consumer","uri":"Consumer.x","dependencies":["Library"]}
            ]}}}
            """.trimIndent()
    }
}
