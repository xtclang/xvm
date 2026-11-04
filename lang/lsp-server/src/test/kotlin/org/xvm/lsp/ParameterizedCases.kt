package org.xvm.lsp

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest

/**
 * Runs only the first case of each parameterized test when the configuration parameter [CASES] is `first`, as the
 * suite's default tier sets it; the other cases are reported as skipped, with the reason. Every case runs otherwise,
 * as with `-PlspTests=all` or a run from the IDE. JUnit registers it through its service file, since
 * junit-platform.properties enables extension autodetection.
 */
class ParameterizedCases : ExecutionCondition {
    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        if (context.getConfigurationParameter(CASES).orElse("all") != "first") {
            return ConditionEvaluationResult.enabled("Every case of a parameterized test runs")
        }
        val case =
            INVOCATION
                .find(context.uniqueId)
                ?.groupValues
                ?.get(1)
                ?.toInt()
        val laterCase =
            case != null &&
                case > 1 &&
                context.testMethod.map { it.isAnnotationPresent(ParameterizedTest::class.java) }.orElse(false)
        return if (laterCase) {
            ConditionEvaluationResult.disabled(
                "The default tier runs only a parameterized test's first case; -PlspTests=all runs every case",
            )
        } else {
            ConditionEvaluationResult.enabled("The first case of a parameterized test, or not a case of one")
        }
    }

    companion object {
        /** The configuration parameter, or system property, that chooses the cases: `first` or `all`. */
        const val CASES = "xtc.lsp.parameterizedCases"

        /** The last segment of a test template's invocation's unique ID, with the invocation's number, from 1. */
        private val INVOCATION = Regex("""\[test-template-invocation:#(\d+)]$""")
    }
}
