package org.xtclang.idea.playbook

import com.google.gson.JsonObject
import com.intellij.driver.client.Driver

/**
 * Shared expectations, native editor actions and explicitly identified client protocol assertions.
 */
class ParityScenarios(
    private val driver: Driver,
    private val fixtures: Map<String, String>,
    private val shared: SharedScenarios,
    private val execute: (String, () -> Unit) -> Unit,
) {
    fun run() {
        navigationCases()
        semanticCases()
        moduleCases()
        dependencyCases()
        renameCases()
        graphCases()
        platformCases()
        typeMoveCases()
        progressCases()
        reliabilityCases()
        indexingCases()
        monikerCases()
        libraryContentCases()
        inlineCompletionCases()
    }

    internal fun case(
        id: String,
        body: ParityWorkspace.(JsonObject) -> Unit,
    ) {
        execute(id) {
            ParityWorkspace(driver, id, fixtures, shared).use { workspace ->
                workspace.body(shared.scenarios.getValue(id).values)
            }
        }
    }
}
