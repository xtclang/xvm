module TestSimple.test.org
    {
    @Inject Console console;
    Log log = new ecstasy.io.ConsoleLog(console);

    void run()
        {
        SkipListMap<String, String> map = new SkipListMap();
        map.dump(log, "initial state");

        map.put("hello", "world");
        map.dump(log, "after put(hello,world)");

        console.println($"size={map.size}, empty={map.empty}, contains(hello)={map.contains("hello")}, hello={map["hello"]}");

        try
            {
            for (Int i : 0..100)
                {
                map.put($"key_{i}", $"val_{i}");
                }
            }
        catch (Exception e)
            {
            map.dump(log, $"after exception {e}");
            }

        map.dump(log, "after 100x");
        console.println($"size={map.size}, empty={map.empty}, contains(key_99)={map.contains("key_99")}, key_99={map["key_99"]}");
        }

// ----- start of skip list map-----
import ecstasy.collections.maps.EntryKeys;
import ecstasy.collections.maps.EntryValues;
import ecstasy.collections.maps.ReifiedEntry;

/**
 * The SkipListMap is an OrderedMap implementation using a "Skip List" data structure.
 *
 * A skip list is a data structure that has logN average time for data retrieval, insertion,
 * update, and deletion. It behaves like a balanced binary tree, yet without the costs normally
 * associated with maintaining a balanced structure.
 *
 * REVIEW re-balance on iteration?
 */
class SkipListMap<Key extends Orderable, Value>
        implements OrderedMap<Key, Value>
    {
    // ----- constructors ---------------

    construct(Orderer? orderer = Null)
        {
        this.orderer     = orderer;
        this.compareKeys = orderer ?: (k1, k2) -> k1 <=> k2;
        }
    finally
        {
        clear();
        }


    // ----- internal state ------------------------------------------------------------------------

    /**
     * The actual Orderer used internally.
     */
    protected/private Orderer compareKeys;

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
     * Structural modification count. Does not include non-structural modifications, such as an
     * entry value being replaced with another value.
     */
    protected/private Int modCount;


    // ----- Map interface ---------------

    @Override
    public/protected Int size;

    @Override
    @RO Boolean empty.get()
        {
        return size == 0;
        }

    @Override
    @Lazy public/private Set<Key> keys.calc()
        {
        return new KeySet();
        }

    @Override
    @Lazy public/private Collection<Value> values.calc()
        {
        return new EntryValues<Key, Value>(this);
        }

    @Override
    @Lazy public/private EntrySet entries.calc()
        {
        return new EntrySet();
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
    SkipListMap put(Key key, Value value)
        {
        do
            {
            IndexStore nodes    = ensureNodes();
            Boolean    upgraded = False;
            if ((Int node, Int height) := findNode(key))
                {
                valueStore.replace(node, height, value);
                }
            else
                {
                // flip a coin to figure out how much "height" the new node will have
                @Inject Random rnd;
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

        ++modCount;
        return this;
        }

    @Override
    SkipListMap remove(Key key)
        {
        if (!empty, (Int removeNode, Int removeHeight) := findNode(key))    // TODO find fully
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
    SkipListMap clear()
        {
        actualNodes  = Null;
        actualKeys   = Null;
        actualValues = Null;
        size         = 0;

        ++modCount;
        return this;
        }


    // ----- OrderedMap ---------------

    @Override
    public/private Orderer? orderer;

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

        Int node;
        Int height;
        if ((node, height) := findNode(key))
            {
            // the specified key does exist in the skip list map
            }
        else
            {
            // the work list contains the nodes that the key would be inserted after
            node   = nodes.getWork(0);
            height = nodes.heightOf(node);

            // there is always a valid insert-after node (although it might be the head node)
            assert node != nil;
            }

        // return the next node
        node   = nodes.getIndex(node, height, 0);
        height = nodes.heightOf(node);
        return node == nil
                ? False
                : (True, keyStore.load(node, height));
        }

    @Override
    conditional Key prev(Key key)
        {
        if (empty)
            {
            return False;
            }

        findNode(key, True);    // TODO find fully

        // the work list contains the nodes that precedes the key, or that the key would be inserted
        // key was not already in the map
        IndexStore nodes = this.nodes;
        Int        prev  = nodes.getWork(0);
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

        if (findNode(key))
            {
            // the specified key does exist in the skip list map
            return True, key;
            }

        // the work list contains the nodes that the key would be inserted after
        IndexStore nodes = this.nodes;
        Int        prev  = nodes.getWork(0);

        // return the next node
        Int next = nodes.getIndex(prev, nodes.heightOf(prev), 0);
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

        if (findNode(key))
            {
            // the specified key does exist in the skip list map
            return True, key;
            }

        // the work list contains the nodes that the key would be inserted after
        IndexStore nodes = this.nodes;
        Int        prev  = nodes.getWork(0);
        return prev == nodes.headNode
                ? False
                : (True, keyStore.load(prev, nodes.heightOf(prev)));
        }


    // ----- Sliceable ---------------

    @Override
    @Op("[..]") SkipListMap slice(Range<Key> indexes)
        {
        // TODO
        TODO need some sort of SliceableMap helper
        }


    // ----- KeySet implementation -----------------------------------------------------------------

    /**
     * A representation of all of the Keys in the Map.
     */
    protected class KeySet
            implements Set<Key>
        {
        @Override
        Int size.get()
            {
            return this.SkipListMap.size;
            }

        @Override
        Iterator<Key> iterator()
            {
            return new KeyIterator();
            }

        /**
         * An iterator over
         */
        protected class KeyIterator
                implements Iterator<Key>
            {
            construct()
                {
                prevNode      = nodes.headNode;
                prevHeight    = nodes.maxHeight;
                expectedCount = modCount;
                }

            construct(KeyIterator that)
                {
                this.prevNode      = that.prevNode;
                this.prevHeight    = that.prevHeight;
                this.prevKey       = that.prevKey;
                this.started       = that.started;
                this.finished      = that.finished;
                this.expectedCount = that.expectedCount;
                }

            protected/private Int     prevNode;
            protected/private Int     prevHeight;
            protected/private Key?    prevKey  = Null;
            protected/private Boolean started  = False;
            protected/private Boolean finished = False;

            /**
             * Expected modification count. If the map changes, we reset the iterator to start after
             * the previously iterated key.
             */
            protected/private Int expectedCount;

            @Override
            conditional Key next()
                {
                if (finished)
                    {
                    return False;
                    }

                IndexStore<> nodes = this.SkipListMap.nodes; // TODO GG try removing the "<>"
                Int          nil   = nodes.nil;

                // make sure that the skip list map's structure has not changed
                if (modCount != expectedCount)
                    {
                    if (started)
                        {
                        // iteration has already begun, so we have a record of the last key that we
                        // iterated; re-initialize this iterator to begin iterations after that key
                        if ((prevNode, prevHeight) := this.SkipListMap.findNode(prevKey ?: assert))
                            {
                            }
                        else
                            {
                            // we did not find the previous key, but the work area is set to the
                            // nodes after which that key would be inserted, so use those as a
                            // starting point
                            prevNode   = nodes.getWork(0);
                            prevHeight = nodes.heightOf(prevNode);
                            }
                        }
                    else
                        {
                        // we're still at the beginning, so no harm has been done
                        prevNode      = nodes.headNode;
                        prevHeight    = nodes.maxHeight;
                        }

                    // the iterator is now adjusted to be in sync with reality
                    expectedCount = modCount;
                    }

                // verify that the iterator can advance to the next node
                Int next = nodes.getIndex(prevNode, prevHeight, 0);
                if (next == nil)
                    {
                    prevKey  = Null;
                    finished = True;
                    return False;
                    }

                // store that next node off so that the iterator will know what it already iterated
                prevNode   = next;
                prevHeight = nodes.heightOf(next);
                Key key    = keyStore.load(prevNode, prevHeight);
                prevKey    = key;
                return True, key;
                }

            @Override
            Boolean knownDistinct()
                {
                return True;
                }

            @Override
            conditional Int knownSize()
                {
                return True, this.SkipListMap.size;
                }

            @Override
            (Iterator<Key>, Iterator<Key>) duplicate()
                {
                return new KeyIterator(this), new KeyIterator(this);
                }
            }

        @Override
        conditional Orderer? orderedBy()
            {
            return True, this.SkipListMap.orderer;
            }

        @Override
        Boolean contains(Key key)
            {
            return this.SkipListMap.contains(key);
            }

        @Override
        KeySet remove(Key key)
            {
            this.SkipListMap.remove(key);
            return this;
            }
        }


    // ----- EntrySet implementation ---------------------------------------------------------------

    /**
     * A representation of all of the Entry objects in the Map.
     */
    protected class EntrySet
            implements Collection<Entry>
        {
        @Override
        Int size.get()
            {
            return this.SkipListMap.size;
            }

        @Override
        Iterator<Entry> iterator()
            {
            return new EntryIterator();
            }

        /**
         * An iterator over
         */
        protected class EntryIterator
                implements Iterator<Entry>
            {
            construct()
                {
                prevNode      = nodes.headNode;
                prevHeight    = nodes.maxHeight;
                expectedCount = modCount;
                }

            construct(EntryIterator that)
                {
                this.prevNode      = that.prevNode;
                this.prevHeight    = that.prevHeight;
                this.prevKey       = that.prevKey;
                this.started       = that.started;
                this.finished      = that.finished;
                this.expectedCount = that.expectedCount;
                }

            protected/private Int     prevNode;
            protected/private Int     prevHeight;
            protected/private Key?    prevKey  = Null;
            protected/private Boolean started  = False;
            protected/private Boolean finished = False;

            /**
             * Expected modification count. If the map changes, we reset the iterator to start after
             * the previously iterated key.
             */
            protected/private Int expectedCount;

            /**
             * The fake entry that gets used over and over during iteration.
             */
            protected/private CursorEntry entry = new CursorEntry();

            @Override
            conditional Entry next()
                {
                if (finished)
                    {
                    return False;
                    }

                IndexStore<> nodes = this.SkipListMap.nodes; // TODO GG try removing the "<>"
                Int          nil   = nodes.nil;

                // make sure that the skip list map's structure has not changed
                if (modCount != expectedCount)
                    {
                    if (started)
                        {
                        // iteration has already begun, so we have a record of the last key that we
                        // iterated; re-initialize this iterator to begin iterations after that key
                        if ((prevNode, prevHeight) := this.SkipListMap.findNode(prevKey ?: assert))
                            {
                            }
                        else
                            {
                            // we did not find the previous key, but the work area is set to the
                            // nodes after which that key would be inserted, so use those as a
                            // starting point
                            prevNode   = nodes.getWork(0);
                            prevHeight = nodes.heightOf(prevNode);
                            }
                        }
                    else
                        {
                        // we're still at the beginning, so no harm has been done
                        prevNode      = nodes.headNode;
                        prevHeight    = nodes.maxHeight;
                        }

                    // the iterator is now adjusted to be in sync with reality
                    expectedCount = modCount;
                    }

                // verify that the iterator can advance to the next node
                Int next = nodes.getIndex(prevNode, prevHeight, 0);
                if (next == nil)
                    {
                    prevKey  = Null;
                    finished = True;
                    return False;
                    }

                // store that next node off so that the iterator will know what it already iterated
                prevNode   = next;
                prevHeight = nodes.heightOf(next);
                prevKey    = keyStore.load(prevNode, prevHeight);
                return True, entry.advance(prevKey?, prevNode, prevHeight) : assert;
                }

            @Override
            Boolean knownDistinct()
                {
                return True;
                }

            @Override
            conditional Int knownSize()
                {
                return True, this.SkipListMap.size;
                }

            @Override
            (Iterator<Entry>, Iterator<Entry>) duplicate()
                {
                return new EntryIterator(this), new EntryIterator(this);
                }
            }

        @Override
        EntrySet remove(Entry entry)
            {
            this.SkipListMap.remove(entry.key, entry.value);
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
            this.expectedCount = this.SkipListMap.modCount;
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
                    throw new OutOfBounds("entry does not exist for key=" + key);
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
                    this.SkipListMap.put(key, value);
                    exists = True;
                    ++expectedCount;
                    }
                }
            }

        @Override
        void delete()
            {
            if (exists)
                {
                this.SkipListMap.remove(key);
                exists = False;
                ++expectedCount;
                }
            }

        @Override
        Map<Key, Value>.Entry reify()
            {
            return new ReifiedEntry<Key, Value>(this.SkipListMap, key);
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Create an IndexStore that can be used to store the skiplist data, and configure the
     * key and value ElementStore objects to use the new IndexStore.
     *
     * @param indexType    the type for the Index stored in the IndexStore
     * @param keyStore     the storage for the keys of the SkipListMap
     * @param valueStore   the storage for the values of the SkipListMap
     *
     * @return the new storage for the nodes of the skiplist
     */
    IndexStore createIndexStore(
            Type<IntNumber>     indexType,
            ElementStore<Key>   keyStore,
            ElementStore<Value> valueStore,
            )
        {
        // calculate the total value height (in terms of number of "Index" values)
        Int valueHeight = keyStore.height + valueStore.height;

        // allocate the Index store
        IndexStore nodes = switch (indexType.DataType)
            {
            // TODO CP "case Int8:" makes it think that it's a literal
            case Int8  : new IndexStore8 (valueHeight);
            case Int16 : new IndexStore16(valueHeight);
            case Int32 : new IndexStore32(valueHeight);
            case Int64 : new IndexStore64(valueHeight);
            default: assert;
            };

        // bind the key and value stores to the new IndexStore
        keyStore.configure(nodes, 0);
        valueStore.configure(nodes, keyStore.height);

        return nodes;
        }

    /**
     * Find the node containing the specified key, and leave the work list pointing to the
     * node (or to where the node _would_ be, if it does not exist).
     *
     * @param key        the key to search for
     * @param findFully  pass True to identify (in the work area) the node that precedes the found
     *                   node at each and every level
     *
     * @return True iff the key is in the map
     * @return (conditional) the node containing the key
     * @return (conditional) the height of the node
     */
    protected conditional (Int node, Int height) findNode(Key key, Boolean findFully = False)
        {
        // TODO implement find fully vs. find fast option

        IndexStore nodes      = this.nodes;
        Int        fromNode   = nodes.headNode;
        Int        fromHeight = nodes.maxHeight;
        Int        nil        = nodes.nil.toInt64();
        Int        node       = nil;
        Int        height     = nil;
        Int        level      = fromHeight-1;
        do
            {
            node = nodes.getIndex(fromNode, fromHeight, level);
            if (node == nil)
                {
                // the "from node" is at the end of its linked list for this level; to find the
                // node we're looking for, drop to the next lower level and follow its linked
                // list
                nodes.setWork(level, fromNode);
                --level;
                }
            else
                {
                // compare the key from the node with the key being searched for
                height = nodes.heightOf(node);
                switch (compareKeys(keyStore.load(node, height), key))
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
                        do
                            {
                            Int nextNode = nodes.getIndex(fromNode, fromHeight, level);
                            while (nextNode != node)
                                {
                                fromNode = nextNode;
                                }
                            nodes.setWork(level, fromNode);
                            }
                        while (--level >= 0);
                        return True, node, height;

                    case Greater:
                        // the node's key comes after the key we're looking for, so we can't
                        // follow the current level's linked list any further; drop to the next
                        // level
                        nodes.setWork(level, fromNode);
                        --level;
                        break;
                    }
                }
            }
        while (level >= 0);

        return False;
        }

    /**
     * TODO doc
     *
     * @param node    the node index (its identity)
     * @param height  the height of the node, which is the number of indexes it holds
     */
    protected void linkWorkTo(Int node, Int height)
        {
        IndexStore nodes = this.nodes;
// TODO GG: for (Int level : [0..height))
        for (Int level : [0..height.toInt64()))
            {
            Int fromNode   = nodes.getWork(level);
            Int fromHeight = nodes.heightOf(fromNode);
            Int nextNode   = nodes.getIndex(fromNode, fromHeight, level);
            nodes.setIndex(fromNode, fromHeight, level, node);
            nodes.setIndex(node, height, level, nextNode);
            }
        }

    /**
     * TODO doc
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
            ElementStore<Key>   keyStore   = createElementStore(Int8, Key);
            ElementStore<Value> valueStore = createElementStore(Int8, Value);

            nodes = createIndexStore(Int8, keyStore, valueStore);

            this.actualNodes  = nodes;
            this.actualKeys   = keyStore;
            this.actualValues = valueStore;
            }
        return nodes;
        }

    /**
     * TODO
     */
    protected void upgradeNodes()
        {
        // determine the current size of the index, and double it
        ElementStore<Key>   oldKeys   = keyStore;
        ElementStore<Value> oldValues = valueStore;
        IndexStore          oldNodes  = nodes;
        Int                 oldNil    = oldNodes.nil.toInt64();
        Type<IntNumber>     newType   = switch (oldNodes.indexWidth)
            {
            case 8:  Int16;
            case 16: Int32;
            case 32: Int64;
            case 64: // nothing bigger than this supported as a skiplist index type
            default: assert;
            }; // TODO GG now it won't allow this redundant type assertion: .as(Type<IntNumber>);

        // create the new storage
        ElementStore<Key>   newKeys   = createElementStore(newType, Key);
        ElementStore<Value> newValues = createElementStore(newType, Value);
        IndexStore          newNodes  = createIndexStore(newType, newKeys, newValues);
        Int                 newNil    = newNodes.nil.toInt64();

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

        this.actualNodes  = newNodes;
        this.actualKeys   = newKeys;
        this.actualValues = newValues;
        }

    /**
     * Create an ElementStore that can be used to store the specified type of element.
     *
     * @param indexType    the type of the index that will be used in the IndexStore (probably
     *                     not yet instantiated)
     * @param objectType   the type of the element that will be stored in the ElementStore
     *
     * @return the new storage for the specified element type
     */
    <Element> ElementStore<Element> createElementStore(
            Type<IntNumber> indexType,
            Type<Element>   elementType,
            )
        {
        if (Element.is(Type<Nullable>)) // TODO GG: if (Element == Nullable)
            {
            return NullStore.INSTANCE;
            }

        // TODO 80x From<8/16/32/64>To<Unchecked><U><8/16/32/64/128>
        // TODO Boolean Char enum
        // TODO signed/unsigned checked/unchecked Int 8/16/32/64/128

        return new ExternalStore<Element>();
        }

    /**
     * Dump the contents of the SkipListMap data structure.
     *
     * @param log
     * @param desc
     */
    void dump(Log log, String? desc = Null)
        {
        log.add($"SkipListMap<{Key}, {Value}> size={size}: {desc ?: ""}");
        actualNodes?.dump(log)            : log.add("- No IndexStore");
        actualKeys?.dump(log, "keys")     : log.add("- No ElementStore for keys");
        actualValues?.dump(log, "values") : log.add("- No ElementStore for values");
        }


    // ----- IndexStore ------------------------------------------------------------------------

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
     * TODO some of the asserts should probably be changed to assert:test
     */
    static class IndexStore<Index extends IntNumber>
        {
        assert()
            {
            assert Index == Int8 || Index == Int16 || Index == Int32 || Index == Int64;
            }

        construct(Int valueHeight = 1)
            {
            assert valueHeight >= 1;

            this.valueHeight = valueHeight;
            this.elements    = new Index[]; // underlying storage is a growable array
            }
        finally
            {
            // worklist, headnode, and freelist are stored consecutively at the start of the array
            elements.fill(toIndex(nil), [0..3*maxHeight));

            // the last index of a node must be negative, and the headnode is treated as a node
            // (except that it has no "values")
            elements[2*maxHeight-1] = toIndex(-nil);
            }

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
            return indexWidth - 1;
            }

        /**
         * The number of bits in an index.
         */
        @Abstract @RO Int indexWidth;

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
            Int node     = elements[freeList].toInt64();
            if (node == nil.toInt64())
                {
                node = elements.size;
                if (node + size >= nil.toInt64())
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
            assert height > 0 && height <= maxHeight; // TODO assert:test ?

            // use the node to store the next free list item, then add it to the free list
            Int freeList       = this.freeList + height - 1;
            elements[node]     = elements[freeList];
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
            assert node != nil; // TODO assert:test ?

            if (node == headNode)
                {
                return maxHeight;
                }

            Int i = node;
            while (elements[i++].toInt64() >= 0)
                {
                }

            Int height = i - node;
            assert height <= maxHeight;  // TODO assert:test ?
            return height;
            }

        /**
         * Convert the specified `Int` value to an `Index`.
         *
         * @param n  an `Int` value
         *
         * @return the corresponding `Index` value
         */
        Index toIndex(Int n);

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
            assert node > 0 && height > 0 && i >= 0 && i < height;  // TODO assert:test ?
            Index index = elements[node+i];
            return (i < height-1 ? index : -index).toInt64();
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
            assert node > 0 && height > 0 && i >= 0 && i < height && index.toInt64() > 1;  // TODO assert:test ?
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
            assert node > 0 && height > 0 && i >= 0 && i < valueHeight; // TODO assert:test ?
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
            assert node > 0 && height > 0 && i >= 0 && i < valueHeight; // TODO assert:test ?
            elements[node+height+i] = toIndex(value);
            }

        /**
         * Obtain the worklist item.
         *
         * @param i  in the range `0 <= i < maxHeight`, where 0 is the lowest height (corresponding
         *           to the linked list of all nodes) and `maxHeight-1` is the highest height (such
         *           that the fewest nodes have this height)
         *
         * @return the previously stored worklist item at the specified height `i`
         */
        Int getWork(Int i)
            {
            assert i >= 0 && i < maxHeight;  // TODO assert:test ?
            return elements[i].toInt64();
            }

        /**
         * Store a worklist item.
         *
         * @param i  in the range `0 <= i < maxHeight`, where 0 is the lowest height (corresponding
         *           to the linked list of all nodes) and `maxHeight-1` is the highest height (such
         *           that the fewest nodes have this height)
         * @param n  the index to store in the worklist at the specified height `i`
         */
        void setWork(Int i, Int n)
            {
            assert i >= 0 && i < maxHeight && (n == nil || n >= 0 && n <= elements.size);  // TODO assert:test ?
            Index index = toIndex(n);
            elements[i] = index;
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
            assert i >= 0 && i < maxHeight;  // TODO assert:test ?
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
            assert i >= 0 && i < maxHeight && (n == nil || n >= 0 && n <= elements.size);  // TODO assert:test ?
            Index index = toIndex(n);
            elements[2*maxHeight+i] = index;
            }

        /**
         * Display a textual dump of the contents of the IndexStore.
         *
         * @param log  the log to dump to
         */
        void dump(Log log)
            {
            log.add(
                    $|IndexStore maxHeight={maxHeight}, headNode={headNode}, freeList={freeList},\
                     | indexWidth={indexWidth}, valueHeight={valueHeight}, nil={nil},\
                     | elements.size={elements.size}
                    );

            function String(Int) toStr = n -> n == nil.toInt64() ? "nil" : n.toString();

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
    static class IndexStore8(Int valueHeight)
            extends IndexStore<Int8>(valueHeight)
        {
        @Override Int  indexWidth.get() {return 8;}
        @Override Int  nil       .get() {return Int8.maxvalue;}
        @Override Int8 toIndex(Int n)   {return n.toInt8();}
        }

    /**
     * A concrete implementation of IndexStore using 16-bit elements.
     */
    static class IndexStore16(Int valueHeight)
            extends IndexStore<Int16>(valueHeight)
        {
        @Override Int   indexWidth.get() {return 16;}
        @Override Int   nil       .get() {return Int16.maxvalue;}
        @Override Int16 toIndex(Int n)   {return n.toInt16();}
        }

    /**
     * A concrete implementation of IndexStore using 32-bit elements.
     */
    static class IndexStore32(Int valueHeight)
            extends IndexStore<Int32>(valueHeight)
        {
        @Override Int   indexWidth.get() {return 32;}
        @Override Int   nil       .get() {return Int32.maxvalue;}
        @Override Int32 toIndex(Int n)   {return n.toInt32();}
        }

    /**
     * A concrete implementation of IndexStore using 64-bit elements.
     */
    static class IndexStore64(Int valueHeight)
            extends IndexStore<Int64>(valueHeight)
        {
        @Override Int   indexWidth.get() {return 64;}
        @Override Int   nil       .get() {return Int64.maxvalue;}
        @Override Int64 toIndex(Int n)   {return n;}
        }


    // ----- ElementStore ----------------------------------------------------------------------

    /**
     * Represents a means of storing elements associated with nodes.
     */
    static interface ElementStore<Element>
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

    /**
     * An implementation that stores the value `Null` very efficiently.
     */
    static const NullStore
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

    /**
     * Converts `Element` references to `Index` values that can be stored in an `IndexStore`,
     * storing the `Element` references external to the `IndexStore` in a separate array.
     */
    static class ExternalStore<Element>
            implements ElementStore<Element>
        {
        // ----- properties --------

        /**
         * Combination element storage and free list. The free list is in the same storage as the
         * elements, as a linked list of `int`.
         */
        private (Element|Int)[] contents = new (Element|Int)[];

        /**
         * Head of the free list is in the same storage as the elements. The special value `-1` is
         * used as the tail of the list.
         */
        private Int firstFree = nil;

        /**
         * This ExternalStore stores the index of each value in each node of the IndexStore.
         */
        private IndexStore? nodes;

        /**
         * This ExternalStore stores the index of each value in each node of the IndexStore,
         * and at this value-offset within the node.
         */
        private Int valueOffset;

        /**
         * A reserved marker that means "none".
         */
        static Int nil = -1;


        // ----- ElementStore interface --------

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
                    contents[i] = e /* TODO GG this is wrong */ .as(Element);
                    break;

                case (False, True):
                    release(node, height);
                    break;

                case (True, False):
                    add(node, height, e /* TODO GG this is wrong */ .as(Element));
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


        // ----- internal --------

        /**
         * Get the index into the contents array of the external storage for the value being held
         * for the specified node. For a `Null` value, the index is `nil`.
         *
         * @param node    the node to get the value storage index out of
         * @param height  the node height
         *
         * @return the index into the contents array of the external storage, or `nil`
         */
        Int getIndex(Int node, Int height)
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
        void setIndex(Int node, Int height, Int index)
            {
            nodes?.setValue(node, height, valueOffset, index) : assert;
            }
        }
    }
// ----- end of skip list map


// ----- LiteRnd random number generator -------------------------------------------------------
// TODO move this out into a class

/**
 * A lightweight implementation of the "xorshift*" pseudo-random number generator.
 */
class LiteRnd // TODO implement Random
    {
    construct(UInt seed = 0)
        {
        if (seed == 0)
            {
            @Inject Clock clock;

            @Unchecked Int128 picos = clock.now.epochPicos.toUnchecked();
            seed = picos.toUInt64() | (picos >>> 64).toUInt64();
            // TODO GG just for fun, try this instead: seed = (picos.toUInt64() | (picos >>> 64).toUInt64()).toUnchecked();
            if (seed == 0)
                {
                seed = 42; // RIP DNA
                }
            }

        n = seed.toUnchecked();
        }

    /**
     * The previous random value.
     */
    private @Unchecked UInt n;

    /**
     * Generate a new random value, using the "xorshift*" algorithm.
     *
     * @return the next random value
     */
    public UInt next()
        {
        @Unchecked UInt rnd = n;

        rnd ^= (rnd >> 12);
        rnd ^= (rnd << 25);
        rnd ^= (rnd >> 27);

        n = rnd;
        return rnd * 0x2545F4914F6CDD1D;
        }
    }
// -----
    }