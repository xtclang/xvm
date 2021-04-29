module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
//        OrderedMap<Int, String> map1 = new SkiplistMap();
//
//        map1.put(1, "a");
//        map1.put(2, "b");
//        map1.put(3, "c");
//        //test(map1, Lesser);
//
//        OrderedMap<Int, String> map2 = map1[3..1];
//        console.println(map2);
//
//        // test(map2, Greater);
//        // TODO some test with an entry from map1 and an entry from map2 should blow up
//        }
//
//    void test(OrderedMap<Int, String> map, Ordered result)
//        {
//        @Unassigned OrderedMap<Int, String>.Entry prev;
//        Loop: for (val entry : map.entries)
//            {
//            if (!Loop.first)
//                {
//                console.println($"entry={entry}; prev={prev}; comp={prev<=>entry}");
//                }
//
//            prev = entry.reify();
//            }

        ParentImpl<String>   p = new ParentImpl(["a", "b"]);
        Parent<String>.Entry e = p.getEntry("a");
        console.println($"{e} -> {e.outer}");

        e = e.reify();
        console.println($"{e} -> {e.outer}");
        }

    interface Parent<Key>
        {
        Key[] keys;

        interface Entry
            {
            Entry reify()
                {
                return this;
                }
            @RO Key key;
            }
        }

    class ParentImpl<Key>(Key[] keys)
            implements Parent<Key>
        {
        Entry getEntry(Key key)
            {
            return new CursorEntry(key);
            }

        class CursorEntry(Key key)
                implements Entry
            {
            @Override
            Entry reify()
                {
                return reifyEntry(this);
                }
            }

        private Entry reifyEntry(Entry e)
            {
            // the Reified anonymous Entry must be a child of the Parent
            return new @Reified(e.key) Entry() {};
            }
        }

    mixin Reified<Key>
            into Parent<Key>.Entry
        {
        @Override
        Key key;

        construct(Key key)
            {
            this.key = key;
            }

        @Override
        String toString()
            {
            return "Reified: " + key + " on " + outer;
            }
        }
    }