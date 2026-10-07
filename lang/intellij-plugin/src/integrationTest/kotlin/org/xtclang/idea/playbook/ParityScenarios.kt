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
        refreshOverlapCases()
        compilerImportCases()
        librarySettingsCases()
        runtimeSettingsCases()
        colorCases()
        highlightingCases()
        documentationCases()
        referenceLensCases()
    }

    internal fun case(
        id: String,
        body: ParityWorkspace.(JsonObject) -> Unit,
    ) {
        execute(id) {
            val workspace = ParityWorkspace(driver, id, fixtures, shared)
            try {
                workspace.body(shared.scenarios.getValue(id).values)
            } catch (failure: Throwable) {
                // Do not race cleanup with a driver command that may still be running.
                if (!failure.mustStopPlaybook()) runCatching(workspace::close).onFailure(failure::addSuppressed)
                throw failure
            }
            workspace.close()
        }
    }
}
