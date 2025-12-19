/**
 * An annotation to indicate that tests should be ignored.
 *
 * Any affected test fixture will be reported as skipped, with the specified reason.
 *
 * * If the annotation is applied to a package all test fixtures in that package are disabled.
 * * If the annotation is applied to a class all test fixtures in that class are disabled.
 * * If the annotation is applied to a `Method` then that test `Method` is disabled.
 *
 * @param reason  the reason for disabling the test.
 */
annotation Disabled(String reason)
        into TestTarget;
