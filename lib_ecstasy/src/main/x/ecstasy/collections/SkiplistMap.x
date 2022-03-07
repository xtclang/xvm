import maps.EntryValues;
import maps.OrderedMapSlice;

/**
 * The SkiplistMap is an OrderedMap implementation using a "Skip List" data structure.
 *
 * A skip list is a data structure that has logN average time for data retrieval, insertion,
 * update, and deletion. It behaves like a balanced binary tree, yet without the costs normally
 * associated with maintaining a balanced structure.
 */
class SkiplistMap<Key extends Orderable, Value>
        implements OrderedMap<Key, Value>
        implements Replicable
        incorporates CopyableMap.ReplicableCopier<Key, Value>
        incorporates conditional MapFreezer<Key extends immutable, Value extends Shareable>
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a SkiplistMap, with an optional initial capacity and an optional non-natural
     * ordering.
     *
     * @param initialCapacity  the initial capacity, in terms of the number of expected entries
     * @param orderer          the Orderer for this Map, or `Null` to use natural order
     */
    construct(Int initialCapacity = 0, Orderer? orderer = Null)
        {
        if (orderer == Null)
            {
            assert orderer := Key.ordered();
            }

        this.compare      = orderer;
        this.initCapacity = initialCapacity;
        }
    finally
        {
        clear();
        }

    /**
     * [Duplicable] constructor.
     */
    construct(SkiplistMap<Key, Value> that)
        {
        construct SkiplistMap(that.size, that.compare);
        }
    finally
        {
        // this could be optimized
        putAll(that);
        }


    // ----- internal state ------------------------------------------------------------------------

    /**
     * The actual Orderer used internally.
     */
    protected/private Orderer compare;

    /**
     * Storage for the nodes. The actual storage may be Null, but if it is, it should never be
     * requested, and thus it is asserted.
     */
    protected IndexStore nodes.get()
        {
        return actualNodes ?: assert;
        }
    private IndexStore? actualNodes;

    /**
     * Storage for the keys. The actual storage may be Null, but if it is, it should never be
     * requested, and thus it is asserted.
     */
    protected ElementStore<Key> keyStore.get()
        {
        return actualKeys ?: assert;
        }
    private ElementStore<Key>? actualKeys;

    /**
     * Storage for the values. The actual storage may be Null, but if it is, it should never be
     * requested, and thus it is asserted.
     */
    protected ElementStore<Value> valueStore.get()
        {
        return actualValues ?: assert;
        }
    private ElementStore<Value>? actualValues;

    /**
     * The initial capacity to allocate, in terms of entries.
     */
    protected/private Int initCapacity;

    /**
     * Structural modification count. Does not include non-structural modifications, such as an
     * entry value being replaced with another value.
     */
    protected/private Int modCount;

    /**
     * The value of [modCount] when the cache result was set.
     */
    private Int cacheModCount = -1;

    /**
     * The cached result (the nodes in the work list) relate to a search for this key.
     */
    private @Unassigned Key cacheKey;

    /**
     * True iff the cached result represents a miss instead of a hit.
     */
    private Boolean cacheMiss;


    // ----- Map interface -------------------------------------------------------------------------

    @Override
    public/protected Int size;

    @Override
    @RO Boolean empty.get()
        {
        return size == 0;
        }

    @Override
    @Lazy public/private OrderedSet<Key> keys.calc()
        {
        KeySet keys = new KeySet();
        if (this.SkiplistMap.is(immutable))
            {
            keys.makeImmutable();
            }
        return keys;
        }

    @Override
    @Lazy public/private Collection<Value> values.calc()
        {
        EntryValues<Key, Value> values = new EntryValues<Key, Value>(this);
        if (this.SkiplistMap.is(immutable))
            {
            values.makeImmutable();
            }
        return values;
        }

    @Override
    @Lazy public/private Collection<Entry> entries.calc()
        {
        EntrySet entries = new EntrySet();
        if (this.SkiplistMap.is(immutable))
            {
            entries.makeImmutable();
            }
        return entries;
        }

    @Override
    Boolean contains(Key key)
        {
        return !empty && findNode(key);
        }

    @Override
    conditional Value get(Key key)
        {
        if (!empty, (Int node, Int height) := findNode(key))
            {
            return True, valueStore.load(node, height);
            }
        return False;
        }

    @Override
    SkiplistMap put(Key key, Value value)
        {
        do
            {
            IndexStore nodes    = ensureNodes();
            Boolean    upgraded = False;
            if ((Int node, Int height) := findNode(key, mutationWork()))
                {
                // note: do NOT increment modCount for a value replace, because there are no
                //       structural changes to the skiplist
                valueStore.replace(node, height, value);
                }
            else
                {
                // flip a coin to figure out how much "height" the new node will have
                private Random rnd = new numbers.PseudoRandom();
                height = nodes.maxHeight.minOf(rnd.int().trailingZeroCount+1);

                // create the new node
                if (node := nodes.alloc(height))
                    {
                    // configure the node
                    keyStore  .add(node, height, key  );
                    valueStore.add(node, height, value);

                    // add the node to the map
                    linkWorkTo(node, height);
                    ++size;
                    ++modCount; // a new node signifies a structural change
                    }
                else
                    {
                    // if the storage is full, expand the storage
                    upgradeNodes();
                    upgraded = True;
                    }
                }
            }
        while (upgraded);

        return this;
        }

    @Override
    SkiplistMap remove(Key key)
        {
        if (!empty, (Int removeNode, Int removeHeight) := findNode(key, mutationWork(), findFully=True))
            {
            // unlink the node
            unlinkWork(removeNode, removeHeight);

            // release the storage
            keyStore  .release(removeNode, removeHeight);
            valueStore.release(removeNode, removeHeight);
            nodes.free(removeNode, removeHeight);

            --size;
            ++modCount;
            }
        return this;
        }

    @Override
    SkiplistMap clear()
        {
        actualNodes  = Null;
        actualKeys   = Null;
        actualValues = Null;
        size         = 0;

        ++modCount;
        return this;
        }


    // ----- OrderedMap interface ------------------------------------------------------------------

    @Override
    conditional Orderer ordered()
        {
        return True, compare;
        }

    @Override
    conditional Key first()
        {
        if (empty)
            {
            return False;
            }

        IndexStore nodes  = this.nodes;
        Int        node   = nodes.getIndex(nodes.headNode, nodes.maxHeight, 0);
        return True, keyStore.load(node, nodes.heightOf(node));
        }

    @Override
    conditional Key last()
        {
        if (empty)
            {
            return False;
            }

        // jump as fast and far as we can until we reach the end of the skip list
        IndexStore nodes  = this.nodes;
        Int        nil    = nodes.nil;
        Int        node   = nodes.headNode;
        Int        height = nodes.headNode;
        Int        level  = height - 1;
        while (True)
            {
            Int next = nodes.getIndex(node, height, level);
            if (next == nil)
                {
                if (level == 0)
                    {
                    // we have reached the end of the lowest level linked list
                    assert node != nodes.headNode;
                    return True, keyStore.load(node, height);
                    }

                --level;
                }
            else
                {
                node   = next;
                height = nodes.heightOf(node);
                }
            }
        }

    @Override
    conditional Key next(Key key)
        {
        if (empty)
            {
            return False;
            }

        IndexStore nodes = this.nodes;
        Int        nil   = nodes.nil;

        Int  node;
        Int  height;
        Work work = readWriteWork();
        if ((node, height) := findNode(key, work))
            {
            // the specified key exists in the skip list map
            }
        else
            {
            // the work list contains the nodes that the key would be inserted after
            node   = work.getWork(0);
            height = nodes.heightOf(node);

            // there is always a valid insert-after node (although it might be the head node);
            // because the key was not found, all of the work nodes will have been initialized
            assert node != nil;
            }

        // return the next node
        node = nodes.getIndex(node, height, 0);
        return node == nil
                ? False
                : (True, keyStore.load(node, nodes.heightOf(node)));
        }

    @Override
    conditional Key prev(Key key)
        {
        if (empty)
            {
            return False;
            }

        Work work = readWriteWork();
        findNode(key, work, findFully=True);

        // the work list contains the nodes that precedes the key, or that the key would be inserted
        // key was not already in the map
        IndexStore nodes = this.nodes;
        Int        prev  = work.getWork(0);
        return prev == nodes.headNode
                ? False
                : (True, keyStore.load(prev, nodes.heightOf(prev)));
        }

    @Override
    conditional Key ceiling(Key key)
        {
        if (empty)
            {
            return False;
            }

        Work work = readWriteWork();
        if (findNode(key, work))
            {
            // the specified key does exist in the skip list map
            return True, key;
            }

        // the work list contains the nodes that the key would be inserted after
        Int prev = work.getWork(0);

        // return the next node
        IndexStore nodes = this.nodes;
        Int        next  = nodes.getIndex(prev, nodes.heightOf(prev), 0);
        return next == nodes.nil
                ? False
                : (True, keyStore.load(next, nodes.heightOf(next)));
        }

    @Override
    conditional Key floor(Key key)
        {
        if (empty)
            {
            return False;
            }

        Work work = readWriteWork();
        if (findNode(key, work))
            {
            // the specified key does exist in the skip list map
            return True, key;
            }

        // the work list contains the nodes that the key would be inserted after
        Int        prev  = work.getWork(0);
        IndexStore nodes = this.nodes;
        return prev == nodes.headNode
                ? False
                : (True, keyStore.load(prev, nodes.heightOf(prev)));
        }


    // ----- Sliceable interface -------------------------------------------------------------------

    @Override
    @Op("[..]") OrderedMap<Key, Value> slice(Range<Key> indexes)
        {
        return new OrderedMapSlice(this, indexes);
        }

    @Override
    @Op("[[..]]") OrderedMap<Key, Value> sliceInclusive(Range<Index> indexes)
        {
        return slice(indexes.ensureInclusive());
        }

    @Override
    @Op("[[..)]") OrderedMap<Key, Value> sliceExclusive(Range<Index> indexes)
        {
        return slice(indexes.ensureExclusive());
        }

    @Override
    OrderedMap<Key, Value> reify()
        {
        return this;
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
            return outer.size;
            }

        @Override
        Iterator<Element> iterator()
            {
            return empty
                    ? Element.emptyIterator
                    : new IteratorImpl();
            }

        /**
         * An iterator implementation with the following guarantees:
         *
         * * Resilient to underlying changes in the map, including additions and removals;
         * * Resilient to map resizing;
         * * Iterates in the underlying map's order;
         * * Regardless of the order of changes, does not ever emit the same element twice;
         * * Regardless of the order of changes, does not ever emit an element that is no longer
         *   present in the underlying map;
         * * For elements added to the underlying map, those that occur in the order before the
         *   most recently emitted element will never be emitted, and those that occur in the order
         *   after the most recently emitted element _will_ be emitted.
         */
        protected class IteratorImpl
                implements Iterator<Element>
            {
            construct()
                {
                if (outer.empty)
                    {
                    finished = True;
                    }
                }


            // ----- properties ---------------------------------------------------------------

            /**
             * Once iteration has started, this is the previously iterated node id.
             */
            protected/private Int prevNode;

            /**
             * Once iteration has started, this is the previously iterated node height.
             */
            protected/private Int prevHeight;

            /**
             * Once iteration has started, this is the previously iterated key.
             */
            @Unassigned
            protected/private Key prevKey;

            /**
             * Set to true once iteration has begun.
             */
            protected/private Boolean started;

            /**
             * Set to true once the iterator has been exhausted.
             */
            protected/private Boolean finished.set(Boolean done)
                {
                // make sure that the iterator has been marked as having started if it is finished
                if (done)
                    {
                    started = True;
                    }

                super(done);
                }

            /**
             * Expected modification count. If the map changes, we reset the iterator to start after
             * the previously iterated key.
             */
            protected/private Int expectedCount = -1;


            // ----- Iterator interface -------------------------------------------------------

            @Override
            conditional Element next()
                {
                if (finished)
                    {
                    return False;
                    }

                IndexStore nodes = this.SkiplistMap.nodes;

                if (started)
                    {
                    // make sure that the skip list map's structure has not changed
                    if (modCount != expectedCount)
                        {
                        // iteration has already begun, so we have a record of the last key that we
                        // iterated; re-initialize this iterator to begin iterations after that key
                        Work work = readWriteWork();
                        if (!((prevNode, prevHeight) := this.SkiplistMap.findNode(prevKey, work)))
                            {
                            // we did not find the previous key, but the work area is set to the
                            // nodes after which that key would be inserted, so use those as a
                            // starting point
                            prevNode   = work.getWork(0);
                            prevHeight = nodes.heightOf(prevNode);
                            }

                        // the iterator is now adjusted to be in sync with reality
                        expectedCount = modCount;
                        }
                    }
                else
                    {
                    prevNode      = nodes.headNode;
                    prevHeight    = nodes.maxHeight;
                    expectedCount = modCount;
                    started       = True;
                    }

                // verify that the iterator can advance to the next node
                Int next = nodes.getIndex(prevNode, prevHeight, 0);
                if (next == nodes.nil)
                    {
                    finished = True;
                    return False;
                    }

                // store that next node off so that the iterator will know what it already iterated
                prevNode   = next;
                prevHeight = nodes.heightOf(next);
                Key key    = keyStore.load(prevNode, prevHeight);
                prevKey    = key;

                return True, toElement(key, prevNode, prevHeight);
                }

            @Override
            Boolean knownDistinct()
                {
                return True;
                }

            @Override
            conditional Int knownSize()
                {
                if (!started)
                    {
                    return True, outer.size;
                    }

                if (finished)
                    {
                    return True, 0;
                    }

                return False;
                }

            @Override
            (IteratorImpl, IteratorImpl) bifurcate()
                {
                return finished
                        ? (this, this)
                        : (this, clone());
                }


            // ----- internal -----------------------------------------------------------------

            /**
             * Given the specified key and skip list node, produce the corresponding element of this
             * collection.
             *
             * @param key     the key associated with the node
             * @param node    the node index
             * @param height  the node height
             *
             * @return the corresponding element
             */
            protected Element toElement(Key key, Int node, Int height);

            /**
             * Copy constructor.
             */
            protected IteratorImpl clone()
                {
                IteratorImpl that  = new IteratorImpl();

                that.prevNode      = this.prevNode;
                that.prevHeight    = this.prevHeight;
                that.started       = this.started;
                that.finished      = this.finished;
                that.expectedCount = this.expectedCount;

                if (started)
                    {
                    that.prevKey = this.prevKey;
                    }

                return that;
                }
            }
        }


    // ----- KeySet implementation -----------------------------------------------------------------

    /**
     * A representation of all of the Keys in the Map.
     */
    protected class KeySet
            extends CollectionImpl<Key>
            implements OrderedSet<Key>
            incorporates conditional KeySetFreezer<Key extends Shareable>
        {
        @Override
        protected class IteratorImpl
            {
            @Override
            protected Key toElement(Key key, Int node, Int height)
                {
                return key;
                }
            }

        @Override
        conditional Orderer ordered()
            {
            return True, this.SkiplistMap.compare;
            }

        @Override
        Boolean contains(Key key)
            {
            return this.SkiplistMap.contains(key);
            }

        @Override
        KeySet remove(Key key)
            {
            this.SkiplistMap.remove(key);
            return this;
            }

        /**
         * Conditional Freezable implementation.
         */
        private static mixin KeySetFreezer<Element extends Orderable+Shareable>
                into SkiplistMap<Element>.KeySet
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
            implements Collection<Entry>
        {
        @Override
        protected class IteratorImpl
            {
            @Override
            protected Entry toElement(Key key, Int node, Int height)
                {
                return entry.advance(prevKey.as(Key), prevNode, prevHeight);
                }

            /**
             * The fake entry that gets used over and over during iteration.
             */
            protected/private CursorEntry entry = new CursorEntry();
            }

        @Override
        Boolean contains(Entry entry)
            {
            if (Value value := this.SkiplistMap.get(entry.key))
                {
                return value == entry.value;
                }
            return False;
            }

        @Override
        EntrySet remove(Entry entry)
            {
            this.SkiplistMap.remove(entry.key, entry.value);
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
        @Override
        public/private @Unassigned Key key;

        protected/private @Unassigned Int node;
        protected/private @Unassigned Int height;
        protected/private @Unassigned Int expectedCount;

        protected CursorEntry advance(Key key, Int node, Int height)
            {
            this.key           = key;
            this.node          = node;
            this.height        = height;
            this.expectedCount = this.SkiplistMap.modCount;
            this.exists        = True;
            return this;
            }

        @Override
        public/protected Boolean exists.get()
            {
            // if the map changed, verify that the key still exists
            if (modCount != expectedCount)
                {
                if ((node, height) := findNode(key))
                    {
                    exists = True;
                    }
                else
                    {
                    exists = False;
                    }

                expectedCount = modCount;
                }

            return super();
            }

        @Override
        Value value
            {
            @Override
            Value get()
                {
                if (exists)
                    {
                    return valueStore.load(node, height);
                    }
                else
                    {
                    throw new OutOfBounds($"entry does not exist for key=\"{key}\"");
                    }
                }

            @Override
            void set(Value value)
                {
                if (exists)
                    {
                    valueStore.replace(node, height, value);
                    }
                else
                    {
                    this.SkiplistMap.put(key, value);
                    }
                }
            }

        @Override
        void delete()
            {
            if (exists)
                {
                this.SkiplistMap.remove(key);
                }
            }

        @Override
        SkiplistMap<Key, Value>.Entry reify()
            {
            return reifyEntry(key);
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Find the node containing the specified key, and leave the work list pointing to the
     * node (or to where the node _would_ be, if it does not exist).
     *
     * @param key        the key to search for
     * @param work       the Work structure to hold the found location in
     * @param findFully  pass True to identify (in the work area) the node that precedes the found
     *                   node at each and every level
     *
     * @return True iff the key is in the map
     * @return (conditional) the node containing the key
     * @return (conditional) the height of the node
     */
    protected conditional (Int node, Int height) findNode(Key key, Work? work = Null, Boolean findFully = False)
        {
        Boolean    found      = False;
        IndexStore nodes      = this.nodes;
        Int        fromNode   = nodes.headNode;
        Int        fromHeight = nodes.maxHeight;
        Int        nil        = nodes.nil;
        Int        node       = nil;
        Int        height     = nil;
        Int        level      = fromHeight-1;

        if (work == Null)
            {
            work = writeOnlyWork();
            }
        else
            {
            // check if the cached result is valid
            CheckCache: if (modCount == cacheModCount)
                {
                switch (key <=> cacheKey)
                    {
                    case Lesser:
                        // the cache location is past where we are searching, so we have to start
                        // searching from the very beginning of the skip list
                        break;

                    case Equal:
                        // same exact key that we searched for the previous call to this method
                        if (cacheMiss)
                            {
                            // the previous findNode call resulted in a miss on this same key
                            return False;
                            }

                        // we have found the key, and the lowest level index that points to non-nil
                        // is pointing at the node with the key
                        found = True;
                        for (level : [0..fromHeight))
                            {
                            fromNode = work.getWork(level);
                            if (fromNode != nil)
                                {
                                fromHeight = nodes.heightOf(fromNode);
                                node       = nodes.getIndex(fromNode, fromHeight, level);
                                height     = nodes.heightOf(node);
                                assert keyStore.load(node, height) == key;
                                if (level == 0 || !findFully)
                                    {
                                    // it was already found fully, or we don't need to find fully
                                    return True, node, height;
                                    }

                                // we found the node with the key, but we still need to make sure
                                // that every work list index from this level down is set (aka "find
                                // fully")
                                break CheckCache;
                                }
                            }
                        assert;

                    case Greater:
                        // we can start searching from the cache location instead of from the
                        // beginning, but we need to determine which level; the answer is whichever
                        // level has a next pointer that points to a node that isn't past the key
                        // that we're finding
                        Int prevNode = -1;
                        for (level : fromHeight-1..0)
                            {
                            fromNode = work.getWork(level);
                            assert fromNode != nil;
                            if (fromNode != prevNode)
                                {
                                fromHeight = nodes.heightOf(fromNode);
                                }

                            node = nodes.getIndex(fromNode, fromHeight, level);
                            if (node == nil)
                                {
                                // nothing beyond this in the list at this level; drop to the next
                                // level
                                continue;
                                }
                            height   = nodes.heightOf(node);
                            prevNode = fromNode;

                            switch (key <=> keyStore.load(node, height))
                                {
                                case Lesser:
                                    // the key that we're looking for comes before the next key in
                                    // the linked list at this level, so drop down another level
                                    break;

                                case Equal:
                                    // this is unusual, but we accidentally found the key that we
                                    // were looking for in the cache of information that was used to
                                    // find a lesser key (obviously which was subsequently found at
                                    // a lower level); the rest of the work
                                    found = True;
                                    break CheckCache;

                                case Greater:
                                    // we can follow this level's list to get closer to the key that
                                    // we're looking for
                                    fromNode   = node;
                                    fromHeight = height;
                                    break CheckCache;
                                }
                            }
                        return False;
                    }
                }
            }

        // this is the main "find" algorithm, which is always used, unless it gets short-circuited
        // by finding the key using the cache information (above)
        if (!found)
            {
            Loop: do
                {
                node = nodes.getIndex(fromNode, fromHeight, level);
                if (node == nil)
                    {
                    // the "from node" is at the end of its linked list for this level; to find the
                    // node we're looking for, drop to the next lower level and follow its linked
                    // list
                    work.setWork(level, fromNode);
                    --level;
                    }
                else
                    {
                    // compare the key from the node with the key being searched for
                    height = nodes.heightOf(node);
                    switch (compare(keyStore.load(node, height), key))
                        {
                        case Lesser:
                            // the node's key comes before the desired key, so advance to the next
                            // node in this list
                            fromNode   = node;
                            fromHeight = height;
                            break;

                        case Equal:
                            // set the remainder of the work pointers to the node(s) that point to
                            // the node that we found
                            work.setWork(level, fromNode);
                            found = True;
                            break Loop;

                        case Greater:
                            // the node's key comes after the key we're looking for, so we can't
                            // follow the current level's linked list any further; drop to the next
                            // level
                            work.setWork(level, fromNode);
                            --level;
                            break;
                        }
                    }
                }
            while (level >= 0);
            }

        if (!this.is(immutable))
            {
            // update the cache
            cacheModCount = modCount;
            cacheKey      = key;
            cacheMiss     = !found;
            }

        if (!found)
            {
            return False;
            }

        // the key was found, but the remainder of the work indexes still need to be set up
        // correctly; if we're finding fully, then we have to find the node at each remaining level
        // that points to the found node; otherwise, those remaining levels are just nil'd out
        if (findFully)
            {
            while (--level >= 0)
                {
                Int nextNode = nodes.getIndex(fromNode, fromHeight, level);
                while (nextNode != node)
                    {
                    assert nextNode != nil;
                    fromNode   = nextNode;
                    fromHeight = nodes.heightOf(fromNode);
                    nextNode   = nodes.getIndex(fromNode, fromHeight, level);
                    }
                work.setWork(level, fromNode);
                }
            }
        else
            {
            while (--level >= 0)
                {
                work.setWork(level, nil);
                }
            }

        return True, node, height;
        }

    /**
     * Whatever nodes are specified in the "work" area, link in the specified node after those
     * nodes.
     *
     * This method assumes that `work == nodes`; the mutability of the SkiplistMap must have already
     * been verified.
     *
     * @param node    the node index (its identity)
     * @param height  the height of the node, which is the number of indexes it holds
     */
    protected void linkWorkTo(Int node, Int height)
        {
        IndexStore nodes = this.nodes;
        for (Int level : [0..height))
            {
            Int fromNode   = nodes.getWork(level);
            Int fromHeight = nodes.heightOf(fromNode);
            Int nextNode   = nodes.getIndex(fromNode, fromHeight, level);
            nodes.setIndex(fromNode, fromHeight, level, node);
            nodes.setIndex(node, height, level, nextNode);
            }
        }

    /**
     * Whatever nodes are specified in the "work" area, unlink from the specified node, and relink
     * to whatever nodes follow it.
     *
     * This method assumes that `work == nodes`; the mutability of the SkiplistMap must have already
     * been verified.
     *
     * @param removeNode    the node index to remove
     * @param removeHeight  the height of the node being removed
     */
    protected void unlinkWork(Int removeNode, Int removeHeight)
        {
        IndexStore nodes = this.nodes;
        for (Int level : [0..removeHeight))
            {
            Int fromNode   = nodes.getWork(level);
            Int fromHeight = nodes.heightOf(fromNode);
            Int nextNode   = nodes.getIndex(removeNode, removeHeight, level);
            nodes.setIndex(fromNode, fromHeight, level, nextNode);
            }
        }

    /**
     * Return the IndexStore, or create one if none exists.
     *
     * @return an IndexStore
     */
    protected IndexStore ensureNodes()
        {
        IndexStore? nodes = this.actualNodes;
        if (nodes == Null)
            {
            // assume that we'll start with the smallest possible structure
            Type<IntNumber> indexType = Int8;

            Int initCapacity = this.initCapacity;
            if (initCapacity > 0)
                {
                // make a guess at what structure is required for the specified number of entries;
                // overhead is 3x max height, and the average node has a key, a value, and 2 indexes
                indexType = IndexStore.selectType(initCapacity, 2);
                }

            // create the key and value stores
            ElementStore<Key>   keyStore;
            ElementStore<Value> valueStore;
            while (True)
                {
                keyStore   = createElementStore(indexType, Key, initCapacity);
                valueStore = createElementStore(indexType, Value, initCapacity);

                // double check that the index type supports a large enough capacity
                if (initCapacity <= 0 || IndexStore.estimateElements(indexType, initCapacity,
                        keyStore.height + valueStore.height) < IndexStore.indexLimit(indexType))
                    {
                    break;
                    }

                indexType = IndexStore.nextBigger(indexType);
                }

            // create the storage for the skip list nodes
            nodes = createIndexStore(indexType, initCapacity, keyStore, valueStore);

            // store off all of the new structures
            this.actualNodes  = nodes;
            this.actualKeys   = keyStore;
            this.actualValues = valueStore;
            this.initCapacity = 0;
            }

        return nodes;
        }

    /**
     * Grow the storage for the skip list nodes.
     */
    protected void upgradeNodes()
        {
        // determine the current size of the index, and double it
        ElementStore<Key>   oldKeys   = keyStore;
        ElementStore<Value> oldValues = valueStore;
        IndexStore          oldNodes  = nodes;
        Int                 oldNil    = oldNodes.nil;

        // create the new storage
        Type<IntNumber>     newType   = IndexStore.nextBigger(oldNodes.Index);
        Int                 capacity  = size + (size >>> 2);                // 25% larger
        ElementStore<Key>   newKeys   = createElementStore(newType, Key, capacity);
        ElementStore<Value> newValues = createElementStore(newType, Value, capacity);
        IndexStore          newNodes  = createIndexStore(newType, capacity, newKeys, newValues);
        Int                 newNil    = newNodes.nil;

        // we'll track the node that we're linking new nodes from, one at each level of height
        Int   maxHeight  = newNodes.maxHeight;
        Int[] prevNode   = new Int[maxHeight];
        Int[] prevHeight = new Int[maxHeight];

        // each starts from the head
        prevNode  .fill(newNodes.headNode);
        prevHeight.fill(maxHeight);

        // to copy all of the data from the old to the new, we will follow the "0 level" linked
        // list of all nodes in the old index store
        Int count   = 0;
        Int oldNode = oldNodes.getIndex(oldNodes.headNode, oldNodes.maxHeight, 0);
        while (oldNode != oldNil)
            {
            ++count;

            // load the data from the old store
            Int   oldHeight = oldNodes.heightOf(oldNode);
            Key   key       = oldKeys  .load(oldNode, oldHeight);
            Value val       = oldValues.load(oldNode, oldHeight);

            // create and populate the new node
            Int newHeight = count.trailingZeroCount + 1;
            assert Int newNode := newNodes.alloc(newHeight);
            newKeys  .add(newNode, newHeight, key);
            newValues.add(newNode, newHeight, val);

            // link it to the end of the linked lists (one link for each height of the new node)
            for (Int level : [0..newHeight))
                {
                newNodes.setIndex(prevNode[level], prevHeight[level], level, newNode);
                prevNode  [level] = newNode;
                prevHeight[level] = newHeight;
                }

            // advance to next entry
            oldNode = oldNodes.getIndex(oldNode, oldHeight, 0);
            }

        // terminate the linked lists
        for (Int level : [0..maxHeight))
            {
            newNodes.setIndex(prevNode[level], prevHeight[level], level, newNil);
            }

        // double-check that we copied the right number of nodes
        assert (count == size);

        this.actualNodes   = newNodes;
        this.actualKeys    = newKeys;
        this.actualValues  = newValues;
        this.cacheModCount = -1;            // cache is obviously destroyed by a resize

        // treat the changing-of-the-nodes as a modification; this allows anything that is caching
        // structural information to realize that they need to look again at the underlying data
        // structures
        ++modCount;
        }

    /**
     * Create an IndexStore that can be used to store the skiplist data, and configure the
     * key and value ElementStore objects to use the new IndexStore.
     *
     * @param indexType     the type for the Index stored in the IndexStore
     * @param initCapacity  the expected number of entries
     * @param keyStore      the storage for the keys of the SkiplistMap
     * @param valueStore    the storage for the values of the SkiplistMap
     *
     * @return the new storage for the nodes of the skiplist
     */
    protected IndexStore createIndexStore(
            Type<IntNumber>     indexType,
            Int                 initCapacity,
            ElementStore<Key>   keyStore,
            ElementStore<Value> valueStore,
            )
        {
        // calculate the total value height (in terms of number of "Index" values)
        Int valueHeight = keyStore.height + valueStore.height;

        // allocate the Index store
        IndexStore nodes = switch (indexType.DataType)
            {
            case Int8:  new IndexStore8 (initCapacity, valueHeight);
            case Int16: new IndexStore16(initCapacity, valueHeight);
            case Int32: new IndexStore32(initCapacity, valueHeight);
            case Int64: new IndexStore64(initCapacity, valueHeight);
            default: assert as $"unsupported type: {indexType.DataType}";
            };

        // bind the key and value stores to the new IndexStore
        keyStore.configure(nodes, 0);
        valueStore.configure(nodes, keyStore.height);

        return nodes;
        }

    /**
     * Create an ElementStore that can be used to store the specified type of element.
     *
     * @param indexType     the type of the index that will be used in the IndexStore (probably
     *                      not yet instantiated)
     * @param elementType   the type of the element that will be stored in the ElementStore
     * @param initCapacity  the expected number of entries
     *
     * @return the new storage for the specified element type
     */
    protected <NewIndex extends IntNumber, Element> ElementStore<Element> createElementStore(
            Type<NewIndex> indexType,
            Type<Element>  elementType,
            Int            initCapacity,
            )
        {
        if (Element.is(Type<Nullable>))
            {
            return NullStore.INSTANCE;
            }

        switch (elementType)
            {
            // the numeric types ...
            case Int8  , UInt8  , @Unchecked Int8  , @Unchecked UInt8  :
            case Int16 , UInt16 , @Unchecked Int16 , @Unchecked UInt16 :
            case Int32 , UInt32 , @Unchecked Int32 , @Unchecked UInt32 :
            case Int64 , UInt64 , @Unchecked Int64 , @Unchecked UInt64 :
            case Int128, UInt128, @Unchecked Int128, @Unchecked UInt128:
            case Dec32, Dec64, Dec128:
            case BFloat16, Float16, Float32, Float64, Float128:
            // and the nullable forms ... TODO CP "case" does not allow "T?"
            case Nullable|Int8  , Nullable|UInt8  , Nullable|@Unchecked Int8  , Nullable|@Unchecked UInt8  :
            case Nullable|Int16 , Nullable|UInt16 , Nullable|@Unchecked Int16 , Nullable|@Unchecked UInt16 :
            case Nullable|Int32 , Nullable|UInt32 , Nullable|@Unchecked Int32 , Nullable|@Unchecked UInt32 :
            case Nullable|Int64 , Nullable|UInt64 , Nullable|@Unchecked Int64 , Nullable|@Unchecked UInt64 :
            case Nullable|Int128, Nullable|UInt128, Nullable|@Unchecked Int128, Nullable|@Unchecked UInt128:
            case Nullable|Dec32, Nullable|Dec64, Nullable|Dec128:
            case Nullable|BFloat16, Nullable|Float16, Nullable|Float32, Nullable|Float64, Nullable|Float128:
                return new NumberStore<elementType.DataType, indexType.DataType>();

            case Bit,     Nullable|Bit:
            case Boolean, Nullable|Boolean:
            case Nibble,  Nullable|Nibble:
                return new SingleIndexStore<elementType.DataType, indexType.DataType>();

            case Char, Nullable|Char:
                return new CharStore<elementType.DataType, indexType.DataType>();

            default:
                // check if the type is an Enum type (or "Enum?" type)
                if (elementType.is(Type<Enum>))
                    {
                    return new EnumStore<elementType.DataType, indexType.DataType>();
                    }
                if (Type notNullPart := isNullable(elementType), notNullPart.is(Type<Enum>))
                    {
                    return new EnumStore<elementType.DataType, indexType.DataType>();
                    }

                return new ExternalStore<elementType.DataType, indexType.DataType>(initCapacity);
            }
        }

    /**
     * Using a fairly strict definition of a "Nullable" type, specifically the one used by the
     * compiler when compiling "T?" for some type "T", determine if the passed type matches that
     * form, and if it does, return the underlying "T".
     *
     * @param t  a type that may be Nullable by this function's strict definition
     *
     * @return True iff the type is Nullable by this function's strict definition
     * @return (conditional) the underlying type
     */
    private static conditional Type isNullable(Type t)
        {
        if ((Type t1, Type t2) := t.relational(), t.form == Intersection && t1 == Nullable)
            {
            return True, t2;
            }

        return False;
        }

    /**
     * Instantiate a reified entry, which must be a child of the map.
     */
    private Entry reifyEntry(Key key)
        {
        return new @maps.KeyEntry(key) Entry() {};
        }

    /**
     * Dump the contents of the SkiplistMap data structure.
     *
     * @param log
     * @param desc
     */
    void dump(Log log, String? desc = Null)
        {
        log.add($"SkiplistMap<{Key}, {Value}> size={size}: {desc ?: ""}");
        actualNodes?.dump(log)            : log.add("- No IndexStore");
        actualKeys?.dump(log, "keys")     : log.add("- No ElementStore for keys");
        actualValues?.dump(log, "values") : log.add("- No ElementStore for values");
        }


    // ----- IndexStore ----------------------------------------------------------------------------

    /**
     * Holds data for skiplist nodes. The nodes are not materialized as objects; rather, they are
     * simply subsections of a single array, with an associated [ExternalStore] if the node's
     * value cannot be stored in the same array.
     *
     * To minimize the size of the data, the height of each node is variable, and the _width_ of
     * the node (the size of the "pointers", which are just indexes into the underlying array) is
     * specified by the `Index` type. Supported widths are `Int8`, `Int16`, `Int32`, and `Int64`.
     *
     * When the underlying array grows to a size that the `Index` type will not be able to address
     * all of its elements, then calls to [alloc] will return `False`. The caller should then create
     * a new IndexStore with a larger 'Index' type, and copy all of the data into it.
     *
     * Any fixed number of values (the [valueHeight]) can be is stored in each node, each as one
     * additional `Index` elements. They are accessed via [getValue] and [setValue].
     *
     * The underlying array of Index is composed of a header, followed by variable length nodes,
     * concatenated after the header. The header is composed of a work list, a head node, and a free
     * list (each of maxHeight Index elements). Then each "node" is composed of 1-maxHeight elements
     * to store the "next node" pointers, and 1+ elements to hold the key, and 0+ elements to hold
     * the value. The node "height" is the number of "next node" indexes that it has; this can be
     * determined by looking at the elements of the node in sequence; the first negative value is
     * the last "next node" index, and hence implies the height of the node.
     */
    protected static class IndexStore<Index extends IntNumber>
            implements Work
        {
        assert()
            {
            assert Index == Int8 || Index == Int16 || Index == Int32 || Index == Int64;
            }

        /**
         * Construct the IndexStore.
         *
         * @param capacity     the expected number of nodes, or 0 to start empty
         * @param valueHeight  the number of elements necessary to hold the key plus the value
         */
        construct(Int capacity, Int valueHeight)
            {
            assert valueHeight >= 1;

            // underlying storage is a growable array; reserve space for the header size and assume
            // an average of 2 "skip" pointers per node (1 + 1/2 + 1/4 + 1/8 + ...)
            this.valueHeight = valueHeight;
            this.elements    = new Index[](3*(sizeOf(Index)-1) + (capacity+1)*(2+valueHeight));
            }
        finally
            {
            // worklist, headnode, and freelist are stored consecutively at the start of the array
            elements.fill(toIndex(nil), [0..3*maxHeight));

            // the last index of a node must be negative, and the headnode is treated as a node
            // (except that it has no "values"); the head node is located at
            // elements[maxHeight..2*maxHeight), i.e. it is the second chunk of the "header"
            elements[2*maxHeight-1] = toIndex(-nil);
            }


        // ----- properties -------------------------------------------------------------------

        /**
         * The underlying storage.
         */
        protected/private Index[] elements;

        /**
         * The head node index.
         */
        @RO Int headNode.get()
            {
            // the head node is located immediately following the work list and immediately before
            // the free list; all three of these sections are maxHeight in size, so the head node
            // is located at `index==maxHeight`
            return maxHeight;
            }

        /**
         * The free list index.
         */
        @RO Int freeList.get()
            {
            // the free list is located immediately following the work list and head node; all
            // three of these sections are maxHeight in size, so the free list is located at
            // `index==maxHeight*2`
            return maxHeight*2;
            }

        /**
         * The maximum number of indexes in an allocation.
         */
        @RO Int maxHeight.get()
            {
            return sizeOf(Index) - 1;
            }

        /**
         * The number of array elements used to hold a `Value`.
         *
         * Since a `Value` is some form of IntNumber, and the Index is some form of IntNumber, the
         * internal array is a uniform array of the `Index` type, and some of those elements are
         * used to store values of the `Value` type. This increases cache locality and decreases the
         * number of allocations and the amount of memory used, at a cost of complexity.
         */
        Int valueHeight;

        /**
         * The `nil` value used in this IndexStore.
         */
        @Abstract @RO Int nil;


        // ----- node management --------------------------------------------------------------

        /**
         * Allocate a node with the specified "height" (number of indexes). The node will contain
         * additional "height" to store a value.
         *
         * When a node is allocated, each of its indexes and its value must be configured before it
         * can be used.
         *
         * @param height  the height of the node, which is the number of indexes it holds
         *
         * @return True iff the node was able to be allocated; False if a larger IndexStore is
         *         necessary
         * @return node  (conditional) the node index, used as the node's identity
         */
        conditional (Int node) alloc(Int height)
            {
            assert height >= 1 && height <= maxHeight; // assert:test ?
            Int size = height + valueHeight;

            // check the free list first
            Int freeList = this.freeList + height - 1;
            Int node     = elements[freeList].abs().toInt64();
            if (node == nil)
                {
                node = elements.size;
                if (node + size >= nil)
                    {
                    return False;
                    }

                elements.fill(toIndex(nil), [node..node+size));
                }
            else
                {
                elements[freeList] = elements[node];
                }
            return True, node;
            }

        /**
         * Free a previously allocated node.
         *
         * @param node    the node index previously returned from [alloc]
         * @param height  the height of the node, which is the number of indexes it holds
         */
        void free(Int node, Int height=0)
            {
            if (height == 0)
                {
                height = heightOf(node);
                }
            assert height > 0 && height <= maxHeight;

            // use the node to store the next free list item, then add it to the free list
            Int freeList       = this.freeList + height - 1;
            elements[node]     = height == 1 ? -elements[freeList] : elements[freeList];
            elements[freeList] = toIndex(node);
            }

        /**
         * Examine a node to determine its "height" (number of indexes).
         *
         * @param node  the node index (its identity)
         *
         * @return  the height of the node, which is the number of indexes it holds
         */
        Int heightOf(Int node)
            {
            assert node != nil;

            if (node == headNode)
                {
                return maxHeight;
                }

            Int i = node;
            while (elements[i++].toInt64() >= 0)
                {
                }

            Int height = i - node;
            assert height <= maxHeight;
            return height;
            }


        // ----- node contents ----------------------------------------------------------------

        /**
         * Get the specified index (ie skiplist pointer) from the specified node.
         *
         * @param node    the node index (its identity)
         * @param height  the height of the node, which is the number of indexes it holds
         * @param i       the zero-based index being requested, `(0 <= i < height)`
         *
         * @return  the specified index from the node
         */
        Int getIndex(Int node, Int height, Int i)
            {
            assert node > 0 && height > 0 && i >= 0 && i < height;
            Int index = elements[node+i].toInt64();
            return (i < height-1 ? index : -index);
            }

        /**
         * Store the specified index (ie skiplist pointer) into the specified node.
         *
         * @param node    the node index (its identity)
         * @param height  the height of the node, which is the number of indexes it holds
         * @param i       the zero-based index being requested, `(0 <= i < height)`
         * @param index   the index (ie skiplist pointer) to store in the node
         */
        void setIndex(Int node, Int height, Int i, Int index)
            {
            assert node > 0 && height > 0 && i >= 0 && i < height && index > 0;
            elements[node+i] = toIndex(i < height-1 ? index : -index);
            }

        /**
         * Get the specified value from the specified node.
         *
         * @param node    the node index (its identity)
         * @param height  the height of the node, which is the number of indexes it holds
         * @param i       the zero-based index of the value being requested, `(0 <= i < valueHeight)`
         *
         * @return the specified value
         */
        Int getValue(Int node, Int height, Int i)
            {
            assert node > 0 && height > 0 && i >= 0 && i < valueHeight;
            return elements[node+height+i].toInt64();
            }

        /**
         * Store the specified value into the specified node.
         *
         * @param node    the node index (its identity)
         * @param height  the height of the node, which is the number of indexes it holds
         * @param i       the zero-based index of the value being requested, `(0 <= i < valueHeight)`
         * @param value   the value to store in the node
         */
        void setValue(Int node, Int height, Int i, Int value)
            {
            assert node > 0 && height > 0 && i >= 0 && i < valueHeight;
            elements[node+height+i] = toIndex(value);
            }

        /**
         * Get the specified index(es) from the specified node, as a byte array.
         *
         * @param node         the node index (its identity)
         * @param height       the height of the node, which is the number of indexes it holds
         * @param valueOffset  the index offset assigned to the ElementStore to hold the value
         * @param valueHeight  the height of the ElementStore
         *
         * @return the specified value elements, as a read/write byte array
         */
        Byte[] valueAsBytes(Int node, Int height, Int valueOffset, Int valueHeight)
            {
            assert node > 0 && height > 0 && valueOffset >= 0 && valueHeight > 0
                    && valueOffset+valueHeight <= this.valueHeight;

            Int start = node+height+valueOffset;
            Int end   = start+valueHeight;
            return elements[start..end).asByteArray();
            }

        @Override
        Int getWork(Int i)
            {
            assert i >= 0 && i < maxHeight;
            return elements[i].toInt64();
            }

        @Override
        void setWork(Int i, Int n)
            {
            assert i >= 0 && i < maxHeight && (n == nil || n >= 0 && n <= elements.size);
            elements[i] = toIndex(n);
            }

        /**
         * Obtain the freelist index.
         *
         * @param i  in the range `0 <= i < maxHeight`, where 0 is the lowest height (corresponding
         *           to the linked list of all nodes) and `maxHeight-1` is the highest height (such
         *           that the fewest nodes have this height)
         *
         * @return the freelist head for the specified height `i`
         */
        Int getFree(Int i)
            {
            assert i >= 0 && i < maxHeight;
            return elements[2*maxHeight+i].toInt64();
            }

        /**
         * Store a freelist index.
         *
         * @param i  in the range `0 <= i < maxHeight`, where 0 is the lowest height (corresponding
         *           to the linked list of all nodes) and `maxHeight-1` is the highest height (such
         *           that the fewest nodes having this height)
         * @param n  the freelist head for the specified height `i`
         */
        void setFree(Int i, Int n)
            {
            assert i >= 0 && i < maxHeight && (n == nil || n >= 0 && n <= elements.size);
            elements[2*maxHeight+i] = toIndex(n);
            }


        // ----- helpers ----------------------------------------------------------------------

        /**
         * Convert the specified `Int` value to an `Index`.
         *
         * @param n  an `Int` value
         *
         * @return the corresponding `Index` value
         */
        Index toIndex(Int n);

        /**
         * Select the appropriate index type for the specified number of nodes.
         *
         * @param capacity     the desired number of nodes
         * @param valueHeight  the number of indexes that will be used to store the key and value
         *
         * @return an IntNumber type, one of Int8, Int16, Int32, or Int64
         */
        static Type<IntNumber> selectType(Int capacity, Int valueHeight)
            {
            Type<IntNumber> indexType = Int8;
            while (estimateElements(indexType, capacity, valueHeight) > indexLimit(indexType))
                {
                indexType = nextBigger(indexType);
                }

            return indexType;
            }

        /**
         * For upgrading index size, choose the next bigger IntNumber type.
         *
         * @param indexType  an IntNumber type, one of Int8, Int16, Int32, or Int64
         *
         * @return an IntNumber type, one of Int8, Int16, Int32, or Int64
         */
        static Type<IntNumber> nextBigger(Type<IntNumber> indexType)
            {
            return switch (indexType)
                {
                case Int8:  Int16;
                case Int16: Int32;
                case Int32: Int64;
                case Int64: assert; // nothing bigger
                default:    assert;
                };
            }

        /**
         * For downgrading an index size, choose the next smaller IntNumber type.
         *
         * @param indexType  an IntNumber type, one of Int8, Int16, Int32, or Int64
         *
         * @return an IntNumber type, one of Int8, Int16, Int32, or Int64
         */
        static Type<IntNumber> nextSmaller(Type<IntNumber> indexType)
            {
            return switch (indexType)
                {
                case Int8:  assert; // nothing bigger
                case Int16: Int8;
                case Int32: Int16;
                case Int64: Int32;
                default:    assert;
                };
            }

        /**
         * For an index size, obtain its size in bits.
         *
         * @param indexType  an IntNumber type, one of Int8, Int16, Int32, or Int64
         *
         * @return an IntNumber type, one of Int8, Int16, Int32, or Int64
         */
        static Int sizeOf(Type<IntNumber> indexType)
            {
            return switch (indexType)
                {
                case Int8:  8;
                case Int16: 16;
                case Int32: 32;
                case Int64: 64;
                default:    assert;
                };
            }

        /**
         * For a given number of nodes, estimate the number of indexes required in the underlying
         * array.
         *
         * @param indexType    an IntNumber type, one of Int8, Int16, Int32, or Int64
         * @param capacity     the number of nodes
         * @param valueHeight  the number of elements used to store the key and value
         *
         * @return the number of array storage elements required
         */
        static Int estimateElements(Type<IntNumber> indexType, Int capacity, Int valueHeight)
            {
            // overhead is 3 groups of indexes of max height (work, head, free)
            // assumption is an average of 2 indexes per node, because the random distribution
            // should cause 1/2 to use 1 index, 1/4 to use 2, 1/8 to use 3, ...
            return sizeOf(indexType) * 3 + capacity * (2 + valueHeight);
            }

        /**
         * For a given index type, determine the largest storage array possible.
         *
         * @param indexType  an IntNumber type, one of Int8, Int16, Int32, or Int64
         *
         * @return the maximum number of array elements
         */
        static Int indexLimit(Type<IntNumber> indexType)
            {
            return switch (indexType)
                {
                case Int8:  Int8 .maxvalue;
                case Int16: Int16.maxvalue;
                case Int32: Int32.maxvalue;
                case Int64: Int64.maxvalue;
                default:    assert;
                };
            }

        /**
         * Display a textual dump of the contents of the IndexStore.
         *
         * @param log   the log to dump to
         * @param desc  the short description of the ElementStore
         */
        void dump(Log log, String? desc = Null)
            {
            log.add(
                    $|{(desc ?: "IndexStore")}
                     | maxHeight={maxHeight}, headNode={headNode}, freeList={freeList},\
                     | valueHeight={valueHeight}, nil={nil}, elements.size={elements.size}
                    );

            function String(Int) toStr = n -> n == nil ? "nil" : n.toString();

            log.add("     work head free");
            log.add("     ==== ==== ====");
            for (Int i : [0..maxHeight))
                {
                Int work  = getWork(i);
                Int index = getIndex(headNode, maxHeight, i);
                Int free  = getFree(i);

                log.add(
                    $|[{i.toString().rightJustify(2)}]\
                     | {toStr(work ).rightJustify(4)}\
                     | {toStr(index).rightJustify(4)}\
                     | {toStr(free ).rightJustify(4)}
                    );
                }
            log.add("nodes:");
            Int count = 0;
            Int node  = maxHeight * 3;
            while (node < elements.size)
                {
                Int height = heightOf(node);
                StringBuffer sb = new StringBuffer();
                sb += $"[{node}] height={height}, indexes=[";

                for (Int i : [0..height))
                    {
                    if (i > 0)
                        {
                        sb += ", ";
                        }
                    sb += toStr(getIndex(node, height, i));
                    }

                sb += "], values=[";

                for (Int i : [0..valueHeight))
                    {
                    if (i > 0)
                        {
                        sb += ", ";
                        }
                    sb += getValue(node, height, i);
                    }

                sb += "]";
                log.add(sb);

                node += height + valueHeight;
                ++count;
                }
            log.add($"(total={count})");
            }
        }

    /**
     * A concrete implementation of IndexStore using 8-bit elements.
     */
    protected static class IndexStore8(Int capacity, Int valueHeight)
            extends IndexStore<Int8>(capacity, valueHeight)
        {
        @Override Int  nil.get()      {return Int8.maxvalue;}
        @Override Int8 toIndex(Int n) {return n.toInt8();}
        }

    /**
     * A concrete implementation of IndexStore using 16-bit elements.
     */
    protected static class IndexStore16(Int capacity, Int valueHeight)
            extends IndexStore<Int16>(capacity, valueHeight)
        {
        @Override Int   nil.get()      {return Int16.maxvalue;}
        @Override Int16 toIndex(Int n) {return n.toInt16();}
        }

    /**
     * A concrete implementation of IndexStore using 32-bit elements.
     */
    protected static class IndexStore32(Int capacity, Int valueHeight)
            extends IndexStore<Int32>(capacity, valueHeight)
        {
        @Override Int   nil.get()      {return Int32.maxvalue;}
        @Override Int32 toIndex(Int n) {return n.toInt32();}
        }

    /**
     * A concrete implementation of IndexStore using 64-bit elements.
     */
    protected static class IndexStore64(Int capacity, Int valueHeight)
            extends IndexStore<Int64>(capacity, valueHeight)
        {
        @Override Int   nil.get()      {return Int64.maxvalue;}
        @Override Int64 toIndex(Int n) {return n;}
        }


    // ----- Work space ----------------------------------------------------------------------------

    /**
     * Represents a "work" node, which points to some "found" or "next" node.
     */
    protected static interface Work
        {
        /**
         * Obtain the worklist item.
         *
         * @param i  in the range `0 <= i < maxHeight`, where 0 is the lowest height (corresponding
         *           to the linked list of all nodes) and `maxHeight-1` is the highest height (such
         *           that the fewest nodes have this height)
         *
         * @return the previously stored worklist item at the specified height `i`
         */
        Int getWork(Int i);

        /**
         * Store a worklist item.
         *
         * @param i  in the range `0 <= i < maxHeight`, where 0 is the lowest height (corresponding
         *           to the linked list of all nodes) and `maxHeight-1` is the highest height (such
         *           that the fewest nodes have this height)
         * @param n  the index to store in the worklist at the specified height `i`
         */
        void setWork(Int i, Int n);
        }

    /**
     * A node that contains only work info.
     */
    protected static class WorkNode
            implements Work
        {
        protected construct(Int maxHeight)
            {
            this.indexes = new Int[maxHeight];
            }

        protected Int[] indexes;

        @Override
        Int getWork(Int i)
            {
            return indexes[i];
            }

        @Override
        void setWork(Int i, Int n)
            {
            indexes[i] = n;
            }
        }

    /**
     * A node that silently ignores work info, and asserts if an attempt is made to retrieve the
     * previously ignored work info.
     */
    protected static Work FakeWork = new Work()
        {
        @Override
        Int getWork(Int i)
            {
            assert;
            }

        @Override
        void setWork(Int i, Int n)
            {
            }
        };

    /**
     * @return a read/write Work object that can be used for mutating operations on the SkiplistMap
     */
    protected Work mutationWork()
        {
        return this.is(immutable SkiplistMap)
                ? throw new ReadOnly()
                : nodes;
        }

    /**
     * @return a read/write Work object that can be used whether or not the SkiplistMap is mutable
     */
    protected Work readWriteWork()
        {
        return this.is(immutable SkiplistMap)
                ? new WorkNode(nodes.maxHeight)
                : nodes;
        }

    /**
     * @return a write-only Work object that can be used whether or not the SkiplistMap is mutable
     */
    protected Work writeOnlyWork()
        {
        return this.is(immutable SkiplistMap)
                ? FakeWork
                : nodes;
        }


    // ----- ElementStore --------------------------------------------------------------------------

    /**
     * Represents a means of storing elements associated with nodes.
     */
    protected static interface ElementStore<Element, Index extends IntNumber>
        {
        /**
         * The number of indexes used by the values stored by this ElementStore.
         */
        @RO Int height;

        /**
         * After the IndexStore has been configured, the ElementStore is configured to use the
         * IndexStore.
         *
         * @param nodes        the IndexStore
         * @param valueOffset  the zero-based offset within a node's value section for this
         *                     ElementStore
         */
        void configure(IndexStore nodes, Int valueOffset);

        /**
         * Load a value from the ElementStore that is associated with the specified node.
         *
         * @param node    the node that contains the information about the value
         * @param height  the node height
         *
         * @return the value for that node
         */
        Element load(Int node, Int height);

        /**
         * Allocate storage in the ElementStore for a new value, and store that value associated
         * with the specified node.
         *
         * @param node    the node to associate with the value
         * @param height  the node height
         * @param e       the value to store, associated with the node
         */
        void add(Int node, Int height, Element e);

        /**
         * Replace a value associated with the specified node with a new value.
         *
         * @param node    the node to associate with the value
         * @param height  the node height
         * @param e       the new value to store
         */
        void replace(Int node, Int height, Element e);

        /**
         * Release the value associated with the specified node.
         *
         * @param node    the node to un-associate the value from
         * @param height  the node height
         */
        void release(Int node, Int height);

        /**
         * Display a textual dump of the contents of the ElementStore.
         *
         * @param log   the log to dump to
         * @param desc  the short description of the ElementStore
         */
        void dump(Log log, String desc);
        }


    // ----- ElementStore: Null value only ---------------------------------------------------------

    /**
     * An implementation that stores the value `Null` very efficiently.
     */
    protected static const NullStore
            implements ElementStore<Nullable>
        {
        static NullStore INSTANCE = new NullStore();

        @Override Int height.get()                                  { return 0; }
        @Override void configure(IndexStore nodes, Int valueOffset) {}
        @Override Element load(Int node, Int height)                { return Null; }
        @Override void add(Int node, Int height, Element e)         {}
        @Override void replace(Int node, Int height, Element e)     {}
        @Override void release(Int node, Int height)                {}

        @Override void dump(Log log, String desc) {log.add($"{desc}=NullStore");}
        }


    // ----- AbstractStore -------------------------------------------------------------------------

    /**
     * Base class for a number of ElementStore implementation.
     */
    protected static class AbstractStore<Element, Index>
            implements ElementStore<Element, Index>
        {
        // ----- properties -------------------------------------------------------------------

        /**
         * This ExternalStore stores the index of each value in each node of the IndexStore.
         */
        protected IndexStore? nodes;

        /**
         * This ExternalStore stores the index of each value in each node of the IndexStore,
         * and at this value-offset within the node.
         */
        protected Int valueOffset;


        // ----- ElementStore interface -------------------------------------------------------

        @Override
        Int height.get()
            {
            return 1;
            }

        @Override
        void configure(IndexStore nodes, Int valueOffset)
            {
            this.nodes       = nodes;
            this.valueOffset = valueOffset;
            }

        @Override
        Element load(Int node, Int height);

        @Override
        void add(Int node, Int height, Element e);

        @Override
        void replace(Int node, Int height, Element e);

        @Override
        void release(Int node, Int height);

        @Override
        void dump(Log log, String desc)
            {
            log.add($"{desc}={&this.actualClass.name}<{Element}> valueOffset={valueOffset}, height={height}");
            }


        // ----- internal ---------------------------------------------------------------------

        /**
         * Get the index into the contents array of the external storage for the value being held
         * for the specified node. For a `Null` value, the index is `nil`.
         *
         * @param node    the node to get the value storage index out of
         * @param height  the node height
         *
         * @return the index into the contents array of the external storage, or `nil`
         */
        protected Int getIndex(Int node, Int height)
            {
            return nodes?.getValue(node, height, valueOffset) : assert;
            }

        /**
         * Update the index into the contents array of the external storage for the value being held
         * for the specified node. For a `Null` value, the index will be `nil`.
         *
         * @param node    the node to put the value storage index into
         * @param height  the node height
         * @param index   the index into the contents array of the external storage, or `nil`
         */
        protected void setIndex(Int node, Int height, Int index)
            {
            nodes?.setValue(node, height, valueOffset, index) : assert;
            }
        }


    // ----- SingleIndexStore: bits that squeeze inside an index -----------------------------------

    /**
     * Stores the small `Element` values tucked inside a single `Index` value.
     */
    protected static class SingleIndexStore<Element, Index>
            extends AbstractStore<Element, Index>
        {
        construct()
            {
            (toIndex, fromIndex) = switch (Element)
                {
                case Bit: (e -> e.toInt64(), i -> (i!=0).toBit());
                case Nullable|Bit: (e -> e?.toInt64() : -1, i -> (i == -1 ? Null : (i!=0).toBit()));

                case Boolean: (e -> e.toInt64(), i -> (i!=0));
                case Nullable|Boolean: (e -> e?.toInt64() : -1, i -> (i == -1 ? Null : i!=0));

                case Nibble: (e -> e.toInt64(), i -> i.toNibble());
                case Nullable|Nibble: (e -> e?.toInt64() : -1, i -> (i == -1 ? Null : i.toNibble()));

                default: assert as $"Unsupported type: {Element}";
                };
            }

        // ----- properties -------------------------------------------------------------------

        /**
         * Function to turn an element into an index.
         */
        protected function Int(Element) toIndex;

        /**
         * Function to turn an index into an element.
         */
        protected function Element(Int) fromIndex;


        // ----- ElementStore interface -------------------------------------------------------

        @Override
        Element load(Int node, Int height)
            {
            return fromIndex(getIndex(node, height));
            }

        @Override
        void add(Int node, Int height, Element e)
            {
            setIndex(node, height, toIndex(e));
            }

        @Override
        void replace(Int node, Int height, Element e)
            {
            setIndex(node, height, toIndex(e));
            }

        @Override
        void release(Int node, Int height)
            {
            // it is not necessary to clean up the slot, but debugging will be ugly if we don't
            setIndex(node, height, 0);
            }
        }


    // ----- CharStore: a character stored as a numeric --------------------------------------------

    /**
     * Stores the `Element` values as `Index` values, using a NumberStore to do the heavy lifting.
     */
    protected static class CharStore<Element, Index>
            implements ElementStore<Element, Index>
        {
        /**
         * The ElementStore that holds the code-points for this CharStore.
         */
        private NumberStore<UInt32, Index> actualStore = new NumberStore();


        // ----- ElementStore interface -------------------------------------------------------

        @Override
        Int height.get()
            {
            return actualStore.height;
            }

        @Override
        void configure(IndexStore nodes, Int valueOffset)
            {
            return actualStore.configure(nodes, valueOffset);
            }

        @Override
        Element load(Int node, Int height)
            {
            return fromCodepoint(actualStore.load(node, height));
            }

        @Override
        void add(Int node, Int height, Element e)
            {
            actualStore.add(node, height, toCodepoint(e));
            }

        @Override
        void replace(Int node, Int height, Element e)
            {
            actualStore.replace(node, height, toCodepoint(e));
            }

        @Override
        void release(Int node, Int height)
            {
            actualStore.release(node, height);
            }

        @Override
        void dump(Log log, String desc)
            {
            log.add($"{desc}={&this.actualClass.name}<{Element}>, actualStore:");
            actualStore.dump(log, desc);
            }


        // ----- internal ---------------------------------------------------------------------

        static UInt32 NullChar = 0x7FFEFFFF;

        private Element fromCodepoint(UInt32 n)
            {
            return (n == NullChar ? Null : new Char(n)).as(Element);
            }

        private UInt32 toCodepoint(Element e)
            {
            return e.is(Char)
                    ? e.codepoint
                    : NullChar;
            }
        }


    // ----- EnumStore: enumerated values ----------------------------------------------------------

    /**
     * Stores the enumerated `Element` values as `Index` values, using another store implementation
     * to do the heavy lifting.
     */
    protected static class EnumStore<Element, Index>
            implements ElementStore<Element, Index>
        {
        construct()
            {
            // determine the size of the enumeration
            Type enumType;
             if (Element.is(Type<Enum>))
                {
                enumType = Element;
                }
            else
                {
                assert enumType := isNullable(Element), enumType.is(Type<Enum>);
                }
            assert Class clz := enumType.fromClass();
            this.enumeration = clz.as(Enumeration);

            (actualStore, toStorageForm) = enumeration.count > 0x7F
                    ? (new NumberStore<Int16, Index>(), n -> n.toInt16())
                    : (new NumberStore<Int8, Index>(), n -> n.toInt8());
            }


        // ----- properties -------------------------------------------------------------------

        /**
         * The enumeration class handled by this EnumStore.
         */
        private Enumeration enumeration;

        /**
         * The ElementStore that holds the enum ordinals for this EnumStore.
         */
        private ElementStore<IntNumber> actualStore;

        /**
         * A function that converts to whatever the
         */
        private function IntNumber(Int) toStorageForm;



        // ----- ElementStore interface -------------------------------------------------------

        @Override
        Int height.get()
            {
            return actualStore.height;
            }

        @Override
        void configure(IndexStore nodes, Int valueOffset)
            {
            return actualStore.configure(nodes, valueOffset);
            }

        @Override
        Element load(Int node, Int height)
            {
            return fromOrdinal(actualStore.load(node, height));
            }

        @Override
        void add(Int node, Int height, Element e)
            {
            actualStore.add(node, height, toOrdinal(e));
            }

        @Override
        void replace(Int node, Int height, Element e)
            {
            actualStore.replace(node, height, toOrdinal(e));
            }

        @Override
        void release(Int node, Int height)
            {
            actualStore.release(node, height);
            }

        @Override
        void dump(Log log, String desc)
            {
            log.add($"{desc}={&this.actualClass.name}<{Element}>, enumeration={enumeration}, actualStore:");
            actualStore.dump(log, desc);
            }


        // ----- internal ---------------------------------------------------------------------

        static Int NullEnum = -1;

        private Element fromOrdinal(IntNumber n)
            {
            return (n == NullEnum ? Null : enumeration.values[n.toInt64()]).as(Element);
            }

        private IntNumber toOrdinal(Element e)
            {
            // reminder: Null itself is an enum, so look for it explicitly
            return toStorageForm(e?.as(Enum).ordinal : NullEnum);
            }
        }


    // ----- NumberStore: built-in numeric types ---------------------------------------------------

    /**
     * Stores the `Element` values as `Index` values, spreading across multiple index values if the
     * numeric type is too large to fit in one.
     */
    protected static class NumberStore<Element, Index>
            extends AbstractStore<Element, Index>
        {
        construct()
            {
            super();

            // determine the size of the numeric type
            Type numType;
            if (Element.is(Type<Number>))
                {
                numType  = Element;
                nullable = False;
                }
            else
                {
                assert numType := isNullable(Element), numType.is(Type<Number>);
                nullable = True;
                }
            this.numType = numType;

            assert bytesPerNum := numType.fixedByteLength();
            Int bytesPerIndex = IndexStore.sizeOf(Index) / 8;
            valueHeight = ((nullable ? bytesPerNum+1 : bytesPerNum) + bytesPerIndex-1) / bytesPerIndex;

            fromBytes = switch (numType)
                {
                case @Unchecked Int8    : a -> a.toInt8()   .toUnchecked().as(Element);
                case @Unchecked Int16   : a -> a.toInt16()  .toUnchecked().as(Element);
                case @Unchecked Int32   : a -> a.toInt32()  .toUnchecked().as(Element);
                case @Unchecked Int64   : a -> a.toInt64()  .toUnchecked().as(Element);
                case @Unchecked Int128  : a -> a.toInt128() .toUnchecked().as(Element);

                case Int8               : a -> a.toInt8()                 .as(Element);
                case Int16              : a -> a.toInt16()                .as(Element);
                case Int32              : a -> a.toInt32()                .as(Element);
                case Int64              : a -> a.toInt64()                .as(Element);
                case Int128             : a -> a.toInt128()               .as(Element);

                case @Unchecked UInt8   : a -> a.toUInt8()  .toUnchecked().as(Element);
                case @Unchecked UInt16  : a -> a.toUInt16() .toUnchecked().as(Element);
                case @Unchecked UInt32  : a -> a.toUInt32() .toUnchecked().as(Element);
                case @Unchecked UInt64  : a -> a.toUInt64() .toUnchecked().as(Element);
                case @Unchecked UInt128 : a -> a.toUInt128().toUnchecked().as(Element);

                case UInt8              : a -> a.toUInt8()                .as(Element);
                case UInt16             : a -> a.toUInt16()               .as(Element);
                case UInt32             : a -> a.toUInt32()               .as(Element);
                case UInt64             : a -> a.toUInt64()               .as(Element);
                case UInt128            : a -> a.toUInt128()              .as(Element);

                case Dec32              : a -> a.toDec32()                .as(Element);
                case Dec64              : a -> a.toDec64()                .as(Element);
                case Dec128             : a -> a.toDec128()               .as(Element);

                case BFloat16           : a -> a.toBFloat16()             .as(Element);
                case Float16            : a -> a.toFloat16()              .as(Element);
                case Float32            : a -> a.toFloat32()              .as(Element);
                case Float64            : a -> a.toFloat64()              .as(Element);
                case Float128           : a -> a.toFloat128()             .as(Element);

                default: assert as $"unsupported numeric type: {Element}";
                };
            }


        // ----- properties -------------------------------------------------------------------

        /**
         * The numeric type stored by the ElementStore.
         */
        Type numType;

        /**
         * True iff `Null` values are supported.
         */
        Boolean nullable;

        /**
         * The number of bytes required to hold a single Element value.
         */
        private Int bytesPerNum;

        /**
         * The byte index in the value section of the index node used to represent a Null value.
         */
        private Int nullByteIndex.get()
            {
            return nullable ? bytesPerNum : -1;
            }

        private static Byte nullByte = 0xFF;

        /**
         * The number of index elements used to store the value.
         */
        protected Int valueHeight;

        /**
         * The function that converts a sequence of bytes to the value.
         */
        function Element(Byte[]) fromBytes;


        // ----- ElementStore interface -------------------------------------------------------

        @Override
        Int height.get()
            {
            return valueHeight;
            }

        @Override
        Element load(Int node, Int height)
            {
            Byte[] storage = getBytes(node, height);
            if (nullByteIndex > 0 && storage[nullByteIndex] == nullByte)
                {
                return Null.as(Element);
                }

            return fromBytes(storage[0..bytesPerNum));
            }

        @Override
        void add(Int node, Int height, Element e)
            {
            replace(node, height, e);
            }

        @Override
        void replace(Int node, Int height, Element e)
            {
            Byte[] storage = getBytes(node, height);
            if (e == Null)
                {
                storage.fill(0);
                storage[nullByteIndex] = nullByte;
                }
            else
                {
                Byte[] newBytes = e.as(Number).toByteArray();
                assert newBytes.size == bytesPerNum;

                storage.replaceAll(0, newBytes);
                if (nullable)
                    {
                    storage[nullByteIndex] = 0;
                    }
                }
            }

        @Override
        void release(Int node, Int height)
            {
            getBytes(node, height).fill(0);
            }


        // ----- internal ---------------------------------------------------------------------

        /**
         * A helper to obtain the byte array (slice) that holds the value for a particular node.
         */
        Byte[] getBytes(Int node, Int height)
            {
            return nodes?.valueAsBytes(node, height, valueOffset, valueHeight) : assert;
            }
        }


    // ----- ExternalStore: Any object -------------------------------------------------------------

    /**
     * Converts `Element` references to `Index` values that can be stored in an `IndexStore`,
     * storing the `Element` references external to the `IndexStore` in a separate array.
     */
    protected static class ExternalStore<Element, Index>
            extends AbstractStore<Element, Index>
        {
        construct(Int initCapacity)
            {
            super();
            contents = new Array<Element|Int>(initCapacity);
            }


        // ----- properties -------------------------------------------------------------------

        /**
         * Combination element storage and free list. The free list is in the same storage as the
         * elements, as a linked list of `int`.
         */
        private (Element|Int)[] contents;

        /**
         * Head of the free list is in the same storage as the elements. The special value `-1` is
         * used as the tail of the list.
         */
        private Int firstFree = nil;

        /**
         * A reserved marker that means "none".
         */
        static Int nil = -1;


        // ----- ElementStore interface -------------------------------------------------------

        @Override
        Element load(Int node, Int height)
            {
            Int i = getIndex(node, height);
            return i == nil
                    ? Null.as(Element)
                    : contents[i].as(Element);
            }

        @Override
        void add(Int node, Int height, Element e)
            {
            Int i;
            if (e == Null)
                {
                i = nil;
                }
            else
                {
                i = firstFree;
                if (i == nil)
                    {
                    i = contents.size;
                    }
                else
                    {
                    firstFree = contents[firstFree].as(Int);
                    }

                contents[i] = e;
                }

            setIndex(node, height, i);
            }

        @Override
        void replace(Int node, Int height, Element e)
            {
            Int i = getIndex(node, height);
            switch (/* was-null */ i == nil, /* is-null */ e == Null)
                {
                case (False, False):
                    // just replace the old value with the new
                    contents[i] = e;
                    break;

                case (False, True):
                    release(node, height);
                    break;

                case (True, False):
                    add(node, height, e);
                    break;

                case (True, True):
                    // no change (Null == Null)
                    break;
                }
            }

        @Override
        void release(Int node, Int height)
            {
            Int i = getIndex(node, height);
            if (i != nil)
                {
                contents[i] = firstFree;
                firstFree   = i;
                setIndex(node, height, nil);
                }
            }

        @Override
        void dump(Log log, String desc)
            {
            log.add($"{desc}=ExternalStore<{Element}> firstFree={firstFree}");
            Loop: for (Element|Int e : contents)
                {
                log.add($"[{Loop.count}] {e}");
                }
            }
        }
    }
