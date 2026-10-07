package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.singleProject
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.referenceLensCases() {
    case("X279") { data ->
        write(data.string("file"), data.string("source"))
        write(data.string("consumerFile"), data.string("consumer"))
        configure(
            listOf(
                SharedScenarios.SourceModule(data.string("module"), uri(data.string("file")), emptyList()),
                SharedScenarios.SourceModule(
                    data.string("consumerModule"),
                    uri(data.string("consumerFile")),
                    listOf(data.string("module")),
                ),
            ),
        )
        val project = with(driver) { singleProject() }
        val document = open(data.string("file"))
        val page = with(driver) { utility(LanguageServicePage::class) }
        val probe = with(driver) { utility(CodeLensProbe::class) }
        val before = with(driver) { withContext(OnDispatcher.EDT) { page.content(project) } }

        fun setting(enabled: Boolean) = with(driver) { withContext(OnDispatcher.EDT) { page.referenceCodeLens(project, enabled) } }

        fun titles() = with(driver) { withContext(OnDispatcher.EDT) { probe.titles(document.editor.editor) } }

        fun click() =
            with(driver) {
                focusEditor(document.editor)
                withContext(OnDispatcher.EDT) { probe.click(document.editor.editor, data.string("initialTitle")) }
            }
        try {
            setting(true)
            clean(document)
            awaitUi("native reference Code Vision includes the closed consumer", 45.seconds) { data.string("initialTitle") in titles() }
            click()
            with(driver) {
                chooseTargets(
                    document.editor,
                    listOf(data.string("file"), data.string("consumerFile")),
                    data.string("consumerFile"),
                    ::click,
                )
            }
            val consumer = open(data.string("consumerFile"))
            replace(consumer, data.string("changedConsumer"))
            clean(consumer)
            open(data.string("file"))
            awaitUi("native reference Code Vision refreshes after a consumer edit", 45.seconds) { data.string("changedTitle") in titles() }
            setting(false)
            awaitUi("reference counts disappear while Run stays visible", 30.seconds) {
                titles().let { values -> values.any { "Run" in it } && values.none { "reference" in it } }
            }
            setting(true)
            awaitUi("reference counts return without restarting", 30.seconds) { data.string("changedTitle") in titles() }
            check(document.text == data.string("source"))
        } finally {
            with(driver) { withContext(OnDispatcher.EDT) { page.restore(project, before) } }
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.CodeLensUi", plugin = "org.xtclang.playbook.probe")
interface CodeLensProbe {
    fun titles(editor: Editor): List<String>

    fun click(
        editor: Editor,
        title: String,
    )
}
