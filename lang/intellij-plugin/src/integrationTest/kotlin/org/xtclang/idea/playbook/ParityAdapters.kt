package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.singleProject
import kotlin.time.Duration.Companion.seconds

/** The language-adapter chooser behind Tools | Switch Ecstasy Language Adapter and its keyboard shortcut. */
internal fun ParityScenarios.adapterChooserCases() {
    case("X286") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        val chooser = with(driver) { utility(AdapterChooserProbe::class) }
        check(chooser.hasShortcut(data.string("actionId"), data.string("shortcut"))) {
            "The adapter action is not bound to ${data.string("shortcut")}; ${chooser.describeShortcuts(data.string("actionId"))}"
        }
        check(chooser.inGroup(data.string("menuGroup"), data.string("actionId"))) { "The adapter action is not in the Tools menu" }
        val page = with(driver) { utility(LanguageServicePage::class) }
        val original = with(driver) { withContext(OnDispatcher.EDT) { page.content(singleProject()) } }
        var previous = protocol.server().getCurrentProcessId()
        try {
            listOf(data.string("treeSitterLabel") to "TreeSitter", data.string("compilerLabel") to "XDK").forEach { (label, expected) ->
                with(driver) {
                    focusEditor(document.editor)
                    withContext(OnDispatcher.EDT) { chooser.invoke(document.editor.editor, data.string("actionId")) }
                    awaitUi("adapter chooser offers $label", 15.seconds) { withContext(OnDispatcher.EDT) { chooser.pick(label) } }
                    awaitUi("adapter restart into $label", 45.seconds) {
                        protocol.server().getCurrentProcessId()?.let { it != previous } == true
                    }
                }
                previous = protocol.server().getCurrentProcessId()
                val status = protocol.query("xtc/languageServiceStatus", emptyMap<String, Any>()).asJsonObject
                check(status.string("adapter") == expected) { "Expected the $expected adapter: $status" }
            }
        } finally {
            with(driver) { withContext(OnDispatcher.EDT) { page.restore(singleProject(), original) } }
        }
    }
}

/** Keymap, menu and chooser checks for the adapter action, through the IDE-side probe. */
@Remote("org.xtclang.idea.playbook.probe.AdapterChooserUi", plugin = "org.xtclang.playbook.probe")
interface AdapterChooserProbe {
    fun hasShortcut(
        actionId: String,
        shortcut: String,
    ): Boolean

    fun describeShortcuts(actionId: String): String

    fun inGroup(
        groupId: String,
        actionId: String,
    ): Boolean

    fun invoke(
        editor: Editor,
        actionId: String,
    )

    fun pick(label: String): Boolean
}
