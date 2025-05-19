/**
 * A simple LRU cache implementation.
 */
service LRUCache<Key extends immutable Hashable, Value extends Shareable>(Int maxSize) {
    assert() {
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
    private Node? head;

    /**
     * The most recently used node.
     */
    private Node? tail;

    // ----- cache API -----------------------------------------------------------------------------

    /**
     * The number of entries (key/value pairs) in the cache.
     */
    Int size.get() = map.size;

    /**
     * Obtain the value associated with the specified key, iff that key is present in the cache. If
     * the key is not present in the map, then this method returns a conditional `False`.
     *
     * @param key  the key to look up in the cache
     *
     * @return a True iff the value associated with the specified key exists in the cache
     * @return (conditional) the value associated with the specified key
     */
    conditional Value get(Key key) {
        if (Node node := map.get(key)) {
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
    @Op("[]") Value? getOrNull(Key key) {
        if (Value value := get(key)) {
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
    @Op("[]=") void put(Key key, Value value) {
        if (Node node := map.get(key)) {
            node.value = value;
            touch(node);
        } else {
            Node node;
            if (map.size >= maxSize) {
                // recycle the least recently used node
                assert node ?= head;
                map.remove(node.key);
                unlink(node);
                node.key   = key;
                node.value = value;
            } else {
                node = new Node(key, value);
            }
            map.put(key, node);
            link(node);
        }
    }

    /**
     * Remove the specified key and any associated value from the cache.
     *
     * @param key  the key to remove from the cache
     */
    void remove(Key key) {
        if (Node node := map.get(key)) {
            map.remove(key);
            unlink(node);
        }
    }

    // ----- Node class ----------------------------------------------------------------------------

    /**
     * The class that holds a single cache entry.
     */
    protected static class Entry<Key, Value>(Key key, Value value, Entry? nextLRU = Null, Entry? prevLRU = Null);

    /**
     * An easier name for referring to `Entry<Key, Value>`.
     */
    protected typedef Entry<Key, Value> as Node;

    /**
     * Touch the [Node], making it the most recently used [Node].
     *
     * @param node  a [Node] that is already in the LRU cache
     */
    protected void touch(Node node) {
        // only move the node if the cache has more than one node, and this node is not already the
        // tail
        if (size > 1 && &node != &tail) {
            // unlink node from present position
            node.nextLRU?.prevLRU = node.prevLRU;
            node.prevLRU?.nextLRU = node.nextLRU;

            if (&node == &head) {
                head = node.nextLRU ?: assert;
            }

            // place the node on the end of the tail
            assert Node oldTail ?= tail;
            oldTail.nextLRU = node;
            tail            = node;
            node.prevLRU    = oldTail;
            node.nextLRU    = Null;
        }
    }

    /**
     * Add the [Node] into the LRU.
     *
     * @param node  the [Node] to add to the LRU cache
     */
    protected void link(Node node) {
        // place the node after the tail
        tail?.nextLRU = node;
        node.prevLRU  = tail;
        node.nextLRU  = Null;
        head        ?:= node;
        tail          = node;
    }

    /**
     * Unlink the [Node] from the LRU list.
     *
     * @param node  the [Node] to remove from the LRU list
     */
    protected void unlink(Node node) {
        node.nextLRU?.prevLRU = node.prevLRU;
        node.prevLRU?.nextLRU = node.nextLRU;

        if (&node == &head) {
            head = node.nextLRU;
        }

        if (&node == &tail) {
            tail = node.prevLRU;
        }
    }
}