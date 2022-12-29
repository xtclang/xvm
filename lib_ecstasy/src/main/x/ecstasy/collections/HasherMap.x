import iterators.EmptyIterator;

import maps.KeyEntry;
import maps.EntryValues;


/**
 * HasherMap is a hashed implementation of the Map interface. It uses the supplied [Hasher] to
 * support hashing and comparison for the `Key` type.
 *
 * In theory, hashing provides _on the order of_ `O(1)` access, update, insertion, and deletion
 * time. There are two assumptions behind that "big-O" estimation:
 *
 * * First, we assume that the key class implements its hash function in a manner that will tend to
 *   hash its values in a relatively uniform distribution across the range of possible hash values.
 *
 * * Second, we assume the absence of an explicit denial-of-service attack that is using unique keys
 *   that either hash to the same value, or modulo to the same value (given a predictable modulo).
 *
 * Unfortunately, in the real world, both of the stated assumptions are often invalid. It is
 * particularly difficult to compensate for the failure of a hash function, such as (a worst case
 * scenario) one that returns a constant, e.g. `return 0`. Such a hash function would naturally
 * **and irrecoverably** degrade a hashed implementation from `O(1)` to `O(n)`. This problem is
 * classified as a self-denial-of-service attack by the developer of the key class, and the fix is
 * to implement the hash function correctly (approaching a uniform distribution).
 *
 * The second case is more nefarious, and such attacks have been encountered repeatedly in the wild.
 * While a poor hash function cannot be compensated for, the use of a _prime_ modulo will largely
 * prevent multiple hashes from landing in the same bucket, and if that does occur, the module can
 * be increased to a new prime modulo.
 *
 * This hashed data structure is optimized for either zero or one entry hashing to a particular
 * bucket. To accomplish this, a larger number of buckets than entries will always be maintained;
 * this has a very nominal cost in terms of memory, because even a single entry is always going to
 * be dramatically larger than the bucket that points to it. Further specialization of the data
 * structure is used to defray the costs associated with bucket collisions and poor hash code
 * distributions. Specifically, a specialized node is used to hold arbitrarily large lists of keys
 * and values that share the same hash code, and another specialized node is used to hold an
 * arbitrarily large tree of nodes that hash to the same bucket. The existence of these two
 * specialized nodes allow the general case to be optimized to a bare minimum structure.
 *
 * The iterators provided by this map are stable in presence of structural changes to the map and
 * **will not** throw [ConcurrentModification], return duplicate entries, return entries that have
 * been removed, or skip entries which remain present over the course of iteration. The iterator
 * **may** return entries which were inserted _after_ the creation of the iterator.
 */
