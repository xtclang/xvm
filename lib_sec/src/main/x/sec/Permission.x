/**
 * A `Permission` represents a targeted action that can be allowed (permitted) or disallowed
 * (revoked).
 */
const Permission(String target, String action) {

    // ----- construction --------------------------------------------------------------------------

    assert() {
        assert:arg checkId(target);
        assert:arg checkId(action);
    }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * A reserved string, used as a wild-card to represent "all targets" or "all actions".
     */
    static String All = "*";

    /**
     * Helper to validate the high level structure of a target or action identity.
     *
     * @param id  a String containing a target or action identity
     *
     * @return `True` iff the `id` has a valid format
     */
    static Boolean checkId(String id) = !id.empty && id == id.trim();

    // ----- API -----------------------------------------------------------------------------------

    /**
     * Determine if this Permission covers `that` Permission. For example, the Permission for
     * `(All, All)` will always return `True` when it is asked if it covers another Permission.
     *
     * @param that  another Permission
     *
     * @return True iff the scope of `this` Permission fully covers `that` Permission
     */
    Boolean covers(Permission that) {
        return (this.action == All || this.action == that.action)
            && (this.target == All || this.target == that.target);
    }
}
