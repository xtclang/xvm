/**
 * A simple LRU cache implementation.
 */
service LRUCache<Key extends immutable Hashable, Value extends Shareable>(Int maxSize)
    {
    assert()
        {
        assert:arg maxSize > 0;
        }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The underlying storage for the cache nodes.
     */
    private HashMap<Key, Node> map = new HashMap();

    /**
     * The least recently used node.
     */
    private Node? lruHead;

    /**
     * The most recently used node.
     */
    private Node? lruTail;


    // ----- cache API -----------------------------------------------------------------------------

    /**
     *The number of entries (key/value pairs) in the cache.
     */
    Int size.get()
        {
        return map.size;
        }

    /**
     * Obtain the value associated with the specified key, iff that key is present in the cache. If
     * the key is not present in the map, then this method returns a conditional `False`.
     *
     * @param key  the key to look up in the cache
     *
     * @return a True iff the value associated with the specified key exists in the cache
     * @return (conditional) the value associated with the specified key
     */
    conditional Value get(Key key)
        {
        if (Node node := map.get(key))
            {
            touch(node);
            return True, node.value;
            }

        return False;
        }

    /**
     * Obtain the value associated with the specified key, or the value `Null` if the key is
     * not present in the cache. This method supports the use of the `[]` operator:
     *
     *     value = cache[key];
     *
     * @param key  the key to look up in the cache
     *
     * @return the value for the associated key if it exists in the cache; otherwise Null
     */
    @Op("[]") Value? getOrNull(Key key)
        {
        if (Value value := get(key))
            {
            return value;
            }

        return Null;
        }

    /**
     * For an in-place Map, store a mapping of the specified key to the specified value, regardless
     * of whether that key is already present in the map. This method supports the use of the `[]`
     * operator:
     *
     *     map[key] = value;
     *
     * @param key    the key to store in the map
     * @param value  the value to associate with the specified key
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     */
    @Op("[]=") void put(Key key, Value value)
        {
        if (Node node := map.get(key))
            {
            node.value = value;
            touch(node);
            }
        else
            {
            if (map.size >= maxSize)
                {
                // recycle the least recently used node
                assert node ?= lruHead;
                map.remove(node.key);
                node.key   = key;
                node.value = value;
                }
            else
                {
                node = new Node(key, value);
                }
            map.put(key, node);
            touch(node);
            }
        }

    /**
     * Remove the specified key and any associated value from the cache.
     *
     * @param key  the key to remove from the cache
     */
    void remove(Key key)
        {
        if (Node node := map.get(key))
            {
            map.remove(key);
            unlink(node);
            }
        }


    // ----- Node class ----------------------------------------------------------------------------

    /**
     * The class that holds a single cache entry.
     */
    protected static class Entry<Key, Value>
            (Key key, Value value, Entry? nextLRU = Null, Entry? prevLRU = Null)
        {
        }

    /**
     * An easier name for referring to `Entry<Key,Value>`.
     */
    protected typedef Entry<Key, Value> as Node;

    /**
     * Touch the node, making it the most recently used node.
     *
     * This method will link the node into the LRU, if the node is not already linked.
     */
    protected void touch(Node node)
        {
        if (&node != &lruTail)
            {
            // unlink node from present position
            node.nextLRU?.prevLRU = node.prevLRU;
            node.prevLRU?.nextLRU = node.nextLRU;

            if (&node == &lruHead)
                {
                lruHead = node.nextLRU;
                }

            // place the node on the end of the tail
            lruTail?.nextLRU = node;
            node.prevLRU = lruTail;
            node.nextLRU = Null;
            lruTail = node;
            }
        }

    /**
     * Completely unlink the node from the LRU list.
     */
    protected void unlink(Node node)
        {
        node.nextLRU?.prevLRU = node.prevLRU;
        node.prevLRU?.nextLRU = node.nextLRU;

        if (&node == &lruHead)
            {
            lruHead = node.nextLRU;
            }

        if (&node == &lruTail)
            {
            lruTail = node.prevLRU;
            }
        }
    }