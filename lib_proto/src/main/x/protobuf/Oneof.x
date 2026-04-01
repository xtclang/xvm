/**
 * Tracks which field is currently set in a protobuf `oneof` group.
 *
 * A `oneof` group is a set of fields where at most one can be set at a time.
 * Setting any field in the group automatically clears the previously set field.
 * On the wire, oneof fields are encoded as regular fields — this class provides
 * the runtime constraint that only one is active.
 *
 * Example usage in an AbstractMessage subclass:
 * ```
 * // oneof result { string name = 4; int32 code = 5; Error error = 6; }
 * Oneof result = new Oneof();
 *
 * String name {
 *     @Override
 *     String get() {
 *         assert Object v := result.get(4) as "name is not set";
 *         return v.as(String);
 *     }
 *
 *     @Override
 *     void set(String value) {
 *         result.set(4, value);
 *     }
 * }
 * ```
 */
class Oneof
        implements Duplicable {

    construct() {
    }

    @Override
    construct(Oneof other) {
        activeField = other.activeField;
        fieldValue  = other.fieldValue;
    }

    /**
     * The field number of the currently set field, or 0 if no field is set.
     */
    private Int activeField = 0;

    /**
     * The value of the currently set field.
     */
    private Object? fieldValue = Null;

    /**
     * @return True if any field in this oneof group is set
     */
    Boolean isSet() {
        return activeField != 0;
    }

    /**
     * @return the field number of the currently set field, or 0 if none
     */
    Int activeFieldNumber.get() {
        return activeField;
    }

    /**
     * Check whether a specific field is the currently active one.
     *
     * @param fieldNumber  the field number to check
     *
     * @return True and the field value if the specified field is set
     */
    conditional Object get(Int fieldNumber) {
        if (activeField == fieldNumber, fieldValue != Null) {
            return True, fieldValue.as(Object);
        }
        return False;
    }

    /**
     * Set a field in this oneof group, clearing any previously set field.
     *
     * @param fieldNumber  the field number to set
     * @param value        the field value
     */
    void set(Int fieldNumber, Object value) {
        activeField = fieldNumber;
        fieldValue  = value;
    }

    /**
     * Clear the oneof group so that no field is set.
     */
    void clear() {
        activeField = 0;
        fieldValue  = Null;
    }

    /**
     * Check whether the given field number belongs to this oneof group
     * (i.e., is one of the valid field numbers for this group).
     *
     * @param fieldNumber   the field number to check
     * @param fieldNumbers  the valid field numbers for this oneof group
     *
     * @return True if the field number is in the group
     */
    static Boolean contains(Int fieldNumber, Int[] fieldNumbers) {
        for (Int fn : fieldNumbers) {
            if (fn == fieldNumber) {
                return True;
            }
        }
        return False;
    }
}
