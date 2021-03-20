module TestSimple.test.org
    {
// TODO GG should this fail? it does, and I didn't think that it would
//    @Inject Console console;
//    Log log = new ConsoleLog(console);

// TODO GG should this fail? it does, and I didn't think that it would
//    static @Inject Console console;
//    static Log log = new ConsoleLog(console);

    @Inject Console console;

    @Lazy Log log.calc()
        {
        import ecstasy.io.ConsoleLog;
        return new ConsoleLog(console);
        }

    void run()
        {
//        LiteRnd rnd = new LiteRnd();
//        for (Int i = 1; i < 10; ++i)
//            {
//            console.println($"rnd[{i}]={rnd.next()}");
//            }

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

    /**
     * The SkipListMap is an OrderedMap implementation using a "Skip List" data structure.
     *
     * A skip list is a data structure that has logN average time for data retrieval, insertion,
     * update, and deletion. It behaves like a balanced binary tree, yet without the costs normally
     * associated with maintaining a balanced structure.
     *
     * TODO re-balance on iteration
     */
    class SkipListMap<Key extends Orderable, Value>
            implements OrderedMap<Key, Value>
        {
        // ----- constructors ---------------

        construct(Orderer? orderer = Null)
            {
            orderer     = orderer;
            compareKeys = orderer ?: (k1, k2) -> k1 <=> k2;
            rnd         = new LiteRnd(); // TODO GG this is a temporary work-around
            }
        finally
            {
            clear();
            }


        // ----- properties ---------------

        /**
         * Random number generator for SkipList "coin flips".
         */
        protected/private LiteRnd rnd; // TODO GG = new LiteRnd();

        /**
         * The orderer exposed as defined by the OrderedMap interface.
         */
        @Override
        public/private Orderer? orderer;

        /**
         * The orderer used internally.
         */
        protected/private Orderer compareKeys;

        /**
         * Storage for the nodes.
         */
        protected/private IndexStore? nodes;

        /**
         * Storage for the keys.
         */
        protected/private ElementStore<Key>? keyStore;

        /**
         * Storage for the values.
         */
        protected/private ElementStore<Value>? valueStore;

        /**
         * Modification count.
         */
        protected/private Int mods;


        // ----- Map interface ---------------

// TODO GG - uses interface method on Map instead of this field
//2021-03-19 11:29:39.312 Service "TestSimple.test.org" (id=0) contended @at <TestSimple.test.org>, fiber 3: Unhandled exception: UnsupportedOperation
//	at SkipListMap.keys.calc() (line=113, op=New_N)
//	at annotations.LazyVar.get() (line=43, op=Invoke_01)
//	at collections.Map.size.get() (line=41, op=L_Get)
//	at SkipListMap.empty.get() (line=91, op=IsEq)
//        @Override
//        public/protected Int size;
        @Override
        public/protected Int size
            {
            Int size_;
            @Override Int get() { return size_; }
            @Override void set(Int n) { size_ = n; }
            }

        @Override
        @RO Boolean empty.get()
            {
            return size == 0;
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
                return True, valueStore?.load(node, height)  : assert;
                }
            return False;
            }

        @Override
        @Lazy Set<Key> keys.calc()
            {
            TODO
            }

        @Override
        SkipListMap put(Key key, Value value)
            {

            do
                {
                IndexStore nodes    = ensureNodes();
                Boolean      upgraded = False;
                if ((Int node, Int height) := findNode(key))
                    {
                    valueStore?.replace(node, height, value) : assert;
                    }
                else
                    {
                    // flip a coin to figure out how much "height" the new node will have
                    height = nodes.maxHeight.minOf(rnd.next().trailingZeroCount+1);

                    // create the new node
                    if (node := nodes.alloc(height))
                        {
                        linkWorkTo(node, height);

                        // configure the node
                        keyStore?  .add(node, height, key  ) : assert;
                        valueStore?.add(node, height, value) : assert;

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

            ++mods;
            return this;
            }

        @Override
        SkipListMap remove(Key key)
            {
            if (!empty)
                {
                TODO
                }
            return this;
            }

        @Override
        SkipListMap clear()
            {
            nodes      = Null;
            keyStore   = Null;
            valueStore = Null;
            size       = 0;

            ++mods;
            return this;
            }


        // ----- OrderedMap ---------------

        @Override
        conditional Key first()
            {
            TODO
            }

        @Override
        conditional Key last()
            {
            TODO
            }

        @Override
        conditional Key next(Key key)
            {
            TODO
            }

        @Override
        conditional Key prev(Key key)
            {
            TODO
            }

        @Override
        conditional Key ceiling(Key key)
            {
            TODO
            }

        @Override
        conditional Key floor(Key key)
            {
            TODO
            }


        // ----- Sliceable ---------------

        @Override
        @Op("[..]") SkipListMap slice(Range<Key> indexes)
            {
            TODO
            }

        @Override
        SkipListMap reify()
            {
            TODO
            }


        // ----- internal ---------------

        /**
         * Find the node containing the specified key, and leave the work list pointing to the
         * node (or to where the node _would_ be, if it does not exist).
         *
         * @return True iff the key is in the map
         * @return (conditional) the node containing the key
         * @return (conditional) the height of the node
         */
        conditional (Int node, Int height) findNode(Key key)
            {
            IndexStore nodes      = ensureNodes();
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
                    switch (compareKeys(keyStore?.load(node, height) : assert, key))
                        {
                        case Lesser:
                            // the node's key comes before the desired key, so advance to the next
                            // node in this list
                            fromNode   = node;
                            fromHeight = height;
                            break;

                        case Equal:
                            // set the remainder of the work pointers to the node that precedes the
                            // node that we found
                            do
                                {
                                assert nodes.getIndex(fromNode, fromHeight, level) == node; // TODO assert:test
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
        void linkWorkTo(Int node, Int height)
            {
            IndexStore nodes = ensureNodes();
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
         * Return the IndexStore, or create one if none exists.
         *
         * @return an IndexStore
         */
        IndexStore ensureNodes()
            {
            IndexStore? nodes = this.nodes;
            if (nodes == Null)
                {
                ElementStore<Key>   keyStore   = createElementStore(Int8, Key);
                ElementStore<Value> valueStore = createElementStore(Int8, Value);

                nodes = createIndexStore(Int8, keyStore, valueStore);

                this.nodes      = nodes;
                this.keyStore   = keyStore;
                this.valueStore = valueStore;
                }
            return nodes;
            }

        /**
         * TODO
         */
        void upgradeNodes()
            {
            // determine the current size of the index, and double it
            ElementStore<Key>   oldKeys   = keyStore   ?: assert;
            ElementStore<Value> oldValues = valueStore ?: assert;
            IndexStore          oldNodes  = nodes      ?: assert;
            Int                 oldNil    = oldNodes.nil.toInt64();
// TODO GG assertion in Expression.java
//            Type<IntNumber>     newType   = switch (oldNodes.indexWidth)
//                {
//                case 8:  Type<Int16>;
//                case 16: Type<Int32>;
//                case 32: Type<Int64>;
//                case 64: // nothing bigger than this supported as a skiplist index type
//                default: assert;
//                }.as(Type<IntNumber>); // TODO GG why is this .as() needed?
            Type<IntNumber> newType  = switch (oldNodes.indexWidth)
                {
                case 8:  Int16.as(Type<IntNumber>); // TODO GG why is this .as() needed?
                case 16: Int32.as(Type<IntNumber>);
                case 32: Int64.as(Type<IntNumber>);
                case 64: // nothing bigger than this supported as a skiplist index type
                default: assert;
                };

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
log.add($"#{count}");
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

            this.nodes      = newNodes;
            this.keyStore   = newKeys;
            this.valueStore = newValues;
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
                return NullStore;
                }

            // TODO 80x From<8/16/32/64>To<Unchecked><U><8/16/32/64/128>
            // TODO Boolean Char enum
            // TODO signed/unsigned checked/unchecked Int 8/16/32/64/128

            return new ExternalStore<Element>();
            }

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
         * Dump the contents of the SkipListMap data structure.
         *
         * @param log
         * @param desc
         */
        void dump(Log log, String? desc = Null)
            {
            log.add($"SkipListMap<{Key}, {Value}>: {desc ?: ""}");
            nodes?.dump(log)                : log.add("No IndexStore");
            keyStore?.dump(log, "keys")     : log.add("No ElementStore for keys");
            valueStore?.dump(log, "values") : log.add("No ElementStore for values");
            }
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
     * TODO some of the asserts should probably be changed to assert:test
     */
    class IndexStore<Index extends IntNumber>
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
            elements.fill(nil, [0..3*maxHeight));

            // the last index of a node must be negative, and the headnode is treated as a node
            // (except that it has no "values")
            elements[2*maxHeight-1] = -nil;
            }

        /**
         * The underlying storage.
         */
        // TODO protected/private Index[] elements;
        Index[] elements;

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
        @Abstract @RO Index nil;

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

                elements.fill(nil, [node..node+size));
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
            assert toIndex(node) != nil; // TODO assert:test ?

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
            Index index = toIndex(n);
            assert i >= 0 && i < maxHeight && (index == nil || n >= 0 && n <= elements.size);  // TODO assert:test ?
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
            Index index = toIndex(n);
            assert i >= 0 && i < maxHeight && (index == nil || n >= 0 && n <= elements.size);  // TODO assert:test ?
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
    class IndexStore8(Int valueHeight)
            extends IndexStore<Int8>(valueHeight)
        {
        @Override Int  indexWidth.get() {return 8;}
        @Override Int8 nil       .get() {return Int8.maxvalue;}
        @Override Int8 toIndex(Int n)   {return n.toInt8();}
        }

    /**
     * A concrete implementation of IndexStore using 16-bit elements.
     */
    class IndexStore16(Int valueHeight)
            extends IndexStore<Int16>(valueHeight)
        {
        @Override Int   indexWidth.get() {return 16;}
        @Override Int16 nil       .get() {return Int16.maxvalue;}
        @Override Int16 toIndex(Int n)   {return n.toInt16();}
        }

    /**
     * A concrete implementation of IndexStore using 32-bit elements.
     */
    class IndexStore32(Int valueHeight)
            extends IndexStore<Int32>(valueHeight)
        {
        @Override Int   indexWidth.get() {return 32;}
        @Override Int32 nil       .get() {return Int32.maxvalue;}
        @Override Int32 toIndex(Int n)   {return n.toInt32();}
        }

    /**
     * A concrete implementation of IndexStore using 64-bit elements.
     */
    class IndexStore64(Int valueHeight)
            extends IndexStore<Int64>(valueHeight)
        {
        @Override Int   indexWidth.get() {return 64;}
        @Override Int64 nil       .get() {return Int64.maxvalue;}
        @Override Int64 toIndex(Int n)   {return n;}
        }


    // ----- ElementStore ------------------------------------------------------------------------

    /**
     * Represents a means of storing elements associated with nodes.
     */
    interface ElementStore<Element>
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
        @Override Int height.get()                                    { return 0; }
        @Override void configure(IndexStore nodes, Int valueOffset) {}
        @Override Element load(Int node, Int height)                  { return Null; }
        @Override void add(Int node, Int height, Element e)           {}
        @Override void replace(Int node, Int height, Element e)       {}
        @Override void release(Int node, Int height)                  {}

        @Override void dump(Log log, String desc) {log.add($"{desc}=NullStore");}
        }

    /**
     * Converts `Element` references to `Index` values that can be stored in an `IndexStore`,
     * storing the `Element` references external to the `IndexStore` in a separate array.
     */
    class ExternalStore<Element>
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


    // ----- LiteRnd random number generator -------------------------------------------------------

    /**
     * A lightweight implementation of a pseudo-random number generator. While more than adequate
     * for coin-flips in a skiplist, this algorithm is selected here only for its efficiency.
     */
    class LiteRnd
        {
        construct(UInt seed = 0)
            {
            if (seed == 0)
                {
                @Inject Clock clock;

                // TODO GG
                // @Unchecked Int128 picos = clock.now.epochPicos.toUnchecked();

                DateTime dt = clock.now;
                seed = (dt.date.epochDay ^ dt.time.picos).toUInt64();
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
    }