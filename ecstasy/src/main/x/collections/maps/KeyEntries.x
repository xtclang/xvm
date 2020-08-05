/**
 * An implementation of the Set for the [Map.entries] property that delegates back to the map and
 * to the map's [Map.keys] set.
 */
class KeyEntries<Key, Value>(Map<Key, Value> contents)
        implements Set<Map<Key, Value>.Entry>
    {
    public/private Map<Key, Value> contents;

    @Override
    Int size.get()
        {
        return contents.size;
        }

    @Override
    Boolean empty.get()
        {
        return contents.empty;
        }

    @Override
    Iterator<Map<Key, Value>.Entry> iterator()
        {
        return new Iterator()
            {
            Iterator<Key> keyIterator = this.KeyEntries.contents.keys.iterator();

            @Override
            conditional Map<Key, Value>.Entry next()
                {
                if (Key key := keyIterator.next())
                    {
                    private CursorEntry<Key, Value> entry = new CursorEntry(this.KeyEntries.contents);
                    return true, entry.advance(key);
                    }

                return false;
                }
            };
        }

    @Override
    KeyEntries remove(Map<Key, Value>.Entry entry)
        {
        verifyMutable();
        contents.remove(entry.key, entry.value);
        return this;
        }

    @Override
    (KeyEntries, Int) removeAll(
            function Boolean (Map<Key, Value>.Entry) shouldRemove)
        {
        verifyMutable();

        CursorEntry<Key, Value> entry = new CursorEntry(contents);
        (_, Int removed) = contents.keys.removeAll(key -> shouldRemove(entry.advance(key)));

        return this, removed;
        }

    @Override
    KeyEntries clear()
        {
        verifyMutable();
        contents.clear();
        return this;
        }

    /**
     * Some operations require that the containing Map be mutable; this method throws an exception
     * if the Map is not mutable.
     *
     * @return True
     *
     * @throws ReadOnly if the Map is not mutable
     */
    protected Boolean verifyMutable()
        {
        if (!contents.inPlace)
            {
            throw new ReadOnly("Map operation requires inPlace == True");
            }
        return True;
        }
    }
