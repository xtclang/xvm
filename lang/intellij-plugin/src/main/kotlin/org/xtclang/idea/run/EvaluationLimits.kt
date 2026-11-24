package org.xtclang.idea.run

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindIntText
import org.jdom.Element
import kotlin.reflect.KMutableProperty0

/**
 * The time limits, in milliseconds, of the code that a debug session runs for the IDE, which both Ecstasy
 * configurations have: code that Evaluate Expression or the Debug window's console runs, such as a call, and the code
 * that computes a lazy value, such as a getter or `toString()`. The language server's `launch` and `attach` take them
 * as [EVALUATION] and [LAZY_VALUE], and end the code once it has run that long. IntelliJ cannot cancel an evaluation
 * (UP41 in doc/errs/remaining.md), so the Evaluate dialog waits that long for code that never ends; a configuration's
 * limits therefore stop at [RANGE]'s end, below the server's own maximum for evaluations, which VS Code can cancel.
 */
internal object EvaluationLimits {
    /** The `launch` and `attach` arguments. */
    const val EVALUATION = "evaluationTimeLimit"
    const val LAZY_VALUE = "lazyValueTimeLimit"

    /** The language server's own limits, which a configuration sends only when it sets others. */
    const val EVALUATION_DEFAULT = 2_000
    const val LAZY_VALUE_DEFAULT = 1_000

    /** The limits a configuration may set. */
    val RANGE = 100..30_000

    /** The limit that a saved configuration's attribute [name] holds, or [default] for none. */
    fun read(
        element: Element,
        name: String,
        default: Int,
    ) = element.getAttributeValue(name)?.toIntOrNull() ?: default

    /** The `launch` or `attach` arguments of the limits: those that differ from the language server's own. */
    fun parameters(
        evaluation: Int,
        lazyValue: Int,
    ): Map<String, Int> =
        buildMap {
            if (evaluation != EVALUATION_DEFAULT) put(EVALUATION, evaluation)
            if (lazyValue != LAZY_VALUE_DEFAULT) put(LAZY_VALUE, lazyValue)
        }

    /** Fails for a limit outside [RANGE]. */
    fun check(
        evaluation: Int,
        lazyValue: Int,
    ) {
        if (evaluation !in RANGE) throw RuntimeConfigurationError(outOfRange("evaluation"))
        if (lazyValue !in RANGE) throw RuntimeConfigurationError(outOfRange("lazy value"))
    }

    /** Why the [what] time limit is refused. */
    fun outOfRange(what: String) = "The $what time limit must be from ${RANGE.first} to ${RANGE.last} ms"

    /** A settings editor's rows for the limits, bound to [evaluation] and [lazyValue]. */
    fun Panel.limitRows(
        evaluation: KMutableProperty0<Int>,
        lazyValue: KMutableProperty0<Int>,
    ) {
        row("Evaluation time limit (ms):") {
            intTextField(RANGE)
                .bindIntText(evaluation)
                .comment(
                    "Under Debug, how long code that Evaluate Expression or the console runs, such as a call, may take " +
                        "before it is ended. IntelliJ cannot cancel an evaluation, so Evaluate Expression waits that long " +
                        "for code that never ends; at most ${RANGE.last}",
                )
        }
        row("Lazy value time limit (ms):") {
            intTextField(RANGE)
                .bindIntText(lazyValue)
                .comment(
                    "Under Debug, how long a lazy value's code, such as a getter or toString(), may take when the " +
                        "Variables view shows it; at most ${RANGE.last}",
                )
        }
    }
}
