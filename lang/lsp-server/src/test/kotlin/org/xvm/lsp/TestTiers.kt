package org.xvm.lsp

import org.junit.jupiter.api.Tag

/**
 * A test that starts real processes: execution workers, `xtc` commands, or a program under the debugger. The suite's
 * default run leaves it out; `-PlspTests=all` runs it with the rest, and `-PlspTests=slow` runs only such tests.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("slow")
annotation class Slow

/**
 * A long repetition workload, such as the extended retention workload. The suite's default run leaves it out;
 * `-PlspTests=all` runs it with the rest, and `-PlspTests=heavy` runs only such tests.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("heavy")
annotation class Heavy
