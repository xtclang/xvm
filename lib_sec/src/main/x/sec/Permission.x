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
        Int     start  = 0;
        Boolean revoke = False;
        if (text.startsWith('!')) {
            revoke = True;
            start  = 1;
        }

        assert:arg Int colon := text.indexOf(':');
        construct Permission(text.substring(colon+1), text[start..<colon], revoke);
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

    /**
     * Describes the relationship between two entities comprised of one or more aspects.
     * * `None` - indicates that for each aspect of the two entities, there is no `Relationship`
     * * `Same` - indicates that for each aspect of the two entities, the aspects are identical
     * * `Covers` - indicates that the aspects of the two entities are not identical, but that each
     *   aspect of first entity covers the corresponding aspect of the second entity
     * * `Covered` - indicates that the aspects of the two entities are not identical, but that each
     *   aspect of first entity is covered by the corresponding aspect of the second entity
     * * `Collides` - indicates that an aspect of the first entity covers the corresponding aspect
     *   of the second, while a different aspect of the first entity is covered by the second
     */
    enum Relationship {None, Same, Covers, Covered, Collides}

    /**
     * Calculate the relation between two path strings, either of which can be (or end with) the
     * wildcard indicator.
     *
     * @param the first path string, representing an aspect of some first entity
     * @param the second path string, representing the corresponding aspect of some second entity
     *
     * @return one of the [Relationship] values: [Same], [Covers], [Covered], or [None]
     */
    static Relationship relate(String first, String second) {
        if (first == second) {
            return Same;
        }

        if (first == All || first.endsWith('*') && second.startsWith(first[0..<first.size-1])) {
            return Covers;
        }

        if (second == All || second.endsWith('*') && first.startsWith(second[0..<second.size-1])) {
            return Covered;
        }

        return None;
    }

    // ----- API -----------------------------------------------------------------------------------

    /**
     * Determine the [Relationship] between `this` `Permission` and other `Permission`, by combining
     * the `Relationship` between the two `Permission`'s actions and the `Relationship` between the
     * two `Permission`'s targets.
     *
     * The computation of the `Relationship` is calculated as follows:
     *
     *             Target: | Same     Covers   Covered  None
     *             --------+--------- -------- -------- --------
     *     Action: Same    | Same     Covers   Covered  None
     *             Covers  | Covers   Covers   Collides None
     *             Covered | Covered  Collides Covered  None
     *             None    | None     None     None     None
     *
     * @return the `Relationship` between `this` and `that` `Permission`
     */
    Relationship relatedTo(Permission that) {
        return switch (relate(this.action, that.action), relate(this.target, that.target)) {
            case (Same, Same): Same;

            case (None, _):
            case (_, None): None;

            case (Covers, Covered):
            case (Covered, Covers): Collides;

            case (Covers, _):
            case (_, Covers): Covers;

            default: Covered;
        };
    }

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
        return covers(that.action, that.target);
    }

    /**
     * Determine if this Permission covers the specified `action` and `target`.
     *
     * Note that this method explicitly does not consider the [revoke] property; that is the
     * responsibility of the caller. This method only evaluates the [action] and [target].
     *
     * @param action  another action name
     * @param target  another target name
     *
     * @return True iff the scope of `this` Permission fully covers the specified action and target
     */
    Boolean covers(String action, String target) {
        return (this.action == All || this.action == action ||
                this.action.endsWith('*') && action.startsWith(this.action[0..<this.action.size-1]))
            && (this.target == All || this.target == target ||
                this.target.endsWith('*') && target.startsWith(this.target[0..<this.target.size-1]));
    }

    @Override
    String toString() = $"{revoke ? "!" : ""}{action}:{target}";
}
