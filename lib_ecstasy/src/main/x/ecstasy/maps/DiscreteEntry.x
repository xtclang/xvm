import Map.Entry;

/**
 * A simple implementation of a complete [Entry] that is fully self-contained, and independent of
 * any `Map`.
 */
class DiscreteEntry<Key, Value>
        implements Entry<Key, Value>
        incorporates conditional StringableEntry<Key extends Stringable, Value extends Stringable>
        incorporates conditional FreezableEntry<Key extends Shareable, Value extends Shareable> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an existent `DiscreteEntry`.
     *
     * @param key       the `Entry` `Key`
     * @param value     the `Entry` `Value`
     * @param readOnly  (optional) `True` indicates that this `Entry` must not be modifiable
     */
    construct(
            Key     key,
            Value   value,
            Boolean readOnly = False,
            ) {
        assert:arg val.is(Value) || !exists;

        this.key      = key;
        this.val      = value;
        this.exists   = True;
        this.readOnly = readOnly;
    }

    /**
     * Construct a non-existent `DiscreteEntry`.
     *
     * @param key       the `Entry` `Key`
     * @param readOnly  (optional) `True` indicates that this `Entry` must not be modifiable
     */
    construct(
            Key     key,
            Boolean readOnly = False,
            ) {
        this.key      = key;
        this.val      = Null;
        this.exists   = False;
        this.readOnly = readOnly;
    }

    /**
     * Construct a `DiscreteEntry` copied from another `Entry`.
     *
     * @param entry     the `Entry` to copy
     * @param readOnly  (optional) `True` indicates that this `Entry` must not be modifiable
     */
    construct(
            Entry<Key, Value> entry,
            Boolean           readOnly = False,
            ) {
        if (entry.exists) {
            construct DiscreteEntry(entry.key, entry.value, readOnly);
        } else {
            construct DiscreteEntry(entry.key, readOnly);
        }
    }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * If `True`, changes to the `Entry`'s [value] and [exists] (via [delete]) are prevented.
     */
    protected Boolean readOnly;

    /**
     * Internal storage for the `Value`, if one [exists].
     */
    protected Value? val;

    // ----- Entry interface -----------------------------------------------------------------------

    @Override
    public/protected Key key;

    @Override
    public/protected Boolean exists;

    @Override
    Value value {
        @Override
        Value get() {
            if (!exists) {
                throw new OutOfBounds();
            }
            return val ?: assert;
        }

        @Override
        void set(Value value) {
            if (readOnly) {
                throw new ReadOnly();
            }
            val    = value;
            exists = True;
        }
    }

    @Override
    void delete() {
        if (readOnly) {
            throw new ReadOnly();
        }
        if (!exists) {
            throw new OutOfBounds();
        }
        val    = Null;
        exists = False;
    }

    // ----- Freezable implementation --------------------------------------------------------------

    /**
     * Mixin that makes `DiscreteEntry` Freezable if `Key` and `Value` are Shareable.
     */
    static mixin FreezableEntry<Key extends Shareable, Value extends Shareable>
            into DiscreteEntry<Key, Value>
            implements Freezable {

        @Override
        immutable FreezableEntry freeze(Boolean inPlace = False) {
            if (this.is(immutable)) {
                return this;
            }

            Key    key = frozen(key);
            Value? val = frozen(val);
            if (inPlace) {
                this.key      = key;
                this.val      = val;
                this.readOnly = True;
                return this.makeImmutable();
            }

            return exists
                    ? new DiscreteEntry(key, val.as(Value), readOnly=True).makeImmutable()
                    : new DiscreteEntry<Key, Value>(key, readOnly=True).makeImmutable();
        }
    }
}