class HasherMap<Key, Value>
        implements HasherReplicable<Key>
        implements CopyableMap<Key, Value>
        implements Hashable
        incorporates conditional MapFreezer<Key extends immutable Object, Value extends Shareable>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct the HasherMap with the specified hasher and (optional) initial capacity.
     *
     * @param hasher        the [Hasher] to use
     * @param initCapacity  the number of expected entries
     */
    @Override
    construct(Hasher<Key> hasher, Int initCapacity = 0)
        {
        this.hasher = hasher;

        // allocate the initial capacity
        (Int bucketCount, this.growAt) = calcBucketCount(initCapacity);
        buckets = new HashBucket<Key, Value>?[bucketCount];
        }

    /**
     * Copy constructor from another [Map].
     *
     * @param hasher  the [Hasher] to use
     * @param that    another map to copy the contents from when constructing this HasherMap
     */
    construct(Hasher<Key> hasher, Map<Key, Value> that)
        {
        if (that.is(HasherMap) && hasher == that.hasher)
            {
            // optimization:
            construct HasherMap(that);
            }
        else
            {
            construct HasherMap(hasher, that.size);
            }
        }
    finally // REVIEW GG+CP - wouldn't it be nice if "finally" could capture from the "construct" block?
            // e.g.: Boolean optimized = that.is(HasherMap) && hasher == that.hasher;
        {
        if (!(that.is(HasherMap) && hasher == that.hasher))
            {
            putAll(that);
            }
        }

    @Override
    construct(HasherMap that)
        {
        this.hasher      = that.hasher;
        this.buckets     = new HashBucket<Key, Value>?[that.buckets.size](i -> that.buckets[i]?.duplicate() : Null);
        this.growAt      = that.growAt;
        this.shrinkAt    = that.shrinkAt;
        this.addCount    = that.addCount;
        this.removeCount = that.removeCount;
        }


    // ----- internal state ------------------------------------------------------------------------

    /**
     * The Hasher is the thing that knows how to take a key and provide a hash code, or compare two
     * keys for equality -- even if the keys don't know how to do that themselves.
     */
    public/private Hasher<Key> hasher;

    /**
     * A "fixed" array of hash buckets.
     */
    private HashBucket<Key, Value>?[] buckets;

    /**
     * The size at which the capacity must grow.
     */
    private Int growAt;

    /**
     * The size at which the capacity must shrink.
     */
    private Int shrinkAt = -1;

    /**
     * The number of entries added.
     */
    private Int addCount = 0;

    /**
     * The number of entries removed.
     */
    private Int removeCount = 0;


    // ----- HashBucket structure ------------------------------------------------------------------

    /**
     * Represents one or more key/value pairs. A HashBucket is basically a miniature map, a subset
     * of a hash map that covers at least one key/value pair, and at most one hash bucket.
     */
    protected static @Abstract class HashBucket<Key, Value>
            implements Duplicable
        {
        @Override
        HashBucket duplicate(Boolean shallow=False)
            {
            return super();
            }

        /**
         * Internal bit-packed field.
         */
        private Byte stats;

        /**
         * Indicate that this node is being iterated.
         */
        void enterIteration()
            {
            if (!this.is(immutable))
                {
                Byte count = stats & 0x7F;
                if (count < 100)
                    {
                    stats = stats & 0x80 | count+1;
                    }
                }
            }

        /**
         * Indicate that this node is no longer being iterated.
         */
        void exitIteration()
            {
            if (!this.is(immutable))
                {
                Byte count = stats & 0x7F;
                if (count < 100)
                    {
                    assert count > 0;
                    stats = stats & 0x80 | count-1;
                    }
                }
            }

        /**
         * Determine if the node is currently being iterated.
         */
        HashBucket copyOnWriteIfNecessary()
            {
            if (!this.is(immutable) && stats & 0x7F != 0)
                {
                val result = this.duplicate(shallow=True);
                this.discard();
                return result;
                }

            return this;
            }

        /**
         * Discard the node.
         */
        void discard()
            {
            if (!this.is(immutable))
                {
                stats |= 0x80;
                }
            }

        /**
         * Discard all nodes from here down.
         */
        void discardAll()
            {
            discard();
            }

        /**
         * Determine if the node has been discarded. A discarded node can only be "held" by an
         * iterator that was in the process of iterating the node when another operation attempted
         * to modify the node, and [copyOnWriteIfNecessary] made a new master copy of the node
         * (which then caused this node to be discarded).
         */
        @RO Boolean discarded.get()
            {
            return stats & 0x80 != 0;
            }

        /**
         * True iff this node can represent more than one hash code.
         */
        @RO Boolean isMultiHash.get()
            {
            return False;
            }

        /**
         * The hash code represented by this HashBucket. (If the HashBucket represents more than one
         * hash code, then accessing this property will throw an exception.)
         */
        @RO Int hash;

        /**
         * How many key/value pairs are within this HashBucket.
         */
        @RO Int size;

        /**
         * Obtain n-th key of this node, iff this is not a multi-hash node.
         *
         * @param index
         *
         * @return the key
         *
         * @throws IllegalState if the node is a multi-hash node
         */
        Key keyAt(Int index);

        /**
         * Obtain the n-th value of this node.
         *
         * @param index
         *
         * @return the value
         *
         * @throws IllegalState if the node is a multi-hash node
         */
        Value valueAt(Int index);

        /**
         * Update the n-th value of this node.
         *
         * @param index  the index into this node
         * @param value  the value
         *
         * @throws IllegalState if the node is a multi-hash node
         */
        void update(Int index, Value value);

        /**
         * Obtain the value associated with the specified key.
         */
        conditional Value get(Hasher<Key> hasher, Int hash, Key key);

        /**
         * Modify the value associated with the specified key.
         *
         * @return True iff an entry for the key was not in the map and now is
         * @return the HashBucket to keep instead of this HashBucket; otherwise this HashBucket
         */
        conditional HashBucket! put(Hasher<Key> hasher, Int hash, Key key, Value value);

        /**
         * Remove the specified key/value pairing.
         *
         * @return True iff an entry for the key was in the map and now is not
         * @return the HashBucket or Null to keep instead of this HashBucket; otherwise this HashBucket
         */
        conditional HashBucket!? remove(Hasher<Key> hasher, Int hash, Key key);

        /**
         * Redistribute this HashBucket as part of re-hashing the HasherMap.
         *
         * @param buckets  the new array of HashBuckets being re-hashed into
         */
        void redistribute(HashBucket!?[] buckets)
            {
            Int                     index     = hash % buckets.size;
            HashBucket<Key, Value>? oldBucket = buckets[index];
            if (oldBucket == Null)
                {
                buckets[index] = this;
                }
            else if (HashBucket<Key, Value> newBucket := oldBucket.adopt(this))
                {
                buckets[index] = newBucket;
                }
            }

        /**
         * Add the node to this HashBucket.
         *
         * @param node  the node to adopt
         *
         * @return True iff the HashBucket needs to be replaced
         * @return the new HashBucket to use in lieu of this
         */
        conditional HashBucket! adopt(HashBucket! node)
            {
            return True, new TreeNode(this, node);
            }
        }

    /**
     * Represents a single key/value pairs.
     */
    protected static class SingleNode<Key, Value>(Int hash, Key key, Value value)
            extends HashBucket<Key, Value>
        {
        @Override
        construct(SingleNode that)
            {
            this.hash  = that.hash;
            this.key   = that.key;
            this.value = that.value;
            }

        @Override
        @RO Int size.get()
            {
            return 1;
            }

        @Override
        Key keyAt(Int index)
            {
            return key;
            }

        @Override
        Value valueAt(Int index)
            {
            return value;
            }

        @Override
        void update(Int index, Value value)
            {
            this.value = value;
            }

        @Override
        conditional Value get(Hasher<Key> hasher, Int hash, Key key)
            {
            if (this.hash == hash && hasher.areEqual(this.key, key))
                {
                return True, value;
                }

            return False;
            }

        @Override
        conditional HashBucket<Key, Value> put(Hasher<Key> hasher, Int hash, Key key, Value value)
            {
            if (this.hash == hash)
                {
                if (hasher.areEqual(this.key, key))
                    {
                    this.value = value;
                    return False;
                    }
                else
                    {
                    // replace this single with a list
                    discard();
                    return True, new ListNode<Key, Value>(hash, this.key, this.value, key, value);
                    }
                }
            else
                {
                // replace this single with a tree of two singles
                val that = new SingleNode<Key, Value>(hash, key, value);
                return True, new TreeNode<Key, Value>(this, that);
                }
            }

        @Override
        conditional HashBucket<Key, Value>? remove(Hasher<Key> hasher, Int hash, Key key)
            {
            if (this.hash == hash && hasher.areEqual(this.key, key))
                {
                discard();
                return True, Null;
                }

            return False;
            }
        }

    /**
     * Represents a list of key/value pairs that share a hash code.
     */
    protected static class ListNode<Key, Value>
            extends HashBucket<Key, Value>
        {
        construct(Int hash, Key key1, Value value1, Key key2, Value value2)
            {
            keys = new Key[](2);
            keys.add(key1);
            keys.add(key2);

            values = new Value[](2);
            values.add(value1);
            values.add(value2);
            }

        @Override
        construct(ListNode that)
            {
            this.hash   = that.hash;
            this.keys   = that.keys.clone();
            this.values = that.values.clone();
            }

        /**
         * The keys in the list of entries managed by this node.
         */
        Key[] keys;

        /**
         * The corresponding values in the list of entries managed by this node.
         */
        Value[] values;

        @Override
        Int hash;

        @Override
        @RO Int size.get()
            {
            return keys.size;
            }

        @Override
        Key keyAt(Int index)
            {
            return keys[index];
            }

        @Override
        Value valueAt(Int index)
            {
            return values[index];
            }

        @Override
        void update(Int index, Value value)
            {
            values[index] = value;
            }

        @Override
        conditional Value get(Hasher<Key> hasher, Int hash, Key key)
            {
            if (this.hash == hash, Int index := find(hasher, key))
                {
                return True, values[index];
                }

            return False;
            }

        @Override
        conditional HashBucket<Key, Value> put(Hasher<Key> hasher, Int hash, Key key, Value value)
            {
            if (this.hash == hash)
                {
                if (Int index := find(hasher, key))
                    {
                    values[index] = value;
                    return False;
                    }

                ListNode<Key, Value> result = copyOnWriteIfNecessary();
                result.keys.add(key);
                result.values.add(value);
                return True, result;
                }

            // replace the list with a tree
            val that = new SingleNode<Key, Value>(hash, key, value);
            return True, new TreeNode<Key, Value>(this, that);
            }

        @Override
        conditional HashBucket<Key, Value>? remove(Hasher<Key> hasher, Int hash, Key key)
            {
            if (this.hash == hash, Int index := find(hasher, key))
                {
                Int count = keys.size;
                if (count == 1)
                    {
                    discard();
                    return True, Null;
                    }

                ListNode<Key, Value> result = copyOnWriteIfNecessary();
                Key[]                keys   = result.keys;
                Value[]              values = result.values;
                Int                  last   = count-1;
                if (index != last)
                    {
                    // take the last one in the list, and move it up to the index of the key that
                    // is being removed
                    keys  [index] = keys  [last];
                    values[index] = values[last];
                    }

                // shrink the list by 1 (delete the last, which is assumed efficient for an array)
                keys.delete(last);
                values.delete(last);

                return True, result;
                }

            return False;
            }

        /**
         * Find the specified key in this node.
         *
         * @return True iff the key is found in this node
         * @return the internal index of the key
         */
        protected conditional Int find(Hasher<Key> hasher, Key key)
            {
            EachKey: for (Key each : keys)
                {
                if (hasher.areEqual(each, key))
                    {
                    return True, EachKey.count;
                    }
                }

            return False;
            }
        }

    /**
     * Represents a binary tree of nodes, sorted by hash code.
     */
    protected static class TreeNode<Key, Value>
            extends HashBucket<Key, Value>
        {
        construct (HashBucket<Key, Value> node1, HashBucket<Key, Value> node2)
            {
            nodes = new HashBucket<Key, Value>[](4);
            if (node1.hash > node2.hash)
                {
                nodes.add(node2);
                nodes.add(node1);
                }
            else
                {
                nodes.add(node1);
                nodes.add(node2);
                }
            }

        @Override
        construct(TreeNode that)
            {
            // deep clone the sub nodes
            this.nodes.map(node -> node.duplicate(), new HashBucket<Key, Value>[](that.nodes.size.maxOf(4)));
            }

        /**
         * Internal shallow copy constructor.
         */
        private construct(HashBucket<Key, Value>[] nodes)
            {
            this.nodes = nodes;
            }

        @Override
        TreeNode duplicate(Boolean shallow=False)
            {
            return shallow
                    ? new TreeNode(nodes.clone())
                    : super();
            }

        /**
         * The HashBuckets that (in aggregate) form this HashBucket. Sorted by hash code.
         */
        HashBucket<Key, Value>[] nodes;

        @Override
        @RO Boolean isMultiHash.get()
            {
            return True;
            }

        @Override
        @RO Int hash.get()
            {
            // this must never be called
            assert;
            }

        @Override
        void discardAll()
            {
            nodes.forEach(node -> node.discard());
            }

        @Override
        @RO Int size.get()
            {
            return nodes.map(node -> node.size).reduce(0, (total, add) -> total+add);
            }

        @Override
        Key keyAt(Int index)
            {
            assert;
            }

        @Override
        Value valueAt(Int index)
            {
            assert;
            }

        @Override
        void update(Int index, Value value)
            {
            assert;
            }

        @Override
        conditional Value get(Hasher<Key> hasher, Int hash, Key key)
            {
            if (Int index := findHash(hash))
                {
                return nodes[index].get(hasher, hash, key);
                }

            return False;
            }

        @Override
        conditional HashBucket<Key, Value> put(Hasher<Key> hasher, Int hash, Key key, Value value)
            {
            (Boolean found, Int index) = findHash(hash);
            if (found)
                {
                HashBucket<Key, Value> oldBucket = nodes[index];
                if (HashBucket<Key, Value> newBucket := oldBucket.put(hasher, hash, key, value))
                    {
                    if (&oldBucket != &newBucket)
                        {
                        // note: it is not necessary to check if an iterator is active here, because
                        //       the arity of the tree (i.e. the number of leaf nodes) is not
                        //       changing, and so this tree node doesn't need to be discarded (copy
                        //       on write is not necessary)
                        nodes[index] = newBucket;
                        }
                    return True, this;
                    }
                }
            else
                {
                // create a single node for the new entry, and add that node to the nodes bin-tree
                TreeNode<Key, Value> result = copyOnWriteIfNecessary();
                val newNode = new SingleNode<Key, Value>(hash, key, value);
                result.nodes.insert(index, newNode);
                return True, result;
                }

            return False;
            }

        @Override
        conditional HashBucket<Key, Value>? remove(Hasher<Key> hasher, Int hash, Key key)
            {
            if (Int index := findHash(hash))
                {
                HashBucket<Key, Value> oldBucket = nodes[index];
                if (HashBucket<Key, Value>? newBucket := oldBucket.remove(hasher, hash, key))
                    {
                    if (newBucket == Null)
                        {
                        if (nodes.size == 2)
                            {
                            // we do not keep a tree node of size 1
                            this.discard();
                            return True, nodes[1-index];
                            }

                        TreeNode<Key, Value> result = copyOnWriteIfNecessary();
                        result.nodes.delete(index);
                        return True, result;
                        }
                    else if (&oldBucket != &newBucket)
                        {
                        // not removing a leaf node, so copy on write is not necessary
                        nodes[index] = newBucket;
                        }

                    return True, this;
                    }
                }

            return False;
            }

        @Override
        void redistribute(HashBucket<Key, Value>?[] buckets)
            {
            nodes.forEach(node -> node.redistribute(buckets));
            discard();
            }

        @Override
        conditional HashBucket<Key, Value> adopt(HashBucket<Key, Value> node)
            {
            (Boolean found, Int index) = findHash(node.hash);
            assert !found;
            nodes.insert(index, node);
            return False;
            }

        /**
         * The number of "leaf" buckets (single or list nodes) in the tree.
         */
        Int leafNodeCount.get()
            {
            return nodes.size;
            }

        /**
         * Obtain the specified "leaf" bucket (a single or a list node) in the tree.
         *
         * @param index  the bucket index to obtain
         *
         * @return the specified "leaf" bucket
         */
        HashBucket<Key, Value> leafNodeAt(Int index)
            {
            return nodes[index];
            }

        /**
         * Find the sub-node in the tree that has the specified hash.
         *
         * @return True iff the node with the specified hash is found
         * @return the index where the node was found, or the index at which to insert the node if
         *         it does not exist
         */
        private (Boolean found, Int index) findHash(Int hash)
            {
            return nodes.binarySearch(node -> hash <=> node.hash);
            }
        }

    /**
     * This is the primary means to find a HashBucket in the HasherMap.
     *
     * @param key  the key to find in the map
     *
     * @return True iff the key is in the map
     * @return the HashBucket identified by the key
     */
    protected conditional HashBucket<Key, Value> bucketFor(Int hash)
        {
        if (empty)
            {
            return False;
            }

        Int bucketId = hash % buckets.size;
        if (HashBucket<Key, Value> bucket ?= buckets[bucketId])
            {
            return True, bucket;
            }

        return False;
        }


    // ----- HasherReplicable interface ------------------------------------------------------------

    /**
     * This is the equivalent of the [Replicable] interface, but specific to the HasherMap, because
     * it requires a Hasher for its construction. It allows a HasherMap to replicate itself without
     * having to resort to reflection, and enforces the contract on sub-classes.
     */
    static interface HasherReplicable<Key>
        {
        /**
         * Construct the HasherMap with the specified hasher and (optional) initial capacity.
         *
         * @param hasher        the [Hasher] to use
         * @param initCapacity  the number of expected entries
         */
        construct(Hasher<Key> hasher, Int initCapacity = 0);
        }


    // ----- Duplicable interface ------------------------------------------------------------------

    @Override
    HasherMap duplicate(function (Key, Value)(Key, Value)? transform = Null)
        {
        if (this.is(immutable HasherMap) && transform == Null)
            {
            return this;
            }

        if (transform == Null)
            {
            return this.new(this);
            }

        HasherMap<Key, Value> that = this.new(hasher);
        for ((Key key, Value value) : this)
            {
            (key, value) = transform(key, value);
            that = that.put(key, value);
            }
        return that;
        }


    // ----- Map interface -------------------------------------------------------------------------

    /**
     * The size of the map is maintained internally.
     */
    @Override
    Int size.get()
        {
        return addCount - removeCount;
        }

    @Override
    Boolean empty.get()
        {
        return addCount == removeCount;
        }

    @Override
    Boolean contains(Key key)
        {
        return get(key);
        }

    @Override
    conditional Value get(Key key)
        {
        Int hash = hasher.hashOf(key);
        if (HashBucket<Key, Value> bucket := bucketFor(hash), Value value := bucket.get(hasher, hash, key))
            {
            return True, value;
            }

        return False;
        }

    @Override
    HasherMap put(Key key, Value value)
        {
        Int     hash  = hasher.hashOf(key);
        Int     index = hash % buckets.size;
        Boolean added = False;
        if (HashBucket<Key, Value> oldBucket ?= buckets[index])
            {
            if (HashBucket<Key, Value> newBucket := oldBucket.put(hasher, hash, key, value))
                {
                added = True;
                if (&newBucket != &oldBucket)
                    {
                    buckets[index] = newBucket;
                    }
                }
            }
        else
            {
            buckets[index] = new SingleNode<Key, Value>(hash, key, value);
            added = True;
            }

        if (added)
            {
            ++addCount;
            checkCapacity();
            }

        return this;
        }

    @Override
    HasherMap putAll(Map<Key, Value> that)
        {
        // check the capacity up front (to avoid multiple resizes); the worst case is that we end
        // up a bit bigger than we want
        checkCapacity(size + that.size);
        return super(that);
        }

    @Override
    HasherMap remove(Key key)
        {
        Int     hash    = hasher.hashOf(key);
        Int     index   = hash % buckets.size;
        Boolean removed = False;
        if (HashBucket<Key, Value> oldBucket ?= buckets[index])
            {
            if (HashBucket<Key, Value>? newBucket := oldBucket.remove(hasher, hash, key))
                {
                ++removeCount;
                if (&newBucket != &oldBucket)
                    {
                    buckets[index] = newBucket;
                    }
                }
            }

        return this;
        }

    @Override
    HasherMap clear()
        {
        Int entryCount = size;
        if (entryCount == 0)
            {
            return this;
            }

        buckets.forEach(bucket -> bucket?.discardAll());

        (Int bucketCount, this.growAt, this.shrinkAt) = selectBucketCount(0);
        if (bucketCount == buckets.size)
            {
            buckets.fill(Null);
            }
        else
            {
            buckets = new HashBucket<Key, Value>?[bucketCount];
            }

        removeCount += entryCount;
        assert size == 0;

        return this;
        }

    @Override
    <Result> Result process(Key key, function Result (Entry) compute)
        {
        return compute(reifyEntry(key));
        }

    @Override
    @Lazy public/private Set<Key> keys.calc()
        {
        KeySet keys = new KeySet();
        if (this.HasherMap.is(immutable))
            {
            keys.makeImmutable();
            }
        return keys;
        }

    @Override
    @Lazy public/private Collection<Value> values.calc()
        {
        EntryValues<Key, Value> values = new EntryValues<Key, Value>(this);
        if (this.HasherMap.is(immutable))
            {
            values.makeImmutable();
            }
        return values;
        }

    @Override
    @Lazy public/private Collection<Entry> entries.calc()
        {
        EntrySet entries = new EntrySet();
        if (this.HasherMap.is(immutable))
            {
            entries.makeImmutable();
            }
        return entries;
        }


    // ----- CollectionImpl base class for KeySet and EntrySet -------------------------------------

    /**
     * An abstract representation of a collection of Key or Entry objects in the Map.
     */
    protected class CollectionImpl<Element>
            implements Collection<Element>
        {
        @Override
        Int size.get()
            {
            return this.HasherMap.size;
            }

        @Override
        Iterator<Element> iterator()
            {
            return empty
                    ? Element.emptyIterator
                    : new IteratorImpl();
            }

        /**
         * An iterator over the map's contents, stable in the presence of both concurrent
         * modification and rehashing. Specifically, this iterator **will not**: throw
         * [ConcurrentModification], return duplicate keys, or skip keys which remain present over
         * the course of iteration. The iterator **may** return keys which were added _after_ the
         * creation of the iterator.
         */
        protected class IteratorImpl
                implements Iterator<Element>
            {
            /**
             * Construct an iterator over all of the keys in the HasherMap.
             */
            protected construct()
                {
                oldBucketCount = this.HasherMap.buckets.size;
                }
            finally
                {
                advanceWithinBucketArray();
                }


            // ----- properties ---------------------------------------------------------------

            /**
             * The index of the next hash bucket to process.
             */
            private Int nextBucket = 0;

            /**
             * The current tree-node being iterated, or Null if none.
             */
            private TreeNode<Key, Value>? treeNode = Null;

            /**
             * The count of leaf buckets in the tree node.
             */
            private Int leafCount = 0;

            /**
             * The index of the next leaf bucket in the tree to process.
             */
            private Int nextLeaf = 0;

            /**
             * The current leaf node (single or list) being iterated, or Null if none.
             */
            private HashBucket<Key, Value>? leafNode = Null;

            /**
             * The number of keys within the [leaf node](bucket).
             */
            private Int keyCount = 0;

            /**
             * The index of the next key within the [leaf node](bucket).
             */
            private Int nextKey = 0;

            /**
             * The number of hash buckets in the map at the time that the current bucket was
             * advanced to. This is used to detect when a map re-hash (capacity change) has
             * occurred.
             */
            private Int oldBucketCount;

            /**
             * A function that indicates exclusion of a hash value from the iteration.
             */
            private function Boolean(Int)? exclude = Null;


            // ----- Iterator implementation --------------------------------------------------

            @Override
            conditional Element next()
                {
                if (HashBucket<Key, Value> leafNode ?= this.leafNode)
                    {
                    do
                        {
                        do
                            {
                            while (nextKey < keyCount)
                                {
                                Int index = nextKey++;
                                if (leafNode.discarded && !this.HasherMap.contains(leafNode.keyAt(index)))
                                    {
                                    continue;
                                    }
                                return True, toElement(leafNode, index);
                                }
                            }
                        while (leafNode := advanceWithinTree(leafNode));
                        }
                    while (leafNode := advanceWithinBucketArray());
                    }
                return False;

                /**
                 * Internal method to advance to the next bucket of keys within the current tree
                 * bucket.
                 */
                private conditional HashBucket<Key, Value> advanceWithinTree(HashBucket<Key, Value> leafNode)
                    {
                    leafNode.exitIteration();

                    while (TreeNode<Key, Value> treeNode ?= this.treeNode, nextLeaf < leafCount)
                        {
                        leafNode = treeNode.leafNodeAt(nextLeaf++);
                        if (exclude?(leafNode.hash))
                            {
                            continue;
                            }

                        leafNode.enterIteration();

                        this.leafNode = leafNode;
                        this.keyCount = leafNode.size;
                        this.nextKey  = 0;
                        return True, leafNode;
                        }

                    this.leafNode = Null;
                    return False;
                    }
                }

            /**
             * Internal method to advance to the next bucket of keys within the map's array of
             * buckets.
             */
            private conditional HashBucket<Key, Value> advanceWithinBucketArray()
                {
                if (TreeNode<Key, Value> treeNode ?= this.treeNode)
                    {
                    treeNode.exitIteration();
                    }

                // it's possible that while we were iterating over the previous bucket of keys
                // that the map changed its modulo, which is hugely disruptive because the
                // iterator proceeds linearly through the buckets, which is far different from
                // it going linearly through the 2^64 hash codes
                HasherMap<Key, Value>     map     = this.HasherMap;
                HashBucket<Key, Value>?[] buckets = map.buckets;
                if (buckets.size != oldBucketCount)
                    {
                    // at this point, we have detected a resize of the map, which changes the
                    // number of buckets, and that number is the hash modulo. what happens now
                    // is that we start again from the first bucket in the newly resized map,
                    // but we introduce a filter that ignores all of the hash codes that this
                    // iterator had already successfully iterated
                    Int prevModulo = oldBucketCount;    // local snap-shot (for lambda capture)
                    Int completed  = nextBucket - 1;    // local snap-shot (for lambda capture)
                    if (completed >= 0)
                        {
                        if (function Boolean(Int) oldExclude ?= exclude)
                            {
                            exclude = hash -> hash % prevModulo <= completed || oldExclude(hash);
                            }
                        else
                            {
                            exclude = hash -> hash % prevModulo <= completed;
                            }
                        }

                    // unfortunately, we have to start over at the first bucket, because all of
                    // the buckets have been reshuffled
                    nextBucket     = 0;
                    oldBucketCount = buckets.size;
                    }

                // advance the modulo until we find a modulo that has any keys
                Int bucketCount = buckets.size;
                NextHashBucket: while (nextBucket < bucketCount)
                    {
                    if (HashBucket<Key, Value> bucket ?= buckets[nextBucket++])
                        {
                        if (bucket.is(TreeNode))
                            {
                            HashBucket<Key, Value> leafNode = bucket.leafNodeAt(0);
                            Int                    index    = 1;
                            Int                    count    = bucket.leafNodeCount;

                            CheckExcluded: if (function Boolean(Int) exclude ?= this.exclude)
                                {
                                NextLeafNode: do
                                    {
                                    if (exclude(leafNode.hash))
                                        {
                                        leafNode = bucket.leafNodeAt(index++);
                                        continue NextLeafNode;
                                        }

                                    break CheckExcluded;
                                    }
                                while (index < count);
                                continue NextHashBucket;
                                }

                            this.treeNode  = bucket;
                            this.leafCount = count;
                            this.nextLeaf  = index;
                            bucket.enterIteration();

                            bucket = leafNode;
                            }
                        else
                            {
                            this.treeNode = Null;
                            if (exclude?(bucket.hash))
                                {
                                continue;
                                }
                            }

                        this.leafNode = bucket;
                        this.keyCount = bucket.size;
                        this.nextKey  = 0;
                        bucket.enterIteration();
                        return True, bucket;
                        }
                    }

                return False;
                }

            @Override
            Boolean knownDistinct()
                {
                return True;
                }

            @Override
            conditional Int knownSize()
                {
                return leafNode == Null
                        ? (True, 0)         // already done iterating
                        : False;
                }

            @Override
            (IteratorImpl, IteratorImpl) bifurcate()
                {
                return this, this.clone();
                }


            // ----- internal -----------------------------------------------------------------

            /**
             * Given the specified bucket and index, produce the corresponding element of this
             * collection.
             *
             * @param bucket  the leaf bucket reference
             * @param index   the index into the bucket of the key/value pair
             *
             * @return the corresponding element
             */
            protected Element toElement(HashBucket<Key, Value> bucket, Int index);

            /**
             * Construct a clone of this KeyIterator.
             */
            protected IteratorImpl clone()
                {
                IteratorImpl that  = new IteratorImpl();

                that.nextBucket     = this.nextBucket;
                that.treeNode       = this.treeNode;
                that.leafCount      = this.leafCount;
                that.nextLeaf       = this.nextLeaf;
                that.leafNode       = this.leafNode;
                that.keyCount       = this.keyCount;
                that.nextKey        = this.nextKey;
                that.oldBucketCount = this.oldBucketCount;
                that.exclude        = this.exclude;

                that.treeNode?.enterIteration();
                that.leafNode?.enterIteration();

                return that;
                }
            }
        }


    // ----- KeySet implementation -----------------------------------------------------------------

    /**
     * A representation of all of the HashBucket objects in the Map.
     */
    protected class KeySet
            extends CollectionImpl<Key>
            implements Set<Key>
            incorporates conditional KeySetFreezer<Key extends Shareable>
        {
        @Override
        protected class IteratorImpl
            {
            @Override
            protected Key toElement(HashBucket<Key, Value> bucket, Int index)
                {
                return bucket.keyAt(index);
                }
            }

        @Override
        Boolean contains(Key key)
            {
            return this.HasherMap.contains(key);
            }

        @Override
        KeySet remove(Key key)
            {
            this.HasherMap.remove(key);
            return this;
            }

        /**
         * Conditional Freezable implementation.
         */
        private static mixin KeySetFreezer<Element extends Shareable>
                into HasherMap<Element>.KeySet
                implements Freezable
            {
            @Override
            immutable Freezable+Set<Key> freeze(Boolean inPlace = False)
                {
                if (this.is(immutable))
                    {
                    return this;
                    }

                return new ListSet<Key>(this, size).freeze(True);
                }
            }
        }


    // ----- EntrySet implementation ---------------------------------------------------------------

    /**
     * A representation of all of the Entry objects in the Map.
     */
    protected class EntrySet
            extends CollectionImpl<Entry>
        {
        @Override
        protected class IteratorImpl
            {
            @Override
            protected Entry toElement(HashBucket<Key, Value> bucket, Int index)
                {
                return entry.advance(bucket, index);
                }

            /**
             * The fake entry that gets used over and over during iteration.
             */
            protected/private CursorEntry entry = new CursorEntry();
            }

        @Override
        Boolean contains(Entry entry)
            {
            if (Value value := this.HasherMap.get(entry.key))
                {
                return value == entry.value;
                }
            return False;
            }

        @Override
        EntrySet remove(Entry entry)
            {
            this.HasherMap.remove(entry.key, entry.value);
            return this;
            }
        }


    // ----- Entry implementation ------------------------------------------------------------------

    /**
     * An implementation of Entry that can be used as a cursor over any number of keys, and
     * delegates back to the map for its functionality.
     */
    protected class CursorEntry
            implements Entry
        {
        protected/private @Unassigned HashBucket<Key, Value> bucket;
        protected/private Int index;

        protected CursorEntry advance(HashBucket<Key, Value> bucket, Int index)
            {
            this.bucket = bucket;
            this.index  = index;
            return this;
            }

        @Override
        public Key key.get()
            {
            return bucket.keyAt(index);
            }

        @Override
        public Boolean exists.get()
            {
            return !bucket.discarded || contains(key);
            }

        @Override
        Value value
            {
            @Override
            Value get()
                {
                if (!bucket.discarded)
                    {
                    return bucket.valueAt(index);
                    }

                if (Value value := this.HasherMap.get(key))
                    {
                    return value;
                    }

                throw new OutOfBounds($"entry does not exist for key=\"{key}\"");
                }

            @Override
            void set(Value value)
                {
                if (bucket.discarded)
                    {
                    put(key, value);
                    }
                else
                    {
                    bucket.update(index, value);
                    }
                }
            }

        @Override
        void delete()
            {
            remove(key);
            }

        @Override
        Entry reify()
            {
            return reifyEntry(key);
            }
        }


    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends HasherMap> Int64 hashCode(CompileType value)
        {
        Int64                   hash   = value.size.toInt64();
        Hasher<CompileType.Key> hasher = value.hasher;
        for (CompileType.Key key : value)
            {
            hash ^= hasher.hashOf(key);
            }
        return hash;
        }

    @Override
    static <CompileType extends HasherMap> Boolean equals(CompileType value1, CompileType value2)
        {
        return Map.equals(value1, value2);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Check to see if the HasherMap needs to grow or shrink based on the current capacity need.
     */
    private void checkCapacity()
        {
        Int size = this.size;
        if (size < shrinkAt)
            {
            // we've lost a lot of weight; shrink the number of buckets
            resize(size);
            }
        else if (size > growAt)
            {
            // we're growing, so make some extra room (assume we need at least double the capacity)
            resize(size * 2);
            }
        }

    /**
     * Check to see if the HasherMap needs to grow or shrink based on a planned capacity need.
     *
     * @param plannedSize  the capacity to assume that the map will need
     */
    private void checkCapacity(Int plannedSize)
        {
        // check if a resize is necessary
        if (plannedSize < shrinkAt || plannedSize > growAt)
            {
            // perform the resize of the hash buckets
            resize(plannedSize);
            }
        }

    /**
     * Resize the HasherMap based on the assumed capacity need.
     *
     * @param plannedSize  the capacity to assume that the map will need
     */
    private void resize(Int plannedSize)
        {
        (Int bucketCount, Int growAt, Int shrinkAt) = selectBucketCount(plannedSize);
        HashBucket<Key, Value>?[] oldBuckets = buckets;
        HashBucket<Key, Value>?[] newBuckets = new HashBucket<Key, Value>?[bucketCount];
        oldBuckets.forEach(bucket -> bucket?.redistribute(newBuckets));

        this.buckets  = newBuckets;
        this.growAt   = growAt;
        this.shrinkAt = shrinkAt;
        }

    /**
     * Instantiate a reified entry, which must be a child of the map.
     */
    private Entry reifyEntry(Key key)
        {
        return new @KeyEntry(key) Entry() {};
        }

    /**
     * Select a desired number of buckets to use for the specified entry capacity.
     *
     * @param capacity  the number of entries to be able to manage efficiently
     *
     * @return the suggested number of buckets to achieve the specified capacity, and the
     *         suggested grow and shrink thresholds
     */
    protected (Int bucketCount, Int growAt, Int shrinkAt) selectBucketCount(Int capacity)
        {
        return calcBucketCount(capacity);
        }

    /**
     * Compute a desired number of buckets to use for the specified entry capacity.
     *
     * @param capacity  the number of entries to be able to manage efficiently
     *
     * @return the suggested number of buckets to achieve the specified capacity, and the
     *         suggested grow and shrink thresholds
     */
    static (Int bucketCount, Int growAt, Int shrinkAt) calcBucketCount(Int capacity)
        {
        assert capacity >= 0;

        // shoot for 20% empty buckets (i.e. 50% oversize)
        Int target = capacity + (capacity / 2) + 15;

        (_, Int index) = PRIMES.binarySearch(target);
        Int64 bucketCount = (index < PRIMES.size ? PRIMES[index] : target).toInt64();

        // shrink when falls below 20% capacity
        Int shrinkThreshold = index <= 8 ? -1 : ((bucketCount >>> 2) - (bucketCount >>> 5) - (bucketCount >>> 6));

        // grow when around 80% capacity
        Int growThreshold = bucketCount - (bucketCount >>> 2) + (bucketCount >>> 5) + (bucketCount >>> 6);

        return bucketCount, growThreshold, shrinkThreshold;
        }

    /**
     * Primes used for bucket array sizes (to ensure a prime modulo).
     */
    static Int[] PRIMES =
        [
        7, 13, 23, 37, 47, 61, 79, 107, 137, 181, 229, 283, 349, 419, 499, 599, 727, 863, 1013,
        1187, 1399, 1697, 2039, 2503, 3253, 4027, 5113, 6679, 8999, 11987, 16381, 21023, 28351,
        39719, 65521, 99991, 149993, 262139, 524269, 1048571, 2097143, 4194301, 8388593, 16777213,
        33554393, 67108859, 134217689, 268435399, 536870909, 1073741789, 2147483647, 4294967291,
        8589934583, 17179869143, 34359738337, 68719476731, 137438953447, 274877906899, 549755813881
        ];
    }