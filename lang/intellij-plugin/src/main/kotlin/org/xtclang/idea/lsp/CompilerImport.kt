package org.xtclang.idea.lsp

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** Owns one import and the last accepted report, independently of a settings dialog or connection. */
internal class CompilerImport(
    private val read: () -> String?,
    private val validate: (String) -> Unit,
) {
    enum class Outcome { SUCCEEDED, CANCELLED, FAILED }

    class Operation(
        val prepare: Boolean,
    )

    data class Result(
        val outcome: Outcome,
        val message: String,
        val finished: Instant = Instant.now(),
    )

    private data class Observed(
        val text: String?,
    )

    private data class State(
        val accepted: String? = null,
        val observed: Observed? = null,
        val operation: Operation? = null,
        val result: Result? = null,
    )

    private val state = AtomicReference(State())

    /** Watcher/configuration reads cannot publish a report from a still-running or failed build. */
    fun current(): String? {
        while (true) {
            val before = state.get()
            if (before.operation != null) return before.accepted
            val text = read()
            if (before.observed == Observed(text)) {
                if (state.get() === before) return before.accepted
                continue
            }
            // Malformed external reports leave the accepted snapshot intact and remain actionable.
            text?.let(validate)
            val after = before.copy(accepted = text, observed = Observed(text))
            if (state.compareAndSet(before, after)) return text
        }
    }

    fun retained(): String? = state.get().accepted

    fun begin(prepare: Boolean): Operation {
        while (true) {
            val before = state.get()
            check(before.operation == null) { "An Ecstasy compiler import is already running" }
            val operation = Operation(prepare)
            if (state.compareAndSet(before, before.copy(operation = operation))) return operation
        }
    }

    /** Called only after the owned process ends. A late callback cannot finish a newer import. */
    fun finish(
        operation: Operation,
        outcome: Outcome,
        detail: String? = null,
        cancelled: () -> Boolean = { false },
    ): Result {
        val before = state.get()
        check(before.operation === operation) { "Compiler import no longer owns this result" }
        val observed = runCatching(read)
        val validation =
            runCatching {
                val text = requireNotNull(observed.getOrThrow()) { "Gradle did not export an Ecstasy compiler model" }
                validate(text)
                text
            }
        val result =
            when {
                outcome == Outcome.CANCELLED || cancelled() -> {
                    Result(
                        Outcome.CANCELLED,
                        "Import cancelled; previous compiler configuration retained.",
                    )
                }

                outcome == Outcome.FAILED -> {
                    Result(outcome, detail ?: "Gradle import failed; previous compiler configuration retained.")
                }

                validation.isFailure -> {
                    Result(
                        Outcome.FAILED,
                        "${validation.exceptionOrNull()?.message}; previous compiler configuration retained.",
                    )
                }

                else -> {
                    Result(Outcome.SUCCEEDED, "Ecstasy compiler inputs imported.")
                }
            }
        val after =
            before.copy(
                accepted = if (result.outcome == Outcome.SUCCEEDED) validation.getOrThrow() else before.accepted,
                observed = if (observed.isSuccess) Observed(observed.getOrNull()) else before.observed,
                operation = null,
                result = result,
            )
        check(state.compareAndSet(before, after)) { "Compiler import ownership changed" }
        return result
    }

    fun description(): String {
        val current = state.get()
        return current.operation?.let {
            if (it.prepare) "Preparing generated Ecstasy inputs…" else "Refreshing evaluated Ecstasy compiler paths…"
        } ?: current.result?.let { "${it.message}\nLast import: ${it.finished}" } ?: "No compiler import run in this IDE session."
    }
}
