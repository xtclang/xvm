package org.xtclang.idea.playbook

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.NoCIServer
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind

/** Starter's CI server for the playbook: it reports nothing, and keeps each IDE failure that Starter reports in [failures]. */
internal class FailureCollector(
    private val failures: MutableList<String>,
) : CIServer by NoCIServer {
    override fun reportTestFailure(
        testName: String,
        message: String,
        details: String,
        linkToLogs: String?,
        kind: SyntheticTestKind,
        generifyTestName: Boolean,
    ) {
        failures += "$testName: $message\n$details\n$linkToLogs"
    }
}
