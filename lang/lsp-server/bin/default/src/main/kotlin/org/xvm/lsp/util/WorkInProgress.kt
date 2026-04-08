package org.xvm.lsp.util

/**
 * Marks a class, function, or property as work in progress.
 *
 * Use this annotation to indicate that an implementation is intentionally
 * incomplete or placeholder, awaiting future development.
 *
 * @param reason Optional description of what's pending or why it's incomplete
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class WorkInProgress(
    val reason: String = "",
)
