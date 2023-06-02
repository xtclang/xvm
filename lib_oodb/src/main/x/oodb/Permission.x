/**
 * A `Permission` represents a targeted action that can be allowed (permitted) or disallowed
 * (revoked).
 */
const Permission(String target, String action) {
    /**
     * A wild-card representing all targets (database objects) that a permission may apply to.
     */
    static String AllTargets = "*";

    /**
     * A wild-card representing all actions (database functions) that a permission may apply to.
     */
    static String AllActions = "*";

    /**
     * Determine if this Permission covers `that` Permission. For example, the Permission for
     * `(AllTargets, AllActions)` will always return `True` when it is asked if it covers
     * another Permission.
     *
     * @param that  another Permission
     *
     * @return True iff the scope of `this` Permission fully covers `that` Permission
     */
    Boolean covers(Permission that) {
        return (this.action == AllActions || this.action == that.action)
            && (this.target == AllTargets || this.target == that.target);
    }
}
