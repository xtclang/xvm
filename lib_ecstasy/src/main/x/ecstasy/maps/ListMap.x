import iterators.EmptyIterator;

import Array.Mutability;

/**
 * `ListMap` is an implementation of a [Map] on top of an [Array] to maintain the order of
 * insertion.
 *
 * The `ListMap` design is predicated on two general assumptions:
 *
 * * The data structure is either in an initial append-intensive mode in which queries do not
 *   occur, or in post-append query-intensive mode, in which mutations are rare;
 * * Deletions (_particularly_ deletions of any entry other than the most recent entry added)
 *   are _extremely_ rare, and are allowed to be expensive.
 *
 * The `ListMap` uses a brute force, `O(N)` search. If the `ListMap` grows beyond an arbitrary size,
 * and if the `Key` type is [Hashable], then the `ListMap` will automatically create a
 * size-optimized hashing lookup data structure when the first such search occurs.
 */
class ListMap<Key, Value>
        implements Map<Key, Value>
        implements Replicable
        incorporates CopyableMap.ReplicableCopier<Key, Value>
        incorporates conditional ListMapIndex<Key extends immutable Hashable, Value>
        incorporates conditional MapFreezer<Key extends immutable Object, Value extends Shareable> {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a mutable `ListMap` with an optional initial capacity.
     *
     * @param initCapacity  an optional suggested capacity for the `Map`, expressed in terms of the
     *                      number of entries
     */
    @Override
    construct(Int initCapacity = 0) {
        this.keyArray = new Array(initCapacity);
        this.valArray = new Array(initCapacity);
        this.inPlace  = True;
    }

    /**
     * Construct a `ListMap` pre-populated with the specified keys and values.
     *
     * By default, the resulting `ListMap` is persistent, relying on the underlying
     * [Array.Mutability] of `Persistent` or `Constant`. When a persistent `ListMap` is
     * instantiated, it makes defensive copies of the passed arrays if they are not themselves
     * already of the `Persistent` or `Constant` [mutability](Array.mutability), to guarantee that
     * subsequent changes to the passed arrays do not impact the contents of this `ListMap`.
     *
     * When the `ListMap` being constructed with `inPlace==True`, it does _not_ make a defensive
     * copy of the passed arrays. On the first mutation to the contents, if the passed arrays are
     * not of the `Mutable` [mutability](Array.mutability), then the `ListMap` will make a `Mutable`
     * array copy of the passed array(s), as necessary.
     *
     * @param keys     the keys for the map
     * @param vals     the values for the map; a `Null` implies that _all_ the values are `Null`
     * @param inPlace  (optional) `True` indicates that the `ListMap` performs mutations "in place"
     */
    construct(Key[] keys, Value[]? vals, Boolean inPlace = False) {
        assert:arg keys.size == vals?.size;
        assert:arg vals != Null || Value.is(Type<Nullable>);

        if (inPlace) {
            // use the provided arrays (at least until the first mutation)
            this.keyArray = keys;
            this.valArray = vals;
        } else { // persistent ListMap
            (this.keyArray, this.valArray) = ensurePersistent(keys, vals);
            this.checked = True;
        }
        this.inPlace = inPlace;
    } finally {
        // pre-freeze the ListMap if the contents of the map are all already immutable
        if (!inPlace && this.keyArray.is(immutable) && this.valArray.is(immutable)) {
            makeImmutable();
        }
    }

    @Override
    construct(ListMap that) {
        this.keyArray = that.keyArray.toArray(Mutable);
        this.valArray = that.valArray?.toArray(Mutable) : Null;
        this.inPlace  = True;
    }

    // ----- ListMap-specific API ------------------------------------------------------------------

    /**
     * Ensure that the `ListMap` is [inPlace] and not `immutable`.
     *
     * @return a mutable, [inPlace] `ListMap`
     */
    ListMap ensureMutable()
        {
        return inPlace && !this.is(immutable)
                ? this
                : new ListMap(this);
        }

    /**
     * Ensure that the `ListMap` is **not** [inPlace].
     *
     * @param doNotClone  specify `True` to modify `this` `ListMap` instead of creating a new
     *                    `ListMap` with the desired state; normally this parameter would be named
     *                    `inPlace` to mean "make the modification to the `Map` in-place", but that
     *                    name is already being used here to differentiate in-place vs persistent
     *
     * @return a persistent (**not** [inPlace]) `ListMap`
     */
    ListMap ensurePersistent(Boolean doNotClone = False)
        {
        if (!inPlace) {
            return this;
        }

        if (doNotClone) {
            (keyArray, valArray) = ensurePersistent(keyArray, valArray);
            checked = True;
            return this;
        }

        return new ListMap(keyArray, valArray);
        }

    // ----- internal ------------------------------------------------------------------------------

    /**
     * @param keys  an array of `Keys` of any [Array.Mutability]
     * @param vals  an array of `Value` of any [Array.Mutability], or `Null`
     *
     * @return keys  an array of `Keys` of `Persistent` or `Constant` [Array.Mutability]
     * @return vals  an array of `Value` of `Persistent` or `Constant` [Array.Mutability], or `Null`
     */
    static <Key, Value> (Key[] keys, Value[]? vals) ensurePersistent(Key[] keys, Value[]? vals) {
        if (keys.mutability > Persistent || vals?.mutability > Persistent : False) { // <- TODO GG
            if (keys.all(k -> k.is(immutable | service))
                    && vals?.all(v -> v.is(immutable | service)) : True) {
                // both arrays can be safely made as "Constant" mutability without mutating
                // any of their elements; this will copy the array(s) if necessary
                keys = keys.freeze();
                vals = vals == Null || vals.empty ? Null : vals.freeze();
                return keys, vals;
            } else {
                // make sure both arrays are at least "Persistent" mutability (to avoid
                // accidentally freezing any of the objects referenced by the arrays); this
                // will copy the array(s) if necessary
                keys = keys.mutability > Persistent ? keys.toArray(Persistent) : keys;
                vals = vals == Null || vals.empty
                        ? Null
                        : vals.mutability > Persistent ? vals.toArray(Persistent) : vals;
            }
        }
        return keys, vals == Null || vals.empty ? Null : vals;
    }

    /**
     * The list of keys in the map, in order of insertion.
     */
    protected Key[] keyArray;

    /**
     * The values in the map, corresponding by index to [keyArray].
     */
    protected Value[]? valArray;

    /**
     * Set to `True` only after the [Array.mutability] of [keyArray] and [valArray] has been checked.
     */
    private Boolean checked = False;

    /**
     * A counter of the number of items added in-place to the map. Used to detect illegal concurrent
     * modifications.
     */
    protected/private Int appends = 0;

    /**
     * A counter of the number of items deleted in-place from the map. Used to detect illegal
     * concurrent modifications.
     */
    protected/private Int deletes = 0;

    /**
     * Find a key in the map's internal list of keys, returning its location in the list.
     *
     * @param key  the key to find
     *
     * @return True iff the key was found
     * @return the conditional index at which the key was found
     */
    protected conditional Int indexOf(Key key) {
        loop: for (Key eachKey : keyArray) {
            if (eachKey == key) {
                return True, loop.count;
            }
        }
        return False;
    }

    /**
     * This method is invoked before an in-place mutating operation occurs, allowing the `ListMap`
     * to clone the original arrays if necessary.
     */
    protected void mutatingInPlace() {
        if (!checked) {
            keyArray = keyArray.toArray(Mutable, inPlace=True);
            valArray = valArray?.toArray(Mutable, inPlace=True);
            // in theory, since this is a copy-on-write approach, one or both of the arrays that
            // were provided at construction could have been modified since then, so re-check that
            // the key and value counts match
            assert keyArray.size == valArray?.size;
            checked  = True;
        }
    }

    /**
     * Delete the entry at the specified index in the map's internal lists of keys and values.
     *
     * @param index  the index of the entry
     */
    protected void deleteEntryAt(Int index) {
        mutatingInPlace();
        keyArray.delete(index);
        valArray?.delete(index);
        ++deletes;
    }

    /**
     * Append an entry to the end of the map's internal lists of keys and values.
     *
     * @param key    the key to append
     * @param value  the value to append
     */
    protected void appendEntry(Key key, Value value) {
        mutatingInPlace();
        keyArray = keyArray.add(key);
        if (Value[] vals ?= valArray) {
            valArray = vals.add(value);
        } else if (value != Null) {
            // this is the first non-null value, and no storage exists for values!
            valArray = makeNulls(keyArray.size, Mutable).add(value);
        }
        ++appends;
    }

    /**
     * Some operations require that mutations to the containing Map be made without copying the map;
     * this method throws an exception if the Map can not be mutated in-place.
     *
     * @return True
     *
     * @throws ReadOnly if the Map is not Mutable
     */
    protected Boolean verifyInPlace() {
        if (!inPlace) {
            throw new ReadOnly("Map operation requires inPlace==True");
        }
        return True;
    }

    /**
     * Create an `Array` filled with the specified number of `Null` values.
     *
     * @param size        the size of the resulting `Array`
     * @param mutability  (optional) the [Array.Mutability] of the `Array` to create
     *
     * @return a `Value[]` `Array` full of `Nulls`
     */
    protected Value[] makeNulls(Int size, Mutability? mutability = Null) {
        return new Value[](size)
                .fill(Null.as(Value), 0..<size)
                .toArray(mutability, inPlace=True);
    }

    // ----- Map interface -------------------------------------------------------------------------

    @Override
    public/private Boolean inPlace;

    @Override
    conditional Orderer? ordered() = (True, Null);

    @Override
    Int size.get() = keyArray.size;

    @Override
    Boolean contains(Key key) = indexOf(key);

    @Override
    conditional Value get(Key key) {
        if (Int index := indexOf(key)) {
            return True, valArray?[index] : Null.as(Value);
        }
        return False;
    }

    @Override
    Iterator<Entry<Key, Value>> iterator() {
        return new Iterator() {
            Int       index       = 0;
            Int       stop        = 0;
            Int       prevDeletes = 0;
            MapEntry? prevEntry   = Null;

            @Override
            conditional Entry<Key, Value> next() {
                // the immediately previously iterated key is allowed to be deleted
                if (MapEntry entry ?= prevEntry) {
                    if (deletes != prevDeletes) {
                        if (deletes - prevDeletes == 1 && 0 < index < stop && !entry.exists) {
                            --stop;
                            --index;
                            ++prevDeletes;
                        } else {
                            throw new ConcurrentModification();
                        }
                    }

                    if (++index >= stop) {
                        // end of iteration
                        return False;
                    }

                    prevEntry = entry <- entry.advance(index);
                    return True, entry;
                }

                Int size = size;
                if (size == 0)  {
                    // empty map
                    return False;
                }

                // this is the first entry, so create the cursor and configure the iterator
                stop        = size;
                prevDeletes = deletes;
                return True, prevEntry <- new MapEntry();
            }
        };
    }

    @Override
    @Lazy Set<Key> keys.calc() = new Keys();

    @Override
    @Lazy Collection<Value> values.calc() = new Values();

    @Override
    @Lazy Collection<Entry<Key, Value>> entries.calc() = new Entries();

    @Override
    ListMap put(Key key, Value value) {
        if (inPlace) {
            if (Int index := indexOf(key)) {
                mutatingInPlace();
                if (valArray == Null && value != Null) {
                    valArray = makeNulls(keyArray.size, Mutable);
                }
                valArray?[index] = value;
            } else {
                appendEntry(key, value);
            }
            return this;
        } else { // persistent map behavior
            Key[]      keyArray   = this.keyArray;
            Value[]?   valArray   = this.valArray;
            Mutability mutability = keyArray.mutability;
            if (mutability == Constant) {
                if (key.is(Shareable) && value.is(Shareable)) {
                    key   = Freezable.frozen(key);
                    value = Freezable.frozen(value);
                } else {
                    // we can't maintain immutability when adding this key/value pair
                    mutability = Persistent;
                    keyArray   = keyArray.toArray(Persistent);
                    valArray   = valArray?.toArray(Persistent);
                }
            }

            if (Int index := indexOf(key)) {
                if (valArray == Null) {
                    if (value == Null) {
                        return this;
                    }
                    valArray = makeNulls(keyArray.size, mutability);
                }
                return new ListMap(keyArray, valArray.replace(index, value));
            } else {
                if (valArray == Null && value != Null) {
                    valArray = makeNulls(keyArray.size, mutability);
                }
                keyArray = keyArray.add(key);
                valArray = valArray?.add(value);
                return new ListMap(keyArray, valArray);
            }
        }
    }

    @Override
    ListMap putAll(Map<Key, Value> that) {
        if (that.empty) {
            return this;
        }

        if (inPlace) {
            for ((Key key, Value value) : that) {
                put(key, value);
            }
            return this;
        }

        // persistent
        Key[]      oldKeys    = keyArray;
        Value[]?   oldVals    = valArray;
        Key[]      newKeys    = new Array<Key>(Mutable, oldKeys);
        Value[]?   newVals    = oldVals == Null ? Null : new Array<Value>(Mutable, oldVals);
        Mutability mutability = oldKeys.mutability;
        for ((Key key, Value value) : that) {
            if (mutability == Constant) {
                if (key.is(Shareable) && value.is(Shareable)) {
                    key   = Freezable.frozen(key);
                    value = Freezable.frozen(value);
                } else {
                    // we can't maintain immutability when adding this key/value pair
                    mutability = Persistent;
                }
            }
            if (newVals == Null && value != Null) {
                newVals = makeNulls(newKeys.size, Mutable);
            }
            if (Int index := this.indexOf(key)) {
                newVals?[index] = value;
            } else {
                newKeys.add(key);
                newVals?.add(value);
            }
        }
        return new ListMap(newKeys.toArray(mutability, inPlace=True),
                           newVals?.toArray(mutability, inPlace=True) : Null);
    }

    @Override
    ListMap remove(Key key) {
        if (Int index := indexOf(key)) {
            if (inPlace) {
                deleteEntryAt(index);
            } else { // persistent
                Key[]    keys = keyArray.delete(index);
                Value[]? vals = valArray?.delete(index) : Null;
                return new ListMap(keys, vals);
            }
        }
        return this;
    }

    @Override
    conditional ListMap remove(Key key, Value value) {
        if (Int index := indexOf(key)) {
            if (valArray?[index] == value : value == Null) {
                if (inPlace) {
                    deleteEntryAt(index);
                    return True, this;
                } else { // persistent
                    Key[]    keys = keyArray.delete(index);
                    Value[]? vals = valArray?.delete(index) : Null;
                    return True, new ListMap(keys, vals);
                }
            }
        }
        return False;
    }

    @Override
    ListMap clear() {
        Int count = size;
        if (count > 0) {
            if (inPlace) {
                keyArray.clear();
                valArray?.clear();
                deletes += count;
            } else { // persistent
                return new ListMap([], []);
            }
        }
        return this;
    }

    @Override
    <Result> Result process(Key key, function Result (Entry<Key, Value>) compute) {
        return compute(new MapEntry(key));
    }

    // ----- Keys Set ------------------------------------------------------------------------------

    /**
     * A set of keys in the `Map`. This is used to implement the [keys] property.
     */
    protected class Keys
            extends MapKeys<Key, Value>(this.Map) {
        @Override
        Key[] toArray(Mutability? mutability = Null) {
            return keyArray.toArray(mutability);
        }
    }

    // ----- Values Collection ---------------------------------------------------------------------

    /**
     * The collection of values in the `Map`.  This is used to implement the [values] property.
     */
    protected class Values
            extends MapValues<Key, Value>(this.Map) {
        @Override
        Value[] toArray(Mutability? mutability = Null) {
            return valArray?.toArray(mutability) : empty ? [] :  makeNulls(size, mutability);
        }
    }

    // ----- Entries Set ---------------------------------------------------------------------------

    /**
     * The collection of `Map` entries. This is used to implement the [entries] property.
     */
    protected class Entries
            extends MapEntries<Key, Value>(this.Map);

    // ----- Entry implementation ------------------------------------------------------------------

    /**
     * An implementation of [Entry] that can be used as either (1) a cursor over the keys and values
     * in the `ListMap`, or (2) as a single-key reified [Entry].
     *
     * The `MapEntry` uses a fail-fast model for concurrent modification detection. Once the
     * `MapEntry` is constructed (or a cursor [advance] occurs), any changes made to the `ListMap`
     * that do not occur via the same `MapEntry` will cause the `MapEntry` to be in an invalid
     * state, and any subsequent operation on the `MapEntry` will throw ConcurrentModification.
     */
    protected class MapEntry
            implements Entry<Key, Value> {
        /**
         * Construct a `MapEntry` for a single key that may or may not exist in the `ListMap`.
         *
         * @param key  the key for the `Entry`
         */
        protected construct(Key key) {
            this.key    = key;
            this.expect = appends + deletes;
        } finally {
            if (index := indexOf(key)) {
                exists = True;
            } else {
                exists = False;
            }
        }

        /**
         * Construct a `MapEntry` for a single key that does exist in the `ListMap`.
         *
         * @param key     the key for the `Entry`
         * @param index   the internal index of the key
         */
        protected construct(Key key, Int index) {
            this.key    = key;
            this.index  = index;
            this.exists = True;
            this.expect = appends + deletes;
        }

        /**
         * Construct a MapEntry in cursor mode.
         *
         * @param cursor  True to indicate that the Entry will be used in "cursor mode"
         */
        protected construct() {
            construct MapEntry(keyArray[0], 0);
            this.cursor = True;
        }

        /**
         * For an entry in cursor mode, advance the cursor to the specified index in the ListMap.
         *
         * @param key    the key for the entry
         */
        protected MapEntry advance(Int index) {
            assert cursor;
            this.key    = keyArray[index];
            this.index  = index;
            this.exists = True;
            this.expect = appends + deletes;
            return this;
        }

        /**
         * Cursor mode is the ability to be re-used over a number of entries in the Map. The
         * opposite of Cursor mode is single key mode.
         */
        protected/private Boolean cursor;

        /**
         * The index of the key in the ListMap, assuming that the key [exists].
         */
        protected/private Int index = -1;

        /**
         * The expected modification count for the ListMap.
         */
        protected/private Int expect = -1;

        @Override
        public/private Key key;

        @Override
        public/private Boolean exists.get() {
            return verifyNoSurprises() & super();
        }

        @Override
        Value value {
            @Override
            Value get() {
                if (exists) {
                    return valArray?[index] : Null.as(Value);
                }

                throw new OutOfBounds("key=" + key);
            }

            @Override
            void set(Value value) {
                verifyInPlace();
                if (exists) {
                    if (valArray == Null && value != Null) {
                        valArray = makeNulls(keyArray.size, Mutable);
                    }
                    valArray?[index] = value;
                } else if (cursor) {
                    // disallow the entry from being re-added (since it lost its cursor position)
                    throw new ReadOnly();
                } else {
                    appendEntry(key, value);
                    index  = keyArray.size - 1;
                    exists = True;
                    ++expect;
                }
            }
        }

        @Override
        void delete() {
            if (verifyInPlace() & exists) {
                deleteEntryAt(index);
                exists = False;
                ++expect;
            }
        }

        @Override
        MapEntry reify() {
            return cursor && verifyNoSurprises() ? new MapEntry(key, index) : this;
        }

        /**
         * Check the expected modification count for the ListMap against the actual modification
         * count.
         *
         * @return True
         *
         * @throws ConcurrentModification if the Entry is in "cursor" mode and the Map has been
         *                                modified in a manner other than through this entry
         */
        protected Boolean verifyNoSurprises() {
            // verify that no changes have occurred to the underlying map
            if (appends + deletes == expect) {
                return True;
            }

            // in "cursor mode", any unexpected underlying map change is disallowed
            if (cursor) {
                throw new ConcurrentModification();
            }

            // otherwise, "fix" the entry to reflect whatever map changes occurred
            if (index := indexOf(key)) {
                exists = True;
            } else {
                exists = False;
            }
            expect = appends + deletes;
            return True;
        }
    }

    // ----- Freezable -----------------------------------------------------------------------------

    @Override
    immutable ListMap makeImmutable() {
        if (this.is(immutable)) {
            return this;
        }

        this.inPlace = False;
        return super();
    }
}