/**
 * A mixin to indicate that tests should be ignored.
 *
 * Any affected test fixture will be reported as skipped, with the specified reason.
 *
 * * If the mixin is applied to a `Package` all test fixtures in that `Package` are disabled.
 * * If the mixin is applied to a `Class` all test fixtures in that `Class` are disabled.
 * * If the mixin is applied to a `Method` then that test `Method` is disabled.
 *
 * @param reason  the reason for disabling the test.
 */
mixin Disabled(String reason)
        into Test.TestTarget;
