package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Editor
import com.intellij.driver.sdk.singleProject
import org.xtclang.idea.playbook.probe.HANG_LIMIT

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
        val gotIt = with(driver) { utility(GotItProbe::class) }
        val original = with(driver) { withContext(OnDispatcher.EDT) { page.content(singleProject()) } }
        var previous = protocol.server().getCurrentProcessId()
        try {
            listOf(data.string("treeSitterLabel") to "TreeSitter", data.string("compilerLabel") to "XDK").forEach { (label, expected) ->
                // the first start with Tree-sitter shows, once, in the Ecstasy editor, what the adapter leaves out
                if (expected == "TreeSitter") gotIt.enable(data.string("treeSitterTooltip"))
                with(driver) {
                    focusEditor(document.editor)
                    withContext(OnDispatcher.EDT) { chooser.invoke(document.editor.editor, data.string("actionId")) }
                    awaitUi("adapter chooser offers $label", HANG_LIMIT) { withContext(OnDispatcher.EDT) { chooser.pick(label) } }
                }
                previous = protocol.awaitRestart("adapter restart into $label", previous)
                val status = protocol.status()
                check(status.string("adapter") == expected) { "Expected the $expected adapter: $status" }
                if (expected == "TreeSitter") {
                    with(driver) {
                        awaitUi("the Tree-sitter Got It tooltip over the editor", HANG_LIMIT) { gotIt.shown(document.editor.editor) }
                    }
                    check(gotIt.dismiss()) { "The Tree-sitter tooltip has no Got It button" }
                }
            }
        } finally {
            gotIt.dismiss()
            with(driver) { withContext(OnDispatcher.EDT) { page.restore(singleProject(), original) } }
        }
    }
}

/** The plugin's Got It tooltips, which the playbook IDE starts with off, through the IDE-side probe. */
@Remote("org.xtclang.idea.playbook.probe.GotItUi", plugin = "org.xtclang.playbook.probe")
interface GotItProbe {
    fun enable(id: String)

    fun shown(editor: Editor): Boolean

    fun dismiss(): Boolean
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
