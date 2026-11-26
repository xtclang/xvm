package org.xtclang.idea.playbook

import com.intellij.driver.sdk.invokeAction
import org.xtclang.idea.playbook.probe.HANG_LIMIT

/** X357, registered after X356 in catalog order: Optimize Imports removes an unused import and sorts the rest. */
internal fun ParityScenarios.organizeImportsCases() {
    case("X357") { data ->
        val document = openModule(data)
        // as Ctrl+Alt+O runs it, in one edit
        with(driver) {
            focusEditor(document.editor)
            invokeAction("OptimizeImports", component = document.editor.component)
            awaitUi("Optimize Imports organizes the imports", HANG_LIMIT) { document.text == data.string("organized") }
        }
        clean(document)
    }
}
