/**
 * A `Permission` represents a targeted action that can be allowed (permitted) or disallowed
 * (revoked).
 *
 * One literal value "*" is reserved for both the action and target strings; it is a wild-card, and
 * as such matches any action or target, respectively. Additionally, a permission action and/or
 * target may end with the character '*', which when used in a [covers] test will match any number
 * of characters.
 *
 * The lexical rules for actions and targets:
 * * Must not be an empty string;
 * * Must not begin or end with white space;
 * * An action may not begin with an exclamation mark;
 * * An action may not contain a colon.
 * Otherwise, no lexical or semantic definition is implied.
 *
 * The String format of a Permission is the action, a colon (':'), and the target; for example, with
 * the action "GET" and the target "/items/42", the permission string format is: `GET:/items/42`.
 * A "revoked" permission is prefixed with `!` (i.e. "not").
 *
 * @param target  the target specified by the permission
 * @param action  the action specified by the permission
 * @param revoke  (optional) indicates that a permission is explicitly revoked on a given [Entity];
 *                in other words, if `True` then the `Permission` object is an "anti-permission"
 */
const Permission(String target, String action, Boolean revoke = False)
        implements Destringable {

    // ----- construction --------------------------------------------------------------------------

    @Override
    construct(String text) {
        Int start = 0;
        if (text.startsWith('!')) {
            revoke = True;
            start  = 1;
        }

        assert:arg Int colon := text.indexOf(':');
        construct Permission(text[start..<colon], text.substring(colon+1));
    }

    assert() {
        assert:arg checkAction(action) as $"Invalid action: {action.quoted()}";
        assert:arg checkTarget(target) as $"Invalid target: {target.quoted()}";
    }

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * A reserved string, used as a wild-card to represent "all targets" or "all actions".
     */
    static String All = "*";

    /**
     * All allowing permission.
     */
    static Permission AllowAll = new Permission(All, All);

    /**
     * Verify that a permission action is lexically valid.
     *
     * @param target  a permission action string
     *
     * @return True iff the name is legal
     */
    static Boolean checkAction(String action) = !action.empty && action == action.trim()
            && !action.startsWith('!') && !action.indexOf(':');

    /**
     * Verify that a permission target is lexically valid.
     *
     * @param target  a permission target string
     *
     * @return True iff the name is legal
     */
    static Boolean checkTarget(String target) = !target.empty && target == target.trim();

    // ----- API -----------------------------------------------------------------------------------

    /**
     * Determine if this Permission covers `that` Permission. For example, the Permission for
     * `(All, All)` will always return `True` when it is asked if it covers another Permission.
     *
     * Note that this method explicitly does not consider the [revoke] property; that is the
     * responsibility of the caller. This method only evaluates the [action] and [target].
     *
     * @param that  another Permission
     *
     * @return True iff the scope of `this` Permission fully covers `that` Permission
     */
    Boolean covers(Permission that) {
        return (this.action == All || this.action == that.action ||
                this.action.endsWith('*') && that.action.startsWith(this.action[0..<this.action.size-1]))
            && (this.target == All || this.target == that.target ||
                this.target.endsWith('*') && that.target.startsWith(this.target[0..<this.target.size-1]));
    }

    @Override
    String toString() = $"{revoke ? "!" : ""}{action}:{target}";
}
